import FerventioShared
import Foundation

enum ForegroundAuthenticationRefreshDisposition {
    case ready
    case signedOut
    case unavailable
    case deferred
}

@MainActor
final class MobileAuthenticationRuntimeBridge {
    private enum AuthenticationRefreshReason: Equatable {
        case foreground
        case rejection
    }

    private struct AuthenticationRefreshFlight {
        let generation: Int
        let reason: AuthenticationRefreshReason
        let task: Task<ForegroundAuthenticationRefreshDisposition, Never>
    }

    private let configuration: AppConfiguration
    private let stateHolder: MobileAuthenticationStateHolder
    private let identityStore: DeviceIdentityStore
    private let sessionStore: AuthenticationSessionStore
    private let coordinator: MobileAuthenticationCoordinator
    private let browser: BackendAuthorizationSessionAdapter
    private var initialRestorePending = true
    private var authorizationInFlight = false
    private var refreshFlight: AuthenticationRefreshFlight?
    private var refreshFlightGeneration = 0
    private var sessionGeneration = 0

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
        defer { initialRestorePending = false }
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

    func refreshForForeground() async -> ForegroundAuthenticationRefreshDisposition {
        await refreshAuthentication(reason: .foreground)
    }

    func refreshAfterAuthenticationRejection() async -> ForegroundAuthenticationRefreshDisposition {
        await refreshAuthentication(reason: .rejection)
    }

    func signIn() async {
        guard !authorizationInFlight else {
            return
        }
        invalidateRefreshFlight()
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
        // Prevent an in-flight foreground/rejection refresh from restoring credentials after
        // the user has explicitly started signing out.
        invalidateRefreshFlight()

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

    private func refreshAuthentication(
        reason: AuthenticationRefreshReason
    ) async -> ForegroundAuthenticationRefreshDisposition {
        guard !initialRestorePending, !authorizationInFlight else {
            return .deferred
        }

        while true {
            guard stateHolder.state.authentication != nil else {
                return .signedOut
            }

            if let flight = refreshFlight {
                let disposition = await flight.task.value
                if refreshFlight?.generation == flight.generation {
                    refreshFlight = nil
                }

                // A foreground refresh may safely reuse a stronger rejection refresh. A
                // rejection refresh must run after a weaker foreground refresh even if that
                // foreground refresh considered the current lease usable.
                if reason == .foreground || flight.reason == .rejection {
                    return disposition
                }
                switch disposition {
                case .ready:
                    continue
                case .signedOut, .unavailable, .deferred:
                    return disposition
                }
            }

            refreshFlightGeneration += 1
            let generation = refreshFlightGeneration
            let expectedSessionGeneration = sessionGeneration
            let task = Task { @MainActor [weak self] in
                guard let self else {
                    return ForegroundAuthenticationRefreshDisposition.unavailable
                }
                return await self.executeRefresh(
                    reason: reason,
                    expectedSessionGeneration: expectedSessionGeneration
                )
            }
            refreshFlight = AuthenticationRefreshFlight(
                generation: generation,
                reason: reason,
                task: task
            )

            let disposition = await task.value
            if refreshFlight?.generation == generation {
                refreshFlight = nil
            }
            return disposition
        }
    }

    private func executeRefresh(
        reason: AuthenticationRefreshReason,
        expectedSessionGeneration: Int
    ) async -> ForegroundAuthenticationRefreshDisposition {
        guard sessionGeneration == expectedSessionGeneration, !Task.isCancelled else {
            return .deferred
        }
        guard let authentication = stateHolder.state.authentication else {
            return .signedOut
        }

        do {
            let identity = try identityStore.loadOrCreate()
            let result: MobileAuthenticationRefreshResult
            switch reason {
            case .foreground:
                result = try await coordinator.refreshAuthenticationForForeground(
                    identity: identity,
                    authentication: authentication
                )
            case .rejection:
                result = try await coordinator.refreshAuthenticationAfterRejection(
                    identity: identity,
                    authentication: authentication
                )
            }

            guard sessionGeneration == expectedSessionGeneration, !Task.isCancelled else {
                return stateHolder.state.authentication == nil ? .signedOut : .deferred
            }
            return try applyRefreshResult(result)
        } catch {
            if Task.isCancelled || sessionGeneration != expectedSessionGeneration {
                return stateHolder.state.authentication == nil ? .signedOut : .deferred
            }
            return .unavailable
        }
    }

    private func invalidateRefreshFlight() {
        sessionGeneration += 1
        refreshFlight?.task.cancel()
        refreshFlight = nil
    }

    private func applyRefreshResult(
        _ result: MobileAuthenticationRefreshResult
    ) throws -> ForegroundAuthenticationRefreshDisposition {
        if result.shouldSignOut {
            try? sessionStore.clear()
            stateHolder.signOut()
            return .signedOut
        }
        guard let refreshed = result.authentication else {
            return .unavailable
        }
        if result.shouldPersist {
            try sessionStore.save(refreshed)
        }
        stateHolder.markSignedIn(authentication: refreshed)
        return .ready
    }
}
