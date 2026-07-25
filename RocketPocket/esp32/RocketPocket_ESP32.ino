/*
 * Rocket Pocket — ESP32 Bluetooth Classic RC car firmware
 * Team Rocket Pocket
 *
 * Pairs with the Rocket Pocket Android app over Bluetooth Classic SPP and drives two
 * DC gear motors through an L298N H-bridge.
 *
 * Board:   ESP32 Dev Module (Bluetooth Classic — NOT an ESP32-S2/S3/C3, those are BLE only)
 * Library: BluetoothSerial (bundled with the Arduino ESP32 core)
 *
 * Protocol — one character per command, exactly what the app sends:
 *
 *   F  forward                 I  forward-right (curve)
 *   B  backward                G  forward-left  (curve)
 *   L  spin left               J  backward-right (curve)
 *   R  spin right              H  backward-left  (curve)
 *   S  stop
 *   +  speed up   (+15)
 *   -  speed down (-15)
 *   T  wiring self-test (drives one motor at a time — see the invert flags below)
 *
 * Sent back to the app:  SPEED:<0-255>\n
 */

#include "BluetoothSerial.h"

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth Classic is not enabled. Enable it in menuconfig, or pick an ESP32 board that supports it.
#endif

BluetoothSerial SerialBT;

// ---------------------------------------------------------------------------------------------
// Wiring — L298N motor driver
// ---------------------------------------------------------------------------------------------
// These are the pins the car is physically wired to, taken from the team's earlier Wi-Fi sketch
// (its "Motor A" is the left side, "Motor B" the right — its turnLeft() drives A backwards and B
// forwards, which only turns the car left if A is the left wheel).
const int LEFT_MOTOR_IN1  = 12;   // L298N IN1   (was Motor A)
const int LEFT_MOTOR_IN2  = 14;   // L298N IN2
const int LEFT_MOTOR_PWM  = 13;   // L298N ENA

const int RIGHT_MOTOR_IN3 = 27;   // L298N IN3   (was Motor B)
const int RIGHT_MOTOR_IN4 = 26;   // L298N IN4
const int RIGHT_MOTOR_PWM = 25;   // L298N ENB

// !! GPIO 12 is a strapping pin (MTDI). The ESP32 samples it at boot to pick the flash voltage,
// and it must read LOW at that moment. An L298N input normally floats low so this works, but if
// flashing ever fails with a checksum or timeout error, unplug the IN1 wire from GPIO 12, flash,
// then plug it back in.

// LEDC PWM channels (the ESP32 has no analogWrite). Only used by core 2.x — see below.
const int LEFT_PWM_CHANNEL  = 0;
const int RIGHT_PWM_CHANNEL = 1;
const int PWM_FREQUENCY     = 5000;  // Hz — matches the frequency the motors already ran at
const int PWM_RESOLUTION    = 8;     // bits -> duty range 0..255

// ---------------------------------------------------------------------------------------------
// LEDC compatibility
// ---------------------------------------------------------------------------------------------
// Arduino ESP32 core 3.x reworked the LEDC API: ledcSetup() + ledcAttachPin() were replaced by a
// single ledcAttach(pin, freq, resolution), and ledcWrite() now takes the *pin* instead of the
// channel. These two helpers keep the sketch compiling on both 2.x and 3.x — on cores older than
// 2.0.5, ESP_ARDUINO_VERSION_MAJOR is undefined and the preprocessor treats it as 0, which
// correctly selects the legacy branch.

