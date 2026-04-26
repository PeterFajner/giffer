import Testing
import Foundation
@testable import Giffer

@Suite("SharedConstants")
struct SharedConstantsTests {

    @Test("appGroupIdentifier matches expected value")
    func appGroupIdentifier() {
        #expect(SharedConstants.appGroupIdentifier == "group.ca.pfaj.giffer")
    }

    @Test("urlScheme matches expected value")
    func urlScheme() {
        #expect(SharedConstants.urlScheme == "giffer")
    }

    @Test("share callback URL roundtrips through the documented format")
    func urlRoundTrip() {
        let filename = "ABC-123.mov"
        let urlString = "\(SharedConstants.urlScheme)://share?file=\(filename)"
        let url = URL(string: urlString)
        #expect(url != nil)

        let components = URLComponents(url: url!, resolvingAgainstBaseURL: false)
        #expect(url!.scheme == "giffer")
        #expect(url!.host == "share")
        let received = components?.queryItems?.first(where: { $0.name == "file" })?.value
        #expect(received == filename)
    }

    @Test("cleanupOldSharedFiles removes files older than 1 hour and keeps newer ones")
    func cleanupOldFiles() throws {
        let fm = FileManager.default
        let testDir = fm.temporaryDirectory
            .appendingPathComponent("SharedConstantsTests-\(UUID().uuidString)")
        try fm.createDirectory(at: testDir, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: testDir) }

        let oldFile = testDir.appendingPathComponent("old.mov")
        let newFile = testDir.appendingPathComponent("new.mov")
        try Data().write(to: oldFile)
        try Data().write(to: newFile)
        // Backdate the old file to 2 hours ago
        let twoHoursAgo = Date().addingTimeInterval(-7200)
        try fm.setAttributes([.creationDate: twoHoursAgo], ofItemAtPath: oldFile.path)

        TestSupport.runCleanup(in: testDir)

        #expect(!fm.fileExists(atPath: oldFile.path))
        #expect(fm.fileExists(atPath: newFile.path))
    }
}
