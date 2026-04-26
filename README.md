# Giffer

Convert iOS Live Photos into animated GIFs, right on your device.

## Features

- Pick a Live Photo from the in-app picker, or share one directly to Giffer from the Photos app
- Trim, adjust frame rate (6–24 fps), resize, and crop
- Three playback modes: forward, reverse, and bounce
- Live preview while editing
- Export as a standard GIF and share anywhere

## Privacy

Giffer collects no data. Live Photos are processed entirely on-device and never leave your phone. See [PRIVACY.md](PRIVACY.md) for details.

## Building

Requirements:

- Xcode 16 or later
- iOS 17 or later (target device or simulator)
- An Apple Developer account if you want to run on a physical device

Steps:

1. Clone the repo
2. Open `Giffer.xcodeproj`
3. Select the `Giffer` scheme and a target device or simulator
4. Build and run

The project also includes a Share Extension target (`GifferShareExtension`) which is built and embedded automatically.

### App Group

The main app and the Share Extension communicate through an App Group (`group.ca.pfaj.giffer`). If you fork the project under a different team ID, update:

- `Giffer/Giffer.entitlements`
- `GifferShareExtension/GifferShareExtension.entitlements`
- `Shared/SharedConstants.swift` — `appGroupIdentifier`
- The bundle identifiers in the Xcode project settings

## Roadmap

See [ROADMAP.md](ROADMAP.md) for planned features.

## License

[MPL-2.0](LICENSE) © Peter Fajner
