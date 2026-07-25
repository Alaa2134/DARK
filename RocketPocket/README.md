# Rocket Pocket — Bluetooth Racing Car Controller

Android controller for the **Rocket Pocket** ESP32 robot car, built by **Team Rocket Pocket**.

Kotlin · Jetpack Compose · MVVM · Bluetooth Classic SPP (not BLE).

---

## 1. What the app does

- **Splash screen** — "Rocket Pocket / Bluetooth Racing Car" with the car logo, for two seconds.
- **Landscape dark racing dashboard** — locked to landscape, immersive full screen, screen kept awake.
- **Connection status** — red `DISCONNECTED`, orange `CONNECTING…`, green `CONNECTED`.
- **Eight-way drive pad** — forward, backward, left, right, and the four curves, with the big red
  **Emergency Stop** in the middle.
- **Live speedometer** — animated 0–255 PWM gauge, updated from the car itself.
- **Safety** — the car stops the moment you lift your finger, slide off a button, leave the app,
  or lose the Bluetooth link.

### Layout

```
┌──────────────────────────────────────────────────────────────┐
│ ROCKET POCKET             ● CONNECTED        [ DISCONNECT ]  │
│ Team Rocket Pocket                                           │
├──────────────────────────────┬───────────────────────────────┤
│    ↖         ↑         ↗     │            ╭───────╮          │
│  FWD LEFT  FORWARD  FWD RIGHT│          ╭╯   150   ╰╮        │
│                              │          │  PWM 0-255│        │
│    ←      ( STOP )      →    │          ╰╮         ╭╯        │
│   LEFT               RIGHT   │            ╰───────╯          │
│                              │                               │
│    ↙         ↓         ↘     │       [ − ]  STEP 15  [ + ]   │
│ BACK LEFT BACKWARD BACK RIGHT│                               │
└──────────────────────────────┴───────────────────────────────┘
```

---

## 2. Command protocol

One character per command — never a word, never a line.

| Button | Sends | Car behaviour |
|---|:---:|---|
| Forward | `F` | both wheels forward |
| Backward | `B` | both wheels backward |
| Left | `L` | spins left in place |
| Right | `R` | spins right in place |
| Fwd Right | `I` | forward curve to the right (right wheel slower) |
| Fwd Left | `G` | forward curve to the left (left wheel slower) |
| Back Right | `J` | reverse curve to the right |
| Back Left | `H` | reverse curve to the left |
| release / slide-off / Emergency Stop | `S` | stop |
| Speed `+` | `+` | PWM +15 |
| Speed `−` | `-` | PWM −15 |

Speed starts at **150**, steps by **15**, and is clamped to **0–255**.

**Car → app:** `SPEED:<0-255>\n` — the car is authoritative, and the gauge follows it.

### Keep-alive

While a direction button is held, the app repeats that character every **300 ms**. The firmware
treats one second of silence as "the driver is gone" and cuts the motors, so a phone that goes
out of range mid-corner stops the car instead of losing it.

---

## 3. Open and run in Android Studio

1. Install **Android Studio Ladybug (2024.2.1)** or newer.
2. **File → Open**, and select the `RocketPocket` folder (the one containing `settings.gradle.kts`)
   — not the repository root.
3. Let Gradle sync. On first sync it downloads AGP 8.7.3, Kotlin 2.0.21 and the Compose libraries,
   so allow a few minutes with a working internet connection.
4. If prompted, install **SDK Platform 35** and **Build Tools 35.0.0** via the SDK Manager.
5. Enable **Developer options → USB debugging** on your phone and connect it.
6. Pick your device in the toolbar and press **Run ▶**.

Requirements: **Android 7.0 (API 24)** or newer, with Bluetooth Classic. The app targets API 35 and
compiles against JDK 17.

---

## 4. Build an APK

**Debug APK** — no signing needed, ideal for competition day:

