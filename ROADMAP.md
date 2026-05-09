# Roadmap

Features that are on the horizon but not yet shipped. No commitments on order or timeline.

## Encoding & output quality

- **Subject extraction** — use `VNGenerateForegroundInstanceMaskRequest` (iOS 17+) on the Neural Engine to mask the foreground subject per frame before encoding, with a progress indicator while masks are computed.
- **GIF transparency toggle** — once subject extraction lands, allow exporting GIFs with a transparent background using a hard-edge threshold on the mask. Edges will not be feathered (GIF only supports 1-bit alpha).
- **Frame deduplication** — detect near-identical consecutive frames and extend the previous frame's delay instead of writing duplicate pixel data, reducing file size for low-motion clips.
- **Dirty-rectangle encoding** — encode only the changed region of each frame relative to the previous, taking advantage of GIF's native frame-disposal model.
- **Per-frame vs. global colour palette toggle** — let the user choose between a single global 256-colour palette (smaller file, sometimes worse colour) and a per-frame palette (better colour, larger file).
- **Lossy compression** — investigate either bundling a pure-Swift lossy GIF compressor or shipping a small precompiled `gifsicle` binary for additional file-size reduction.

## Input sources

- **Video files** — accept regular video files (e.g. `.mov`, `.mp4`) as input, in addition to Live Photos.
- **Still images** — accept a single still image as input (output would be a one-frame GIF, useful as a stepping stone toward burst-photo support).
- **Auto-trim long videos** — when video import lands, automatically trim any clip longer than 60 seconds to its first minute, with a notice to the user.

## Integration points

- **Photo Editing Extension** — add a `com.apple.photo-editing` extension target so Giffer appears in the Photos app's edit-screen "..." menu. Unlike share/action extensions (which can't read Live Photo video data — see below), Photo Editing Extensions get a `PHContentEditingInput` and can build a `PHLivePhotoEditingContext` with a `frameProcessor` block that delivers every frame of the paired video. The extension would run the existing GIF pipeline against those frames and either save the result to the Photo Library via `PHPhotoLibrary.performChanges` or present a `UIActivityViewController` from inside the extension UI. User flow is 3 taps (photo → Edit → extension button) vs 2 for the share sheet, but it's the only Apple-blessed path that actually works.

## Considered and rejected

- **Share extension for "share Live Photo to Giffer" from Photos.app** — attempted and removed. iOS share extensions cannot extract the paired video component of a Live Photo: `loadObject(ofClass: PHLivePhoto.self)` returns "Could not coerce", and `loadFileRepresentation(forTypeIdentifier: "com.apple.live-photo")` fails with `PHPhotosErrorDomain Code=-1`. This is a longstanding iOS bug (open since iOS 11, Apple Feedback #35325817, still unresolved). Apple's documentation claims it works; in practice it has never worked from extensions. The in-app `PhotosPicker` is the only reliable path. See <https://developer.apple.com/forums/thread/22132> for the decade-long thread.
