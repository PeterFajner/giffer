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
