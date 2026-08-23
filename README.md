# MobiLine

An Android/Kotlin wheel-alignment measurement app, modeled on the Gyraline
G1/G2 concept: use the phone's onboard sensors to read camber and toe off a
wheel rim.

## What's here

```
app/src/main/java/com/example/gyrolinediy/
├── MainActivity.kt                 # Compose nav host: calibration -> home -> preflight -> camber/toe
├── sensors/
│   └── OrientationRepository.kt    # Sensor fusion, smoothing, calibration offset math
└── ui/
    ├── CalibrationScreen.kt        # Step 1: zero the device against a flat reference
    ├── HomeScreen.kt               # Mode picker: Camber / Toe / Recalibrate
    ├── PreflightScreen.kt          # Safety/accuracy checklist gating each measurement session
    ├── CamberScreen.kt             # Live camber readout + ballpark adjustment guidance
    └── ToeScreen.kt                # Rear-referenced toe capture + ballpark adjustment guidance
```

## How it works

1. **OrientationRepository** wraps Android's `TYPE_ROTATION_VECTOR` sensor
   (fused accelerometer + gyro + magnetometer where available) rather than
   raw gyro integration, so yaw doesn't drift — this matters a lot for toe
   measurement specifically.
2. **CalibrationScreen** uses a **two-position** calibration to find the
   phone's built-in tilt: you place the phone flat on a level surface and
   capture, then rotate it 180° in place and capture again. Half the
   difference between the two readings is the mount tilt (it reverses sign
   with the rotation), which is stored as a `CalibrationOffset`. From there
   you land on the **HomeScreen** to
   pick a mode.
3. **CamberScreen** subtracts the calibration offset from live roll
   readings, smooths them with a simple exponential moving average
   (`SmoothedValue`) to kill jitter, and shows green/red feedback based on
   how level the device is — mirroring the "diamond turns green" UX
   pattern from commercial tools.
4. **ToeScreen** works off yaw instead of roll. Critical: yaw is only a
   meaningful compass heading when the phone is held **flat** (screen facing
   up toward the sky) with the **top of the phone pointing toward the front
   of the car**, the same way on every wheel. A car's steel bends the
   phone's magnetic field *differently at each corner*, so absolute compass
   readings are not trustworthy parked next to a car. The app therefore
   reports two kinds of result:

   - **Total toe per axle (THE result to trust)** — the *direct*
     left-vs-right difference between the two wheels on the same axle. No
     body reference is involved: the two readings share the same hand hold
     and seconds, so the car's magnetic distortion is common-mode and
     cancels out. This is the number that does read correctly.
   - **Individual wheel toe (diagnostic only)** — each wheel compared
     against a body reference spot captured on the *same side* moments
     earlier (the commercial manual: "approximately 20 seconds... after
     refreshing the base": pinch-weld seam, rocker edge, or frame rail).
     Because the phone is hand-held, these are more sensitive to the
     phone's direction and are best treated as a rough cross-check.

   Both totals and individuals pass through a 180° **flip-correction**: a
   reading that lands beyond ±90° (impossible for real alignment) means the
   phone got rotated ~180° between the two measurements it compares, and
   the app rotates it back. The results screen also draws a **top-down tire
   graphic per wheel** (`res/drawable-nodpi/tire.png`), each rotated by
   that wheel's individual toe and exaggerated 15× so sub-2° angles are
   actually visible (front of the car points up; left wheels tilt clockwise
   for toe-in, right wheels counter-clockwise). If the tire art sits
   sideways for your asset, fix it once via `ToeVisualBaseRotation` in
   `ToeScreen.kt`. The sign convention (positive = toe-in) is a
   guess based on a typical mounting orientation — flip it in `ToeScreen.kt`
   if your physical rig reads backwards. Calibrate this against a vehicle
   you already know is tracking straight, once, and then trust it.
   Note: this rear-axle-average approach is a solid practical
   approximation, not a lab-grade "true centerline" — the commercial
   tool's more rigorous Body Align mode measures fixed points on the car's
   frame/body instead. Worth building later if you need tighter accuracy.
5. **PreflightScreen** is a checklist (level ground, steering centered,
   parking brake on, tires inflated, suspension settled) that has to be
   fully checked before either measurement mode is reachable. These
   conditions matter because the sensor only reports the wheel's angle
   relative to gravity/heading — if the car itself isn't in a consistent
   neutral state (steering turned, parked on a slope, suspension not
   settled), the reading will be numerically accurate but not represent
   the car's real alignment.
6. **Adjustment guidance** on both CamberScreen and ToeScreen interprets
   the saved reading against generic passenger-car norms (not a
   vehicle-specific spec — this app has no spec database) and names the
   general adjustment mechanism (tie rod ends for toe; camber
   bolts/shims/adjustable arms for camber, where the suspension supports
   it) without giving a "turn this many degrees" instruction, since that
   depends on the specific vehicle. Treat this as "does this look
   unusual," not a definitive verdict — a proper spec lookup and shop
   double-check are still the right move for anything beyond a rough
   ballpark check.

## Running it

Open the `GyrolineDIY/` folder directly in Android Studio (Koala or newer):
File → Open → select this folder. It will sync Gradle automatically.
Minimum SDK is 26 (Android 8.0), so it'll run on most phones from the last
several years. You'll need a real device (or an emulator with simulated
sensors) to test — the Android emulator does let you inject fake sensor
values under Extended Controls → Virtual Sensors.

## Suggested next steps (in order of difficulty)

1. **Persist calibration** — right now the offset resets every app launch.
   Use Jetpack DataStore to save it.
2. **Vehicle/session storage** — add a Room database to save readings per
   vehicle, similar to the "Recent Vehicles" concept in commercial tools.
3. **Caster** — hardest one. You'll capture camber at three steering
   positions (straight, turned left, turned right) and compute caster
   from the rate of camber change between them. This needs some trig
   worked out based on your specific measurement geometry — happy to help
   work through that math when you get there.
4. **Physical mount** — a 3D-printed or improvised bracket to hold the
   phone flat and consistently against the rim matters more for accuracy
   than any code change.

## No Gradle wrapper included

This zip intentionally omits the `gradlew` wrapper scripts/jar, since
generating them requires downloading the wrapper jar from
`services.gradle.org`, which isn't reachable from the environment that
built this. This is not a problem in practice: when you open the folder
in Android Studio, it will either prompt you to regenerate the wrapper
automatically, or just build using its own bundled Gradle version. If it
doesn't prompt, go to **File → Sync Project with Gradle Files** once.

## A note on accuracy

Phone accelerometers/gyros vary a lot in quality across devices. Expect
more noise than a dedicated MEMS unit like the commercial tools use. If you
find the built-in sensors too noisy for your purposes, an external IMU
breakout board (e.g. BNO055, which does its own sensor fusion in hardware)
connected over Bluetooth LE is a solid upgrade path without rewriting your
app's UI or math — just swap out what feeds `OrientationRepository`.
