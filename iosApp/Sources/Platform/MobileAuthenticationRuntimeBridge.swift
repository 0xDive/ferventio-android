import FerventioShared
import Foundation

@MainActor
final class MobileAuthenticationRuntimeBridge {
    private let configuration: AppConfiguration
    private let stateHolder: MobileAuthenticationStateHolder
    private let identityStore: DeviceIdentityStore
    private let sessionStore: AuthenticationSessionStore
    private let coordinator: MobileAuthenticationCoordinator
    private let browser: BackendAuthorizationSessionAdapter
    private var authorizationInFlight = false

    init(
        configuration: AppConfiguration,
        stateHolder: MobileAuthenticationStateHolder,
        identityStore: DeviceIdentityStore,
        sessionStore: AuthenticationSessionStore,
        coordinator: MobileAuthenticationCoordinator = MobileAuthenticationCoordinator(),
        browser: BackendAuthorizationSessionAdapter = BackendAuthorizationSessionAdapter()
    ) {
        self.configuration = configuration
        self.stateHolder = stateHolder
        self.identityStore = identityStore
        self.sessionStore = sessionStore
        self.coordinator = coordinator
        self.browser = browser
    }

    static func live(
        stateHolder: MobileAuthenticationStateHolder
    ) throws -> MobileAuthenticationRuntimeBridge {
        let configuration = try AppConfiguration.live()
        let keychain = KeychainStore(service: configuration.keychainService)
        return MobileAuthenticationRuntimeBridge(
            configuration: configuration,
            stateHolder: stateHolder,
            identityStore: DeviceIdentityStore(store: keychain),
            sessionStore: AuthenticationSessionStore(store: keychain)
        )
    }

    func restore() async {
        do {
            guard let persisted = try sessionStore.load() else {
                stateHolder.restore(authentication: nil)
                return
            }
            let identity = try identityStore.loadOrCreate()
            let authentication = try await coordinator.restoreAuthentication(
                identity: identity,
                authentication: persisted
            )
            try sessionStore.save(authentication)
            stateHolder.restore(authentication: authentication)
        } catch {
            stateHolder.markFailed(errorMessage: String(describing: error))
        }
    }

    func signIn() async {
        guard !authorizationInFlight else {
            return
        }
        authorizationInFlight = true
        defer { authorizationInFlight = false }

        stateHolder.beginAuthorization()
        do {
            let identity = try identityStore.loadOrCreate()
            let request = try await coordinator.startAuthorization(
                serverUrl: configuration.serverURL,
                identity: identity,
                callbackScheme: configuration.callbackScheme
            )
            let callback = try await browser.authenticate(request: request)
            let authentication = try await coordinator.completeAuthorization(
                identity: identity,
                callback: callback
            )
            try sessionStore.save(authentication)
            stateHolder.markSignedIn(authentication: authentication)
        } catch {
            stateHolder.markFailed(errorMessage: String(describing: error))
        }
    }

    @discardableResult
    func signOut() async -> Bool {
        // Server-side revocation is best-effort. Local sign-out must still succeed offline; the
        // push bridge performs a secret-bound DELETE afterwards as an independent fallback.
        do {
            if let authentication = try sessionStore.load(),
               let identity = try identityStore.loadExisting() {
                try await coordinator.revokeDevice(
                    identity: identity,
                    authentication: authentication
                )
            }
        } catch {
            // Intentionally continue with local credential removal.
        }

        do {
            try sessionStore.clear()
            stateHolder.signOut()
            return true
        } catch {
            stateHolder.markFailed(errorMessage: String(describing: error))
            return false
        }
    }
}
