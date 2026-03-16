import SwiftUI

struct CropOverlayView: View {
    @Binding var cropRect: CGRect
    let imageAspectRatio: CGFloat

    @State private var initialRect: CGRect = .zero
    @State private var isDragging = false
    @State private var activeHandle: CropHandle? = nil

    private let handleHitSize: CGFloat = 44
    private let handleVisualLength: CGFloat = 20
    private let handleThickness: CGFloat = 3

    var body: some View {
        GeometryReader { geo in
            let imgRect = aspectFitRect(in: geo.size)

            ZStack {
                // Dimmed region outside crop
                Canvas { context, size in
                    let fullRect = CGRect(origin: .zero, size: size)
                    let pixelCrop = cropToPixels(imgRect: imgRect)

                    var path = Path(fullRect)
                    path.addPath(Path(pixelCrop))
                    context.fill(path, with: .color(.black.opacity(0.5)), style: FillStyle(eoFill: true))
                }
                .allowsHitTesting(false)

                // Grid + border + handles
                Canvas { context, _ in
                    let cr = cropToPixels(imgRect: imgRect)

                    // Border
                    context.stroke(Path(cr), with: .color(.white), lineWidth: 1)

                    // Rule of thirds
                    let lineColor = Color.white.opacity(isDragging ? 0.5 : 0.25)
                    for i in 1...2 {
                        let f = CGFloat(i) / 3.0
                        var vPath = Path()
                        vPath.move(to: CGPoint(x: cr.minX + f * cr.width, y: cr.minY))
                        vPath.addLine(to: CGPoint(x: cr.minX + f * cr.width, y: cr.maxY))
                        context.stroke(vPath, with: .color(lineColor), lineWidth: 0.5)

                        var hPath = Path()
                        hPath.move(to: CGPoint(x: cr.minX, y: cr.minY + f * cr.height))
                        hPath.addLine(to: CGPoint(x: cr.maxX, y: cr.minY + f * cr.height))
                        context.stroke(hPath, with: .color(lineColor), lineWidth: 0.5)
                    }

                    // L-shaped corner handles
                    for corner in CropHandle.corners {
                        let pos = handlePosition(corner, cr: cr)
                        let (hd, vd) = corner.lDirections
                        var p = Path()
                        p.addRect(CGRect(
                            x: pos.x + (hd < 0 ? hd * handleVisualLength : 0),
                            y: pos.y - handleThickness / 2,
                            width: handleVisualLength,
                            height: handleThickness
                        ))
                        p.addRect(CGRect(
                            x: pos.x - handleThickness / 2,
                            y: pos.y + (vd < 0 ? vd * handleVisualLength : 0),
                            width: handleThickness,
                            height: handleVisualLength
                        ))
                        context.fill(p, with: .color(.white))
                    }

                    // Edge midpoint handles
                    for edge in CropHandle.edges {
                        let pos = handlePosition(edge, cr: cr)
                        let isH = (edge == .top || edge == .bottom)
                        let rect = CGRect(
                            x: pos.x - (isH ? 18 : handleThickness / 2),
                            y: pos.y - (isH ? handleThickness / 2 : 18),
                            width: isH ? 36 : handleThickness,
                            height: isH ? handleThickness : 36
                        )
                        context.fill(Path(roundedRect: rect, cornerRadius: handleThickness / 2), with: .color(.white))
                    }
                }
                .allowsHitTesting(false)

                // Gesture layer: single-finger drag for handles/move
                Color.clear
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0, coordinateSpace: .local)
                            .onChanged { value in
                                let cr = cropToPixels(imgRect: imgRect)

                                if !isDragging {
                                    isDragging = true
                                    initialRect = cropRect
                                    activeHandle = hitTest(point: value.startLocation, cropPixels: cr)
                                }

                                let normalized = pixelToNormalized(value.location, imgRect: imgRect)
                                let clamped = CGPoint(
                                    x: max(0, min(1, normalized.x)),
                                    y: max(0, min(1, normalized.y))
                                )

                                guard let handle = activeHandle else { return }

                                if handle == .move {
                                    let startNorm = pixelToNormalized(value.startLocation, imgRect: imgRect)
                                    let dx = clamped.x - max(0, min(1, startNorm.x))
                                    let dy = clamped.y - max(0, min(1, startNorm.y))
                                    var r = initialRect
                                    r.origin.x = clamp(initialRect.origin.x + dx, 0, 1 - r.width)
                                    r.origin.y = clamp(initialRect.origin.y + dy, 0, 1 - r.height)
                                    cropRect = r
                                } else {
                                    cropRect = handle.resize(initial: initialRect, to: clamped)
                                }
                            }
                            .onEnded { _ in
                                isDragging = false
                                activeHandle = nil
                            }
                    )
            }
        }
    }

    // MARK: - Aspect-fit rect computation

    private func aspectFitRect(in size: CGSize) -> CGRect {
        guard size.width > 0, size.height > 0, imageAspectRatio > 0 else {
            return CGRect(origin: .zero, size: size)
        }
        let viewAspect = size.width / size.height
        let fitW: CGFloat
        let fitH: CGFloat
        if imageAspectRatio > viewAspect {
            fitW = size.width
            fitH = size.width / imageAspectRatio
        } else {
            fitH = size.height
            fitW = size.height * imageAspectRatio
        }
        return CGRect(
            x: (size.width - fitW) / 2,
            y: (size.height - fitH) / 2,
            width: fitW,
            height: fitH
        )
    }

    // MARK: - Coordinate helpers

    private func cropToPixels(imgRect: CGRect) -> CGRect {
        CGRect(
            x: imgRect.origin.x + cropRect.origin.x * imgRect.width,
            y: imgRect.origin.y + cropRect.origin.y * imgRect.height,
            width: cropRect.width * imgRect.width,
            height: cropRect.height * imgRect.height
        )
    }

    private func pixelToNormalized(_ point: CGPoint, imgRect: CGRect) -> CGPoint {
        CGPoint(
            x: (point.x - imgRect.origin.x) / max(1, imgRect.width),
            y: (point.y - imgRect.origin.y) / max(1, imgRect.height)
        )
    }

    // MARK: - Hit testing

    private func hitTest(point: CGPoint, cropPixels cr: CGRect) -> CropHandle {
        let thresh = handleHitSize / 2

        for corner in CropHandle.corners {
            let pos = handlePosition(corner, cr: cr)
            if abs(point.x - pos.x) < thresh && abs(point.y - pos.y) < thresh {
                return corner
            }
        }

        for edge in CropHandle.edges {
            let pos = handlePosition(edge, cr: cr)
            switch edge {
            case .top, .bottom:
                if abs(point.y - pos.y) < thresh && point.x > cr.minX && point.x < cr.maxX {
                    return edge
                }
            case .left, .right:
                if abs(point.x - pos.x) < thresh && point.y > cr.minY && point.y < cr.maxY {
                    return edge
                }
            default: break
            }
        }

        return .move
    }

    private func handlePosition(_ handle: CropHandle, cr: CGRect) -> CGPoint {
        switch handle {
        case .topLeft:     return CGPoint(x: cr.minX, y: cr.minY)
        case .topRight:    return CGPoint(x: cr.maxX, y: cr.minY)
        case .bottomLeft:  return CGPoint(x: cr.minX, y: cr.maxY)
        case .bottomRight: return CGPoint(x: cr.maxX, y: cr.maxY)
        case .top:         return CGPoint(x: cr.midX, y: cr.minY)
        case .bottom:      return CGPoint(x: cr.midX, y: cr.maxY)
        case .left:        return CGPoint(x: cr.minX, y: cr.midY)
        case .right:       return CGPoint(x: cr.maxX, y: cr.midY)
        case .move:        return CGPoint(x: cr.midX, y: cr.midY)
        }
    }

    private func clamp(_ v: CGFloat, _ lo: CGFloat, _ hi: CGFloat) -> CGFloat {
        max(lo, min(hi, v))
    }
}

