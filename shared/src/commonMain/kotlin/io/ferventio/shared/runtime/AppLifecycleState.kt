package io.ferventio.shared.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLifecyclePhase {
    ACTIVE,
    INACTIVE,
    BACKGROUND,
}

/**
 * Shared lifecycle state fed by platform adapters.
 *
 * Platform hosts own lifecycle observation; shared UI and runtime services consume this holder
 * instead of depending on UIKit or Android lifecycle types directly.
 */
class AppLifecycleStateHolder(
    initialPhase: AppLifecyclePhase,
) {
    constructor() : this(AppLifecyclePhase.BACKGROUND)

    var phase by mutableStateOf(initialPhase)
        private set

    fun markActive() {
        phase = AppLifecyclePhase.ACTIVE
    }

    fun markInactive() {
        phase = AppLifecyclePhase.INACTIVE
    }

    fun markBackground() {
        phase = AppLifecyclePhase.BACKGROUND
    }
}
