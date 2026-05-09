import Foundation
import CoreGraphics
@testable import Giffer

enum TestSupport {

    static func solidImage(width: Int, height: Int, gray: CGFloat) -> CGImage {
        let colorSpace = CGColorSpaceCreateDeviceGray()
        let context = CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.none.rawValue
        )!
        context.setFillColor(gray: gray, alpha: 1.0)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        return context.makeImage()!
    }
}
