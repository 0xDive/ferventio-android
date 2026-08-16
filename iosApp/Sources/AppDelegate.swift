import FerventioShared
import UIKit

@main
@MainActor
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    private let lifecycleState = AppLifecycleStateHolder()
    private lazy var lifecycleObserver = AppLifecycleObserver(stateHolder: lifecycleState)

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        lifecycleObserver.start(applicationState: application.applicationState)

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = MainViewControllerKt.MainViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }

    func applicationWillTerminate(_ application: UIApplication) {
        lifecycleObserver.stop()
    }
}
