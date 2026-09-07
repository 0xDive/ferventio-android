import Foundation
import Security

protocol SecureKeyValueStoring: Sendable {
    func set(_ value: String, forKey key: String) throws
    func string(forKey key: String) throws -> String?
    func removeValue(forKey key: String) throws
}

struct KeychainStore: SecureKeyValueStoring, Sendable {
    enum Error: Swift.Error, Equatable {
        case unexpectedStatus(OSStatus)
        case invalidData
    }

    private let service: String

    init(service: String) {
        self.service = service
    }

    func set(_ value: String, forKey key: String) throws {
        guard let data = value.data(using: .utf8) else {
            throw Error.invalidData
        }

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)

        var item = query
        item[kSecValueData as String] = data
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly

        let status = SecItemAdd(item as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw Error.unexpectedStatus(status)
        }
    }

    func string(forKey key: String) throws -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound {
            return nil
        }
        guard status == errSecSuccess else {
            throw Error.unexpectedStatus(status)
        }
        guard let data = result as? Data, let value = String(data: data, encoding: .utf8) else {
            throw Error.invalidData
        }
        return value
    }

    func removeValue(forKey key: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw Error.unexpectedStatus(status)
        }
    }
}
