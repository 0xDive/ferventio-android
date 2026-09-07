import UIKit

@MainActor
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    private var appDelegate: AppDelegate? {
        UIApplication.shared.delegate as? AppDelegate
    }

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene,
              let appDelegate else {
            return
        }

        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = appDelegate.makeRootViewController()
        self.window = window
        window.makeKeyAndVisible()
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        appDelegate?.sceneDidBecomeActive()
    }

    func sceneWillResignActive(_ scene: UIScene) {
        appDelegate?.sceneWillResignActive()
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
        appDelegate?.sceneWillEnterForeground()
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        appDelegate?.sceneDidEnterBackground()
    }

    func sceneDidDisconnect(_ scene: UIScene) {
        appDelegate?.sceneDidDisconnect()
        window = nil
    }
}
