import Foundation
import Security

struct DeviceIdentity: Equatable, Sendable {
    let installationID: String
    let deviceSecret: String
}

struct DeviceIdentityStore: Sendable {
    enum Error: Swift.Error, Equatable {
        case randomGenerationFailed(OSStatus)
    }

    private let store: any SecureKeyValueStoring

    init(store: any SecureKeyValueStoring) {
        self.store = store
    }

    func loadOrCreate() throws -> DeviceIdentity {
        let installationID = try store.string(forKey: Keys.installationID)
        let deviceSecret = try store.string(forKey: Keys.deviceSecret)

        if let installationID,
           let deviceSecret,
           isValid(installationID: installationID, deviceSecret: deviceSecret) {
            return DeviceIdentity(
                installationID: installationID,
                deviceSecret: deviceSecret
            )
        }

        let identity = DeviceIdentity(
            installationID: UUID().uuidString.lowercased(),
            deviceSecret: try makeDeviceSecret()
        )
        try store.set(identity.installationID, forKey: Keys.installationID)
        try store.set(identity.deviceSecret, forKey: Keys.deviceSecret)
        return identity
    }

    func clear() throws {
        try store.removeValue(forKey: Keys.installationID)
        try store.removeValue(forKey: Keys.deviceSecret)
    }

    private func makeDeviceSecret() throws -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = bytes.withUnsafeMutableBytes { buffer in
            SecRandomCopyBytes(kSecRandomDefault, buffer.count, buffer.baseAddress!)
        }
        guard status == errSecSuccess else {
            throw Error.randomGenerationFailed(status)
        }
        return Data(bytes)
            .base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private func isValid(installationID: String, deviceSecret: String) -> Bool {
        let installationID = installationID.trimmingCharacters(in: .whitespacesAndNewlines)
        let deviceSecret = deviceSecret.trimmingCharacters(in: .whitespacesAndNewlines)
        return !installationID.isEmpty
            && installationID.count <= 128
            && deviceSecret.count >= 32
            && deviceSecret.count <= 256
    }

    private enum Keys {
        static let installationID = "device.installation-id.v1"
        static let deviceSecret = "device.secret.v1"
    }
}
