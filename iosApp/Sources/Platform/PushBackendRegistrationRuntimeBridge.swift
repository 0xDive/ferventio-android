import FerventioShared
import Foundation

@MainActor
final class PushBackendRegistrationRuntimeBridge {
    private let stateHolder: PushRegistrationStateHolder
    private let workspaceState: WorkspaceRuntimeStateHolder
    private let identityStore: DeviceIdentityStore
    private let coordinator: ApnsPushRegistrationCoordinator
    private let appVersion: String

    private var synchronizationInFlight = false
    private var pendingAuthentication: StoredAuthentication?
    private var lastRegisteredPushContextRevision: Int64?
    private var lastRegisteredAuthenticationFingerprint: String?

    init(
        stateHolder: PushRegistrationStateHolder,
        workspaceState: WorkspaceRuntimeStateHolder,
        identityStore: DeviceIdentityStore,
        coordinator: ApnsPushRegistrationCoordinator = ApnsPushRegistrationCoordinator(),
        appVersion: String
    ) {
        self.stateHolder = stateHolder
        self.workspaceState = workspaceState
        self.identityStore = identityStore
        self.coordinator = coordinator
        self.appVersion = appVersion
    }

    static func live(
        stateHolder: PushRegistrationStateHolder,
        workspaceState: WorkspaceRuntimeStateHolder,
        bundle: Bundle = .main
    ) throws -> PushBackendRegistrationRuntimeBridge {
        let configuration = try AppConfiguration.live(bundle: bundle)
        let keychain = KeychainStore(service: configuration.keychainService)
        return PushBackendRegistrationRuntimeBridge(
            stateHolder: stateHolder,
            workspaceState: workspaceState,
            identityStore: DeviceIdentityStore(store: keychain),
            appVersion: resolvedAppVersion(bundle: bundle)
        )
    }

    func synchronize(authentication: StoredAuthentication?) async {
        guard let authentication else {
            return
        }

        pendingAuthentication = authentication
        guard !synchronizationInFlight else {
            return
        }

        synchronizationInFlight = true
        defer { synchronizationInFlight = false }

        var nextAuthentication = pendingAuthentication
        pendingAuthentication = nil

        while let currentAuthentication = nextAuthentication {
            let revision = workspaceState.pushContextRevision
            let authenticationFingerprint = fingerprint(for: currentAuthentication)
            guard
                let deviceToken = stateHolder.deviceToken,
                requiresSynchronization(
                    revision: revision,
                    authenticationFingerprint: authenticationFingerprint
                )
            else {
                nextAuthentication = pendingAuthentication
                pendingAuthentication = nil
                continue
            }

            let contextChanged = await synchronizeOnce(
                authentication: currentAuthentication,
                authenticationFingerprint: authenticationFingerprint,
                deviceToken: deviceToken,
                pushContextRevision: revision
            )

            if let queuedAuthentication = pendingAuthentication {
                nextAuthentication = queuedAuthentication
                pendingAuthentication = nil
            } else if contextChanged {
                // Serialize token/workspace changes behind an in-flight PUT so the newest context
                // always wins on the backend instead of allowing stale completion order.
                nextAuthentication = currentAuthentication
            } else {
                nextAuthentication = nil
            }
        }
    }

    private func requiresSynchronization(
        revision: Int64,
        authenticationFingerprint: String
    ) -> Bool {
        stateHolder.needsBackendRegistration ||
            lastRegisteredPushContextRevision != revision ||
            lastRegisteredAuthenticationFingerprint != authenticationFingerprint
    }

    private func synchronizeOnce(
        authentication: StoredAuthentication,
        authenticationFingerprint: String,
        deviceToken: String,
        pushContextRevision: Int64
    ) async -> Bool {
        stateHolder.markBackendRegistrationStarted()
        let workspace = workspaceState.snapshot

        do {
            let identity = try identityStore.loadOrCreate()
            _ = try await coordinator.registerAuthenticatedWorkspace(
                serverUrl: authentication.backendCredential.serverUrl,
                identity: identity,
                apnsDeviceToken: deviceToken,
                appVersion: appVersion,
                authentication: authentication,
                workspace: workspace
            )

            guard
                stateHolder.deviceToken == deviceToken,
                workspaceState.pushContextRevision == pushContextRevision
            else {
                return true
            }

            lastRegisteredPushContextRevision = pushContextRevision
            lastRegisteredAuthenticationFingerprint = authenticationFingerprint
            stateHolder.markBackendRegistered()
            return false
        } catch {
            guard
                stateHolder.deviceToken == deviceToken,
                workspaceState.pushContextRevision == pushContextRevision
            else {
                return true
            }

            stateHolder.markBackendRegistrationFailed(
                message: String(describing: error)
            )
            return false
        }
    }

    private func fingerprint(for authentication: StoredAuthentication) -> String {
        let session = authentication.accessLease?.session
        return [
            authentication.backendCredential.serverUrl,
            session?.userId ?? "",
            session?.login ?? ""
        ].joined(separator: "\u{1F}")
    }

    private static func resolvedAppVersion(bundle: Bundle) -> String {
        for key in ["CFBundleShortVersionString", "CFBundleVersion"] {
            if let rawValue = bundle.object(forInfoDictionaryKey: key) as? String {
                let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
                if !value.isEmpty {
                    return value
                }
            }
        }
        return "unknown"
    }
}
