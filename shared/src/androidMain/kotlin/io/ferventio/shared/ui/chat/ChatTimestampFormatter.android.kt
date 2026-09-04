package io.ferventio.shared.ui.chat

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val chatTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal actual fun formatChatTimestamp(epochMillis: Long): String = chatTimestampFormatter.format(
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
)
