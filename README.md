# Nearby Connections API sample, Android Things <-> Android

- Import the project on Android Studio
- Install `things-app` in an Android Things device, granting permissions
```bash
./gradlew :things-app:assembleDebug
adb install -g things-app/build/outputs/apk/things-app-debug.apk
```
- Run `things-app` clicking on the "run" button on Android Studio, or via:
```bash
adb shell am start com.example.nearby.things/.MainActivity
```
- Install `mobile-app` in an Android device (e.g. phone), granting permissions
```bash
./gradlew :mobile-app:assembleDebug
adb install -g mobile-app/build/outputs/apk/mobile-app-debug.apk
```
- Run `mobile-app` clicking on the "run" button on Android Studio, or via:
```bash
adb shell am start com.example.nearby.companion/.MainActivity
```
