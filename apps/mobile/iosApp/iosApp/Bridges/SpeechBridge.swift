import AVFoundation
import Foundation
import PulseKit
import Speech

/// SFSpeechRecognizer forced on-device (requiresOnDeviceRecognition): voice works offline.
final class SpeechBridge: NSObject, NativeSpeechBridge {
    private let audio = AVAudioEngine()
    private var task: SFSpeechRecognitionTask?
    private var request: SFSpeechAudioBufferRecognitionRequest?

    func isAvailable() -> Bool { SFSpeechRecognizer()?.supportsOnDeviceRecognition ?? false }

    func start(languageTag: String, onPartial: @escaping (String) -> Void, onFinal: @escaping (String, Float) -> Void, onError: @escaping (String) -> Void) {
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: languageTag)) ?? SFSpeechRecognizer() else {
            onError("no recognizer"); return
        }
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.requiresOnDeviceRecognition = true
        request.shouldReportPartialResults = true
        self.request = request

        let input = audio.inputNode
        input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buffer, _ in
            request.append(buffer)
        }
        do { try audio.start() } catch { onError(error.localizedDescription); return }

        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            if let result {
                let text = result.bestTranscription.formattedString
                if result.isFinal {
                    let confidence = result.bestTranscription.segments.last?.confidence ?? 0.8
                    onFinal(text, confidence)
                    self?.stop()
                } else {
                    onPartial(text)
                }
            } else if let error {
                onError(error.localizedDescription)
                self?.stop()
            }
        }
    }

    func stop() {
        audio.stop()
        audio.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.cancel()
        task = nil
    }
}
