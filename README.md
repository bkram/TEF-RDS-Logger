# TEF RDS Logger

Android Compose app for capturing and decoding RDS Spy frames from TEF6686_ESP32 Wi‑Fi receivers (TCP 7373) and saving them to `.spy` logs.

Based on some initial code and idea from Andreas Mikula. 

## Requirements
- JDK 21 (Gradle wrapper managed)
- Android SDK platform **36.1** and build-tools **36.1.0**
- minSdk 28, target/compileSdk 36

## Build
```bash
./gradlew --version          # primes wrapper
./gradlew :app:assembleDebug # produces app/build/outputs/apk/debug/app-debug.apk
```

## Usage
1) Launch the app on a device on the same network as the TEF6686_ESP32 receiver.  
2) Enter the receiver IP (port 7373 RDS Spy mode) and tap **Start**.  
3) Switch to **Capture** to view incoming hex blocks and **Decode** for parsed services.  
4) Stop capture, then tap **Save** to write a timestamped `.spy` log.

## Logging behavior
- The app keeps every frame received during a session in memory; the UI shows the latest 1,000 lines for readability.
- Saving writes the full session to a `.spy` file (no truncation) in the app’s external Downloads directory, with a timestamped name like `<pi-or-rds>_YYYYMMDD_HHmmss.spy`; it falls back to internal storage if external isn’t available.
- Clearing or starting a new session resets the in-memory log and decode state.
