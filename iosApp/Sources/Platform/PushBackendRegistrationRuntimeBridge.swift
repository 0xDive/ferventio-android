import FerventioShared
import Foundation

@MainActor
final class PushBackendRegistrationRuntimeBridge {
    private let stateHolder: PushRegistrationStateHolder
    private let identityStore: DeviceIdentityStore
    private let coordinator: ApnsPushRegistrationCoordinator
    private let appVersion: String

    private var synchronizationInFlight = false
    private var pendingAuthentication: StoredAuthentication?

    init(
        stateHolder: PushRegistrationStateHolder,
        identityStore: DeviceIdentityStore,
        coordinator: ApnsPushRegistrationCoordinator = ApnsPushRegistrationCoordinator(),
        appVersion: String
    ) {
        self.stateHolder = stateHolder
        self.identityStore = identityStore
        self.coordinator = coordinator
        self.appVersion = appVersion
    }

    static func live(
        stateHolder: PushRegistrationStateHolder,
        bundle: Bundle = .main
    ) throws -> PushBackendRegistrationRuntimeBridge {
        let configuration = try AppConfiguration.live(bundle: bundle)
        let keychain = KeychainStore(service: configuration.keychainService)
        return PushBackendRegistrationRuntimeBridge(
            stateHolder: stateHolder,
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
            guard
                stateHolder.needsBackendRegistration,
                let deviceToken = stateHolder.deviceToken
            else {
                nextAuthentication = pendingAuthentication
                pendingAuthentication = nil
                continue
            }

            let tokenRotated = await synchronizeOnce(
                authentication: currentAuthentication,
                deviceToken: deviceToken
            )

            if let queuedAuthentication = pendingAuthentication {
                nextAuthentication = queuedAuthentication
                pendingAuthentication = nil
            } else if tokenRotated, stateHolder.needsBackendRegistration {
                // Serialize token rotation behind the in-flight PUT so the newest token always wins
                // on the backend even if APNs updates it while an older request is awaiting I/O.
                nextAuthentication = currentAuthentication
            } else {
                nextAuthentication = nil
            }
        }
    }

    private func synchronizeOnce(
        authentication: StoredAuthentication,
        deviceToken: String
    ) async -> Bool {
        stateHolder.markBackendRegistrationStarted()

        do {
            let identity = try identityStore.loadOrCreate()
            _ = try await coordinator.register(
                serverUrl: authentication.backendCredential.serverUrl,
                identity: identity,
                apnsDeviceToken: deviceToken,
                appVersion: appVersion
            )

            guard stateHolder.deviceToken == deviceToken else {
                return true
            }
            stateHolder.markBackendRegistered()
            return false
        } catch {
            guard stateHolder.deviceToken == deviceToken else {
                return true
            }
            stateHolder.markBackendRegistrationFailed(
                message: String(describing: error)
            )
            return false
        }
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
