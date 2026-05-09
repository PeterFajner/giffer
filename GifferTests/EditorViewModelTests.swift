import Testing
import Foundation
@testable import Giffer

@Suite("EditorViewModel")
@MainActor
struct EditorViewModelTests {

    @Test("fresh viewModel has expected defaults")
    func defaults() {
        let vm = EditorViewModel()
        #expect(vm.livePhoto == nil)
        #expect(vm.extractedFrames.isEmpty)
        #expect(vm.exportedData == nil)
        #expect(vm.isExtracting == false)
        #expect(vm.isEncoding == false)
        #expect(vm.errorMessage == nil)
        #expect(vm.config.fps == 12)
        #expect(vm.config.resolutionScale == 1.0)
        #expect(vm.config.playbackMode == .forward)
        #expect(vm.config.cropRect == nil)
    }

    @Test("scaledDimensions accounts for crop and resolution scale")
    func scaledDimensions() {
        let vm = EditorViewModel()
        vm.originalSize = CGSize(width: 1000, height: 500)
        vm.config.resolutionScale = 0.5
        vm.config.cropRect = CGRect(x: 0, y: 0, width: 0.4, height: 0.6)
        let dims = vm.scaledDimensions
        #expect(dims.width == 200)  // 1000 * 0.5 * 0.4
        #expect(dims.height == 150) // 500 * 0.5 * 0.6
    }

    @Test("scaledDimensions floors to at least 1×1")
    func scaledDimensionsMinimum() {
        let vm = EditorViewModel()
        vm.originalSize = CGSize(width: 0, height: 0)
        let dims = vm.scaledDimensions
        #expect(dims.width >= 1)
        #expect(dims.height >= 1)
    }

    @Test("imageAspectRatio is 1 for zero-height")
    func aspectRatioGuard() {
        let vm = EditorViewModel()
        vm.originalSize = .zero
        #expect(vm.imageAspectRatio == 1.0)
    }

    @Test("formattedFileSize fallbacks")
    func formattedSize() {
        let vm = EditorViewModel()
        #expect(vm.formattedFileSize == "—")
        vm.isEncoding = true
        #expect(vm.formattedFileSize == "…")
    }

    @Test("canShare requires data and not encoding")
    func canShareConditions() {
        let vm = EditorViewModel()
        #expect(vm.canShare == false)
        vm.exportedData = Data([0])
        #expect(vm.canShare == true)
        vm.isEncoding = true
        #expect(vm.canShare == false)
    }
}
