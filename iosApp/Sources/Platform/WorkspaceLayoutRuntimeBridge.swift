import FerventioShared
import Foundation

@MainActor
final class WorkspaceLayoutRuntimeBridge {
    private let stateHolder: WorkspaceRuntimeStateHolder
    private let identityStore: DeviceIdentityStore
    private let coordinator: WorkspaceLayoutMutationCoordinator

    init(
        stateHolder: WorkspaceRuntimeStateHolder,
        identityStore: DeviceIdentityStore,
        coordinator: WorkspaceLayoutMutationCoordinator = WorkspaceLayoutMutationCoordinator()
    ) {
        self.stateHolder = stateHolder
        self.identityStore = identityStore
        self.coordinator = coordinator
    }

    static func live(
        stateHolder: WorkspaceRuntimeStateHolder
    ) throws -> WorkspaceLayoutRuntimeBridge {
        let configuration = try AppConfiguration.live()
        let keychain = KeychainStore(service: configuration.keychainService)
        return WorkspaceLayoutRuntimeBridge(
            stateHolder: stateHolder,
            identityStore: DeviceIdentityStore(store: keychain)
        )
    }

    func setSplitFilterQuery(
        authentication: StoredAuthentication?,
        splitId: String,
        filterQuery: String
    ) async -> Bool {
        guard let authentication else { return authenticationUnavailable() }
        do {
            _ = try await coordinator.setSplitFilterQuery(
                identity: identityStore.loadOrCreate(),
                authentication: authentication,
                state: stateHolder,
                splitId: splitId,
                filterQuery: filterQuery
            )
            return true
        } catch {
            return false
        }
    }

    func setSplitChannel(
        authentication: StoredAuthentication?,
        splitId: String,
        channelId: String
    ) async -> Bool {
        guard let authentication else { return authenticationUnavailable() }
        do {
            _ = try await coordinator.setSplitChannel(
                identity: identityStore.loadOrCreate(),
                authentication: authentication,
                state: stateHolder,
                splitId: splitId,
                channelId: channelId
            )
            return true
        } catch {
            return false
        }
    }

    func focusSplit(
        authentication: StoredAuthentication?,
        splitId: String
    ) async -> Bool {
        guard let authentication else { return authenticationUnavailable() }
        do {
            _ = try await coordinator.focusSplit(
                identity: identityStore.loadOrCreate(),
                authentication: authentication,
                state: stateHolder,
                splitId: splitId
            )
            return true
        } catch {
            return false
        }
    }

    func addSplit(authentication: StoredAuthentication?) async -> Bool {
        guard let authentication else { return authenticationUnavailable() }
        do {
            _ = try await coordinator.addSplit(
                identity: identityStore.loadOrCreate(),
                authentication: authentication,
                state: stateHolder,
                channelId: stateHolder.selectedChannelId
            )
            return true
        } catch {
            return false
        }
    }

    func removeSplit(
        authentication: StoredAuthentication?,
        splitId: String
    ) async -> Bool {
        guard let authentication else { return authenticationUnavailable() }
        do {
            _ = try await coordinator.removeSplit(
                identity: identityStore.loadOrCreate(),
                authentication: authentication,
                state: stateHolder,
                splitId: splitId
            )
            return true
        } catch {
            return false
        }
    }

    func setPrimaryFraction(
        authentication: StoredAuthentication?,
        fraction: KotlinFloat
    ) async -> Bool {
        guard let authentication else { return authenticationUnavailable() }
        do {
            _ = try await coordinator.setPrimaryFraction(
                identity: identityStore.loadOrCreate(),
                authentication: authentication,
                state: stateHolder,
                fraction: fraction.floatValue
            )
            return true
        } catch {
            return false
        }
    }

    private func authenticationUnavailable() -> Bool {
        stateHolder.markMutationFailed(errorMessage: "Authentication is unavailable")
        return false
    }
}
