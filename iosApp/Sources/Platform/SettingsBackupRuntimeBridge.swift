import FerventioShared
import Foundation

@MainActor
final class SettingsBackupRuntimeBridge {
    private let runtime: IosSettingsBackupRuntime
    private let revisionHistoryRuntime: IosSettingsRevisionHistoryRuntime
    private let identityStore: DeviceIdentityStore
    private let timestampFormatter = ISO8601DateFormatter()

    private init(
        runtime: IosSettingsBackupRuntime,
        revisionHistoryRuntime: IosSettingsRevisionHistoryRuntime,
        identityStore: DeviceIdentityStore
    ) {
        self.runtime = runtime
        self.revisionHistoryRuntime = revisionHistoryRuntime
        self.identityStore = identityStore
    }

    static func live(
        runtime: IosSettingsBackupRuntime,
        revisionHistoryRuntime: IosSettingsRevisionHistoryRuntime
    ) throws -> SettingsBackupRuntimeBridge {
        let configuration = try AppConfiguration.live()
        let keychain = KeychainStore(service: configuration.keychainService)
        return SettingsBackupRuntimeBridge(
            runtime: runtime,
            revisionHistoryRuntime: revisionHistoryRuntime,
            identityStore: DeviceIdentityStore(store: keychain)
        )
    }

    func exportBackup(authentication: StoredAuthentication?) async throws -> String {
        let authentication = try requireAuthentication(authentication)
        return try await runtime.exportBackup(
            identity: identityStore.loadOrCreate(),
            authentication: authentication,
            currentAppVersion: appVersion,
            createdAt: timestampFormatter.string(from: Date())
        )
    }

    func importBackup(
        authentication: StoredAuthentication?,
        payload: String
    ) async throws -> Bool {
        let authentication = try requireAuthentication(authentication)
        return try await runtime.importBackup(
            identity: identityStore.loadOrCreate(),
            authentication: authentication,
            payload: payload,
            currentAppVersion: appVersion,
            createdAt: timestampFormatter.string(from: Date())
        ).boolValue
    }

    func resumePendingImport(authentication: StoredAuthentication?) async throws -> Bool {
        let authentication = try requireAuthentication(authentication)
        return try await runtime.resumePendingImport(
            identity: identityStore.loadOrCreate(),
            authentication: authentication,
            currentAppVersion: appVersion,
            createdAt: timestampFormatter.string(from: Date())
        ).boolValue
    }

    func keepLocal(authentication: StoredAuthentication?) async throws -> Bool {
        let authentication = try requireAuthentication(authentication)
        return try await runtime.keepLocal(
            identity: identityStore.loadOrCreate(),
            authentication: authentication,
            currentAppVersion: appVersion,
            createdAt: timestampFormatter.string(from: Date())
        ).boolValue
    }

    func useServer(authentication: StoredAuthentication?) async throws -> Bool {
        let authentication = try requireAuthentication(authentication)
        return try await runtime.useServer(authentication: authentication).boolValue
    }

    func loadRevisionHistory(authentication: StoredAuthentication?) async throws {
        let authentication = try requireAuthentication(authentication)
        _ = try await revisionHistoryRuntime.loadHistory(
            identity: identityStore.loadOrCreate(),
            authentication: authentication
        )
    }

    func restoreRevision(
        authentication: StoredAuthentication?,
        revision: Int64
    ) async throws -> Int64 {
        let authentication = try requireAuthentication(authentication)
        return try await revisionHistoryRuntime.restoreRevision(
            identity: identityStore.loadOrCreate(),
            authentication: authentication,
            revision: revision
        ).int64Value
    }

    func reportExported() {
        runtime.reportExported()
    }

    func reportExportCancelled() {
        runtime.reportExportCancelled()
    }

    func reportFileFailure(_ message: String) {
        runtime.reportFileFailure(message: message)
    }

    func discardPendingImport() {
        try? runtime.discardPendingImport()
    }

    private var appVersion: String {
        let value = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        return value?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
            ? value!
            : "0.0.0"
    }

    private func requireAuthentication(_ authentication: StoredAuthentication?) throws -> StoredAuthentication {
        guard let authentication else {
            throw SettingsBackupBridgeError.authenticationUnavailable
        }
        return authentication
    }
}

private enum SettingsBackupBridgeError: LocalizedError {
    case authenticationUnavailable

    var errorDescription: String? {
        switch self {
        case .authenticationUnavailable:
            return "Authentication is unavailable"
        }
    }
}
