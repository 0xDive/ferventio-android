import Foundation

@MainActor
func withActiveSceneAuthenticationRecovery<T>(
    _ operation: @MainActor @Sendable () async -> T,
    deferred: @autoclosure () -> T
) async -> T {
    if ActiveSceneRecoveryContext.generation != nil {
        return await operation()
    }
    guard ActiveSceneRecoveryGeneration.active else {
        return deferred()
    }
    let generation = ActiveSceneRecoveryGeneration.current
    return await ActiveSceneRecoveryContext.$generation.withValue(generation) {
        await operation()
    }
}
