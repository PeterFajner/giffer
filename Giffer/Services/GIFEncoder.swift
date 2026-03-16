import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

final class GIFEncoder {

    static func encode(
        frames: [CGImage],
        config: GIFConfiguration,
        progress: @escaping (Double) -> Void
    ) throws -> Data {
        let croppedFrames: [CGImage]
        if let cropRect = config.cropRect {
            croppedFrames = frames.compactMap { frame in
                let w = CGFloat(frame.width)
                let h = CGFloat(frame.height)
                let pixelRect = CGRect(
                    x: cropRect.origin.x * w,
                    y: cropRect.origin.y * h,
                    width: cropRect.width * w,
                    height: cropRect.height * h
                )
                return frame.cropping(to: pixelRect)
            }
        } else {
            croppedFrames = frames
        }

        let orderedFrames = applyPlaybackMode(frames: croppedFrames, mode: config.playbackMode)
        guard !orderedFrames.isEmpty else { throw EncodingError.noFrames }

        let data = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            data as CFMutableData,
            UTType.gif.identifier as CFString,
            orderedFrames.count,
            nil
        ) else { throw EncodingError.destinationCreationFailed }

        let gifProperties: [String: Any] = [
            kCGImagePropertyGIFDictionary as String: [
                kCGImagePropertyGIFLoopCount as String: 0
            ]
        ]
        CGImageDestinationSetProperties(destination, gifProperties as CFDictionary)

        let frameDelay = 1.0 / Double(config.fps)
        let frameProperties: [String: Any] = [
            kCGImagePropertyGIFDictionary as String: [
                kCGImagePropertyGIFDelayTime as String: frameDelay,
                kCGImagePropertyGIFUnclampedDelayTime as String: frameDelay
            ]
        ]

        for (i, frame) in orderedFrames.enumerated() {
            CGImageDestinationAddImage(destination, frame, frameProperties as CFDictionary)
            progress(Double(i + 1) / Double(orderedFrames.count))
        }

        guard CGImageDestinationFinalize(destination) else {
            throw EncodingError.finalizationFailed
        }

        return data as Data
    }

    // MARK: - Playback

    static func applyPlaybackMode(frames: [CGImage], mode: PlaybackMode) -> [CGImage] {
        switch mode {
        case .forward:
            return frames
        case .reverse:
            return frames.reversed()
        case .bounce:
            if frames.count <= 2 { return frames + frames.reversed() }
            return frames + frames.dropFirst().dropLast().reversed()
        }
    }

    enum EncodingError: LocalizedError {
        case noFrames
        case destinationCreationFailed
        case finalizationFailed

        var errorDescription: String? {
            switch self {
            case .noFrames: return "No frames to encode"
            case .destinationCreationFailed: return "Failed to create image destination"
            case .finalizationFailed: return "Failed to finalize image"
            }
        }
    }
}
