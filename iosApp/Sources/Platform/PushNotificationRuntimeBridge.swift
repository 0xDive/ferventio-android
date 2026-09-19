import FerventioShared
import Foundation
import UserNotifications

@MainActor
final class PushNotificationRuntimeBridge {
    private let authorizationService: PushNotificationAuthorizing
    private let stateHolder: PushRegistrationStateHolder

    init(
        stateHolder: PushRegistrationStateHolder,
        authorizationService: PushNotificationAuthorizing = PushNotificationAuthorizationService()
    ) {
        self.stateHolder = stateHolder
        self.authorizationService = authorizationService
    }

    func refreshAuthorizationStatus() async {
        let status = await authorizationService.authorizationStatus()
        apply(status)
    }

    func refreshAuthorizationAndRestoreRemoteRegistration() async {
        let status = await authorizationService.authorizationStatus()
        apply(status)

        switch status {
        case .authorized, .provisional, .ephemeral:
            // Re-registering with APNs is intentionally safe and does not present the notification
            // permission prompt. iOS may rotate the device token, so established permission should
            // always restore remote registration when the app launches or returns to foreground.
            requestRemoteRegistration()
        case .notDetermined, .denied:
            break
        @unknown default:
            break
        }
    }

    func requestAuthorizationAndRegister() async throws -> Bool {
        let granted = try await authorizationService.requestAuthorization()
        await refreshAuthorizationStatus()
        if granted {
            requestRemoteRegistration()
        }
        return granted
    }

    func requestRemoteRegistration() {
        stateHolder.markRegistrationRequested()
        authorizationService.registerForRemoteNotifications()
    }

    func unregister() {
        authorizationService.unregisterForRemoteNotifications()
        stateHolder.clearRegistration()
    }

    func didRegister(deviceToken: Data) {
        stateHolder.markRegistered(token: deviceToken.apnsHexToken)
    }

    func didFailToRegister(error: Swift.Error) {
        stateHolder.markRegistrationFailed(message: error.localizedDescription)
    }

    private func apply(_ status: UNAuthorizationStatus) {
        switch status {
        case .notDetermined:
            stateHolder.markAuthorizationNotDetermined()
        case .denied:
            stateHolder.markAuthorizationDenied()
        case .authorized:
            stateHolder.markAuthorizationAuthorized()
        case .provisional:
            stateHolder.markAuthorizationProvisional()
        case .ephemeral:
            stateHolder.markAuthorizationEphemeral()
        @unknown default:
            stateHolder.markAuthorizationUnknown()
        }
    }
}

private extension Data {
    var apnsHexToken: String {
        map { String(format: "%02x", $0) }.joined()
    }
}