// MARK: - Handle enum

private enum CropHandle {
    case topLeft, topRight, bottomLeft, bottomRight
    case top, bottom, left, right
    case move

    static let corners: [CropHandle] = [.topLeft, .topRight, .bottomLeft, .bottomRight]
    static let edges: [CropHandle] = [.top, .bottom, .left, .right]

    var lDirections: (CGFloat, CGFloat) {
        switch self {
        case .topLeft:     return ( 1,  1)
        case .topRight:    return (-1,  1)
        case .bottomLeft:  return ( 1, -1)
        case .bottomRight: return (-1, -1)
        default:           return ( 0,  0)
        }
    }

    private static let minSize: CGFloat = 0.05

    func resize(initial: CGRect, to point: CGPoint) -> CGRect {
        let ms = CropHandle.minSize
        switch self {
        case .topLeft:
            let w = max(ms, initial.maxX - point.x)
            let h = max(ms, initial.maxY - point.y)
            return CGRect(x: initial.maxX - w, y: initial.maxY - h, width: w, height: h)
        case .topRight:
            let w = max(ms, point.x - initial.minX)
            let h = max(ms, initial.maxY - point.y)
            return CGRect(x: initial.minX, y: initial.maxY - h, width: w, height: h)
        case .bottomLeft:
            let w = max(ms, initial.maxX - point.x)
            let h = max(ms, point.y - initial.minY)
            return CGRect(x: initial.maxX - w, y: initial.minY, width: w, height: h)
        case .bottomRight:
            let w = max(ms, point.x - initial.minX)
            let h = max(ms, point.y - initial.minY)
            return CGRect(x: initial.minX, y: initial.minY, width: w, height: h)
        case .top:
            let h = max(ms, initial.maxY - point.y)
            return CGRect(x: initial.minX, y: initial.maxY - h, width: initial.width, height: h)
        case .bottom:
            let h = max(ms, point.y - initial.minY)
            return CGRect(x: initial.minX, y: initial.minY, width: initial.width, height: h)
        case .left:
            let w = max(ms, initial.maxX - point.x)
            return CGRect(x: initial.maxX - w, y: initial.minY, width: w, height: initial.height)
        case .right:
            let w = max(ms, point.x - initial.minX)
            return CGRect(x: initial.minX, y: initial.minY, width: w, height: initial.height)
        case .move:
            return initial
        }
    }
}
