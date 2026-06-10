# Remote emulator + build server

A persistent headless Android emulator and build toolchain live on **panoramix**, reachable
from anywhere over Tailscale. You build on your Mac, the work runs on the server, and you
watch/interact with the emulator in a browser.

```
  Mac  ──Tailscale──>  panoramix
   │                     ├─ docker: android-emulator  (Android 14, KVM-accelerated)
   │                     │     • noVNC web view   :6080
   │                     │     • adb              :5555
   │                     └─ host: JDK 17 + Android SDK + repo clone  (server-side builds)
   └─ ./remote-build.sh ──rsync+ssh──> gradlew installDebug ──adb──> emulator
```

The ports are bound to panoramix's **Tailscale IP** (`100.98.75.10`), so they're reachable
across your tailnet but not exposed unauthenticated on the LAN.

## Connect to it

**Watch / tap the screen (browser):** <http://panoramix:6080> — the noVNC viewer. Click the
screen to interact; it's a full Android 14 device.

**adb from the Mac:**
```bash
adb connect panoramix:5555
adb devices                      # -> panoramix:5555  device
adb -s panoramix:5555 install some.apk
adb -s panoramix:5555 exec-out screencap -p > shot.png
```

## Build from the Mac

```bash
cd android
./remote-build.sh                 # rsync working tree -> build on panoramix -> install + launch
./remote-build.sh assembleDebug   # or any gradle task(s)
./remote-build.sh testDebugUnitTest
```

It syncs your **working tree** (uncommitted changes included), builds on panoramix's 8 cores,
and installs to the emulator. Watch the result at <http://panoramix:6080>. Overrides:
`GIFFER_REMOTE_HOST`, `GIFFER_EMU_ADB`.

## Start / stop the emulator

The container does **not** auto-start (restart policy `no`), so after a panoramix reboot you
bring it up yourself:

```bash
ssh panoramix docker start android-emulator   # start (emulator cold-boots in ~1 min)
ssh panoramix docker stop  android-emulator   # stop
ssh panoramix docker ps -a                    # status (include stopped)
ssh panoramix docker logs -f android-emulator # logs
ssh panoramix docker rm -f android-emulator   # tear down (loses installed apps/state)
```

Installed apps and emulator state persist across stop/start; only `docker rm` wipes them.
After `docker start`, wait for boot (`docker exec android-emulator adb shell getprop
sys.boot_completed` → `1`) before `adb connect panoramix:5555`. To make it auto-start on
reboot again: `docker update --restart=unless-stopped android-emulator`.

## One-time server setup (already done — kept here for reproducibility)

On panoramix (Ubuntu, x86_64, has `/dev/kvm` + Docker):

```bash
# --- emulator container (budtmo/docker-android) ---
# (restart policy left at the default 'no' — start/stop it manually; see "Start / stop")
# Nexus 4 (768x1280 @ 320dpi) + 4 cores / 4 GB: panoramix has no usable GPU for the emulator,
# so rendering is software (swiftshader) — a lower-res device is much faster. "Nexus 5"
# (1080x1920) is a higher-res alternative if you want more screen real estate over speed.
docker run -d --name android-emulator \
  -p 100.98.75.10:6080:6080 -p 100.98.75.10:5555:5555 \
  -e EMULATOR_DEVICE="Nexus 4" -e WEB_VNC=true \
  -e EMULATOR_ADDITIONAL_ARGS="-cores 4 -memory 4096 -no-boot-anim" \
  --device /dev/kvm \
  budtmo/docker-android:emulator_14.0

# --- host build toolchain ---
sudo apt-get install -y openjdk-17-jdk-headless unzip curl git
mkdir -p ~/android-sdk/cmdline-tools
curl -fsSL -o /tmp/clt.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip
unzip -q /tmp/clt.zip -d /tmp/clt && mv /tmp/clt/cmdline-tools ~/android-sdk/cmdline-tools/latest
export ANDROID_HOME=$HOME/android-sdk
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses
~/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"
git clone https://github.com/PeterFajner/giffer.git
printf 'sdk.dir=%s/android-sdk\n' "$HOME" > ~/giffer/android/local.properties
```

Requirements that made this work: panoramix is bare-metal x86_64 with `/dev/kvm` (KVM
acceleration), Docker, and Tailscale. To host it elsewhere, swap the Tailscale IP in the
`docker run` port bindings and in `remote-build.sh`'s `GIFFER_EMU_ADB` default.

## Notes

- This is a **separate emulator** from Firebase Test Lab (see [FIREBASE.md](FIREBASE.md)). Use
  this one for interactive hands-on testing (tap around, drag the crop/trim handles); use Test
  Lab for automated pass/fail runs on many device models.
- To load a Motion Photo into the emulator for manual testing:
  `adb -s panoramix:5555 push some-motion-photo.jpg /sdcard/Pictures/` then rescan with
  `adb -s panoramix:5555 shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/some-motion-photo.jpg`.
