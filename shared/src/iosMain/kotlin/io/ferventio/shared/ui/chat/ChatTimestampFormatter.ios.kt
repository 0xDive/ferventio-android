package io.ferventio.shared.ui.chat

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

private const val APPLE_REFERENCE_DATE_UNIX_SECONDS = 978_307_200.0

internal actual fun formatChatTimestamp(epochMillis: Long): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "HH:mm"
    }
    val unixSeconds = epochMillis.toDouble() / 1_000.0
    val date = NSDate(
        timeIntervalSinceReferenceDate = unixSeconds - APPLE_REFERENCE_DATE_UNIX_SECONDS,
    )
    return formatter.stringFromDate(date)
}
