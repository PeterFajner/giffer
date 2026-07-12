import SwiftUI
import PhotosUI

struct PickerScreen: View {
    @State private var selectedItems: [PhotosPickerItem] = []
    @State private var navigateToEditor = false
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

            Text("Pick several in a row to stitch them, just like Photos")
                .font(.caption)
                .foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            PhotosPicker(
                selection: $selectedItems,
                matching: .livePhotos,
                photoLibrary: .shared()
            ) {
                Label("Select Live Photos", systemImage: "photo.on.rectangle")
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
        .navigationDestination(isPresented: $navigateToEditor) {
            EditScreen(viewModel: viewModel)
        }
        .onChange(of: selectedItems) { _, newItems in
            guard !newItems.isEmpty else { return }
            isLoading = true
            Task {
                var photos: [PHLivePhoto] = []
                for item in newItems {
                    if let photo = try? await item.loadTransferable(type: PHLivePhoto.self) {
                        photos.append(photo)
                    }
                }
                if !photos.isEmpty {
                    viewModel.loadLivePhotos(photos)
                    navigateToEditor = true
                }
                isLoading = false
                selectedItems = []
            }
        }
        .overlay(alignment: .bottom) {
            if let count = viewModel.outOfMemoryPhotoCount {
                Text("Out of memory — try selecting fewer photos (\(count) selected)")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .background(.black.opacity(0.82), in: Capsule())
                    .padding(.horizontal, 32)
                    .padding(.bottom, 40)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task {
                        try? await Task.sleep(for: .seconds(3))
                        viewModel.outOfMemoryPhotoCount = nil
                    }
            }
        }
        .animation(.easeInOut(duration: 0.3), value: viewModel.outOfMemoryPhotoCount)
    }
}
