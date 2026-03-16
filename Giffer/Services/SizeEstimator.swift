import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

final class SizeEstimator {

    static func estimatedSize(
        sampleFrames: [CGImage],
        totalFrameCount: Int,
        config: GIFConfiguration
    ) -> Int64 {
        guard !sampleFrames.isEmpty, totalFrameCount > 0 else { return 0 }

        let samplesToEncode = Array(sampleFrames.prefix(3))
        let data = NSMutableData()

        guard let destination = CGImageDestinationCreateWithData(
            data as CFMutableData,
            UTType.gif.identifier as CFString,
            samplesToEncode.count,
            nil
        ) else { return 0 }

        let frameDelay = 1.0 / Double(config.fps)
        let frameProperties: [String: Any] = [
            kCGImagePropertyGIFDictionary as String: [
                kCGImagePropertyGIFDelayTime as String: frameDelay
            ]
        ]

        for frame in samplesToEncode {
            CGImageDestinationAddImage(destination, frame, frameProperties as CFDictionary)
        }

        guard CGImageDestinationFinalize(destination) else { return 0 }

        let bytesPerFrame = Int64(data.length) / Int64(samplesToEncode.count)
        let effectiveFrameCount: Int

        switch config.playbackMode {
        case .forward, .reverse:
            effectiveFrameCount = totalFrameCount
        case .bounce:
            effectiveFrameCount = max(1, totalFrameCount * 2 - 2)
        }

        return bytesPerFrame * Int64(effectiveFrameCount)
    }
}
