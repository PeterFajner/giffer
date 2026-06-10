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

Results print a console URL with per-device pass/fail, logcat, and video. The two tests
assert that each sample Motion Photo yields decoded frames with correct dimensions and a
valid `GIF89a`.

## Quick smoke alternative (no test APK)

A Robo test crawls the UI automatically (validates launch + the picker/about screens, no
crash) without needing the instrumentation APK:

```bash
gcloud firebase test android run --type robo \
  --app app/build/outputs/apk/debug/app-debug.apk --timeout 90s
```
