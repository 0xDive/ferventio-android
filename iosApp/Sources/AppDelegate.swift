import FerventioShared
import UIKit

@main
@MainActor
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    private let runtimeState = MainViewControllerKt.IosRuntimeState()
    private lazy var lifecycleObserver = AppLifecycleObserver(
        stateHolder: runtimeState.lifecycle
    )
    private lazy var pushRuntimeBridge = PushNotificationRuntimeBridge(
        stateHolder: runtimeState.pushRegistration
    )

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        lifecycleObserver.start(applicationState: application.applicationState)
        Task {
            await pushRuntimeBridge.refreshAuthorizationStatus()
        }

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = MainViewControllerKt.MainViewController()
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

    func applicationWillTerminate(_ application: UIApplication) {
        lifecycleObserver.stop()
    }
}
