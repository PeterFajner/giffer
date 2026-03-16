import Foundation
import CoreGraphics

final class ConfigStore {
    static let shared = ConfigStore()

    private let fileURL: URL

    private init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        fileURL = docs.appendingPathComponent("saved_configs.json")
    }

    func load(for assetIdentifier: String) -> GIFConfiguration? {
        guard let data = try? Data(contentsOf: fileURL),
              let dict = try? JSONDecoder().decode([String: SavedConfig].self, from: data),
              let saved = dict[assetIdentifier] else {
            return nil
        }
        return saved.toGIFConfiguration()
    }

    func save(_ config: GIFConfiguration, for assetIdentifier: String) {
        var dict: [String: SavedConfig]
        if let data = try? Data(contentsOf: fileURL),
           let existing = try? JSONDecoder().decode([String: SavedConfig].self, from: data) {
            dict = existing
        } else {
            dict = [:]
        }
        dict[assetIdentifier] = SavedConfig(from: config)
        if let data = try? JSONEncoder().encode(dict) {
            try? data.write(to: fileURL)
        }
    }

    struct SavedConfig: Codable {
        var resolutionScale: Double
        var fps: Int
        var trimStart: Double
        var trimEnd: Double
        var cropX: Double?
        var cropY: Double?
        var cropWidth: Double?
        var cropHeight: Double?
        var playbackMode: String

        init(from config: GIFConfiguration) {
            resolutionScale = config.resolutionScale
            fps = config.fps
            trimStart = config.trimStart
            trimEnd = config.trimEnd
            if let crop = config.cropRect {
                cropX = crop.origin.x
                cropY = crop.origin.y
                cropWidth = crop.width
                cropHeight = crop.height
            }
            playbackMode = config.playbackMode.rawValue
        }

        func toGIFConfiguration() -> GIFConfiguration {
            var c = GIFConfiguration()
            c.resolutionScale = resolutionScale
            c.fps = fps
            c.trimStart = trimStart
            c.trimEnd = trimEnd
            if let x = cropX, let y = cropY, let w = cropWidth, let h = cropHeight {
                c.cropRect = CGRect(x: x, y: y, width: w, height: h)
            }
            c.playbackMode = PlaybackMode(rawValue: playbackMode) ?? .forward
            return c
        }
    }
}
