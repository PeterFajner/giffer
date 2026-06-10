# Android Live Photos & Toolchain — Research

Background research for the Android port of Giffer. Two parts: (1) how "Live Photos"
work on Android and how they differ from iOS, and (2) how to build/test Android with a
minimal footprint (no full Android Studio).

---

## Part 1 — "Live Photos" on Android (Motion Photos)

### Terminology

There is no single "Live Photo" on Android. Each vendor has its own name and, crucially,
its own on-disk format:

| Vendor | Name | Storage |
| --- | --- | --- |
| Apple (iOS) | Live Photo | **Two** resources: a still (HEIC/JPG) + a paired `.mov`, paired in the photo library |
| Google (Pixel) | **Motion Photo** (older: **Micro Video**) | **One** JPEG/HEIC file with an MP4 appended at the end |
| Samsung | **Motion Photo** | **One** JPEG with an MP4 appended via a proprietary `MotionPhoto_Data` trailer |
| Motorola / OnePlus / others | "Active Shots" / "Live Photos" | Single-file embedded video |

The key conceptual difference from iOS: **Android stores the motion as a single container
file with the video embedded inside it**, whereas iOS keeps the still and the video as two
separate resources of one `PHAsset`. So the iOS step "grab the `.pairedVideo` resource"
becomes, on Android, "find and slice the MP4 byte-range out of the picked image file."

Other practical differences:
- Most Android motion photos have **no audio** (iOS Live Photos do). We don't need audio anyway.
- There is no `PHLivePhoto` / `PHLivePhotoView` analog. Once the embedded MP4 is extracted
  it's just a normal short video.

### Google Motion Photo format (the spec)

Official spec: <https://developer.android.com/media/platform/motion-photo-format>

A Motion Photo is the primary still image followed by a complete MP4 appended at the end.
Location of the MP4 is described in the file's **XMP** metadata:

- `Camera`/`GCamera` namespace (`http://ns.google.com/photos/1.0/camera/`):
  - `MotionPhoto = 1` — flags the file as a motion photo
  - `MotionPhotoVersion = 1`
  - `MotionPhotoPresentationTimestampUs` — µs timestamp of the video frame matching the still
- `Container` / `Item` namespaces — an ordered directory of items, each with:
  - `Item:Mime` (`image/jpeg`, `image/heic`, `video/mp4`, …)
  - `Item:Semantic` (`Primary` or `MotionPhoto`)
  - `Item:Length` — byte length of the item. The MP4's length lets you seek **from the end**:
    `mp4_start = file_size - video_Item_Length`.
  - `Item:Padding` — for HEIC/AVIF the video is wrapped in an `mpvd` box; padding is 8 bytes.

Legacy **Micro Video** (filenames `MVIMG_*.jpg`) instead uses a single tag
`GCamera:MicroVideoOffset` measured from the end of file: `mp4_start = file_size - offset`.

**Gotcha (verified across sources):** the `MotionPhoto=1` XMP flag is often retained by
editors *after the video has been stripped*. Files can have the flag but no MP4. Always
confirm a real MP4 is present rather than trusting XMP.

### Samsung Motion Photo format

Samsung does **not** use Google's XMP container. Layout:
`[JPEG][16-byte ASCII "MotionPhoto_Data"][MP4]` plus a Samsung Extended Format (SEF) index
(`SEFH`…`SEFT`) at the very end. The pragmatic extraction is: find the first
`MotionPhoto_Data` marker, the MP4 begins right after the 16-byte name.

Refs: <https://github.com/joemck/ExtractMotionPhotos>, <https://github.com/doodspav/motionphoto>

### How we extract on Android — the recommended path

The single best finding: **Jetpack Media3 (ExoPlayer) ships an official
`MetadataRetriever` that understands motion photos** and returns a `MotionPhotoMetadata`
with exact byte offsets — for both Google Motion Photos and legacy Micro Videos. No XMP
parsing needed.

<https://developer.android.com/media/media3/exoplayer/retrieving-metadata>

`MotionPhotoMetadata` exposes `videoStartPosition` / `videoEndPosition` — read that byte
range, write it to a temp `.mp4`, done.

Strategy used in this port (`MotionPhotoExtractor.kt`):
1. **Pick** the image with the Jetpack Photo Picker (`ActivityResultContracts.PickVisualMedia`).
2. **Cache** the picked `content://` URI bytes to a temp file.
3. **Locate the MP4**: try Media3 `MetadataRetriever` → `MotionPhotoMetadata` byte range
   first (covers Google + Micro Video). Fall back to a raw byte scan: the Samsung
   `MotionPhoto_Data` marker, then the MP4 `ftyp` box (the box starts 4 bytes before the
   literal `ftyp`).
