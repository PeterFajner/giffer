import AVFoundation
import CoreMedia
import ImageIO
import Photos

/// Mirrors the Photos app's behaviour of playing consecutive Live Photos as one
/// continuous clip. Given several Live Photos, it orders them on a shared
/// wall-clock timeline, trims the overlap that neighbouring captures share
/// (each Live Photo records ~1.5s before and after the shutter, so captures
/// taken <1.5s apart overlap), and splices them into a single composition.
final class LivePhotoStitcher {

    struct Result {
        /// The stitched asset (an `AVComposition`), or the lone paired video
        /// when only one Live Photo was supplied.
        let asset: AVAsset
        /// False when the selection contained a wall-clock gap too large to be
        /// a continuous burst — the clips were still spliced with a hard cut.
        let isConsecutive: Bool
        let clipCount: Int
        /// Temporary paired-video files backing `asset`. The caller must keep
        /// these alive while reading `asset`, then delete them.
        let tempURLs: [URL]
    }

    /// Largest wall-clock gap (seconds) between one clip's end and the next
    /// clip's start that still counts as a continuous burst. A true burst
    /// overlaps (negative gap); anything beyond this is treated as a cut.
    private static let continuityTolerance = 0.15

    static func stitch(_ livePhotos: [PHLivePhoto]) async throws -> Result {
        var clips: [Clip] = []
        for livePhoto in livePhotos {
            if let clip = try? await Clip.make(from: livePhoto) {
                clips.append(clip)
            }
        }
        guard !clips.isEmpty else { throw LivePhotoExtractor.ExtractionError.noVideoComponent }

        // Order by when each clip's motion actually began in wall-clock time —
        // the picker hands items back in tap order, not capture order.
        clips.sort { $0.movieStartWall < $1.movieStartWall }

        // Single clip: nothing to composite, hand back its paired video directly.
        if clips.count == 1 {
            return Result(asset: clips[0].asset, isConsecutive: true, clipCount: 1,
                          tempURLs: [clips[0].videoURL])
        }

        let composition = AVMutableComposition()
        guard let compTrack = composition.addMutableTrack(
            withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else {
            throw LivePhotoExtractor.ExtractionError.noVideoTrack
        }

        var cursor = CMTime.zero
        var lastWallEnd: Double? = nil
        var isConsecutive = true
        var transformSet = false

        for clip in clips {
            guard let srcTrack = try? await clip.asset.loadTracks(withMediaType: .video).first else {
                continue
            }

            // How much of this clip's front overlaps the previous clip's tail.
            var trimFront = 0.0
            if let lastEnd = lastWallEnd {
                let overlap = lastEnd - clip.movieStartWall   // >0 overlap, <0 gap
                if overlap > continuityTolerance {
                    trimFront = min(overlap, clip.duration)
                } else if overlap < -continuityTolerance {
                    isConsecutive = false                     // real gap → hard cut
                }
            }

            let insertDuration = clip.duration - trimFront
            lastWallEnd = clip.movieStartWall + clip.duration
            guard insertDuration > 0 else { continue }

            let range = CMTimeRange(
                start: CMTime(seconds: trimFront, preferredTimescale: 600),
                duration: CMTime(seconds: insertDuration, preferredTimescale: 600))
            do {
                try compTrack.insertTimeRange(range, of: srcTrack, at: cursor)
            } catch {
                continue
            }

            if !transformSet, let transform = try? await srcTrack.load(.preferredTransform) {
                compTrack.preferredTransform = transform
                transformSet = true
            }
            cursor = cursor + CMTime(seconds: insertDuration, preferredTimescale: 600)
        }

        return Result(asset: composition, isConsecutive: isConsecutive,
                      clipCount: clips.count, tempURLs: clips.map(\.videoURL))
    }

    // MARK: - Clip

    private struct Clip {
        let asset: AVURLAsset
        let videoURL: URL
        let duration: Double
        /// Absolute wall-clock time the shutter fired (seconds since reference).
        let shutterWall: Double
        /// Seconds from the movie's start to the shutter frame within it.
        let stillOffset: Double

        /// When this clip's motion began, on the shared wall-clock axis.
        var movieStartWall: Double { shutterWall - stillOffset }

        static func make(from livePhoto: PHLivePhoto) async throws -> Clip {
            let resources = PHAssetResource.assetResources(for: livePhoto)
            guard let videoResource = resources.first(where: { $0.type == .pairedVideo }) else {
                throw LivePhotoExtractor.ExtractionError.noVideoComponent
            }

            let videoURL = try await LivePhotoExtractor.writeResource(videoResource, fileExtension: "mov")
            let asset = AVURLAsset(url: videoURL)
            let duration = CMTimeGetSeconds(try await asset.load(.duration))

            // Where the shutter sits inside this clip. Falls back to the middle,
            // since a Live Photo is ~1.5s before + 1.5s after the shutter.
            let stillOffset = (await stillImageOffset(for: asset)) ?? (duration / 2)

            // Anchor the clip on the wall-clock axis. The still image's EXIF
            // carries sub-second precision, which we need for frame-accurate
            // overlap trimming; the movie's creationdate is only second-granular
            // and is used only as a fallback.
            var shutterWall: Double? = nil
            if let photoResource = resources.first(where: { $0.type == .photo }) {
                shutterWall = await shutterWallTime(from: photoResource)
            }
            if shutterWall == nil, let created = await creationDate(for: asset) {
                // creationdate ≈ movie start; express it as a shutter time so the
                // movieStartWall math below stays consistent with the EXIF path.
                shutterWall = created.timeIntervalSinceReferenceDate + stillOffset
            }
            guard let wall = shutterWall else {
                throw LivePhotoExtractor.ExtractionError.noTimestamp
            }

            return Clip(asset: asset, videoURL: videoURL, duration: duration,
                        shutterWall: wall, stillOffset: stillOffset)
        }
    }

    // MARK: - Timing metadata

    /// Presentation time of the `still-image-time` marker inside a Live Photo's
    /// paired video — i.e. how far into the movie the shutter frame sits.
    private static func stillImageOffset(for asset: AVAsset) async -> Double? {
        guard let tracks = try? await asset.loadTracks(withMediaType: .metadata) else { return nil }
        for track in tracks {
            guard let formats = try? await track.load(.formatDescriptions) else { continue }
            let carriesStillImageTime = formats.contains { fmt in
                guard let ids = CMMetadataFormatDescriptionGetIdentifiers(fmt) as? [String] else {
                    return false
                }
                return ids.contains { $0.contains("still-image-time") }
            }
            guard carriesStillImageTime else { continue }

            guard let reader = try? AVAssetReader(asset: asset) else { return nil }
            let output = AVAssetReaderTrackOutput(track: track, outputSettings: nil)
            guard reader.canAdd(output) else { return nil }
            reader.add(output)
            guard reader.startReading() else { return nil }
            defer { reader.cancelReading() }

            if let sample = output.copyNextSampleBuffer() {
                let seconds = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sample))
                return seconds.isFinite ? seconds : nil
            }
        }
        return nil
    }

    /// Sub-second wall-clock time the still image was captured, read from EXIF.
    private static func shutterWallTime(from resource: PHAssetResource) async -> Double? {
        guard let url = try? await LivePhotoExtractor.writeResource(resource, fileExtension: "img") else {
            return nil
        }
        defer { try? FileManager.default.removeItem(at: url) }

        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let exif = props[kCGImagePropertyExifDictionary] as? [CFString: Any],
              let dateString = exif[kCGImagePropertyExifDateTimeOriginal] as? String else {
            return nil
        }

        // EXIF DateTimeOriginal has no timezone; we parse every clip in a fixed
        // zone. All clips in a burst share the same zone, so the *relative*
        // offsets that drive overlap trimming are correct regardless.
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
        guard let date = formatter.date(from: dateString) else { return nil }

        var subsecond = 0.0
        if let sub = exif[kCGImagePropertyExifSubsecTimeOriginal] as? String,
           let value = Double("0." + sub) {
            subsecond = value
        }
        return date.timeIntervalSinceReferenceDate + subsecond
    }

    /// Fallback wall-clock anchor: the movie container's creation date.
    private static func creationDate(for asset: AVAsset) async -> Date? {
        if let items = try? await asset.load(.metadata) {
            for item in items where item.identifier == .quickTimeMetadataCreationDate {
                if let date = try? await item.load(.dateValue) { return date }
            }
        }
        if let common = try? await asset.load(.commonMetadata) {
            for item in common where item.commonKey == .commonKeyCreationDate {
                if let date = try? await item.load(.dateValue) { return date }
            }
        }
        return nil
    }
}
