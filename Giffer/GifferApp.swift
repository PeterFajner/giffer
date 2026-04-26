import SwiftUI

@main
struct GifferApp: App {
    @State private var viewModel = EditorViewModel()
    @State private var editorRoute: UUID? = nil

    init() {
        Task.detached(priority: .background) {
            SharedConstants.cleanupOldSharedFiles()
        }
    }

    var body: some Scene {
        WindowGroup {
            NavigationStack {
                PickerScreen(editorRoute: $editorRoute, viewModel: viewModel)
            }
            .onOpenURL { url in
                handleIncomingURL(url)
            }
        }
    }

    private func handleIncomingURL(_ url: URL) {
        guard url.scheme == SharedConstants.urlScheme,
              url.host == "share",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let filename = components.queryItems?.first(where: { $0.name == "file" })?.value,
              let sharedDir = SharedConstants.sharedDirectory
        else { return }

        // Any app on the device can call giffer://share?file=...; restrict to
        // the exact filename shape the share extension produces so a malicious
        // caller can't path-traverse out of SharedLivePhotos.
        guard filename.hasSuffix(".mov"),
              UUID(uuidString: String(filename.dropLast(4))) != nil
        else { return }

        let videoURL = sharedDir.appendingPathComponent(filename)
        guard FileManager.default.fileExists(atPath: videoURL.path) else { return }

        viewModel.loadFromVideoURL(videoURL)
        editorRoute = UUID()

        Task.detached(priority: .background) {
            SharedConstants.cleanupOldSharedFiles()
        }
    }
}