static inline void pwmAttach(int pin, int channel) {
#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
  (void)channel;
  ledcAttach(pin, PWM_FREQUENCY, PWM_RESOLUTION);
#else
  ledcSetup(channel, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcAttachPin(pin, channel);
#endif
}

static inline void pwmWrite(int pin, int channel, int duty) {
#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
  (void)channel;
  ledcWrite(pin, duty);
#else
  (void)pin;
  ledcWrite(channel, duty);
#endif
}

// ---------------------------------------------------------------------------------------------
// Wiring corrections — fix a mis-wired car here instead of re-soldering
// ---------------------------------------------------------------------------------------------
// Send 'T' from the app (or the Serial Monitor) to run the self-test: it drives each motor
// forward then backward on its own and prints what it is doing, which tells you exactly which
// flag to flip.
//
//   Car drives backwards when you press Forward   -> set BOTH invert flags to true
//   Car spins on the spot when you press Forward  -> set ONE invert flag to true
//   Left and right are swapped                    -> set SWAP_MOTORS to true
const bool INVERT_LEFT_MOTOR  = false;
const bool INVERT_RIGHT_MOTOR = false;
const bool SWAP_MOTORS        = false;

// ---------------------------------------------------------------------------------------------
// Speed
// ---------------------------------------------------------------------------------------------
const int DEFAULT_SPEED = 150;   // must match ControlViewModel.DEFAULT_SPEED
const int SPEED_STEP    = 15;    // must match ControlViewModel.SPEED_STEP
const int MIN_SPEED     = 0;
const int MAX_SPEED     = 255;

// How hard the inner wheel is driven during a curve. Lower gives a tighter arc, higher a wider
// one. 55% keeps the inner wheel clearly turning rather than crawling.
const float CURVE_INNER_RATIO = 0.55f;

// A geared DC motor under load will not start below roughly this duty — it just buzzes and
// stalls. Curves clamp the inner wheel up to it, so a curve never leaves one side dead and
// pivoting around a stopped wheel instead of tracing an arc.
const int MIN_MOVE_DUTY = 80;

int currentSpeed = DEFAULT_SPEED;

// ---------------------------------------------------------------------------------------------
// Safety watchdog
// ---------------------------------------------------------------------------------------------
// While a button is held the app repeats the command every 300 ms, so a healthy link never
// goes quiet for long. If the phone drops out of range mid-corner the repeats stop, and one
// second of silence is taken as "the driver is gone" — cut the motors.
const unsigned long COMMAND_TIMEOUT_MS = 1000;
const unsigned long SPEED_BROADCAST_MS = 250;

unsigned long lastCommandAt   = 0;
unsigned long lastBroadcastAt = 0;
bool isMoving = false;

// ---------------------------------------------------------------------------------------------
// Motor primitives
// ---------------------------------------------------------------------------------------------

// Signed duty: positive drives forward, negative reverses, zero coasts.
void driveLeft(int duty) {
  if (INVERT_LEFT_MOTOR) duty = -duty;
  int magnitude = constrain(abs(duty), 0, MAX_SPEED);
  digitalWrite(LEFT_MOTOR_IN1, duty > 0 ? HIGH : LOW);
  digitalWrite(LEFT_MOTOR_IN2, duty < 0 ? HIGH : LOW);
  pwmWrite(LEFT_MOTOR_PWM, LEFT_PWM_CHANNEL, magnitude);
}

void driveRight(int duty) {
  if (INVERT_RIGHT_MOTOR) duty = -duty;
  int magnitude = constrain(abs(duty), 0, MAX_SPEED);
  digitalWrite(RIGHT_MOTOR_IN3, duty > 0 ? HIGH : LOW);
  digitalWrite(RIGHT_MOTOR_IN4, duty < 0 ? HIGH : LOW);
  pwmWrite(RIGHT_MOTOR_PWM, RIGHT_PWM_CHANNEL, magnitude);
}

void drive(int leftDuty, int rightDuty) {
  if (SWAP_MOTORS) {
    driveLeft(rightDuty);
    driveRight(leftDuty);
  } else {
    driveLeft(leftDuty);
    driveRight(rightDuty);
  }
  isMoving = (leftDuty != 0 || rightDuty != 0);

  // Printing the resulting duties next to the command makes a wrong turn obvious in one line.
  Serial.print("  -> L=");
  Serial.print(leftDuty);
  Serial.print(" R=");
  Serial.println(rightDuty);

  // The same line goes back over Bluetooth, where the app shows it in its RX strip. That turns
  // the phone into the serial monitor: if this text appears, the car received the command and
  // drove the motors, so anything still not moving is electrical rather than a lost command.
  SerialBT.print("ACK L=");
  SerialBT.print(leftDuty);
  SerialBT.print(" R=");
  SerialBT.println(rightDuty);
}

void stopMotors() {
  drive(0, 0);
  isMoving = false;
}

int innerWheelDuty() {
  int duty = (int)(currentSpeed * CURVE_INNER_RATIO);

  // Never hand the inner wheel a duty too small to actually turn it. At the default speed of
  // 150 the raw 55% is 82, but at lower speeds the ratio alone would fall under the motor's
  // starting threshold. Clamping up to MIN_MOVE_DUTY — capped by the current speed so the
  // inner wheel can never outrun the outer one — keeps both sides alive through the curve.
  if (duty < MIN_MOVE_DUTY) {
    duty = min(MIN_MOVE_DUTY, currentSpeed);
  }
  return duty;
}

// ---------------------------------------------------------------------------------------------
// Self-test
// ---------------------------------------------------------------------------------------------

// Drives one motor at a time so you can see which physical wheel responds and which way it
// turns. Run it with the car held off the ground, then set the invert/swap flags to match.
void runSelfTest() {
  const int testDuty = 150;

  Serial.println("SELF TEST: left motor FORWARD");
  drive(testDuty, 0);
  delay(1000);
  drive(0, 0);
  delay(400);

  Serial.println("SELF TEST: left motor BACKWARD");
  drive(-testDuty, 0);
  delay(1000);
  drive(0, 0);
  delay(400);

  Serial.println("SELF TEST: right motor FORWARD");
  drive(0, testDuty);
  delay(1000);
  drive(0, 0);
  delay(400);

  Serial.println("SELF TEST: right motor BACKWARD");
  drive(0, -testDuty);
  delay(1000);

  stopMotors();
  Serial.println("SELF TEST: done");
  Serial.println("  Wrong wheel moved      -> set SWAP_MOTORS = true");
  Serial.println("  Wheel turned backwards -> set that motor's INVERT flag to true");

  // The watchdog measures silence since the last command; this test blocked for ~5 s.
  lastCommandAt = millis();
}

// ---------------------------------------------------------------------------------------------
// Telemetry
// ---------------------------------------------------------------------------------------------

// The app treats the car as authoritative about its own PWM, so every change is announced.
void broadcastSpeed() {
  SerialBT.print("SPEED:");
  SerialBT.println(currentSpeed);
  lastBroadcastAt = millis();
}

void changeSpeed(int delta) {
  currentSpeed = constrain(currentSpeed + delta, MIN_SPEED, MAX_SPEED);
  broadcastSpeed();
  Serial.print("Speed -> ");
  Serial.println(currentSpeed);
}

// ---------------------------------------------------------------------------------------------
// Command handling
// ---------------------------------------------------------------------------------------------

void handleCommand(char command) {
  lastCommandAt = millis();

  // Printed before acting so it lands above the "-> L= R=" line that drive() emits.
  Serial.print("RX: ");
  Serial.println(command);

  switch (command) {
    case 'F':  // forward
      drive(currentSpeed, currentSpeed);
      break;

    case 'B':  // backward
      drive(-currentSpeed, -currentSpeed);
      break;

    case 'L':  // spin left in place
      drive(-currentSpeed, currentSpeed);
      break;

    case 'R':  // spin right in place
      drive(currentSpeed, -currentSpeed);
      break;

    case 'I':  // forward-right curve: right wheel slower
      drive(currentSpeed, innerWheelDuty());
      break;

    case 'G':  // forward-left curve: left wheel slower
      drive(innerWheelDuty(), currentSpeed);
      break;

    case 'J':  // backward-right curve
      drive(-currentSpeed, -innerWheelDuty());
      break;

    case 'H':  // backward-left curve
      drive(-innerWheelDuty(), -currentSpeed);
      break;

    case 'S':  // stop
      stopMotors();
      break;

    case '+':
      changeSpeed(SPEED_STEP);
      break;

    case '-':
      changeSpeed(-SPEED_STEP);
      break;

    case 'T':  // wiring self-test
    case 't':
      runSelfTest();
      return;

    default:
      // Ignore newlines and anything unrecognised rather than reacting unpredictably.
      return;
  }
}

// ---------------------------------------------------------------------------------------------
// Arduino entry points
// ---------------------------------------------------------------------------------------------

void setup() {
  Serial.begin(115200);

  pinMode(LEFT_MOTOR_IN1, OUTPUT);
  pinMode(LEFT_MOTOR_IN2, OUTPUT);
  pinMode(RIGHT_MOTOR_IN3, OUTPUT);
  pinMode(RIGHT_MOTOR_IN4, OUTPUT);

  pwmAttach(LEFT_MOTOR_PWM, LEFT_PWM_CHANNEL);
  pwmAttach(RIGHT_MOTOR_PWM, RIGHT_PWM_CHANNEL);

  stopMotors();

  // This name is what the Android app looks for in the paired-device list.
  SerialBT.begin("Rocket Pocket");

  lastCommandAt = millis();
  Serial.println("Rocket Pocket ready — pair with \"Rocket Pocket\"");
  broadcastSpeed();
}

void loop() {
  while (SerialBT.available()) {
    handleCommand((char)SerialBT.read());
  }

  unsigned long now = millis();

  // The phone is gone entirely — stop at once rather than waiting out the timeout.
  if (isMoving && !SerialBT.hasClient()) {
    stopMotors();
    Serial.println("Bluetooth client disconnected, motors stopped");
  }

  // Watchdog: a press that never got its matching 'S' must not run forever.
  if (isMoving && (now - lastCommandAt > COMMAND_TIMEOUT_MS)) {
    stopMotors();
    Serial.println("Watchdog: no command received, motors stopped");
  }

  // Keep the app's gauge live even when nothing is changing.
  if (now - lastBroadcastAt > SPEED_BROADCAST_MS) {
    broadcastSpeed();
  }
}
