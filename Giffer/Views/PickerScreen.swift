import SwiftUI
import PhotosUI

struct PickerScreen: View {
    @State private var selectedItem: PhotosPickerItem? = nil
    @Binding var editorRoute: UUID?
    @State private var isLoading = false
    @State private var showAbout = false

    @Bindable var viewModel: EditorViewModel

    var body: some View {
        VStack(spacing: 32) {
            Spacer()

            Image(systemName: "livephoto")
                .font(.system(size: 80))
                .foregroundStyle(.secondary)

            Text("Giffer")
                .font(.largeTitle.bold())

            Text("Convert Live Photos to GIFs")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            PhotosPicker(
                selection: $selectedItem,
                matching: .livePhotos,
                photoLibrary: .shared()
            ) {
                Label("Select Live Photo", systemImage: "photo.on.rectangle")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(.blue)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .padding(.horizontal, 40)

            if isLoading {
                ProgressView("Loading Live Photo...")
            }

            Spacer()
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showAbout = true
                } label: {
                    Image(systemName: "questionmark.circle")
                }
                .accessibilityLabel("About")
            }
        }
        .sheet(isPresented: $showAbout) {
            AboutView()
        }
        .navigationDestination(item: $editorRoute) { _ in
            EditScreen(viewModel: viewModel)
        }
        .onChange(of: selectedItem) { _, newItem in
            guard let newItem else { return }
            isLoading = true
            Task {
                if let photo = try? await newItem.loadTransferable(type: PHLivePhoto.self) {
                    viewModel.loadLivePhoto(photo)
                    editorRoute = UUID()
                }
                isLoading = false
                selectedItem = nil
            }
        }
    }
}
