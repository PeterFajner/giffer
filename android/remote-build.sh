#!/usr/bin/env bash
# Build Giffer on the remote server and install to its emulator.
#
# Syncs the local working tree up first (committed or not), so you can iterate
# without committing. Requires the one-time server setup in REMOTE.md.
#
# Usage:
#   ./remote-build.sh                 # build + install debug APK, launch it
#   ./remote-build.sh assembleDebug   # any gradle task(s) instead of installDebug
#
# Env overrides:
#   GIFFER_REMOTE_HOST   ssh host of the build server      (default: panoramix)
#   GIFFER_EMU_ADB       host:port of the emulator's adb   (default: 100.98.75.10:5555)
set -euo pipefail

HOST="${GIFFER_REMOTE_HOST:-panoramix}"
EMU_ADB="${GIFFER_EMU_ADB:-100.98.75.10:5555}"
REMOTE_DIR="giffer/android"
TASKS="${*:-installDebug}"

cd "$(dirname "$0")"

echo "==> Syncing working tree to $HOST:$REMOTE_DIR"
rsync -az --delete \
  --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' \
  --exclude 'app/build/' --exclude 'local.properties' \
  --exclude 'app/src/androidTest/assets/motionphoto/' \
  ./ "$HOST:$REMOTE_DIR/"

echo "==> Running 'gradlew $TASKS' on $HOST"
ssh "$HOST" "bash -lc '
  set -e
  cd ~/$REMOTE_DIR
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
  export ANDROID_HOME=\$HOME/android-sdk
  export PATH=\$ANDROID_HOME/platform-tools:\$PATH
  adb connect $EMU_ADB >/dev/null 2>&1 || true
  ./gradlew $TASKS
  if printf %s \"$TASKS\" | grep -q installDebug; then
    adb -s $EMU_ADB shell monkey -p com.leaptools.giffer -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  fi
'"

echo "==> Done. Watch the emulator at http://$HOST:6080"
