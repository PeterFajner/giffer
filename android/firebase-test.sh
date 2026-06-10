#!/usr/bin/env bash
# Build the app + instrumentation APKs ON the remote server (panoramix), pull them back, and
# run them on Firebase Test Lab from this machine (gcloud lives here, the build toolchain
# lives on panoramix). See android/FIREBASE.md and android/REMOTE.md.
#
# Prereqs: SSH to the build host + the one-time server setup (REMOTE.md), and `gcloud auth
# login` here. Usage:
#   ./firebase-test.sh                       # default Test Lab device
#   ./firebase-test.sh --device model=oriole,version=33
set -euo pipefail

HOST="${GIFFER_REMOTE_HOST:-panoramix}"
REMOTE_DIR="giffer/android"
JAVA="/usr/lib/jvm/java-17-openjdk-amd64"
OUT="$(mktemp -d)"

cd "$(dirname "$0")"

echo "==> Syncing working tree to $HOST"
rsync -az --delete \
  --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' --exclude 'app/build/' \
  --exclude 'local.properties' --exclude 'keystore.properties' --exclude 'keystore/' \
  --exclude 'app/src/androidTest/assets/motionphoto/' \
  ./ "$HOST:$REMOTE_DIR/"

echo "==> Fetching test assets + building APKs on $HOST"
ssh "$HOST" "bash -lc '
  set -e
  cd ~/$REMOTE_DIR
  ./fetch-test-assets.sh
  export JAVA_HOME=$JAVA
  export ANDROID_HOME=\$HOME/android-sdk
  ./gradlew assembleDebug assembleDebugAndroidTest
'"

echo "==> Pulling APKs"
scp -q "$HOST:$REMOTE_DIR/app/build/outputs/apk/debug/app-debug.apk" "$OUT/"
scp -q "$HOST:$REMOTE_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" "$OUT/"

echo "==> Running on Firebase Test Lab"
gcloud firebase test android run \
  --type instrumentation \
  --app "$OUT/app-debug.apk" \
  --test "$OUT/app-debug-androidTest.apk" \
  --timeout 5m \
  --directories-to-pull /sdcard/Android/data/com.leaptools.giffer/files/screenshots \
  "$@"
