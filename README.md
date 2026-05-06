# BT Audio Controller

Android app that connects to Bluetooth devices and plays audio via a web interface.

## What it does
- Lists all Bluetooth devices paired to your phone
- Connects/disconnects to A2DP devices (headphones, speakers)
- "Steals" connections from other devices
- Play audio files or streams
- Control via web browser on any device

## Build Instructions

### Option 1: GitHub Actions (Easiest)
1. Create a GitHub repository
2. Push this entire project to it
3. Go to Actions tab → Run workflow
4. Download the APK from the build artifacts

### Option 2: Build on PC (Linux/Mac/Windows with Android Studio)
```bash
cd bt-audio-app
./gradlew assembleDebug
# APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

### Option 3: Build on Linux PC (no Android Studio needed)
```bash
cd bt-audio-app
chmod +x build-apk.sh
./build-apk.sh
```

## Install
1. Copy the APK to your phone
2. Enable "Install unknown apps" for your file manager
3. Tap the APK to install
4. Open "BT Audio Controller"
5. Open http://localhost:5000 in your browser

## Permissions
The app requests Bluetooth permissions at runtime. Grant all permissions for full functionality.
