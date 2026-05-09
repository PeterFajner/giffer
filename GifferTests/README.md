# GifferTests

Unit tests using Swift Testing.

## Wiring up the test target (one-time)

The tests aren't yet attached to a target in `Giffer.xcodeproj`. To enable them:

1. Open `Giffer.xcodeproj` in Xcode.
2. **File → New → Target…** → **Unit Testing Bundle**.
3. Configure:
   - Product Name: `GifferTests`
   - Testing System: **Swift Testing**
   - Target to be Tested: `Giffer`
   - Embed in: `Giffer`
4. Click **Finish**.
5. Xcode will create a `GifferTests/` folder with a stub. **Delete** the auto-generated stub `.swift` file.
6. Right-click the `GifferTests` group in the Project Navigator → **Add Files to "Giffer"…** → select the existing `.swift` files in this folder (do *not* tick "Copy items if needed"; tick "Add to targets: GifferTests" only).
7. `Cmd+U` to run.

## Files

- `TestSupport.swift` — fixture builders shared across tests (mock `CGImage`s, etc.).
- `PlaybackModeTests.swift` — `GIFEncoder.applyPlaybackMode` (forward / reverse / bounce).
- `GIFEncoderTests.swift` — `GIFEncoder.encode` round-trip (validates magic bytes, frame count, crop, progress).
- `EditorViewModelTests.swift` — defaults, `scaledDimensions` math, `canShare` gating.
