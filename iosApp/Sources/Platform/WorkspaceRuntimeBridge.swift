import FerventioShared
import Foundation

@MainActor
final class WorkspaceRuntimeBridge {
    private let stateHolder: WorkspaceRuntimeStateHolder
    private let settingsState: SharedAppSettingsStateHolder
    private let identityStore: DeviceIdentityStore
    private let coordinator: WorkspaceBootstrapCoordinator

    init(
        stateHolder: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder,
        identityStore: DeviceIdentityStore,
        coordinator: WorkspaceBootstrapCoordinator = WorkspaceBootstrapCoordinator()
    ) {
        self.stateHolder = stateHolder
        self.settingsState = settingsState
        self.identityStore = identityStore
        self.coordinator = coordinator
    }

    static func live(
        stateHolder: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder
    ) throws -> WorkspaceRuntimeBridge {
        let configuration = try AppConfiguration.live()
        let keychain = KeychainStore(service: configuration.keychainService)
        return WorkspaceRuntimeBridge(
            stateHolder: stateHolder,
            settingsState: settingsState,
            identityStore: DeviceIdentityStore(store: keychain)
        )
    }

    func restore(authentication: StoredAuthentication?) async -> Bool {
        guard let authentication else {
            stateHolder.clear()
            settingsState.clear()
            return false
        }

        stateHolder.markLoadStarted()
        do {
            let identity = try identityStore.loadOrCreate()
            let outcome = try await coordinator.bootstrap(
                identity: identity,
                authentication: authentication,
                state: stateHolder,
                settingsState: settingsState
            )
            stateHolder.markLoadReady(settingsRevision: outcome.settingsRevision)
            return true
        } catch {
            stateHolder.markLoadFailed(errorMessage: String(describing: error))
            return false
        }
    }

    func savePreferences(
        authentication: StoredAuthentication?,
        preferences: SharedAppPreferences
    ) async {
        guard let authentication else {
            settingsState.markSaveFailed(message: "Authentication is unavailable")
            return
        }
        do {
            let identity = try identityStore.loadOrCreate()
            _ = try await coordinator.savePreferences(
                identity: identity,
                authentication: authentication,
                preferences: preferences,
                settingsState: settingsState
            )
        } catch {
            // The shared coordinator already records a user-visible save failure.
        }
    }
}
