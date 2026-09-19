package io.ferventio.shared.ui.moderation

import kotlin.time.Clock

internal fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
