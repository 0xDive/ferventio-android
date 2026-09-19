import Foundation

@MainActor
enum ActiveSceneRecoveryGeneration {
    private(set) static var current: UInt64 = 0
    private(set) static var active = false

    @discardableResult
    static func beginActive() -> UInt64 {
        current &+= 1
        active = true
        return current
    }

    static func invalidate() {
        current &+= 1
        active = false
    }

    static func isCurrent(_ generation: UInt64?) -> Bool {
        guard let generation else { return true }
        return generation == current
    }

    static var currentTaskIsValid: Bool {
        isCurrent(ActiveSceneRecoveryContext.generation)
    }
}

enum ActiveSceneRecoveryContext {
    @TaskLocal static var generation: UInt64?
}
