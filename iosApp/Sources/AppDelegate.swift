import FerventioShared
import UIKit
import UserNotifications

@main
@MainActor
final class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    var window: UIWindow?

    private let runtimeState = MainViewControllerKt.IosRuntimeState()
    private var authenticationRuntimeBridge: MobileAuthenticationRuntimeBridge?
    private var workspaceRuntimeBridge: WorkspaceRuntimeBridge?
    private var pushBackendRegistrationRuntimeBridge: PushBackendRegistrationRuntimeBridge?
    private var authenticatedChatRuntimeBridge: AuthenticatedChatRuntimeBridge?
    private lazy var lifecycleObserver = AppLifecycleObserver(
        stateHolder: runtimeState.lifecycle
    )
    private lazy var pushRuntimeBridge = PushNotificationRuntimeBridge(
        stateHolder: runtimeState.pushRegistration
    )
    private lazy var pushNavigationBridge = PushNotificationNavigationBridge(
        inbox: runtimeState.pushNavigation
    )

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        lifecycleObserver.start(applicationState: application.applicationState)
        authenticatedChatRuntimeBridge = AuthenticatedChatRuntimeBridge(
            stateHolder: runtimeState.chat,
            onAuthenticationRequired: { [weak self] in
                Task { @MainActor [weak self] in
                    await self?.recoverAuthenticationAfterChatRejection()
                }
            }
        )
        Task {
            await pushRuntimeBridge.refreshAuthorizationAndRestoreRemoteRegistration()
        }

        do {
            pushBackendRegistrationRuntimeBridge = try PushBackendRegistrationRuntimeBridge.live(
                stateHolder: runtimeState.pushRegistration,
                workspaceState: runtimeState.workspace
            )
        } catch {
            runtimeState.pushRegistration.markBackendRegistrationFailed(
                message: String(describing: error)
            )
        }

        do {
            workspaceRuntimeBridge = try WorkspaceRuntimeBridge.live(
                stateHolder: runtimeState.workspace
            )
        } catch {
            runtimeState.workspace.markLoadFailed(
                errorMessage: String(describing: error)
            )
        }

        do {
            let authenticationRuntimeBridge = try MobileAuthenticationRuntimeBridge.live(
                stateHolder: runtimeState.authentication
            )
            self.authenticationRuntimeBridge = authenticationRuntimeBridge
            Task { @MainActor [weak self] in
                await authenticationRuntimeBridge.restore()
                await self?.restoreWorkspaceAndSynchronizePush()
            }
        } catch {
            runtimeState.authentication.markFailed(
                errorMessage: String(describing: error)
            )
        }

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = MainViewControllerKt.MainViewController(
            onAuthenticate: { [weak self] in
                Task { @MainActor [weak self] in
                    await self?.authenticationRuntimeBridge?.signIn()
                    await self?.restoreWorkspaceAndSynchronizePush()
                }
            },
            onSignOut: { [weak self] in
                Task { @MainActor [weak self] in
                    await self?.signOutAndCleanup()
                }
            },
            onRequestNotificationPermission: { [weak self] in
                Task { @MainActor [weak self] in
                    await self?.requestNotificationPermission()
                }
            },
            onOpenNotificationSettings: { [weak self] in
                Task { @MainActor [weak self] in
                    self?.openNotificationSettings()
                }
            }
        )
        window.makeKeyAndVisible()
        self.window = window
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        Task { @MainActor [weak self] in
            guard let self else {
                return
            }
            await pushRuntimeBridge.refreshAuthorizationAndRestoreRemoteRegistration()
            guard let authenticationRuntimeBridge else {
                return
            }
            let disposition: ForegroundAuthenticationRefreshDisposition
            if runtimeState.chat.authenticationRequired {
                disposition = await authenticationRuntimeBridge.refreshAfterAuthenticationRejection()
            } else {
                disposition = await authenticationRuntimeBridge.refreshForForeground()
            }
            switch disposition {
            case .deferred:
                return
            case .signedOut:
                authenticatedChatRuntimeBridge?.stop(clearState: true)
                runtimeState.workspace.clear()
                await synchronizePushBackendRegistration()
                return
            case .unavailable:
                authenticatedChatRuntimeBridge?.stop()
                return
            case .ready:
                break
            }
            if runtimeState.workspace.isReadyForPushRegistration {
                await synchronizePushBackendRegistration()
                synchronizeAuthenticatedChatRuntime()
            } else {
                await restoreWorkspaceAndSynchronizePush()
            }
        }
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        authenticatedChatRuntimeBridge?.stop()
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        pushRuntimeBridge.didRegister(deviceToken: deviceToken)
        Task { @MainActor [weak self] in
            await self?.synchronizePushBackendRegistration()
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Swift.Error
    ) {
        pushRuntimeBridge.didFailToRegister(error: error)
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        guard let payload = PushNotificationNavigationPayload(
            userInfo: response.notification.request.content.userInfo
        ) else {
            return
        }
        await MainActor.run {
            pushNavigationBridge.handle(payload)
        }
    }

    func applicationWillTerminate(_ application: UIApplication) {
        authenticatedChatRuntimeBridge?.stop()
        lifecycleObserver.stop()
    }

    private func requestNotificationPermission() async {
        do {
            _ = try await pushRuntimeBridge.requestAuthorizationAndRegister()
        } catch {
            await pushRuntimeBridge.refreshAuthorizationStatus()
        }
    }

    private func openNotificationSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            return
        }
        UIApplication.shared.open(url)
    }

    private func signOutAndCleanup() async {
        guard let authenticationRuntimeBridge else {
            return
        }
        guard await authenticationRuntimeBridge.signOut() else {
            return
        }
        authenticatedChatRuntimeBridge?.stop(clearState: true)
        runtimeState.workspace.clear()
        await synchronizePushBackendRegistration()
    }

    private func recoverAuthenticationAfterChatRejection() async {
        guard let authenticationRuntimeBridge else {
            return
        }
        switch await authenticationRuntimeBridge.refreshAfterAuthenticationRejection() {
        case .deferred:
            return
        case .signedOut:
            authenticatedChatRuntimeBridge?.stop(clearState: true)
            runtimeState.workspace.clear()
            await synchronizePushBackendRegistration()
        case .unavailable:
            authenticatedChatRuntimeBridge?.stop()
        case .ready:
            if runtimeState.workspace.isReadyForPushRegistration {
                await synchronizePushBackendRegistration()
                synchronizeAuthenticatedChatRuntime()
            } else {
                await restoreWorkspaceAndSynchronizePush()
            }
        }
    }

    private func restoreWorkspaceAndSynchronizePush() async {
        let authentication = runtimeState.authentication.state.authentication
        guard let authentication else {
            authenticatedChatRuntimeBridge?.stop(clearState: true)
            runtimeState.workspace.clear()
            await synchronizePushBackendRegistration()
            return
        }
        guard let workspaceRuntimeBridge else {
            authenticatedChatRuntimeBridge?.stop()
            return
        }
        if await workspaceRuntimeBridge.restore(authentication: authentication) {
            await synchronizePushBackendRegistration()
            synchronizeAuthenticatedChatRuntime()
        } else {
            authenticatedChatRuntimeBridge?.stop()
        }
    }

    private func synchronizePushBackendRegistration() async {
        await pushBackendRegistrationRuntimeBridge?.synchronize(
            authentication: runtimeState.authentication.state.authentication
        )
    }

    private func synchronizeAuthenticatedChatRuntime() {
        authenticatedChatRuntimeBridge?.synchronize(
            authentication: runtimeState.authentication.state.authentication,
            workspaceState: runtimeState.workspace
        )
    }
}
