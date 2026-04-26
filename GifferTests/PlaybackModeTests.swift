import Testing
import CoreGraphics
@testable import Giffer

@Suite("GIFEncoder.applyPlaybackMode")
struct PlaybackModeTests {

    private func makeFrames(_ count: Int) -> [CGImage] {
        (0..<count).map { _ in TestSupport.solidImage(width: 2, height: 2, gray: 0.5) }
    }

    @Test("forward returns frames unchanged")
    func forwardIdentity() {
        let frames = makeFrames(3)
        let result = GIFEncoder.applyPlaybackMode(frames: frames, mode: .forward)
        #expect(result.count == 3)
        #expect(result.map(ObjectIdentifier.init) == frames.map(ObjectIdentifier.init))
    }

    @Test("reverse reverses frame order")
    func reverseOrder() {
        let frames = makeFrames(3)
        let result = GIFEncoder.applyPlaybackMode(frames: frames, mode: .reverse)
        #expect(result.count == 3)
        #expect(result.map(ObjectIdentifier.init)
            == frames.reversed().map(ObjectIdentifier.init))
    }

    @Test("bounce on three+ frames does not duplicate endpoints")
    func bounceTriplet() {
        let frames = makeFrames(3)
        let result = GIFEncoder.applyPlaybackMode(frames: frames, mode: .bounce)
        // [a, b, c, b] — last frame and first frame appear once each
        #expect(result.count == 4)
        let ids = result.map(ObjectIdentifier.init)
        #expect(ids[0] == ObjectIdentifier(frames[0]))
        #expect(ids[1] == ObjectIdentifier(frames[1]))
        #expect(ids[2] == ObjectIdentifier(frames[2]))
        #expect(ids[3] == ObjectIdentifier(frames[1]))
    }

    @Test("bounce on four frames")
    func bounceQuad() {
        let frames = makeFrames(4)
        let result = GIFEncoder.applyPlaybackMode(frames: frames, mode: .bounce)
        // [a, b, c, d, c, b]
        #expect(result.count == 6)
    }

    @Test("bounce on empty input returns empty")
    func bounceEmpty() {
        let result = GIFEncoder.applyPlaybackMode(frames: [], mode: .bounce)
        #expect(result.isEmpty)
    }

    @Test("bounce on single frame")
    func bounceSingle() {
        let frames = makeFrames(1)
        let result = GIFEncoder.applyPlaybackMode(frames: frames, mode: .bounce)
        #expect(result.count == 2)
    }
}
