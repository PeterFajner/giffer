import Foundation

enum SharedConstants {
    static let appGroupIdentifier = "group.ca.pfaj.giffer"
    static let urlScheme = "giffer"
    static let sharedDirectoryName = "SharedLivePhotos"

    static var sharedContainerURL: URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier)
    }

    static var sharedDirectory: URL? {
        guard let container = sharedContainerURL else { return nil }
        let dir = container.appendingPathComponent(sharedDirectoryName)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// Cleans up shared files older than 1 hour
    static func cleanupOldSharedFiles() {
        guard let dir = sharedDirectory else { return }
        let cutoff = Date().addingTimeInterval(-3600)
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: dir, includingPropertiesForKeys: [.creationDateKey]
        ) else { return }
        for file in files {
            if let attrs = try? FileManager.default.attributesOfItem(atPath: file.path),
               let created = attrs[.creationDate] as? Date,
               created < cutoff {
                try? FileManager.default.removeItem(at: file)
            }
        }
    }
}
