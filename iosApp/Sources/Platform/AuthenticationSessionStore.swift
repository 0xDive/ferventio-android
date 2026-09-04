import FerventioShared
import Foundation

@MainActor
struct AuthenticationSessionStore {
    private let store: any SecureKeyValueStoring
    private let codec: StoredAuthenticationJsonCodec

    init(
        store: any SecureKeyValueStoring,
        codec: StoredAuthenticationJsonCodec = StoredAuthenticationJsonCodec()
    ) {
        self.store = store
        self.codec = codec
    }

    func load() throws -> StoredAuthentication? {
        guard let payload = try store.string(forKey: Keys.authentication) else {
            return nil
        }
        do {
            return try codec.decode(payload: payload)
        } catch {
            // Fail closed. Corrupted or stale payloads must never escape secure storage as a
            // partially trusted session, and retrying the same malformed value is not useful.
            try? store.removeValue(forKey: Keys.authentication)
            return nil
        }
    }

    func save(_ authentication: StoredAuthentication) throws {
        let payload = try codec.encode(authentication: authentication)
        try store.set(payload, forKey: Keys.authentication)
    }

    func clear() throws {
        try store.removeValue(forKey: Keys.authentication)
    }

    private enum Keys {
        // Keep the proven native-iOS key so existing installs do not fork secure-storage names.
        static let authentication = "auth.session.v1"
    }
}
