import SwiftUI
import AVFoundation
import Photos
import PhotosUI
import Observation

@Observable
final class EditorViewModel {
    var livePhotos: [PHLivePhoto] = []
    var config = GIFConfiguration()
    var extractedFrames: [CGImage] = []
    var originalSize: CGSize = .zero
    var videoDuration: Double = 0

    var exportedData: Data? = nil
    var isExtracting = false
    var isEncoding = false
    var errorMessage: String? = nil

    /// Set once after loading when the selection wasn't a continuous burst;
    /// the editor shows a brief toast and clears it.
    var showNonConsecutiveToast = false

    /// Set to the selected-photo count when extraction would exhaust memory.
    /// Drives the editor to pop back to the picker, which shows a toast.
    var outOfMemoryPhotoCount: Int? = nil

    /// Stitched asset + its backing temp files, cached so trim/quality
    /// re-extractions reuse the composition instead of re-stitching.
    private var stitchedAsset: AVAsset?
    private var stitchTempURLs: [URL] = []

    private var encodeTask: Task<Void, Never>?

    var previewFrames: [CGImage] {
        GIFEncoder.applyPlaybackMode(frames: extractedFrames, mode: config.playbackMode)
    }

    var scaledDimensions: (width: Int, height: Int) {
        let cropW = config.cropRect?.width ?? 1.0
        let cropH = config.cropRect?.height ?? 1.0
        let w = Int(originalSize.width * config.resolutionScale * cropW)
        let h = Int(originalSize.height * config.resolutionScale * cropH)
        return (max(1, w), max(1, h))
    }

    var imageAspectRatio: CGFloat {
        guard originalSize.height > 0 else { return 1 }
        return originalSize.width / originalSize.height
    }

    var formattedFileSize: String {
        guard let data = exportedData else {
            return isEncoding ? "…" : "—"
        }
        return ByteCountFormatter.string(fromByteCount: Int64(data.count), countStyle: .file)
    }

    var canShare: Bool {
        exportedData != nil && !isEncoding
    }

    private var lastShareTempURL: URL?

    deinit {
        encodeTask?.cancel()
        if let url = lastShareTempURL {
            try? FileManager.default.removeItem(at: url)
        }
        for url in stitchTempURLs {
            try? FileManager.default.removeItem(at: url)
        }
    }

    private static func userMessage(_ friendly: String, error: Error) -> String {
        "\(friendly)\n\(error.localizedDescription)"
    }

    func loadLivePhotos(_ photos: [PHLivePhoto]) {
        clearStitchCache()
        livePhotos = photos
        config = GIFConfiguration()
        Task {
            await extractFrames(initialLoad: true)
        }
    }

    @MainActor
    func extractFrames(initialLoad: Bool = false) async {
        guard !livePhotos.isEmpty else { return }
        isExtracting = true
        errorMessage = nil
        exportedData = nil

        do {
            let asset: AVAsset
            if let cached = stitchedAsset {
                asset = cached
            } else {
                let stitch = try await LivePhotoStitcher.stitch(livePhotos)
                stitchedAsset = stitch.asset
                stitchTempURLs = stitch.tempURLs
                asset = stitch.asset
                if initialLoad && !stitch.isConsecutive {
                    showNonConsecutiveToast = true
                }
            }

            let result = try await LivePhotoExtractor.extractFrames(
                from: asset,
                config: config,
                progress: { _ in }
            )
            extractedFrames = result.frames
            originalSize = result.originalSize
            videoDuration = result.duration
            scheduleEncode()
        } catch LivePhotoExtractor.ExtractionError.insufficientMemory {
            // Hand off to the picker: it pops this screen and shows a toast.
            outOfMemoryPhotoCount = livePhotos.count
        } catch {
            errorMessage = Self.userMessage(
                "Couldn't read this Live Photo.", error: error)
        }
        isExtracting = false
    }

    private func clearStitchCache() {
        for url in stitchTempURLs {
            try? FileManager.default.removeItem(at: url)
        }
        stitchTempURLs = []
        stitchedAsset = nil
    }

    func scheduleEncode() {
        encodeTask?.cancel()
        exportedData = nil
        encodeTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(150))
            guard !Task.isCancelled else { return }
            await self?.encode()
        }
    }

    @MainActor
    private func encode() async {
        guard !extractedFrames.isEmpty else { return }
        isEncoding = true
        errorMessage = nil

        let frames = extractedFrames
        let cfg = config

        do {
            let data = try await Task.detached {
                try GIFEncoder.encode(frames: frames, config: cfg, progress: { _ in })
            }.value
            guard !Task.isCancelled else { return }
            exportedData = data
        } catch {
            if !Task.isCancelled {
                errorMessage = Self.userMessage(
                    "Couldn't encode the GIF.", error: error)
            }
        }
        isEncoding = false
    }

    @MainActor
    func reExtractIfNeeded() async {
        await extractFrames()
    }

    func tempFileForShare() -> URL? {
        guard let data = exportedData else { return nil }
        if let prev = lastShareTempURL {
            try? FileManager.default.removeItem(at: prev)
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("giffer-\(UUID().uuidString).gif")
        try? data.write(to: url)
        lastShareTempURL = url
        return url
    }
}
