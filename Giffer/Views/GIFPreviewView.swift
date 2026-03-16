import SwiftUI

struct GIFPreviewView: View {
    let frames: [CGImage]
    let fps: Int
    var cropRect: CGRect? = nil

    @State private var currentIndex = 0
    @State private var timer: Timer?

    private var safeIndex: Int {
        guard !frames.isEmpty else { return 0 }
        return currentIndex % frames.count
    }

    var body: some View {
        Group {
            if frames.isEmpty {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(white: 0.15))
                    .overlay {
                        ProgressView()
                            .tint(.white)
                    }
            } else {
                frameImage(frames[safeIndex])
            }
        }
        .frame(maxWidth: .infinity)
        .onAppear { startTimer() }
        .onDisappear { stopTimer() }
        .onChange(of: fps) { _, _ in restartTimer() }
        .onChange(of: frames.count) { _, newCount in
            if newCount == 0 || currentIndex >= newCount {
                currentIndex = 0
            }
            restartTimer()
        }
    }

    @ViewBuilder
    private func frameImage(_ frame: CGImage) -> some View {
        if let crop = cropRect, let cropped = cropCGImage(frame, to: crop) {
            Image(decorative: cropped, scale: 1.0)
                .resizable()
                .aspectRatio(contentMode: .fit)
        } else {
            Image(decorative: frame, scale: 1.0)
                .resizable()
                .aspectRatio(contentMode: .fit)
        }
    }

    private func cropCGImage(_ image: CGImage, to rect: CGRect) -> CGImage? {
        let pixelRect = CGRect(
            x: CGFloat(image.width) * rect.origin.x,
            y: CGFloat(image.height) * rect.origin.y,
            width: CGFloat(image.width) * rect.width,
            height: CGFloat(image.height) * rect.height
        )
        guard pixelRect.width >= 1, pixelRect.height >= 1 else { return nil }
        return image.cropping(to: pixelRect)
    }

    private func startTimer() {
        stopTimer()
        guard frames.count > 1 else { return }
        timer = Timer.scheduledTimer(withTimeInterval: 1.0 / Double(max(1, fps)), repeats: true) { _ in
            Task { @MainActor in
                guard frames.count > 1 else { return }
                currentIndex = (currentIndex + 1) % frames.count
            }
        }
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }

    private func restartTimer() {
        stopTimer()
        startTimer()
    }
}
