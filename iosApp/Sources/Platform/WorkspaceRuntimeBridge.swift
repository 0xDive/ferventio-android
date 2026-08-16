import FerventioShared
import Foundation

@MainActor
final class WorkspaceRuntimeBridge {
    private let stateHolder: WorkspaceRuntimeStateHolder
    private let identityStore: DeviceIdentityStore
    private let coordinator: WorkspaceBootstrapCoordinator

    init(
        stateHolder: WorkspaceRuntimeStateHolder,
        identityStore: DeviceIdentityStore,
        coordinator: WorkspaceBootstrapCoordinator = WorkspaceBootstrapCoordinator()
    ) {
        self.stateHolder = stateHolder
        self.identityStore = identityStore
        self.coordinator = coordinator
    }

    static func live(
        stateHolder: WorkspaceRuntimeStateHolder
    ) throws -> WorkspaceRuntimeBridge {
        let configuration = try AppConfiguration.live()
        let keychain = KeychainStore(service: configuration.keychainService)
        return WorkspaceRuntimeBridge(
            stateHolder: stateHolder,
            identityStore: DeviceIdentityStore(store: keychain)
        )
    }

    func restore(authentication: StoredAuthentication?) async -> Bool {
        guard let authentication else {
            stateHolder.clear()
            return false
        }

        stateHolder.markLoadStarted()
        do {
            let identity = try identityStore.loadOrCreate()
            let outcome = try await coordinator.bootstrap(
                identity: identity,
                authentication: authentication,
                state: stateHolder
            )
            stateHolder.markLoadReady(settingsRevision: outcome.settingsRevision)
            return true
        } catch {
            stateHolder.markLoadFailed(errorMessage: String(describing: error))
            return false
        }
    }
}
