import FerventioShared
import Foundation

@MainActor
enum SavedFiltersTransferRuntimeBridge {
    static func importFilters(
        authentication: StoredAuthentication?,
        state: SharedSavedFiltersStateHolder,
        raw: String
    ) async -> Bool {
        guard let authentication else {
            state.markSaveFailed(message: "Authentication is unavailable")
            return false
        }
        do {
            let configuration = try AppConfiguration.live()
            let keychain = KeychainStore(service: configuration.keychainService)
            let identity = try DeviceIdentityStore(store: keychain).loadOrCreate()
            _ = try await SavedFiltersTransferCoordinator().importFilters(
                identity: identity,
                authentication: authentication,
                raw: raw,
                filtersState: state
            )
            return true
        } catch {
            return false
        }
    }
}
