#!/usr/bin/env bash
# Build the app + instrumentation APKs and run them on Firebase Test Lab.
# Prereqs (one-time, interactive — see android/FIREBASE.md):
#   gcloud auth login
#   gcloud config set project <your-project-id>
# Usage:
#   ./firebase-test.sh                       # default Test Lab device
#   ./firebase-test.sh --device model=oriole,version=33   # pick a device
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export PATH="/opt/homebrew/share/google-cloud-sdk/bin:$PATH"

echo "==> Fetching Motion Photo test assets"
./fetch-test-assets.sh

echo "==> Building app + androidTest APKs"
./gradlew assembleDebug assembleDebugAndroidTest

APP=app/build/outputs/apk/debug/app-debug.apk
TEST=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

echo "==> Running on Firebase Test Lab"
gcloud firebase test android run \
  --type instrumentation \
  --app "$APP" \
  --test "$TEST" \
  --timeout 5m \
  "$@"
