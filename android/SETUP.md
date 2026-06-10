# Minimal Android toolchain & emulator setup

For building/testing Giffer-Android without the full Android Studio IDE. Tailored to: Apple
Silicon macOS with limited disk, x86_64 Linux home servers (`idefix`, `panoramix`), no
physical Android phone. Background and citations are in [RESEARCH.md](RESEARCH.md).

## 1. Build SDK on the Mac (~1 GB)

```bash
# JDK 17 — required by the Android Gradle Plugin
brew install openjdk@17
set -gx JAVA_HOME (brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home  # fish

# Android command-line tools only (NOT the IDE):
# download commandlinetools-mac-*.zip from https://developer.android.com/studio
mkdir -p ~/android-sdk/cmdline-tools
unzip ~/Downloads/commandlinetools-mac-*.zip -d /tmp/clt
mv /tmp/clt/cmdline-tools ~/android-sdk/cmdline-tools/latest   # the 'latest/' layout is required

# fish env (add to ~/.config/fish/config.fish)
set -gx ANDROID_HOME ~/android-sdk
set -gx PATH $PATH $ANDROID_HOME/cmdline-tools/latest/bin $ANDROID_HOME/platform-tools

# install build packages + accept licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
sdkmanager --licenses
```

Then build with the Gradle wrapper (no separate Gradle install needed):

```bash
cd android
./gradlew assembleDebug         # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest     # unit tests
```

## 2. Run the emulator on a Linux server (keeps the ~1.5 GB image off the Mac)

On an **x86_64** server (idefix/panoramix) use **x86_64** system images so KVM accelerates
them:

```bash
emulator -accel-check          # confirm "KVM is installed and usable"
ls -l /dev/kvm                 # must exist and be readable by your user

sdkmanager "emulator" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n test35 -k "system-images;android-35;google_apis;x86_64" --device pixel_6
emulator -avd test35 -no-window -no-audio -no-boot-anim -gpu swiftshader
```

(`-gpu swiftshader_indirect` is deprecated; use `-gpu swiftshader` or `-gpu swangle`. KVM is
effectively required — a server that is itself a VM may lack it unless nested virt is on.)

### Deploy to the remote emulator from the Mac

The emulator binds adb to `127.0.0.1` on the server, so `adb connect idefix:5555` will NOT
work directly. Tunnel it over SSH:

```bash
ssh -N -L 5555:127.0.0.1:5555 you@idefix &     # run on the Mac
adb connect localhost:5555
./gradlew installDebug
```

Or just check out + build + run on the server itself (no cross-network adb).

## 3. No-install alternatives

- **Firebase Test Lab** — upload the APK + instrumented tests; runs on real/virtual cloud
  devices. Free Spark quota: 10 virtual + 5 physical runs/day. Zero local disk.
- **Gradle Managed Devices** with ATD images (`systemImageSource = "aosp-atd"`) for headless
  automated instrumented tests.
- **appetize.io** — interact with the APK in a browser.

## Disk budget summary

| Use | Footprint |
| --- | --- |
| Build only (Mac) | ~0.8–1 GB |
| + full local emulator (Mac, arm64 image) | ~4–6 GB |
| Emulator on Linux server (x86_64 image) | ~0 GB on the Mac |
