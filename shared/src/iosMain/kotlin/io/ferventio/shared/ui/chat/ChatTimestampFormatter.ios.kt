package io.ferventio.shared.ui.chat

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

internal actual fun formatChatTimestamp(epochMillis: Long): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "HH:mm"
    }
    val date = NSDate(timeIntervalSince1970 = epochMillis.toDouble() / 1_000.0)
    return formatter.stringFromDate(date)
}
