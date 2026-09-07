import FerventioShared
import Foundation

@MainActor
final class WorkspaceRuntimeBridge {
    private let stateHolder: WorkspaceRuntimeStateHolder
    private let settingsState: SharedAppSettingsStateHolder
    private let rulesState: SharedMessageRulesStateHolder
    private let filtersState: SharedSavedFiltersStateHolder
    private let identityStore: DeviceIdentityStore
    private let coordinator: WorkspaceBootstrapCoordinator

    init(
        stateHolder: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder,
        rulesState: SharedMessageRulesStateHolder = MainViewControllerKt.IosRuntimeState().messageRules,
        filtersState: SharedSavedFiltersStateHolder = MainViewControllerKt.IosRuntimeState().savedFilters,
        identityStore: DeviceIdentityStore,
        coordinator: WorkspaceBootstrapCoordinator = WorkspaceBootstrapCoordinator()
    ) {
        self.stateHolder = stateHolder
        self.settingsState = settingsState
        self.rulesState = rulesState
        self.filtersState = filtersState
        self.identityStore = identityStore
        self.coordinator = coordinator
    }

    static func live(
        stateHolder: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder,
        rulesState: SharedMessageRulesStateHolder = MainViewControllerKt.IosRuntimeState().messageRules,
        filtersState: SharedSavedFiltersStateHolder = MainViewControllerKt.IosRuntimeState().savedFilters
    ) throws -> WorkspaceRuntimeBridge {
        let configuration = try AppConfiguration.live()
        let keychain = KeychainStore(service: configuration.keychainService)
        return WorkspaceRuntimeBridge(
            stateHolder: stateHolder,
            settingsState: settingsState,
            rulesState: rulesState,
            filtersState: filtersState,
            identityStore: DeviceIdentityStore(store: keychain)
        )
    }

    func restore(authentication: StoredAuthentication?) async -> Bool {
        guard let authentication else {
            stateHolder.clear()
            settingsState.clear()
            rulesState.clear()
            filtersState.clear()
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
            rulesState.restore(snapshot: outcome.messageRules)
            filtersState.restore(snapshot: outcome.savedFilters)
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

    func upsertHighlightRule(
        authentication: StoredAuthentication?,
        rule: HighlightRule
    ) async -> Bool {
        guard let authentication else {
            rulesState.markSaveFailed(message: "Authentication is unavailable")
            return false
        }
        do {
            let identity = try identityStore.loadOrCreate()
            _ = try await coordinator.upsertHighlightRule(
                identity: identity,
                authentication: authentication,
                rule: rule,
                rulesState: rulesState
            )
            return true
        } catch {
            return false
        }
    }

    func deleteHighlightRule(
        authentication: StoredAuthentication?,
        ruleId: String
    ) async -> Bool {
        guard let authentication else {
            rulesState.markSaveFailed(message: "Authentication is unavailable")
            return false
        }
        do {
            let identity = try identityStore.loadOrCreate()
            _ = try await coordinator.deleteHighlightRule(
                identity: identity,
                authentication: authentication,
                ruleId: ruleId,
                rulesState: rulesState
            )
            return true
        } catch {
            return false
        }
    }

    func upsertIgnoreRule(
        authentication: StoredAuthentication?,
        rule: IgnoreRule
    ) async -> Bool {
        guard let authentication else {
            rulesState.markSaveFailed(message: "Authentication is unavailable")
            return false
        }
        do {
            let identity = try identityStore.loadOrCreate()
            _ = try await coordinator.upsertIgnoreRule(
                identity: identity,
                authentication: authentication,
                rule: rule,
                rulesState: rulesState
            )
            return true
        } catch {
            return false
        }
    }

    func deleteIgnoreRule(
        authentication: StoredAuthentication?,
        ruleId: String
    ) async -> Bool {
        guard let authentication else {
            rulesState.markSaveFailed(message: "Authentication is unavailable")
            return false
        }
        do {
            let identity = try identityStore.loadOrCreate()
            _ = try await coordinator.deleteIgnoreRule(
                identity: identity,
                authentication: authentication,
                ruleId: ruleId,
                rulesState: rulesState
            )
            return true
        } catch {
            return false
        }
    }

    func upsertSavedFilter(
        authentication: StoredAuthentication?,
        filter: SavedMessageFilter
    ) async -> Bool {
        guard let authentication else {
            filtersState.markSaveFailed(message: "Authentication is unavailable")
            return false
        }
        do {
            let identity = try identityStore.loadOrCreate()
            _ = try await coordinator.upsertSavedFilter(
                identity: identity,
                authentication: authentication,
                filter: filter,
                filtersState: filtersState
            )
            return true
        } catch {
            return false
        }
    }

    func deleteSavedFilter(
        authentication: StoredAuthentication?,
        filterId: String
    ) async -> Bool {
        guard let authentication else {
            filtersState.markSaveFailed(message: "Authentication is unavailable")
            return false
        }
        do {
            let identity = try identityStore.loadOrCreate()
            _ = try await coordinator.deleteSavedFilter(
                identity: identity,
                authentication: authentication,
                filterId: filterId,
                filtersState: filtersState
            )
            return true
        } catch {
            return false
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
        targetIndex: KotlinInt
    ) async -> Bool {
        await performMutation(authentication: authentication) { identity, authentication in
            _ = try await coordinator.moveChannel(
                identity: identity,
                authentication: authentication,
                channelId: channelId,
                targetIndex: targetIndex.int32Value,
                state: stateHolder,
                settingsState: settingsState
            )
        }
    }

    func setChannelPinned(
        authentication: StoredAuthentication?,
        channelId: String,
        pinned: KotlinBoolean
    ) async -> Bool {
        await performMutation(authentication: authentication) { identity, authentication in
            _ = try await coordinator.setChannelPinned(
                identity: identity,
                authentication: authentication,
                channelId: channelId,
                pinned: pinned.boolValue,
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
        operation: @MainActor (MobileDeviceIdentity, StoredAuthentication) async throws -> Void
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
