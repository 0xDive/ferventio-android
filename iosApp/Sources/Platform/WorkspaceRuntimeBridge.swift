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

    func addChannel(
        authentication: StoredAuthentication?,
        login: String
    ) async -> Bool {
        await performMutation(authentication: authentication) { identity, authentication in
            _ = try await coordinator.addChannel(
                identity: identity,
                authentication: authentication,
                loginInput: login,
                state: stateHolder,
                settingsState: settingsState
            )
        }
    }

    func removeChannel(
        authentication: StoredAuthentication?,
        channelId: String
    ) async -> Bool {
        await performMutation(authentication: authentication) { identity, authentication in
            _ = try await coordinator.removeChannel(
                identity: identity,
                authentication: authentication,
                channelId: channelId,
                state: stateHolder,
                settingsState: settingsState
            )
        }
    }

    func moveChannel(
        authentication: StoredAuthentication?,
        channelId: String,
        targetIndex: Int32
    ) async -> Bool {
        await performMutation(authentication: authentication) { identity, authentication in
            _ = try await coordinator.moveChannel(
                identity: identity,
                authentication: authentication,
                channelId: channelId,
                targetIndex: targetIndex,
                state: stateHolder,
                settingsState: settingsState
            )
        }
    }

    func setChannelPinned(
        authentication: StoredAuthentication?,
        channelId: String,
        pinned: Bool
    ) async -> Bool {
        await performMutation(authentication: authentication) { identity, authentication in
            _ = try await coordinator.setChannelPinned(
                identity: identity,
                authentication: authentication,
                channelId: channelId,
                pinned: pinned,
                state: stateHolder,
                settingsState: settingsState
            )
        }
    }

    func renameChannel(
        authentication: StoredAuthentication?,
        channelId: String,
        title: String?
    ) async -> Bool {
        await performMutation(authentication: authentication) { identity, authentication in
            _ = try await coordinator.renameChannelTab(
                identity: identity,
                authentication: authentication,
                channelId: channelId,
                title: title,
                state: stateHolder,
                settingsState: settingsState
            )
        }
    }

    func persistSelectedChannel(
        authentication: StoredAuthentication?,
        channelId: String
    ) async -> Bool {
        await performMutation(authentication: authentication) { identity, authentication in
            _ = try await coordinator.selectChannel(
                identity: identity,
                authentication: authentication,
                channelId: channelId,
                state: stateHolder,
                settingsState: settingsState
            )
        }
    }

    private func performMutation(
        authentication: StoredAuthentication?,
        operation: (MobileDeviceIdentity, StoredAuthentication) async throws -> Void
    ) async -> Bool {
        guard let authentication else {
            stateHolder.markMutationFailed(errorMessage: "Authentication is unavailable")
            return false
        }
        stateHolder.markMutationStarted()
        do {
            let identity = try identityStore.loadOrCreate()
            try await operation(identity, authentication)
            stateHolder.markMutationSucceeded()
            return true
        } catch {
            stateHolder.markMutationFailed(errorMessage: String(describing: error))
            return false
        }
    }
}
