import Foundation

enum EditorTool: String, CaseIterable, Identifiable {
    case trim
    case speed
    case quality

    var id: String { rawValue }

    var label: String {
        switch self {
        case .trim:    return "Trim"
        case .speed:   return "Speed"
        case .quality: return "Size"
        }
    }

    var icon: String {
        switch self {
        case .trim:    return "scissors"
        case .speed:   return "gauge.with.needle"
        case .quality: return "arrow.up.right.and.arrow.down.left"
        }
    }
}
