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
        let generation = ActiveSceneRecoveryGeneration.beginActive()
        ActiveSceneRecoveryContext.$generation.withValue(generation) {
            appDelegate?.sceneDidBecomeActive()
        }
    }

    func sceneWillResignActive(_ scene: UIScene) {
        ActiveSceneRecoveryGeneration.invalidate()
        appDelegate?.sceneWillResignActive()
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
        appDelegate?.sceneWillEnterForeground()
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        ActiveSceneRecoveryGeneration.invalidate()
        appDelegate?.sceneDidEnterBackground()
    }

    func sceneDidDisconnect(_ scene: UIScene) {
        ActiveSceneRecoveryGeneration.invalidate()
        appDelegate?.sceneDidDisconnect()
        window = nil
    }
}
