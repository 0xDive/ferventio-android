import FerventioShared
import UIKit
import UserNotifications

@main
@MainActor
final class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    var window: UIWindow?

    private let runtimeState = MainViewControllerKt.IosRuntimeState()
    private var authenticationRuntimeBridge: MobileAuthenticationRuntimeBridge?
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
        Task {
            await pushRuntimeBridge.refreshAuthorizationStatus()
        }

        do {
            let authenticationRuntimeBridge = try MobileAuthenticationRuntimeBridge.live(
                stateHolder: runtimeState.authentication
            )
            self.authenticationRuntimeBridge = authenticationRuntimeBridge
            Task { @MainActor in
                await authenticationRuntimeBridge.restore()
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
                }
            }
        )
        window.makeKeyAndVisible()
        self.window = window
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        Task {
            await pushRuntimeBridge.refreshAuthorizationStatus()
        }
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        pushRuntimeBridge.didRegister(deviceToken: deviceToken)
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
        lifecycleObserver.stop()
    }
}
