import FerventioShared
import Foundation

@MainActor
final class AuthenticatedChatRuntimeBridge {
    private let stateHolder: ChatRuntimeStateHolder
    private let attentionHolder: ChatAttentionStateHolder
    private let coordinator: AuthenticatedChatRuntimeCoordinator
    private let onAuthenticationRequired: () -> Void

    private var task: Task<Void, Never>?
    private var generation = 0
    private var runningFingerprint: String?

    init(
        stateHolder: ChatRuntimeStateHolder,
        attentionHolder: ChatAttentionStateHolder,
        onAuthenticationRequired: @escaping () -> Void = {}
    ) {
        self.stateHolder = stateHolder
        self.attentionHolder = attentionHolder
        self.coordinator = AuthenticatedChatRuntimeCoordinator(
            state: stateHolder,
            attention: attentionHolder
        )
        self.onAuthenticationRequired = onAuthenticationRequired
    }

    func synchronize(
        authentication: StoredAuthentication?,
        workspaceState: WorkspaceRuntimeStateHolder
    ) {
        guard
            let authentication,
            workspaceState.isReadyForPushRegistration,
            !workspaceState.channelIds.isEmpty
        else {
            stop(clearState: authentication == nil)
            return
        }

        let fingerprint = runtimeFingerprint(
            authentication: authentication,
            workspaceState: workspaceState
        )
        if runningFingerprint == fingerprint, task != nil {
            return
        }

        stop(clearState: false)
        generation += 1
        let currentGeneration = generation
        runningFingerprint = fingerprint
        let workspace = workspaceState.snapshot

        task = Task { @MainActor [weak self] in
            guard let self else {
                return
            }
            do {
                try await coordinator.run(
                    authentication: authentication,
                    workspace: workspace
                )
            } catch {
                // The shared runtime owns connection/error state. Swift only owns lifecycle.
            }
            guard generation == currentGeneration else {
                return
            }
            let authenticationRequired = stateHolder.authenticationRequired
            task = nil
            runningFingerprint = nil
            if authenticationRequired {
                onAuthenticationRequired()
            }
        }
    }

    func stop(clearState: Bool = false) {
        generation += 1
        coordinator.close()
        task?.cancel()
        task = nil
        runningFingerprint = nil
        if clearState {
            stateHolder.clear()
            attentionHolder.clear()
        }
    }

    private func runtimeFingerprint(
        authentication: StoredAuthentication,
        workspaceState: WorkspaceRuntimeStateHolder
    ) -> String {
        let session = authentication.accessLease?.session
        let leaseExpiresAt = authentication.accessLease?.leaseExpiresAtEpochMillis ?? 0
        return [
            session?.userId ?? "",
            String(leaseExpiresAt),
            String(workspaceState.pushContextRevision)
        ].joined(separator: "\u{1F}")
    }
}
