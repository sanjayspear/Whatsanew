import CryptoKit
import Foundation
import MediaPipeTasksGenAI
import PulseKit

/// Runs the universal open-source model (Qwen2.5-0.5B `.task`) with MediaPipe LLM Inference,
/// the same runtime and file the Android app uses, so both platforms understand requests identically.
final class MediaPipeBridge: NSObject, NativeMediaPipeBridge {
    private var llm: LlmInference?
    private let queue = DispatchQueue(label: "pulse.llm", qos: .userInitiated)

    func load(modelPath: String) -> Bool {
        do {
            let options = LlmInference.Options(modelPath: modelPath)
            options.maxTokens = 1024
            llm = try LlmInference(options: options)
            return true
        } catch {
            return false
        }
    }

    func generate(prompt: String, onResult: @escaping (String?, String?) -> Void) {
        queue.async { [weak self] in
            guard let llm = self?.llm else { onResult(nil, "model not loaded"); return }
            do { onResult(try llm.generateResponse(inputText: prompt), nil) } catch { onResult(nil, error.localizedDescription) }
        }
    }

    func close() { llm = nil }
}

/// Downloads the model into Application Support (excluded from iCloud backup) and verifies its SHA-256.
final class ModelStoreBridge: NSObject, NativeModelBridge, URLSessionDownloadDelegate {
    private var session: URLSession!
    private var pending: [Int: (id: String, sha: String, progress: (Double) -> Void, done: (String?, String?) -> Void)] = [:]

    override init() {
        super.init()
        session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
    }

    private func dir(_ id: String) -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        var url = base.appendingPathComponent("models/\(id)", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try? url.setResourceValues(values)
        return url
    }

    func installedPath(modelId: String) -> String? {
        let f = dir(modelId).appendingPathComponent("model.task")
        return FileManager.default.fileExists(atPath: f.path) ? f.path : nil
    }

    func download(modelId: String, url: String, sha256: String, onProgress: @escaping (Double) -> Void, onDone: @escaping (String?, String?) -> Void) {
        guard let u = URL(string: url) else { onDone(nil, "bad model URL"); return }
        let task = session.downloadTask(with: u)
        pending[task.taskIdentifier] = (modelId, sha256, onProgress, onDone)
        task.resume()
    }

    func delete(modelId: String) { try? FileManager.default.removeItem(at: dir(modelId)) }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        guard totalBytesExpectedToWrite > 0, let p = pending[downloadTask.taskIdentifier] else { return }
        p.progress(min(0.999, Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)))
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        guard let p = pending.removeValue(forKey: downloadTask.taskIdentifier) else { return }
        p.progress(1.0)
        do {
            if !p.sha.hasPrefix("PENDING") {
                let digest = try SHA256.hash(data: Data(contentsOf: location, options: .mappedIfSafe))
                let hex = digest.map { String(format: "%02x", $0) }.joined()
                guard hex == p.sha.lowercased() else { p.done(nil, "Downloaded file failed verification. Try again."); return }
            }
            let target = dir(p.id).appendingPathComponent("model.task")
            try? FileManager.default.removeItem(at: target)
            try FileManager.default.moveItem(at: location, to: target)
            p.done(target.path, nil)
        } catch {
            p.done(nil, error.localizedDescription)
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if let error, let p = pending.removeValue(forKey: task.taskIdentifier) { p.done(nil, error.localizedDescription) }
    }
}
