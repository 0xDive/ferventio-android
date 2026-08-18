import FerventioShared
import UIKit
import UserNotifications

@main
@MainActor
final class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    private let runtimeState = MainViewControllerKt.IosRuntimeState()
    private var authenticationRuntimeBridge: MobileAuthenticationRuntimeBridge?
    private var workspaceRuntimeBridge: WorkspaceRuntimeBridge?
    private var pushBackendRegistrationRuntimeBridge: PushBackendRegistrationRuntimeBridge?
    private var authenticatedChatRuntimeBridge: AuthenticatedChatRuntimeBridge?
    private var isPrimarySceneActive = false
    private lazy var networkRecoveryObserver = NetworkRecoveryObserver(
        onReachable: { [weak self] in
            await self?.recoverAfterNetworkAvailable()
        }
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
        runtimeState.lifecycle.markInactive()
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
                stateHolder: runtimeState.workspace,
                settingsState: runtimeState.settings
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

        networkRecoveryObserver.start()
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(
            name: "Default Configuration",
            sessionRole: connectingSceneSession.role
        )
        configuration.delegateClass = SceneDelegate.self
        return configuration
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
        networkRecoveryObserver.stop()
    }

    func makeRootViewController() -> UIViewController {
        MainViewControllerKt.MainViewController(
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
            },
            onSaveSettings: { [weak self] preferences in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    await workspaceRuntimeBridge?.savePreferences(
                        authentication: runtimeState.authentication.state.authentication,
                        preferences: preferences
                    )
                }
            },
            onSelectChannel: { [weak self] channelId in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceRuntimeBridge?.persistSelectedChannel(
                        authentication: runtimeState.authentication.state.authentication,
                        channelId: channelId
                    )
                }
            },
            onAddChannel: { [weak self] login in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    let succeeded = await workspaceRuntimeBridge?.addChannel(
                        authentication: runtimeState.authentication.state.authentication,
                        login: login
                    ) ?? false
                    if succeeded {
                        await synchronizeWorkspaceTransportAfterChannelSetChanged()
                    }
                }
            },
            onSetChannelPinned: { [weak self] channelId, pinned in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceRuntimeBridge?.setChannelPinned(
                        authentication: runtimeState.authentication.state.authentication,
                        channelId: channelId,
                        pinned: pinned
                    )
                }
            },
            onRenameChannel: { [weak self] channelId, title in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceRuntimeBridge?.renameChannel(
                        authentication: runtimeState.authentication.state.authentication,
                        channelId: channelId,
                        title: title
                    )
                }
            },
            onRemoveChannel: { [weak self] channelId in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    let succeeded = await workspaceRuntimeBridge?.removeChannel(
                        authentication: runtimeState.authentication.state.authentication,
                        channelId: channelId
                    ) ?? false
                    if succeeded {
                        runtimeState.chat.retainChannels(channelIds: runtimeState.workspace.channelIds)
                        await synchronizeWorkspaceTransportAfterChannelSetChanged()
                    }
                }
            },
            onMoveChannel: { [weak self] channelId, targetIndex in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceRuntimeBridge?.moveChannel(
                        authentication: runtimeState.authentication.state.authentication,
                        channelId: channelId,
                        targetIndex: targetIndex
                    )
                }
            }
        )
    }

    func sceneDidBecomeActive() {
        isPrimarySceneActive = true
        runtimeState.lifecycle.markActive()
        Task { @MainActor [weak self] in
            guard let self else { return }
            await pushRuntimeBridge.refreshAuthorizationAndRestoreRemoteRegistration()
            await refreshAuthenticationAndSynchronizeRuntime()
        }
    }

    func sceneWillResignActive() {
        isPrimarySceneActive = false
        runtimeState.lifecycle.markInactive()
    }

    func sceneWillEnterForeground() {
        runtimeState.lifecycle.markInactive()
    }

    func sceneDidEnterBackground() {
        isPrimarySceneActive = false
        runtimeState.lifecycle.markBackground()
        authenticatedChatRuntimeBridge?.stop()
    }

    func sceneDidDisconnect() {
        isPrimarySceneActive = false
        authenticatedChatRuntimeBridge?.stop()
    }

    private func requestNotificationPermission() async {
        do {
            _ = try await pushRuntimeBridge.requestAuthorizationAndRegister()
        } catch {
            await pushRuntimeBridge.refreshAuthorizationStatus()
        }
    }

    private func openNotificationSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private func signOutAndCleanup() async {
        guard let authenticationRuntimeBridge else { return }
        guard await authenticationRuntimeBridge.signOut() else { return }
        authenticatedChatRuntimeBridge?.stop(clearState: true)
        clearAuthenticatedWorkspaceState()
        await synchronizePushBackendRegistration()
    }

    private func recoverAuthenticationAfterChatRejection() async {
        guard let authenticationRuntimeBridge else { return }
        switch await authenticationRuntimeBridge.refreshAfterAuthenticationRejection() {
        case .deferred:
            return
        case .signedOut:
            authenticatedChatRuntimeBridge?.stop(clearState: true)
            clearAuthenticatedWorkspaceState()
            await synchronizePushBackendRegistration()
        case .unavailable:
            authenticatedChatRuntimeBridge?.stop()
        case .ready:
            await synchronizeReadyAuthenticatedRuntime()
        }
    }

    private func recoverAfterNetworkAvailable() async {
        guard isPrimarySceneActive else { return }
        await pushRuntimeBridge.refreshAuthorizationAndRestoreRemoteRegistration()
        await refreshAuthenticationAndSynchronizeRuntime()
    }

    private func refreshAuthenticationAndSynchronizeRuntime() async {
        guard let authenticationRuntimeBridge else { return }
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
            clearAuthenticatedWorkspaceState()
            await synchronizePushBackendRegistration()
        case .unavailable:
            authenticatedChatRuntimeBridge?.stop()
        case .ready:
            await synchronizeReadyAuthenticatedRuntime()
        }
    }

    private func synchronizeReadyAuthenticatedRuntime() async {
        guard isPrimarySceneActive else {
            authenticatedChatRuntimeBridge?.stop()
            return
        }
        if runtimeState.workspace.isReadyForPushRegistration {
            await synchronizePushBackendRegistration()
            synchronizeAuthenticatedChatRuntime()
        } else {
            await restoreWorkspaceAndSynchronizePush()
        }
    }

    private func restoreWorkspaceAndSynchronizePush() async {
        let authentication = runtimeState.authentication.state.authentication
        guard let authentication else {
            authenticatedChatRuntimeBridge?.stop(clearState: true)
            clearAuthenticatedWorkspaceState()
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

    private func synchronizeWorkspaceTransportAfterChannelSetChanged() async {
        await synchronizePushBackendRegistration()
        synchronizeAuthenticatedChatRuntime()
    }

    private func clearAuthenticatedWorkspaceState() {
        runtimeState.workspace.clear()
        runtimeState.settings.clear()
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
