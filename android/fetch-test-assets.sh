#!/usr/bin/env bash
# Downloads real Motion Photo sample files used by the instrumentation test.
# These are not committed to the repo (third-party binaries); fetch them on demand.
# Source: immich-app/test-assets (real Pixel + Samsung device captures).
set -euo pipefail
DIR="$(dirname "$0")/app/src/androidTest/assets/motionphoto"
mkdir -p "$DIR"

base="https://raw.githubusercontent.com/immich-app/test-assets/main/formats/motionphoto"
for f in pixel-8a.jpg samsung-one-ui-6.jpg; do
  if [ -f "$DIR/$f" ]; then
    echo "have $f"
  else
    echo "fetching $f"
    curl -fsSL -o "$DIR/$f" "$base/$f"
  fi
done
echo "test assets ready in $DIR"
