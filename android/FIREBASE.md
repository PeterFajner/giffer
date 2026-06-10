# Running on Firebase Test Lab

Test Lab runs the app + an instrumentation test on **real cloud devices**. We use it to
verify the device-only extraction code (Media3 `MotionPhotoMetadata`, the Samsung byte-scan
fallback, and `MediaMetadataRetriever` frame decoding) against real Pixel and Samsung Motion
Photos — paths the on-host Robolectric unit tests can't exercise.

The instrumentation test is `app/src/androidTest/.../MotionPhotoExtractorInstrumentedTest.kt`;
the sample files are bundled in `app/src/androidTest/assets/motionphoto/`.

## One-time setup (interactive — needs your Google account)

```bash
# 1. Authenticate (opens a browser)
gcloud auth login

# 2. Use an existing project, or create one:
gcloud projects create giffer-test-lab --name="Giffer Test Lab"   # optional
gcloud config set project <your-project-id>

# 3. Enable the APIs Test Lab needs
gcloud services enable testing.googleapis.com toolresults.googleapis.com

# 4. Link the project to Firebase (Spark/free plan is fine):
#    visit https://console.firebase.google.com → Add project → pick <your-project-id>
```

Firebase's free **Spark** plan includes a Test Lab quota of **10 virtual + 5 physical**
device-runs per day — enough for this.

## Run

`firebase-test.sh` builds the APKs **on panoramix** (the Mac no longer has the Android SDK —
see [REMOTE.md](REMOTE.md)): it rsyncs the working tree, fetches the test assets and builds
`assembleDebug assembleDebugAndroidTest` on the server, pulls both APKs back, and submits them
to Test Lab with `gcloud` (which still runs here). Override the build host with
`GIFFER_REMOTE_HOST`.

```bash
cd android
./firebase-test.sh                                  # default device
./firebase-test.sh --device model=MediumPhone.arm,version=34   # specific virtual device
./firebase-test.sh --device model=oriole,version=33            # Pixel 6, physical
```

List available devices/versions:

```bash
gcloud firebase test android models list
```

Results print a console URL with per-device pass/fail, logcat, and video. The tests:
- `MotionPhotoExtractorInstrumentedTest` — asserts each sample Motion Photo (Pixel + Samsung)
  yields decoded frames with correct dimensions and a valid `GIF89a`.
- `EditorUiScreenshotTest` — renders the real editor against an extracted Motion Photo and
  captures screenshots of the editor, crop mode, and the trim tool.

### Screenshots

`EditorUiScreenshotTest` writes PNGs to the app's external files dir, and the runner pulls
that directory into the results bucket (`--directories-to-pull`). Download them with:

```bash
# grab the bucket path printed as "Raw results will be stored in your GCS bucket at ..."
gsutil cp '<bucket>/**screenshots/*.png' .
```

They land under `<device>/artifacts/sdcard/Android/data/com.leaptools.giffer/files/screenshots/`.

## Quick smoke alternative (no test APK)

A Robo test crawls the UI automatically (validates launch + the picker/about screens, no
crash) without needing the instrumentation APK:

```bash
gcloud firebase test android run --type robo \
  --app app/build/outputs/apk/debug/app-debug.apk --timeout 90s
```
