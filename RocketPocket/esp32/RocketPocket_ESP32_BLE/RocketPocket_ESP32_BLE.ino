/*
 * Rocket Pocket — ESP32 BLE car firmware (for the iPhone / iPad app)
 * Team Rocket Pocket — Horus University of Egypt, Faculty of AI
 *
 * WHY THIS FILE EXISTS
 * --------------------
 * iOS does not let third-party apps use Bluetooth Classic (SPP) at all — only BLE, or Classic
 * through Apple's licensed MFi hardware. So the iPhone app cannot talk to the BluetoothSerial
 * sketch. This is the same car, the same pins and the exact same one-character protocol, but
 * carried over BLE instead.
 *
 *   Flash THIS sketch when driving from an iPhone or iPad.
 *   Flash RocketPocket_ESP32.ino when driving from Android.
 *
 * Board:   ESP32 Dev Module
 * Service: Nordic UART Service (NUS), the de-facto standard for serial-over-BLE
 *
 * Protocol — one character per command, identical to the Classic build:
 *
 *   F  forward                 I  forward-right (curve)
 *   B  backward                G  forward-left  (curve)
 *   L  spin left               J  backward-right (curve)
 *   R  spin right              H  backward-left  (curve)
 *   S  stop
 *   +  speed up   (+15)        -  speed down (-15)
 *   T  wiring self-test
 *
 * Sent back to the app:  SPEED:<0-255>  and  ACK L=<duty> R=<duty>
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>

#define DEVICE_NAME "Rocket Pocket"

// Nordic UART Service. "RX" and "TX" are named from the peripheral's point of view: the phone
// writes to RX, and the car notifies on TX.
#define NUS_SERVICE_UUID "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_RX_UUID      "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define NUS_TX_UUID      "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

BLECharacteristic *txCharacteristic = nullptr;
bool deviceConnected = false;

// ---------------------------------------------------------------------------------------------
// Wiring — L298N motor driver (identical to the Classic sketch)
// ---------------------------------------------------------------------------------------------
const int LEFT_MOTOR_IN1  = 12;   // L298N IN1
const int LEFT_MOTOR_IN2  = 14;   // L298N IN2
const int LEFT_MOTOR_PWM  = 13;   // L298N ENA

const int RIGHT_MOTOR_IN3 = 27;   // L298N IN3
const int RIGHT_MOTOR_IN4 = 26;   // L298N IN4
const int RIGHT_MOTOR_PWM = 25;   // L298N ENB

// !! GPIO 12 is a strapping pin (MTDI): the ESP32 samples it at boot to choose the flash
// voltage and it must read LOW then. If flashing fails with a checksum error, unplug the IN1
// wire from GPIO 12, flash, and reconnect it.

const int LEFT_PWM_CHANNEL  = 0;
const int RIGHT_PWM_CHANNEL = 1;
const int PWM_FREQUENCY     = 5000;
const int PWM_RESOLUTION    = 8;

// Fix a mis-wired car here rather than re-soldering. Send 'T' to run the self-test first.
const bool INVERT_LEFT_MOTOR  = false;
const bool INVERT_RIGHT_MOTOR = false;
const bool SWAP_MOTORS        = false;

const int DEFAULT_SPEED = 150;
const int SPEED_STEP    = 15;
const int MIN_SPEED     = 0;
const int MAX_SPEED     = 255;

const float CURVE_INNER_RATIO = 0.55f;
const int   MIN_MOVE_DUTY     = 80;

int currentSpeed = DEFAULT_SPEED;

const unsigned long COMMAND_TIMEOUT_MS = 1000;
const unsigned long SPEED_BROADCAST_MS = 250;

unsigned long lastCommandAt   = 0;
unsigned long lastBroadcastAt = 0;
bool isMoving = false;

// ---------------------------------------------------------------------------------------------
// LEDC compatibility — Arduino ESP32 core 2.x and 3.x
// ---------------------------------------------------------------------------------------------

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
// Sending back to the phone
// ---------------------------------------------------------------------------------------------

void notify(const String &line) {
  if (!deviceConnected || txCharacteristic == nullptr) return;
  String payload = line + "\n";
  txCharacteristic->setValue((uint8_t *)payload.c_str(), payload.length());
  txCharacteristic->notify();
}

// ---------------------------------------------------------------------------------------------
// Motor primitives
// ---------------------------------------------------------------------------------------------

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

  Serial.print("  -> L=");
  Serial.print(leftDuty);
  Serial.print(" R=");
  Serial.println(rightDuty);

  // Echoed to the phone so the app's RX strip can prove the command was received and acted on.
  notify("ACK L=" + String(leftDuty) + " R=" + String(rightDuty));
}

void stopMotors() {
  drive(0, 0);
  isMoving = false;
}

int innerWheelDuty() {
  int duty = (int)(currentSpeed * CURVE_INNER_RATIO);
  // Never hand the inner wheel a duty too small to actually turn it, or the car pivots around
  // a stalled wheel instead of tracing an arc.
  if (duty < MIN_MOVE_DUTY) duty = min(MIN_MOVE_DUTY, currentSpeed);
  return duty;
}

void broadcastSpeed() {
  notify("SPEED:" + String(currentSpeed));
  lastBroadcastAt = millis();
}

void changeSpeed(int delta) {
  currentSpeed = constrain(currentSpeed + delta, MIN_SPEED, MAX_SPEED);
  broadcastSpeed();
  Serial.print("Speed -> ");
  Serial.println(currentSpeed);
}

void runSelfTest() {
  const int testDuty = 150;

  Serial.println("SELF TEST: left motor FORWARD");
  drive(testDuty, 0);  delay(1000);
  drive(0, 0);         delay(400);

  Serial.println("SELF TEST: left motor BACKWARD");
  drive(-testDuty, 0); delay(1000);
  drive(0, 0);         delay(400);

  Serial.println("SELF TEST: right motor FORWARD");
  drive(0, testDuty);  delay(1000);
  drive(0, 0);         delay(400);

  Serial.println("SELF TEST: right motor BACKWARD");
  drive(0, -testDuty); delay(1000);

  stopMotors();
  Serial.println("SELF TEST: done");
  lastCommandAt = millis();
}

// ---------------------------------------------------------------------------------------------
// Command handling
// ---------------------------------------------------------------------------------------------

void handleCommand(char command) {
  lastCommandAt = millis();

  Serial.print("RX: ");
  Serial.println(command);

  switch (command) {
    case 'F': drive(currentSpeed, currentSpeed);              break;
    case 'B': drive(-currentSpeed, -currentSpeed);            break;
    case 'L': drive(-currentSpeed, currentSpeed);             break;
    case 'R': drive(currentSpeed, -currentSpeed);             break;
    case 'I': drive(currentSpeed, innerWheelDuty());          break;
    case 'G': drive(innerWheelDuty(), currentSpeed);          break;
    case 'J': drive(-currentSpeed, -innerWheelDuty());        break;
    case 'H': drive(-innerWheelDuty(), -currentSpeed);        break;
    case 'S': stopMotors();                                   break;
    case '+': changeSpeed(SPEED_STEP);                        break;
    case '-': changeSpeed(-SPEED_STEP);                       break;

    case 'T':
    case 't': runSelfTest();                                  break;

    default:
      // Ignore newlines and anything unrecognised.
      break;
  }
}

// ---------------------------------------------------------------------------------------------
// BLE callbacks
// ---------------------------------------------------------------------------------------------

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) override {
    deviceConnected = true;
    lastCommandAt = millis();
    Serial.println("Phone connected");
  }

  void onDisconnect(BLEServer *server) override {
    deviceConnected = false;
    Serial.println("Phone disconnected — stopping");
    stopMotors();
    // Advertise again so the car can be found without a power cycle.
    BLEDevice::startAdvertising();
  }
};

class RxCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    String value = characteristic->getValue().c_str();
    for (size_t i = 0; i < value.length(); i++) {
      handleCommand(value[i]);
    }
  }
};

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

  BLEDevice::init(DEVICE_NAME);
  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *service = server->createService(NUS_SERVICE_UUID);

  txCharacteristic = service->createCharacteristic(
    NUS_TX_UUID,
    BLECharacteristic::PROPERTY_NOTIFY
  );

  BLECharacteristic *rxCharacteristic = service->createCharacteristic(
    NUS_RX_UUID,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );
  rxCharacteristic->setCallbacks(new RxCallbacks());

  service->start();

  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(NUS_SERVICE_UUID);
  advertising->setScanResponse(true);
  BLEDevice::startAdvertising();

  lastCommandAt = millis();
  Serial.println("Rocket Pocket BLE ready — look for \"" DEVICE_NAME "\" in the iPhone app");
}

void loop() {
  unsigned long now = millis();

  // The phone is gone entirely — stop at once.
  if (isMoving && !deviceConnected) {
    stopMotors();
    Serial.println("Not connected, motors stopped");
  }

  // While a button is held the app repeats the command every 300 ms, so a healthy link never
  // goes quiet. A second of silence means the driver is gone: cut the motors.
  if (isMoving && (now - lastCommandAt > COMMAND_TIMEOUT_MS)) {
    stopMotors();
    Serial.println("Watchdog: no command received, motors stopped");
  }

  if (deviceConnected && (now - lastBroadcastAt > SPEED_BROADCAST_MS)) {
    broadcastSpeed();
  }

  delay(5);
}
