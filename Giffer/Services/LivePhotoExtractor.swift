import AVFoundation
import CoreGraphics
import os
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
        return try await writeResource(videoResource, fileExtension: "mov")
    }

    /// Writes a Photos asset resource (paired video, still photo, …) to a
    /// temporary file. Callers own the returned URL and must delete it.
    static func writeResource(_ resource: PHAssetResource, fileExtension: String) async throws -> URL {
        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + "." + fileExtension)

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let options = PHAssetResourceRequestOptions()
            options.isNetworkAccessAllowed = true
            PHAssetResourceManager.default().writeData(for: resource, toFile: tempURL, options: options) { error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }

        return tempURL
    }

    /// Samples frames out of an already-prepared asset. The asset may be a
    /// single Live Photo's paired video or a stitched `AVComposition` — this
    /// stage is agnostic to how it was assembled.
    static func extractFrames(
        from asset: AVAsset,
        config: GIFConfiguration,
        progress: @escaping (Double) -> Void
    ) async throws -> ExtractionResult {
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

        // Every frame is held decoded in memory at once. A long stitched
        // composition can demand several GB of CGImages, which the OS answers
        // with an uncatchable jetsam kill — so refuse up front when the
        // estimated peak would eat more than half the memory left to us.
        // `os_proc_available_memory()` reports the bytes remaining before this
        // process is terminated (0 when unavailable, in which case we skip).
        let bytesPerFrame = Double(scaledWidth * scaledHeight) * 4.0   // RGBA
        let estimatedPeak = bytesPerFrame * Double(frameCount)
        let availableMemory = Double(os_proc_available_memory())
        if availableMemory > 0, estimatedPeak > availableMemory * 0.5 {
            throw ExtractionError.insufficientMemory
        }

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
        case noTimestamp
        case insufficientMemory

        var errorDescription: String? {
            switch self {
            case .noVideoComponent: return "No video component found in Live Photo"
            case .noVideoTrack: return "No video track found"
            case .noTimestamp: return "Could not determine capture time"
            case .insufficientMemory: return "Not enough memory to build this GIF"
            }
        }
    }
}
