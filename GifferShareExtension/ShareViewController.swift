import UIKit
import UniformTypeIdentifiers
import Photos
import PhotosUI

class ShareViewController: UIViewController {

    private let spinner = UIActivityIndicatorView(style: .large)

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        spinner.translatesAutoresizingMaskIntoConstraints = false
        spinner.startAnimating()
        view.addSubview(spinner)
        NSLayoutConstraint.activate([
            spinner.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])

        SharedConstants.cleanupOldSharedFiles()
        processSharedItem()
    }

    private func processSharedItem() {
        guard let extensionItem = extensionContext?.inputItems.first as? NSExtensionItem,
              let attachments = extensionItem.attachments else {
            cancelWithError("No items received")
            return
        }

        Task {
            do {
                let videoURL = try await extractVideoFromAttachments(attachments)
                let sharedURL = try saveToSharedContainer(videoURL)
                openMainApp(filename: sharedURL.lastPathComponent)
            } catch {
                cancelWithError(error.localizedDescription)
            }
        }
    }

    // MARK: - Video Extraction

    private func extractVideoFromAttachments(_ providers: [NSItemProvider]) async throws -> URL {
        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.livePhoto.identifier) {
                let livePhoto = try await loadLivePhoto(from: provider)
                return try await extractVideoURL(from: livePhoto)
            }
        }
        throw ShareError.notALivePhoto
    }

    private func loadLivePhoto(from provider: NSItemProvider) async throws -> PHLivePhoto {
        try await withCheckedThrowingContinuation { continuation in
            provider.loadObject(ofClass: PHLivePhoto.self) { object, error in
                if let livePhoto = object as? PHLivePhoto {
                    continuation.resume(returning: livePhoto)
                } else {
                    continuation.resume(throwing: error ?? ShareError.notALivePhoto)
                }
            }
        }
    }

    private func extractVideoURL(from livePhoto: PHLivePhoto) async throws -> URL {
        let resources = PHAssetResource.assetResources(for: livePhoto)
        guard let videoResource = resources.first(where: { $0.type == .pairedVideo }) else {
            throw ShareError.notALivePhoto
        }

        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".mov")

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let options = PHAssetResourceRequestOptions()
            options.isNetworkAccessAllowed = true
            PHAssetResourceManager.default().writeData(
                for: videoResource, toFile: tempURL, options: options
            ) { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume() }
            }
        }

        return tempURL
    }

    // MARK: - Shared Container

    private func saveToSharedContainer(_ videoURL: URL) throws -> URL {
        guard let sharedDir = SharedConstants.sharedDirectory else {
            throw ShareError.noSharedContainer
        }
        let filename = UUID().uuidString + ".mov"
        let destinationURL = sharedDir.appendingPathComponent(filename)
        // Copy to .partial then rename so the main app never observes a half-written file
        // if the extension is suspended mid-copy.
        let partialURL = destinationURL.appendingPathExtension("partial")
        try FileManager.default.copyItem(at: videoURL, to: partialURL)
        try FileManager.default.moveItem(at: partialURL, to: destinationURL)
        try? FileManager.default.removeItem(at: videoURL)
        return destinationURL
    }

    // MARK: - Open Main App

    private func openMainApp(filename: String) {
        let urlString = "\(SharedConstants.urlScheme)://share?file=\(filename)"
        guard let url = URL(string: urlString) else {
            cancelWithError("Invalid URL")
            return
        }

        extensionContext?.open(url) { [weak self] success in
            if success {
                self?.extensionContext?.completeRequest(returningItems: nil)
            } else {
                self?.cancelWithError("Could not open Giffer")
            }
        }
    }

    private func cancelWithError(_ message: String) {
        let error = NSError(
            domain: "ca.pfaj.giffer.share", code: 0,
            userInfo: [NSLocalizedDescriptionKey: message]
        )
        extensionContext?.cancelRequest(withError: error)
    }

    // MARK: - Errors

    enum ShareError: LocalizedError {
        case notALivePhoto
        case noSharedContainer

        var errorDescription: String? {
            switch self {
            case .notALivePhoto: return "Giffer only supports Live Photos"
            case .noSharedContainer: return "Could not access shared storage"
            }
        }
    }
}