4. **Write** the MP4 bytes to a temp file.
5. **Decode frames** at the target fps with `MediaMetadataRetriever.getFrameAtTime(..,
   OPTION_CLOSEST)` — the direct analog of iOS `AVAssetImageGenerator`.

There is **no public `MediaStore` column** that flags an image as a motion photo, so we
treat any picked image as "possibly motion" and probe it; if no MP4 is found we surface a
clear error.

Open-source references: `googleinterns/libmphoto` (archived, format reference),
`doodspav/motionphoto`, `joemck/ExtractMotionPhotos`, `sirion/android-motion-photos`.

---

## Part 2 — Minimal build & emulator setup (no Android Studio)

The user is on Apple-Silicon macOS with limited disk and has x86_64 Linux home servers
(`idefix`, `panoramix`) and no Android phone.

### Build-only SDK on the Mac (~1 GB)

1. **JDK 17** (required by current Android Gradle Plugin 8.x/9.x):
   `brew install openjdk@17`, then point `JAVA_HOME` at it.
2. **Command line tools only** (not the IDE) from <https://developer.android.com/studio>
   (the `commandlinetools-mac-*.zip`, ~150 MB zipped).
   - Gotcha: `sdkmanager` requires the layout `cmdline-tools/latest/bin/...` — you must
     move the unzipped `cmdline-tools/*` into a `latest/` subfolder.
3. Set env (fish):
   ```fish
   set -gx ANDROID_HOME ~/android-sdk
   set -gx PATH $PATH $ANDROID_HOME/cmdline-tools/latest/bin $ANDROID_HOME/platform-tools
   ```
   (`ANDROID_SDK_ROOT` is deprecated; use `ANDROID_HOME`.)
4. Install packages + accept licenses:
   ```bash
   sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
   sdkmanager --licenses
   ```
5. Build with the Gradle **wrapper** (no separate Gradle install needed):
   ```bash
   ./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
   ./gradlew installDebug       # build + install to a connected device/emulator
   ```

Per-package on-disk budget: cmdline-tools ~600 MB, platform-tools ~50 MB, one platform
~150 MB, build-tools ~80 MB. Build-only ≈ **0.8–1 GB**. The big item (the ~1.5 GB system
image) is only needed if you run an emulator locally — keep it off the Mac.

### Running the emulator — keep it off the Mac

On Apple Silicon you'd need `arm64-v8a` system images (x86_64 images are unusably slow).
A full local emulator + AVD runtime is ~4–6 GB. Better: run the emulator on a Linux server.

On an **x86_64 Linux server** (idefix/panoramix), use **`x86_64` system images** so KVM
accelerates them (matching host arch):
```bash
emulator -accel-check          # confirm KVM usable
ls -l /dev/kvm                 # must exist & be readable
sdkmanager "emulator" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n test35 -k "system-images;android-35;google_apis;x86_64" --device pixel_6
emulator -avd test35 -no-window -no-audio -no-boot-anim -gpu swiftshader
```
Notes: KVM is effectively mandatory (bare-metal servers usually have `/dev/kvm`; a server
that is itself a VM may lack nested virt). `-gpu swiftshader_indirect` is **deprecated** —
use `-gpu swiftshader` (or `-gpu swangle`).

**Connecting from the Mac:** the emulator binds adb to `127.0.0.1` on the server, so a bare
`adb connect idefix:5555` does **not** work. Use an SSH tunnel:
```bash
ssh -N -L 5555:127.0.0.1:5555 you@idefix &
adb connect localhost:5555
./gradlew installDebug
```
Or just check out + build + test entirely on the server (no cross-network adb).

### Lighter alternatives (no local emulator)

- **Firebase Test Lab** — upload the APK + instrumented tests, runs on real/virtual devices
  in the cloud. Free Spark quota: 10 virtual + 5 physical runs/day. Zero local disk. Best
  no-install way to smoke-test on real hardware.
- **Gradle Managed Devices** with **ATD** images (`systemImageSource = "aosp-atd"`) — declare
  a device in `build.gradle`, AGP downloads/runs/tears down a lightweight headless emulator
  for instrumented tests. Run it on a Linux server.
- **appetize.io** — interact with the APK in a browser; good for quick manual checks.

### Recommendation for this user

Install only the **build SDK on the Mac (~1 GB)**. For interactive testing, stand up a
**headless x86_64 emulator on idefix/panoramix** (KVM-accelerated) and reach it via an
**SSH tunnel**. For automated/CI checks, lead with **Firebase Test Lab** (free, zero disk).
See `SETUP.md` for the exact command sequence used by this project.
