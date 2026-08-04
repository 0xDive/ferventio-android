package io.ferventio.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

internal const val CHAT_LIST_RESOURCE_ID = "ferventio-chat-list"
internal const val CHAT_LIST_TIMEOUT_MILLIS = 60_000L

internal fun requireChatList(
    device: UiDevice,
    timeoutMillis: Long = CHAT_LIST_TIMEOUT_MILLIS,
): UiObject2 {
    val resourceWaitMillis = (timeoutMillis / 2).coerceAtLeast(1L)
    device.wait(
        Until.findObject(By.res(CHAT_LIST_RESOURCE_ID)),
        resourceWaitMillis,
    )?.let { return it }

    // Some OEM accessibility bridges do not map Compose testTag to a resource ID.
    // The app exposes this description only in PERFORMANCE_TESTING target variants.
    device.wait(
        Until.findObject(By.desc(CHAT_LIST_RESOURCE_ID)),
        (timeoutMillis - resourceWaitMillis).coerceAtLeast(1L),
    )?.let { return it }

    val visibleNodes = device.findObjects(By.pkg(TARGET_PACKAGE))
        .asSequence()
        .mapNotNull { node ->
            node.resourceName?.takeIf(String::isNotBlank)
                ?: node.contentDescription?.takeIf(String::isNotBlank)
                ?: node.text?.takeIf(String::isNotBlank)
        }
        .distinct()
        .take(20)
        .joinToString()
        .ifBlank { "<none>" }

    error(
        "Ferventio chat list was not found within ${timeoutMillis}ms; " +
            "currentPackage=${device.currentPackageName}; visibleNodes=$visibleNodes",
    )
}

private const val CHAT_SWIPE_STEPS = 24
private const val CHAT_SWIPE_SETTLE_MILLIS = 120L

/**
 * Scrolls the Compose chat by injecting screen-coordinate swipes instead of keeping a UiObject2.
 *
 * LazyColumn updates its accessibility subtree while items are recycled. A UiObject2 captured before
 * the first fling can therefore become stale before the next gesture. Coordinate gestures keep the
 * benchmark focused on rendering and do not retain an accessibility node between frames.
 */
internal fun swipeChatList(
    device: UiDevice,
    direction: Direction,
    repetitions: Int,
) {
    require(repetitions > 0) { "repetitions must be positive" }
    requireChatList(device)

    val width = device.displayWidth
    val height = device.displayHeight
    check(width > 0 && height > 0) {
        "Benchmark device has invalid display size: ${width}x${height}"
    }

    val x = width / 2
    val top = (height * 3 / 10).coerceIn(1, height - 2)
    val bottom = (height * 7 / 10).coerceIn(1, height - 2)
    check(bottom > top) {
        "Benchmark swipe area is invalid: top=$top, bottom=$bottom, height=$height"
    }

    repeat(repetitions) {
        val injected = when (direction) {
            Direction.UP -> device.swipe(x, bottom, x, top, CHAT_SWIPE_STEPS)
            Direction.DOWN -> device.swipe(x, top, x, bottom, CHAT_SWIPE_STEPS)
            else -> error("Only vertical chat swipes are supported: $direction")
        }
        check(injected) { "Failed to inject $direction chat swipe" }
        Thread.sleep(CHAT_SWIPE_SETTLE_MILLIS)
    }
}
