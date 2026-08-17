import Foundation
import Network

@MainActor
final class NetworkRecoveryObserver {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "io.ferventio.network-recovery")
    private let onReachable: @MainActor @Sendable () async -> Void
    private var started = false

    init(onReachable: @escaping @MainActor @Sendable () async -> Void) {
        self.onReachable = onReachable
    }

    func start() {
        guard !started else {
            return
        }
        started = true
        let onReachable = onReachable
        monitor.pathUpdateHandler = { path in
            guard path.status == .satisfied else {
                return
            }
            Task { @MainActor in
                await onReachable()
            }
        }
        monitor.start(queue: queue)
    }

    func stop() {
        guard started else {
            return
        }
        started = false
        monitor.cancel()
    }
}
