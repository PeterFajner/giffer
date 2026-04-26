import Testing
import CoreGraphics
import ImageIO
@testable import Giffer

@Suite("GIFEncoder.encode")
struct GIFEncoderTests {

    @Test("produces a valid GIF with correct frame count")
    func encodesValidGIF() throws {
        let frames = (0..<4).map { _ in TestSupport.solidImage(width: 8, height: 8, gray: 0.3) }
        var config = GIFConfiguration()
        config.fps = 12
        config.playbackMode = .forward

        let data = try GIFEncoder.encode(frames: frames, config: config, progress: { _ in })

        #expect(data.count > 0)
        // GIF magic
        #expect(data.starts(with: Array("GIF89a".utf8)) || data.starts(with: Array("GIF87a".utf8)))

        // Round-trip — read back frame count
        let cfData = data as CFData
        let source = CGImageSourceCreateWithData(cfData, nil)
        #expect(source != nil)
        #expect(CGImageSourceGetCount(source!) == 4)
    }

    @Test("bounce mode doubles output length minus endpoints")
    func bounceRoundTrip() throws {
        let frames = (0..<3).map { _ in TestSupport.solidImage(width: 4, height: 4, gray: 0.5) }
        var config = GIFConfiguration()
        config.playbackMode = .bounce

        let data = try GIFEncoder.encode(frames: frames, config: config, progress: { _ in })
        let source = CGImageSourceCreateWithData(data as CFData, nil)!
        // 3 frames bounce → 4 frames in output
        #expect(CGImageSourceGetCount(source) == 4)
    }

    @Test("empty frames throws noFrames")
    func emptyFramesThrows() {
        let config = GIFConfiguration()
        #expect(throws: GIFEncoder.EncodingError.self) {
            try GIFEncoder.encode(frames: [], config: config, progress: { _ in })
        }
    }

    @Test("crop applies before encode")
    func cropApplied() throws {
        let frames = (0..<2).map { _ in TestSupport.solidImage(width: 100, height: 100, gray: 0.5) }
        var config = GIFConfiguration()
        config.cropRect = CGRect(x: 0.25, y: 0.25, width: 0.5, height: 0.5)

        let data = try GIFEncoder.encode(frames: frames, config: config, progress: { _ in })
        let source = CGImageSourceCreateWithData(data as CFData, nil)!
        let firstFrame = CGImageSourceCreateImageAtIndex(source, 0, nil)!
        // 0.5 of 100 = 50
        #expect(firstFrame.width == 50)
        #expect(firstFrame.height == 50)
    }

    @Test("progress callback fires once per frame")
    func progressCallback() throws {
        let frames = (0..<5).map { _ in TestSupport.solidImage(width: 4, height: 4, gray: 0.5) }
        let config = GIFConfiguration()
        var values: [Double] = []
        _ = try GIFEncoder.encode(frames: frames, config: config) { values.append($0) }
        #expect(values.count == 5)
        #expect(values.last == 1.0)
    }
}
