import SwiftUI

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    private let githubURL = URL(string: "https://github.com/peterfajner/giffer")!
    private let privacyURL = URL(string: "https://github.com/peterfajner/giffer/blob/main/PRIVACY.md")!

    private var versionString: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "—"
        let build = info?["CFBundleVersion"] as? String ?? "—"
        return "\(version) (\(build))"
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        Image(systemName: "livephoto")
                            .font(.system(size: 36))
                            .foregroundStyle(.blue)
                            .frame(width: 56, height: 56)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Giffer")
                                .font(.title2.bold())
                            Text("Version \(versionString)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }

                Section {
                    LabeledContent("Author", value: "Peter Fajner")
                    Link(destination: githubURL) {
                        Label("GitHub Repository", systemImage: "arrow.up.right.square")
                    }
                    Link(destination: privacyURL) {
                        Label("Privacy Policy", systemImage: "hand.raised")
                    }
                }

                Section {
                    Text("Giffer converts Live Photos into animated GIFs. No data is collected or transmitted.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("About")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