```bash
cd RocketPocket
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

In the IDE, the same thing lives under **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

**Release APK** — signed, for sharing with the team:

1. **Build → Generate Signed Bundle / APK…**
2. Choose **APK**, then **Create new…** to make a keystore (keep the `.jks` file and passwords safe;
   you cannot update the app later without them).
3. Pick the **release** build variant and finish.

Output: `app/build/outputs/apk/release/app-release.apk`

Copy the APK to the phone and open it; allow "Install unknown apps" for your file manager if asked.

---

## 5. Pair the phone with the ESP32

Bluetooth Classic requires the device to be **bonded in Android settings first** — the app only
lists devices that are already paired.

1. Flash `esp32/RocketPocket_ESP32.ino` to the ESP32:
   - Arduino IDE → **Boards Manager** → install **esp32 by Espressif Systems**.
   - **Tools → Board → ESP32 Dev Module**. Do *not* use an ESP32-S2/S3/C3: those have BLE only and
     no Bluetooth Classic, and the sketch will refuse to compile.
   - Select the port and press **Upload**.
2. Power the car. Open the Serial Monitor at **115200 baud** — you should see
   `Rocket Pocket ready — pair with "Rocket Pocket"`.
3. On the phone: **Settings → Bluetooth**, scan, and tap **Rocket Pocket**. Accept the pairing
   (PIN `1234` or `0000` if one is requested).
4. Open the app, tap **CONNECT BLUETOOTH**, and grant the permission prompt.
5. In the dialog, **Rocket Pocket** is detected automatically, pinned to the top with a green
   border and marked `THIS IS THE CAR`. Tap it.
6. The status pill turns green.

### Permissions

| Android version | Requested |
|---|---|
| 12 and newer (API 31+) | `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` — **no location prompt** |
| 11 and older (API ≤30) | `ACCESS_FINE_LOCATION`, which the old platform required for Bluetooth |

---

## 6. Wiring (L298N)

| ESP32 pin | L298N | Purpose |
|---:|---|---|
| GPIO 26 | IN1 | left motor direction A |
| GPIO 27 | IN2 | left motor direction B |
| GPIO 14 | ENA | left motor PWM |
| GPIO 33 | IN3 | right motor direction A |
| GPIO 25 | IN4 | right motor direction B |
| GPIO 32 | ENB | right motor PWM |
| GND | GND | **common ground — required** |

Power the motors from the battery pack through the L298N, not from the ESP32's 3.3 V pin. Tie the
battery ground, the L298N ground and the ESP32 ground together, or the signals will float.

Change the pin numbers at the top of the sketch if your build differs. If a wheel spins the wrong
way, swap that motor's two `IN` wires.

---

## 7. Testing each button

Keep the ESP32 Serial Monitor open at 115200 baud. Every accepted character is echoed as `RX: <c>`,
which is the quickest way to confirm the link end to end. **Put the car on a stand with the wheels
off the ground for the first pass.**

| # | Test | What to do | Expected |
|---|---|---|---|
| 1 | Splash | Launch the app | Logo + "Rocket Pocket" + "Bluetooth Racing Car", then the dashboard after 2 s |
| 2 | Disconnected guard | Press any direction before connecting | Snackbar: `Connect to Rocket Pocket first`, motors stay still |
| 3 | Connect | Tap **CONNECT BLUETOOTH** → Rocket Pocket | Pill: red → orange → green |
| 4 | Forward | Hold **FORWARD** | `RX: F`, both wheels forward |
| 5 | Backward | Hold **BACKWARD** | `RX: B`, both wheels backward |
| 6 | Left | Hold **LEFT** | `RX: L`, wheels counter-rotate, car spins left |
| 7 | Right | Hold **RIGHT** | `RX: R`, car spins right |
| 8 | Fwd Right | Hold **FWD RIGHT** | `RX: I`, left wheel full, right wheel slower — forward arc |
| 9 | Fwd Left | Hold **FWD LEFT** | `RX: G`, mirror of the above |
| 10 | Back Right | Hold **BACK RIGHT** | `RX: J`, reverse arc to the right |
| 11 | Back Left | Hold **BACK LEFT** | `RX: H`, reverse arc to the left |
| 12 | **Release** | Release any held button | `RX: S` immediately, wheels stop |
| 13 | **Slide off** | Hold a button, then slide your finger off it without lifting | `RX: S`, wheels stop — this is the ACTION_CANCEL path |
| 14 | Emergency Stop | While driving, hit the red centre button | `RX: S`, instant stop, snackbar `Emergency stop sent` |
| 15 | Speed up | Tap **+** | `RX: +`, gauge sweeps up by 15, needle and colour move |
| 16 | Speed down | Tap **−** | `RX: -`, gauge drops by 15 |
| 17 | Speed clamp | Tap **+** past 255 / **−** past 0 | Stops at 255 and 0, never wraps |
| 18 | Live gauge | Watch the gauge while idle | Tracks the car's `SPEED:` messages |
| 19 | **Home button** | Drive forward, then press Home | `RX: S`, car stops on `onPause`/`onStop` |
| 20 | **Out of range** | Drive forward, then walk the phone out of range | Keep-alive stops; within ~1 s the watchdog cuts the motors and logs `Watchdog: no command received` |
| 21 | Connection lost | Power the car off while connected | Snackbar `Bluetooth Connection Lost`, pill turns red |
| 22 | Disconnect | Tap **DISCONNECT** | `RX: S` then the link closes, pill red |
| 23 | Reconnect | Tap **CONNECT BLUETOOTH** again | Reconnects cleanly without restarting the app |

---

## 8. Project structure

```
RocketPocket/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/rocketpocket/car/
│   │   ├── MainActivity.kt                    lifecycle stop hooks, immersive landscape
│   │   ├── navigation/AppNavigation.kt        splash → control
│   │   ├── bluetooth/
│   │   │   ├── Command.kt                     the protocol characters
│   │   │   ├── ConnectionState.kt             disconnected / connecting / connected
│   │   │   ├── PairedDevice.kt                bonded device + auto-detection
│   │   │   └── BluetoothController.kt         sockets, streams, reader loop, teardown
│   │   ├── viewmodel/ControlViewModel.kt      state, safety gate, speed, keep-alive
│   │   └── ui/
│   │       ├── screens/{SplashScreen,ControlScreen}.kt
│   │       ├── components/{ControlButton,DirectionPad,Speedometer,
│   │       │               SpeedPanel,ConnectionStatusPill,BluetoothDeviceDialog}.kt
│   │       └── theme/{Color,Theme,Type}.kt
│   └── res/                                   strings, theme, vector logo, launcher icons
├── esp32/RocketPocket_ESP32.ino               matching car firmware
└── gradle/libs.versions.toml                  version catalogue
```

### How the safety rules are implemented

- **Press / release / cancel** — `ControlButton` uses `awaitFirstDown()` for `ACTION_DOWN` and
  `waitForUpOrCancellation()`, which covers `ACTION_UP` (finger lifted) *and* `ACTION_CANCEL`
  (finger slid off). Both outcomes call the same release handler, so no gesture leaves the car driving.
- **No movement while disconnected** — `BluetoothController.send()` returns false unless the state is
  `CONNECTED`, and `ControlViewModel` answers a blocked press with `Connect to Rocket Pocket first`.
- **Leaving the app** — `MainActivity.onPause`/`onStop` send `S`, and `ControlScreen` registers its own
  lifecycle observer as a second line of defence. `onDestroy` writes `S` synchronously *before*
  closing the socket, so the byte actually leaves the phone.
- **Losing the link** — a read or write failure flips the state to disconnected and raises
  `Bluetooth Connection Lost`; input stream, output stream and socket are each closed in their own
  `try`/`catch` so one failure cannot skip the rest.

---

## 9. The car moves the wrong way — fixing it in one line

Wrong movement is almost always wiring, not code. Two tools make it a two-minute fix.

### The TX / RX strip in the app

The dashboard header shows the last byte sent (**TX**) and the last line received (**RX**). Press
a button and read TX:

- **TX shows the right character** (`F` for Forward, `I` for Fwd Right, and so on) — the app is
  fine, the fault is in the wiring. Use the self-test below.
- **TX does not change** — the press is not registering, or you are not connected.

This means you never need a laptop to tell an app problem from a car problem.

### The `T` self-test

Hold the car with the **wheels off the ground**, open the Serial Monitor at 115200, and send `T`.
Each motor runs forward then backward on its own:

```
SELF TEST: left motor FORWARD
SELF TEST: left motor BACKWARD
SELF TEST: right motor FORWARD
SELF TEST: right motor BACKWARD
```

Watch which wheel moves and which way, then set the flags at the top of the sketch:

| What you saw | Fix |
|---|---|
| Press Forward, car drives backwards | `INVERT_LEFT_MOTOR = true` **and** `INVERT_RIGHT_MOTOR = true` |
| Press Forward, car spins on the spot | Set the invert flag for whichever motor turned the wrong way |
| Left and right are swapped | `SWAP_MOTORS = true` |
| The wrong wheel moved during the test | `SWAP_MOTORS = true` |

Re-flash and re-test. Every command also prints the resulting duties, so a wrong turn is visible
in one line:

```
RX: I
  -> L=150 R=60
```

`L=150 R=60` means the left wheel is driving harder than the right, so the car curves to the
right — which is what `I` (Fwd Right) should do.

---

## 10. Troubleshooting

| Symptom | Fix |
|---|---|
| Car missing from the dialog | Pair it in Android Bluetooth settings first — the app only lists bonded devices |
| `Connection failed` | Power-cycle the ESP32; make sure no other phone holds the connection (SPP allows only one) |
| Connects then drops instantly | Check the common ground and that the motor battery is not browning out the ESP32 |
| Buttons do nothing, pill is green | Confirm the sketch is the one in `esp32/`, and the Serial Monitor is at 115200 |
| Car drives the wrong way | Run the `T` self-test and set the invert/swap flags — see §9 |
| Some buttons do not respond | Fixed: a queue of snackbars used to cover the bottom button row. Update to the latest build |
| Curves too tight or too wide | Adjust `CURVE_INNER_RATIO` in the sketch (0.0 = pivot, 1.0 = straight) |
| Sketch will not compile | You are on an ESP32-S2/S3/C3 — those have no Bluetooth Classic. Use a classic ESP32 |
| Car keeps stopping while held | The link is dropping packets; check battery level and distance |
