package io.ferventio.benchmark

import android.content.Intent

internal const val TARGET_PACKAGE = "io.ferventio.app"
private const val TARGET_ACTIVITY = "$TARGET_PACKAGE.MainActivity"
private const val ACTION_PERFORMANCE_TEST =
    "io.ferventio.app.action.PERFORMANCE_TEST"
private const val EXTRA_INITIAL_MESSAGES =
    "io.ferventio.app.extra.PERFORMANCE_INITIAL_MESSAGES"
private const val EXTRA_MESSAGES_PER_SECOND =
    "io.ferventio.app.extra.PERFORMANCE_MESSAGES_PER_SECOND"
private const val EXTRA_DURATION_SECONDS =
    "io.ferventio.app.extra.PERFORMANCE_DURATION_SECONDS"

internal fun performanceIntent(
    initialMessages: Int,
    messagesPerSecond: Int = 0,
    durationSeconds: Int = 0,
): Intent = Intent(ACTION_PERFORMANCE_TEST)
    .setClassName(TARGET_PACKAGE, TARGET_ACTIVITY)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    .putExtra(EXTRA_INITIAL_MESSAGES, initialMessages)
    .putExtra(EXTRA_MESSAGES_PER_SECOND, messagesPerSecond)
    .putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
