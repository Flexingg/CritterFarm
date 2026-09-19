# Emulator

An Android Virtual Device is set up on this machine so the app can be run and verified without a
physical phone.

**Verified working** on 2026-09-19: Android 15 (API 35), `x86_64`, KVM-accelerated, headless.
Boot took ~40 s; the signed release APK installed and launched with no crashes.

| Thing | Value |
|---|---|
| AVD name | `critterfarm_api35` |
| Device profile | `pixel_6` (1080 × 2400) |
| System image | `system-images;android-35;google_apis;x86_64` |
| SDK root | `/home/hermes/android-sdk` |
| KVM | `/dev/kvm` present → hardware acceleration |

> `google_apis` (not `google_apis_playstore`) is enough: Health Connect is present on this image
> (`com.google.android.healthconnect.controller`), and it needs no Play Store sign-in. It is
> **not** preinstalled on every image — if `pm list packages | grep health` comes back empty the
> app still runs and simply shows every zone as a Dormant Zone, which is itself worth testing.

## Boot it

Headless (what CI/agents should use — no window, still screenshots fine):

```bash
export HOME=/home/hermes
export ANDROID_HOME=/home/hermes/android-sdk
$ANDROID_HOME/emulator/emulator -avd critterfarm_api35 \
  -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -memory 2048 &
```

With a visible window (drop `-no-window`; a desktop session exists on `DISPLAY=:0`):

```bash
$ANDROID_HOME/emulator/emulator -avd critterfarm_api35 -gpu swiftshader_indirect
```

Wait for boot, then check:

```bash
export PATH="$ANDROID_HOME/platform-tools:$PATH"
adb wait-for-device
adb shell getprop sys.boot_completed    # -> 1
adb devices                             # -> emulator-5554  device
```

## Install and run the app

```bash
adb install -r /home/hermes/repos/CritterFarm/dist/CritterFarm-1.0-release.apk
adb shell am start -n com.critterfarm/.MainActivity
adb logcat -d | grep -iE "FATAL EXCEPTION|AndroidRuntime"   # crash check
```

## Screenshots without a window

```bash
adb exec-out screencap -p > shot.png
```

## Drive the UI from the command line

Compose has no stable view ids, so find elements from the uiautomator dump:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml
# then tap the middle of the node's bounds
adb shell input tap 540 1380
```

Two gotchas learned the hard way:

- **Snackbars last ~4 s.** Screenshot within ~1 s of a tap or you will "prove" a working button is
  dead. This app's Claim chest shows a snackbar when there is no health data yet.
- The app **persists `hasSeenOnboarding`**, so a warm relaunch goes straight to the farm. Force a
  clean run with `adb shell pm clear com.critterfarm` (wipes game state too) or
  `adb shell am force-stop com.critterfarm` (keeps it).

## Recreating the AVD from scratch

```bash
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVDM="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
yes | "$SDKM" --licenses
"$SDKM" "emulator" "system-images;android-35;google_apis;x86_64"
echo no | "$AVDM" create avd -n critterfarm_api35 -k "system-images;android-35;google_apis;x86_64" -d pixel_6
```

The emulator needs roughly 2 GB of RAM; check `free -h` before booting on this shared box.
