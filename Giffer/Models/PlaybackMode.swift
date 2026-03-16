import Foundation

enum PlaybackMode: String, CaseIterable, Identifiable {
    case forward = "Forward"
    case reverse = "Reverse"
    case bounce = "Bounce"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .forward: return "play"
        case .reverse: return "backward"
        case .bounce:  return "repeat"
        }
    }
}
