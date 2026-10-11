import CryptoKit
import Foundation
import PulseKit

/// Ed25519 verification of model manifests (see ModelUpdater) using CryptoKit.
final class CryptoBridge: NSObject, NativeCryptoBridge {
    func verifyEd25519(payloadBase64: String, signatureBase64: String, publicKeyBase64: String) -> Bool {
        guard let payload = Data(base64Encoded: payloadBase64),
              let signature = Data(base64Encoded: signatureBase64),
              let keyData = Data(base64Encoded: publicKeyBase64),
              let key = try? Curve25519.Signing.PublicKey(rawRepresentation: keyData) else { return false }
        return key.isValidSignature(signature, for: payload)
    }
}
