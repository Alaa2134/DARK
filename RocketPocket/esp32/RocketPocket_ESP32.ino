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
const int LEFT_MOTOR_IN1  = 26;   // L298N IN1
const int LEFT_MOTOR_IN2  = 27;   // L298N IN2
const int LEFT_MOTOR_PWM  = 14;   // L298N ENA

const int RIGHT_MOTOR_IN3 = 33;   // L298N IN3
const int RIGHT_MOTOR_IN4 = 25;   // L298N IN4
const int RIGHT_MOTOR_PWM = 32;   // L298N ENB

// LEDC PWM channels (the ESP32 has no analogWrite).
const int LEFT_PWM_CHANNEL  = 0;
const int RIGHT_PWM_CHANNEL = 1;
const int PWM_FREQUENCY     = 1000;  // Hz
const int PWM_RESOLUTION    = 8;     // bits -> duty range 0..255

// ---------------------------------------------------------------------------------------------
// Speed
// ---------------------------------------------------------------------------------------------
const int DEFAULT_SPEED = 150;   // must match ControlViewModel.DEFAULT_SPEED
const int SPEED_STEP    = 15;    // must match ControlViewModel.SPEED_STEP
const int MIN_SPEED     = 0;
const int MAX_SPEED     = 255;

// How hard the inner wheel is driven during a curve. 40% makes the car arc; drop it towards
// 0 for a tighter turn, raise it towards 100 for a wider one.
const float CURVE_INNER_RATIO = 0.40f;

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
  int magnitude = constrain(abs(duty), 0, MAX_SPEED);
  digitalWrite(LEFT_MOTOR_IN1, duty > 0 ? HIGH : LOW);
  digitalWrite(LEFT_MOTOR_IN2, duty < 0 ? HIGH : LOW);
  ledcWrite(LEFT_PWM_CHANNEL, magnitude);
}

void driveRight(int duty) {
  int magnitude = constrain(abs(duty), 0, MAX_SPEED);
  digitalWrite(RIGHT_MOTOR_IN3, duty > 0 ? HIGH : LOW);
  digitalWrite(RIGHT_MOTOR_IN4, duty < 0 ? HIGH : LOW);
  ledcWrite(RIGHT_PWM_CHANNEL, magnitude);
}

void drive(int leftDuty, int rightDuty) {
  driveLeft(leftDuty);
  driveRight(rightDuty);
  isMoving = (leftDuty != 0 || rightDuty != 0);
}

void stopMotors() {
  drive(0, 0);
  isMoving = false;
}

int innerWheelDuty() {
  return (int)(currentSpeed * CURVE_INNER_RATIO);
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

    default:
      // Ignore newlines and anything unrecognised rather than reacting unpredictably.
      return;
  }

  Serial.print("RX: ");
  Serial.println(command);
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

  ledcSetup(LEFT_PWM_CHANNEL, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcAttachPin(LEFT_MOTOR_PWM, LEFT_PWM_CHANNEL);
  ledcSetup(RIGHT_PWM_CHANNEL, PWM_FREQUENCY, PWM_RESOLUTION);
  ledcAttachPin(RIGHT_MOTOR_PWM, RIGHT_PWM_CHANNEL);

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
