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
    private var authenticationLeaseRefreshTask: Task<Void, Never>?
    private var isPrimarySceneActive = false
    private lazy var workspaceLayoutRuntimeBridge = try? WorkspaceLayoutRuntimeBridge.live(
        stateHolder: runtimeState.workspace
    )
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
            messageRulesState: runtimeState.messageRules
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
                settingsState: runtimeState.settings,
                rulesState: runtimeState.messageRules,
                filtersState: runtimeState.savedFilters
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
                self?.scheduleAuthenticationLeaseRefresh()
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
        cancelAuthenticationLeaseRefresh()
        authenticatedChatRuntimeBridge?.stop()
        networkRecoveryObserver.stop()
    }

    func makeRootViewController() -> UIViewController {
        MainViewControllerKt.MainViewController(
            onAuthenticate: { [weak self] in
                Task { @MainActor [weak self] in
                    await self?.authenticationRuntimeBridge?.signIn()
                    await self?.restoreWorkspaceAndSynchronizePush()
                    self?.scheduleAuthenticationLeaseRefresh()
                }
            },
            onSignOut: { [weak self] in
                Task { @MainActor [weak self] in
                    await self?.signOutAndCleanup()
                }
            },
            onAuthenticationRequired: { [weak self] in
                Task { @MainActor [weak self] in
                    await self?.recoverAuthenticationAfterChatRejection()
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
            onUpsertHighlightRule: { [weak self] rule in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceRuntimeBridge?.upsertHighlightRule(
                        authentication: runtimeState.authentication.state.authentication,
                        rule: rule
                    )
                }
            },
            onDeleteHighlightRule: { [weak self] ruleId in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceRuntimeBridge?.deleteHighlightRule(
                        authentication: runtimeState.authentication.state.authentication,
                        ruleId: ruleId
                    )
                }
            },
            onUpsertIgnoreRule: { [weak self] rule in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceRuntimeBridge?.upsertIgnoreRule(
                        authentication: runtimeState.authentication.state.authentication,
                        rule: rule
                    )
                }
            },
            onDeleteIgnoreRule: { [weak self] ruleId in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceRuntimeBridge?.deleteIgnoreRule(
                        authentication: runtimeState.authentication.state.authentication,
                        ruleId: ruleId
                    )
                }
            },
            onUpsertSavedFilter: { [weak self] filter in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceRuntimeBridge?.upsertSavedFilter(
                        authentication: runtimeState.authentication.state.authentication,
                        filter: filter
                    )
                }
            },
            onDeleteSavedFilter: { [weak self] filterId in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceRuntimeBridge?.deleteSavedFilter(
                        authentication: runtimeState.authentication.state.authentication,
                        filterId: filterId
                    )
                }
            },
            onImportSavedFilters: { [weak self] raw in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await SavedFiltersTransferRuntimeBridge.importFilters(
                        authentication: runtimeState.authentication.state.authentication,
                        state: runtimeState.savedFilters,
                        raw: raw
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
            },
            onSetSplitFilterQuery: { [weak self] splitId, filterQuery in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceLayoutRuntimeBridge?.setSplitFilterQuery(
                        authentication: runtimeState.authentication.state.authentication,
                        splitId: splitId,
                        filterQuery: filterQuery
                    )
                }
            },
            onSetSplitChannel: { [weak self] splitId, channelId in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceLayoutRuntimeBridge?.setSplitChannel(
                        authentication: runtimeState.authentication.state.authentication,
                        splitId: splitId,
                        channelId: channelId
                    )
                }
            },
            onFocusSplit: { [weak self] splitId in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceLayoutRuntimeBridge?.focusSplit(
                        authentication: runtimeState.authentication.state.authentication,
                        splitId: splitId
                    )
                }
            },
            onAddSplit: { [weak self] in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceLayoutRuntimeBridge?.addSplit(
                        authentication: runtimeState.authentication.state.authentication
                    )
                }
            },
            onRemoveSplit: { [weak self] splitId in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceLayoutRuntimeBridge?.removeSplit(
                        authentication: runtimeState.authentication.state.authentication,
                        splitId: splitId
                    )
                }
            },
            onSetPrimaryFraction: { [weak self] fraction in
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    _ = await workspaceLayoutRuntimeBridge?.setPrimaryFraction(
                        authentication: runtimeState.authentication.state.authentication,
                        fraction: fraction
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
        cancelAuthenticationLeaseRefresh()
        runtimeState.lifecycle.markInactive()
    }

    func sceneWillEnterForeground() {
        runtimeState.lifecycle.markInactive()
    }

    func sceneDidEnterBackground() {
        isPrimarySceneActive = false
        cancelAuthenticationLeaseRefresh()
        runtimeState.lifecycle.markBackground()
        authenticatedChatRuntimeBridge?.stop()
    }

    func sceneDidDisconnect() {
        isPrimarySceneActive = false
        cancelAuthenticationLeaseRefresh()
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
        cancelAuthenticationLeaseRefresh()
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
            cancelAuthenticationLeaseRefresh()
            authenticatedChatRuntimeBridge?.stop(clearState: true)
            clearAuthenticatedWorkspaceState()
            await synchronizePushBackendRegistration()
        case .unavailable:
            authenticatedChatRuntimeBridge?.stop()
            scheduleAuthenticationLeaseRefresh()
        case .ready:
            await synchronizeReadyAuthenticatedRuntime()
            scheduleAuthenticationLeaseRefresh()
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
            cancelAuthenticationLeaseRefresh()
            authenticatedChatRuntimeBridge?.stop(clearState: true)
            clearAuthenticatedWorkspaceState()
            await synchronizePushBackendRegistration()
        case .unavailable:
            authenticatedChatRuntimeBridge?.stop()
            scheduleAuthenticationLeaseRefresh()
        case .ready:
            await synchronizeReadyAuthenticatedRuntime()
            scheduleAuthenticationLeaseRefresh()
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
            cancelAuthenticationLeaseRefresh()
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
        runtimeState.messageRules.clear()
        runtimeState.savedFilters.clear()
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

    private func scheduleAuthenticationLeaseRefresh() {
        cancelAuthenticationLeaseRefresh()
        guard
            isPrimarySceneActive,
            let lease = runtimeState.authentication.state.authentication?.accessLease
        else {
            return
        }

        let nowEpochMillis = Int64(Date().timeIntervalSince1970 * 1_000)
        let untilSafetyWindowMillis =
            lease.leaseExpiresAtEpochMillis - nowEpochMillis - authenticationRefreshLeadTimeMillis
        let delayMillis = untilSafetyWindowMillis > 0
            ? untilSafetyWindowMillis
            : authenticationRefreshRetryDelayMillis
        let delayNanoseconds = UInt64(delayMillis) * 1_000_000

        authenticationLeaseRefreshTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(nanoseconds: delayNanoseconds)
            } catch {
                return
            }
            guard let self, !Task.isCancelled, isPrimarySceneActive else { return }
            authenticationLeaseRefreshTask = nil
            await refreshAuthenticationAndSynchronizeRuntime()
        }
    }

    private func cancelAuthenticationLeaseRefresh() {
        authenticationLeaseRefreshTask?.cancel()
        authenticationLeaseRefreshTask = nil
    }

    private let authenticationRefreshLeadTimeMillis: Int64 = 5_000
    private let authenticationRefreshRetryDelayMillis: Int64 = 30_000
}
