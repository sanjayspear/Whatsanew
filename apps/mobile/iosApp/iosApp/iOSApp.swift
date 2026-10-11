import SwiftUI
import PulseKit

@main
struct PulseiOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
    }
}

/// Hosts the shared Compose Multiplatform UI. Swift-only Apple APIs are injected as bridges.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let platform = PlatformContext(
            llm: FoundationModelsBridge.makeIfSupported(),
            speech: SpeechBridge(),
            crypto: CryptoBridge()
        )
        return MainViewControllerKt.MainViewController(context: platform)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
