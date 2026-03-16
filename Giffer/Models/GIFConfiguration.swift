import CoreGraphics

struct GIFConfiguration {
    var resolutionScale: CGFloat = 1.0
    var fps: Int = 12
    var trimStart: Double = 0.0
    var trimEnd: Double = 1.0
    var cropRect: CGRect? = nil
    var playbackMode: PlaybackMode = .forward

    var trimRange: ClosedRange<Double> {
        trimStart...trimEnd
    }
}
