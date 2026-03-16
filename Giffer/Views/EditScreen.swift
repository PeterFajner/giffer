import SwiftUI

struct EditScreen: View {
    @Bindable var viewModel: EditorViewModel

    @State private var enabledTools: Set<EditorTool> = []
    @State private var selectedTool: EditorTool? = nil

    @State private var savedFPS: Int = 12
    @State private var savedScale: CGFloat = 1.0
    @State private var savedTrimStart: Double = 0.0
    @State private var savedTrimEnd: Double = 1.0

    @State private var isCropMode = false
    @State private var cropRect = CGRect(x: 0, y: 0, width: 1, height: 1)
    @State private var showShareSheet = false
    @State private var debounceTask: Task<Void, Never>?

    private static let defaultFPS = 12
    private static let defaultScale: CGFloat = 1.0

    private var hasCrop: Bool {
        cropRect.origin.x > 0.01 || cropRect.origin.y > 0.01
            || cropRect.width < 0.99 || cropRect.height < 0.99
    }

    private var previewCrop: CGRect? {
        if isCropMode { return nil }
        if !hasCrop { return nil }
        return cropRect
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            previewArea
            bottomPanel
        }
        .background(.black)
        .preferredColorScheme(.dark)
        .navigationTitle("Edit")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.black, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .sheet(isPresented: $showShareSheet) {
            if let url = viewModel.tempFileForShare() {
                ShareSheet(url: url)
            }
        }
        .onChange(of: isCropMode) { _, _ in
            viewModel.config.cropRect = hasCrop ? cropRect : nil
            viewModel.scheduleEncode()
        }
        .onChange(of: cropRect) { _, _ in
            viewModel.config.cropRect = hasCrop ? cropRect : nil
            viewModel.scheduleEncode()
        }
        .onChange(of: viewModel.config.resolutionScale) { _, _ in scheduleReExtract() }
        .onChange(of: viewModel.config.trimStart) { _, _ in scheduleReExtract() }
        .onChange(of: viewModel.config.trimEnd) { _, _ in scheduleReExtract() }
        .onChange(of: viewModel.config.fps) { _, _ in viewModel.scheduleEncode() }
        .onChange(of: viewModel.config.playbackMode) { _, _ in viewModel.scheduleEncode() }
        .onAppear { restoreToolStates() }
    }

    // MARK: - Preview

    private var previewArea: some View {
        ZStack {
            Color.black

            GIFPreviewView(
                frames: viewModel.previewFrames,
                fps: viewModel.config.fps,
                cropRect: previewCrop
            )
            .padding(16)

            if isCropMode {
                CropOverlayView(
                    cropRect: $cropRect,
                    imageAspectRatio: viewModel.imageAspectRatio
                )
                .padding(16)
            }

            if viewModel.isExtracting {
                ProgressView("Extracting…")
                    .tint(.white)
                    .foregroundStyle(.white)
                    .padding(16)
                    .background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 10))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Top Bar

    private var topBar: some View {
        HStack(spacing: 10) {
            playbackButtons

            Spacer()

            infoText

            shareButton
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color(white: 0.1))
    }

    private var playbackButtons: some View {
        HStack(spacing: 2) {
            ForEach(PlaybackMode.allCases) { mode in
                let isCurrent = viewModel.config.playbackMode == mode
                Button {
                    viewModel.config.playbackMode = mode
                } label: {
                    Image(systemName: mode.icon)
                        .font(.system(size: 14, weight: .medium))
                        .frame(width: 36, height: 30)
                        .background(isCurrent ? Color.white.opacity(0.2) : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .foregroundStyle(isCurrent ? .white : .white.opacity(0.5))
                }
            }
        }
        .padding(2)
        .background(Color.white.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var infoText: some View {
        let dims = viewModel.scaledDimensions
        return HStack(spacing: 8) {
            Text("\(dims.width)×\(dims.height)")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
            Text(viewModel.formattedFileSize)
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
        }
    }

    private var shareButton: some View {
        Button {
            showShareSheet = true
        } label: {
            Group {
                if viewModel.isEncoding {
                    ProgressView()
                        .tint(.black)
                        .scaleEffect(0.7)
                } else {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 15, weight: .semibold))
                }
            }
            .frame(width: 36, height: 30)
            .background(viewModel.canShare ? Color.yellow : Color.gray)
            .foregroundStyle(.black)
            .clipShape(RoundedRectangle(cornerRadius: 6))
        }
        .disabled(!viewModel.canShare)
    }

    // MARK: - Bottom Panel

    private var bottomPanel: some View {
        VStack(spacing: 0) {
            if let tool = selectedTool {
                toolControl(for: tool)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }

            if let error = viewModel.errorMessage {
                Text(error)
                    .foregroundStyle(.red)
                    .font(.caption)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 4)
            }

            if isCropMode && hasCrop {
                HStack {
                    Button("Reset Crop") {
                        cropRect = CGRect(x: 0, y: 0, width: 1, height: 1)
                    }
                    .font(.caption)
                    .foregroundStyle(.yellow)
                    Spacer()
                }
                .padding(.horizontal, 24)
                .padding(.top, 12)
                .transition(.opacity)
            }

            toolStrip
                .padding(.horizontal, 24)
                .padding(.top, 8)
                .padding(.bottom, 16)
        }
        .background(Color(white: 0.1))
    }

    // MARK: - Tool Strip

    private var toolStrip: some View {
        HStack(spacing: 0) {
            ForEach(EditorTool.allCases) { tool in
                toolButton(tool)
            }
            cropButton
        }
    }

    private func toolButton(_ tool: EditorTool) -> some View {
        let isEnabled = enabledTools.contains(tool)
        let isSelected = selectedTool == tool
        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                handleToolTap(tool)
            }
        } label: {
            VStack(spacing: 4) {
                ZStack {
                    Circle()
                        .fill(circleColor(enabled: isEnabled, selected: isSelected))
                        .frame(width: 44, height: 44)
                    Image(systemName: tool.icon)
                        .font(.system(size: 18))
                        .foregroundStyle(iconColor(enabled: isEnabled, selected: isSelected))
                }
                Text(tool.label)
                    .font(.caption2)
                    .foregroundStyle(labelColor(enabled: isEnabled, selected: isSelected))
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var cropButton: some View {
        let isSelected = isCropMode
        let isEnabled = hasCrop
        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                if isCropMode {
                    isCropMode = false
                } else {
                    if let prev = selectedTool, enabledTools.contains(prev), isToolAtDefault(prev) {
                        enabledTools.remove(prev)
                    }
                    selectedTool = nil
                    isCropMode = true
                }
            }
        } label: {
            VStack(spacing: 4) {
                ZStack {
                    Circle()
                        .fill(circleColor(enabled: isEnabled, selected: isSelected))
                        .frame(width: 44, height: 44)
                    Image(systemName: "crop")
                        .font(.system(size: 18))
                        .foregroundStyle(iconColor(enabled: isEnabled, selected: isSelected))
                }
                Text("Crop")
                    .font(.caption2)
                    .foregroundStyle(labelColor(enabled: isEnabled, selected: isSelected))
            }
            .frame(maxWidth: .infinity)
        }
    }

    // MARK: - Tool button colors

    private func circleColor(enabled: Bool, selected: Bool) -> Color {
        if selected { return .yellow }
        if enabled  { return .white.opacity(0.25) }
        return .white.opacity(0.1)
    }

    private func iconColor(enabled: Bool, selected: Bool) -> Color {
        if selected { return .black }
        if enabled  { return .white }
        return .white.opacity(0.5)
    }

    private func labelColor(enabled: Bool, selected: Bool) -> Color {
        if selected { return .yellow }
        if enabled  { return .white.opacity(0.8) }
        return .white.opacity(0.4)
    }

    // MARK: - Tool tap logic

    private func handleToolTap(_ tool: EditorTool) {
        if isCropMode { isCropMode = false }

        if let prev = selectedTool, prev != tool, enabledTools.contains(prev), isToolAtDefault(prev) {
            enabledTools.remove(prev)
        }

        let isEnabled = enabledTools.contains(tool)
        let isSelected = selectedTool == tool

        if isSelected {
            deactivateTool(tool)
            selectedTool = nil
        } else if isEnabled {
            selectedTool = tool
        } else {
            activateTool(tool)
            selectedTool = tool
        }
    }

    private func isToolAtDefault(_ tool: EditorTool) -> Bool {
        switch tool {
        case .trim:
            return abs(viewModel.config.trimStart) < 0.001
                && abs(viewModel.config.trimEnd - 1.0) < 0.001
        case .speed:
            return viewModel.config.fps == Self.defaultFPS
        case .quality:
            return abs(viewModel.config.resolutionScale - Self.defaultScale) < 0.01
        }
    }

    private func activateTool(_ tool: EditorTool) {
        enabledTools.insert(tool)
        switch tool {
        case .trim:
            viewModel.config.trimStart = savedTrimStart
            viewModel.config.trimEnd = savedTrimEnd
        case .speed:
            viewModel.config.fps = savedFPS
        case .quality:
            viewModel.config.resolutionScale = savedScale
        }
    }

    private func deactivateTool(_ tool: EditorTool) {
        switch tool {
        case .trim:
            savedTrimStart = viewModel.config.trimStart
            savedTrimEnd = viewModel.config.trimEnd
            viewModel.config.trimStart = 0.0
            viewModel.config.trimEnd = 1.0
        case .speed:
            savedFPS = viewModel.config.fps
            viewModel.config.fps = Self.defaultFPS
        case .quality:
            savedScale = viewModel.config.resolutionScale
            viewModel.config.resolutionScale = Self.defaultScale
        }
        enabledTools.remove(tool)
    }

    // MARK: - Tool Controls

    @ViewBuilder
    private func toolControl(for tool: EditorTool) -> some View {
        switch tool {
        case .trim:
            TrimSliderView(
                trimStart: $viewModel.config.trimStart,
                trimEnd: $viewModel.config.trimEnd,
                frames: viewModel.extractedFrames
            )
        case .speed:
            speedSlider
        case .quality:
            qualitySlider
        }
    }

    private var speedSlider: some View {
        VStack(spacing: 2) {
            HStack {
                Text("6").font(.caption2).foregroundStyle(.secondary)
                Spacer()
                Text("\(viewModel.config.fps) fps")
                    .font(.caption.monospacedDigit().weight(.medium))
                Spacer()
                Text("24").font(.caption2).foregroundStyle(.secondary)
            }

            GeometryReader { geo in
                let fraction = CGFloat(Self.defaultFPS - 6) / CGFloat(24 - 6)
                let inset: CGFloat = 14
                let xPos = inset + fraction * (geo.size.width - inset * 2)
                Circle()
                    .fill(Color.white.opacity(0.3))
                    .frame(width: 5, height: 5)
                    .position(x: xPos, y: geo.size.height / 2)
            }
            .frame(height: 8)
            .allowsHitTesting(false)

            Slider(
                value: Binding(
                    get: { Double(viewModel.config.fps) },
                    set: { viewModel.config.fps = Int($0) }
                ),
                in: 6...24,
                step: 1
            )
            .tint(.yellow)
        }
    }

    private var qualitySlider: some View {
        VStack(spacing: 4) {
            let dims = viewModel.scaledDimensions
            HStack {
                Text("Small").font(.caption2).foregroundStyle(.secondary)
                Spacer()
                Text("\(dims.width)×\(dims.height)")
                    .font(.caption.monospacedDigit().weight(.medium))
                Spacer()
                Text("Full").font(.caption2).foregroundStyle(.secondary)
            }
            Slider(value: $viewModel.config.resolutionScale, in: 0.1...1.0, step: 0.05)
                .tint(.yellow)
        }
    }

    // MARK: - Helpers

    private func restoreToolStates() {
        let cfg = viewModel.config
        if abs(cfg.trimStart) > 0.001 || abs(cfg.trimEnd - 1.0) > 0.001 {
            enabledTools.insert(.trim)
            savedTrimStart = cfg.trimStart
            savedTrimEnd = cfg.trimEnd
        }
        if cfg.fps != Self.defaultFPS {
            enabledTools.insert(.speed)
            savedFPS = cfg.fps
        }
        if abs(cfg.resolutionScale - Self.defaultScale) > 0.01 {
            enabledTools.insert(.quality)
            savedScale = cfg.resolutionScale
        }
        if let crop = cfg.cropRect {
            cropRect = crop
        }
    }

    private func scheduleReExtract() {
        viewModel.exportedData = nil
        debounceTask?.cancel()
        debounceTask = Task {
            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled else { return }
            await viewModel.reExtractIfNeeded()
        }
    }
}

// MARK: - Share sheet

struct ShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
