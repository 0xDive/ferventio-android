import FerventioShared
import UIKit

@MainActor
final class AppLifecycleObserver: NSObject {
    private let stateHolder: AppLifecycleStateHolder
    private let notificationCenter: NotificationCenter
    private var isStarted = false

    init(
        stateHolder: AppLifecycleStateHolder,
        notificationCenter: NotificationCenter = .default
    ) {
        self.stateHolder = stateHolder
        self.notificationCenter = notificationCenter
        super.init()
    }

    func start(applicationState: UIApplication.State = UIApplication.shared.applicationState) {
        guard !isStarted else { return }
        isStarted = true

        notificationCenter.addObserver(
            self,
            selector: #selector(didBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
        notificationCenter.addObserver(
            self,
            selector: #selector(willResignActive),
            name: UIApplication.willResignActiveNotification,
            object: nil
        )
        notificationCenter.addObserver(
            self,
            selector: #selector(willEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
        notificationCenter.addObserver(
            self,
            selector: #selector(didEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )

        sync(applicationState)
    }

    func stop() {
        guard isStarted else { return }
        notificationCenter.removeObserver(self)
        isStarted = false
    }

    @objc private func didBecomeActive() {
        stateHolder.markActive()
    }

    @objc private func willResignActive() {
        stateHolder.markInactive()
    }

    @objc private func willEnterForeground() {
        stateHolder.markInactive()
    }

    @objc private func didEnterBackground() {
        stateHolder.markBackground()
    }

    private func sync(_ applicationState: UIApplication.State) {
        switch applicationState {
        case .active:
            stateHolder.markActive()
        case .inactive:
            stateHolder.markInactive()
        case .background:
            stateHolder.markBackground()
        @unknown default:
            stateHolder.markInactive()
        }
    }
}
