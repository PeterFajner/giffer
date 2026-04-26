import Foundation
import CoreGraphics
@testable import Giffer

enum TestSupport {

    static func solidImage(width: Int, height: Int, gray: CGFloat) -> CGImage {
        let colorSpace = CGColorSpaceCreateDeviceGray()
        let context = CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.none.rawValue
        )!
        context.setFillColor(gray: gray, alpha: 1.0)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        return context.makeImage()!
    }

    /// Re-implements the cleanup logic used by SharedConstants.cleanupOldSharedFiles
    /// against an arbitrary directory, so we can test the "older than 1 hour" rule
    /// without touching the real shared-app-group container.
    static func runCleanup(in directory: URL) {
        let cutoff = Date().addingTimeInterval(-3600)
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: [.creationDateKey]
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
