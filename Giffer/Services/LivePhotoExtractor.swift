import AVFoundation
import CoreGraphics
import Photos
import PhotosUI

struct ExtractionResult {
    let frames: [CGImage]
    let originalSize: CGSize
    let duration: Double
}

final class LivePhotoExtractor {

    static func extractVideoURL(from livePhoto: PHLivePhoto) async throws -> URL {
        let resources = PHAssetResource.assetResources(for: livePhoto)
        guard let videoResource = resources.first(where: { $0.type == .pairedVideo }) else {
            throw ExtractionError.noVideoComponent
        }

        let tempDir = FileManager.default.temporaryDirectory
        let tempURL = tempDir.appendingPathComponent(UUID().uuidString + ".mov")

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let options = PHAssetResourceRequestOptions()
            options.isNetworkAccessAllowed = true
            PHAssetResourceManager.default().writeData(for: videoResource, toFile: tempURL, options: options) { error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }

        return tempURL
    }

    static func extractFrames(
        from livePhoto: PHLivePhoto,
        config: GIFConfiguration,
        progress: @escaping (Double) -> Void
    ) async throws -> ExtractionResult {
        let videoURL = try await extractVideoURL(from: livePhoto)
        defer { try? FileManager.default.removeItem(at: videoURL) }
        return try await extractFrames(fromVideoURL: videoURL, config: config, progress: progress)
    }

    static func extractFrames(
        fromVideoURL videoURL: URL,
        config: GIFConfiguration,
        progress: @escaping (Double) -> Void
    ) async throws -> ExtractionResult {
        let asset = AVURLAsset(url: videoURL)
        let duration = try await asset.load(.duration)
        let durationSeconds = CMTimeGetSeconds(duration)

        guard let track = try await asset.loadTracks(withMediaType: .video).first else {
            throw ExtractionError.noVideoTrack
        }

        let naturalSize = try await track.load(.naturalSize)
        let transform = try await track.load(.preferredTransform)
        let correctedSize = naturalSize.applying(transform)
        let originalSize = CGSize(width: abs(correctedSize.width), height: abs(correctedSize.height))

        let trimStart = config.trimStart * durationSeconds
        let trimEnd = config.trimEnd * durationSeconds
        let trimmedDuration = trimEnd - trimStart

        let frameCount = max(1, Int(trimmedDuration * Double(config.fps)))
        let frameDuration = trimmedDuration / Double(frameCount)

        let generator = AVAssetImageGenerator(asset: asset)
        generator.requestedTimeToleranceBefore = CMTime(seconds: frameDuration / 2, preferredTimescale: 600)
        generator.requestedTimeToleranceAfter = CMTime(seconds: frameDuration / 2, preferredTimescale: 600)
        generator.appliesPreferredTrackTransform = true

        let scaledWidth = originalSize.width * config.resolutionScale
        let scaledHeight = originalSize.height * config.resolutionScale
        generator.maximumSize = CGSize(width: scaledWidth, height: scaledHeight)

        var frames: [CGImage] = []
        frames.reserveCapacity(frameCount)

        for i in 0..<frameCount {
            let time = CMTime(seconds: trimStart + Double(i) * frameDuration, preferredTimescale: 600)
            let (image, _) = try await generator.image(at: time)

            frames.append(image)

            progress(Double(i + 1) / Double(frameCount))
        }

        return ExtractionResult(frames: frames, originalSize: originalSize, duration: durationSeconds)
    }

    enum ExtractionError: LocalizedError {
        case noVideoComponent
        case noVideoTrack

        var errorDescription: String? {
            switch self {
            case .noVideoComponent: return "No video component found in Live Photo"
            case .noVideoTrack: return "No video track found"
            }
        }
    }
}
