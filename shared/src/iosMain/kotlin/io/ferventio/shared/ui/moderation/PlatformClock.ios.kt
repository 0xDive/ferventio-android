package io.ferventio.shared.ui.moderation

import platform.Foundation.NSDate

internal actual fun currentEpochMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1_000.0).toLong()
