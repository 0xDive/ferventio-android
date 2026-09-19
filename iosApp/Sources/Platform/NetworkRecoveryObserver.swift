import Foundation
import Network

@MainActor
final class NetworkRecoveryObserver {
    private let queue = DispatchQueue(label: "io.ferventio.network-recovery")
    private let onReachable: @MainActor @Sendable () async -> Void
    private var monitor: NWPathMonitor?
    private var lastReachable: Bool?
    private var recoveryInFlight = false
    private var recoveryPending = false

    init(onReachable: @escaping @MainActor @Sendable () async -> Void) {
        self.onReachable = onReachable
    }

    func start() {
        guard monitor == nil else {
            return
        }
        let monitor = NWPathMonitor()
        self.monitor = monitor
        monitor.pathUpdateHandler = { [weak self] path in
            let reachable = path.status == .satisfied
            Task { @MainActor [weak self] in
                await self?.handlePathUpdate(reachable: reachable)
            }
        }
        monitor.start(queue: queue)
    }

    func stop() {
        monitor?.pathUpdateHandler = nil
        monitor?.cancel()
        monitor = nil
        lastReachable = nil
        recoveryPending = false
    }

    private func handlePathUpdate(reachable: Bool) async {
        guard monitor != nil else {
            return
        }
        let wasReachable = lastReachable
        lastReachable = reachable
        guard wasReachable == false, reachable else {
            return
        }

        recoveryPending = true
        guard !recoveryInFlight else {
            return
        }

        recoveryInFlight = true
        defer { recoveryInFlight = false }
        while recoveryPending, monitor != nil {
            recoveryPending = false
            let generation = ActiveSceneRecoveryGeneration.current
            await ActiveSceneRecoveryContext.$generation.withValue(generation) {
                await onReachable()
            }
        }
    }
}
