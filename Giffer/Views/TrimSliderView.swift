import SwiftUI

struct TrimSliderView: View {
    @Binding var trimStart: Double
    @Binding var trimEnd: Double
    let frames: [CGImage]

    private let handleWidth: CGFloat = 16
    private let barHeight: CGFloat = 58
    private let cornerRadius: CGFloat = 8

    @State private var leftDragStart: Double?
    @State private var rightDragStart: Double?

    var body: some View {
        GeometryReader { geo in
            let totalWidth = geo.size.width
            let usableWidth = max(1, totalWidth - handleWidth * 2)
            let leftX = handleWidth + trimStart * usableWidth
            let rightX = handleWidth + trimEnd * usableWidth
            let selectedInner = max(0, rightX - leftX)

            ZStack(alignment: .leading) {
                // Filmstrip background
                filmstripBackground(width: totalWidth)
                    .clipShape(RoundedRectangle(cornerRadius: cornerRadius))

                // Dimmed regions outside trim
                HStack(spacing: 0) {
                    Rectangle()
                        .fill(.black.opacity(0.6))
                        .frame(width: max(0, leftX - handleWidth))
                    Spacer(minLength: 0)
                    Rectangle()
                        .fill(.black.opacity(0.6))
                        .frame(width: max(0, totalWidth - rightX - handleWidth))
                }
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius))

                // Yellow border around selected region
                let selectedWidth = selectedInner + handleWidth * 2
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(Color.yellow, lineWidth: 3)
                    .frame(width: selectedWidth, height: barHeight)
                    .offset(x: leftX - handleWidth)

                // Top and bottom yellow lines
                VStack {
                    Rectangle()
                        .fill(Color.yellow)
                        .frame(width: selectedInner, height: 3)
                    Spacer(minLength: 0)
                    Rectangle()
                        .fill(Color.yellow)
                        .frame(width: selectedInner, height: 3)
                }
                .offset(x: leftX)

                // Left handle
                trimHandle(isLeading: true)
                    .offset(x: leftX - handleWidth)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                if leftDragStart == nil {
                                    leftDragStart = trimStart
                                }
                                guard let start = leftDragStart else { return }
                                let delta = value.translation.width / usableWidth
                                trimStart = (start + delta).clamped(to: 0...max(0, trimEnd - 0.02))
                            }
                            .onEnded { _ in leftDragStart = nil }
                    )

                // Right handle
                trimHandle(isLeading: false)
                    .offset(x: rightX)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                if rightDragStart == nil {
                                    rightDragStart = trimEnd
                                }
                                guard let start = rightDragStart else { return }
                                let delta = value.translation.width / usableWidth
                                trimEnd = (start + delta).clamped(to: min(1, trimStart + 0.02)...1.0)
                            }
                            .onEnded { _ in rightDragStart = nil }
                    )
            }
            .frame(height: barHeight)
        }
        .frame(height: barHeight)
    }

    private func filmstripBackground(width: CGFloat) -> some View {
        HStack(spacing: 0) {
            if frames.count < 2 {
                Rectangle().fill(.gray.opacity(0.3))
            } else {
                let thumbCount = max(1, Int(width / 40))
                ForEach(0..<thumbCount, id: \.self) { i in
                    let frameIndex = i * (frames.count - 1) / max(1, thumbCount - 1)
                    let safeIndex = min(max(0, frameIndex), frames.count - 1)
                    Image(decorative: frames[safeIndex], scale: 1.0)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: width / CGFloat(thumbCount), height: barHeight)
                        .clipped()
                }
            }
        }
    }

    private func trimHandle(isLeading: Bool) -> some View {
        RoundedRectangle(cornerRadius: cornerRadius)
            .fill(Color.yellow)
            .frame(width: handleWidth, height: barHeight)
            .overlay {
                Image(systemName: isLeading ? "chevron.compact.left" : "chevron.compact.right")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.black.opacity(0.7))
            }
            .contentShape(Rectangle())
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
