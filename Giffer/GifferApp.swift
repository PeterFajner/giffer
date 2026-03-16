import SwiftUI

@main
struct GifferApp: App {
    @State private var viewModel = EditorViewModel()

    var body: some Scene {
        WindowGroup {
            NavigationStack {
                PickerScreen(viewModel: viewModel)
            }
        }
    }
}
