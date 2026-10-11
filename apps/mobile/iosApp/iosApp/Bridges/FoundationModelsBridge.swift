import Foundation
import PulseKit
#if canImport(FoundationModels)
import FoundationModels
#endif

/// Apple's on-device ~3B model (iOS 26+, Apple Intelligence devices). Kotlin's TieredAiEngine
/// calls this only when the rules rung is unsure, and only when the device isn't thermally throttled.
enum FoundationModelsBridge {
    static func makeIfSupported() -> NativeLlmBridge? {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) { return FoundationModelsLlm() }
        #endif
        return nil
    }
}

#if canImport(FoundationModels)
@available(iOS 26.0, *)
final class FoundationModelsLlm: NSObject, NativeLlmBridge {
    func isAvailable() -> Bool {
        if case .available = SystemLanguageModel.default.availability { return true }
        return false
    }

    func isThermallyThrottled() -> Bool {
        let state = ProcessInfo.processInfo.thermalState
        return state == .serious || state == .critical
    }

    func generate(systemInstruction: String, prompt: String, onResult: @escaping (String?, String?) -> Void) {
        Task {
            do {
                let session = LanguageModelSession(instructions: systemInstruction)
                let response = try await session.respond(to: prompt)
                onResult(response.content, nil)
            } catch {
                onResult(nil, error.localizedDescription)
            }
        }
    }
}
#endif
