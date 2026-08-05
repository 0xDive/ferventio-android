package io.ferventio.app.data

import io.ferventio.app.testing.DeterministicFuzzer
import java.time.Instant
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupCodecFuzzTest {
    @Test
    fun arbitraryBackupTextNeverThrowsJvmErrors() {
        val fuzz = DeterministicFuzzer(seed = 0x0F99_1306L)

        repeat(1_000) { iteration ->
            val raw = fuzz.text(maxLength = 32_768)
            try {
                SettingsBackupCodec.decode(raw)
            } catch (_: Exception) {
                // Malformed input is expected. JVM Errors still escape and fail the test.
            }
        }
    }

    @Test
    fun mutationsCanOnlyDecodeWithAValidContentHash() {
        val fuzz = DeterministicFuzzer(seed = 0x0F99_1307L)
        val content = sampleContent()
        val source = SettingsBackupCodec.encode(
            SettingsBackupDocument(
                createdAt = Instant.parse("2026-07-31T00:00:00Z").toString(),
                appVersion = "0.0.1-test",
                contentHash = SettingsBackupCodec.contentHash(content),
                content = content,
            ),
            pretty = false,
        )

        repeat(750) {
            val mutated = fuzz.mutate(source)
            try {
                val decoded = SettingsBackupCodec.decode(mutated)
                assertEquals(SettingsBackupCodec.contentHash(decoded.content), decoded.contentHash)
            } catch (_: Exception) {
                // Rejecting a mutation is the expected fail-closed path.
            }
        }
    }

    @Test
    fun deeplyNestedBackupIsRejectedByTheSharedGuard() {
        val deep = "[".repeat(SettingsBackupCodec.MAX_BACKUP_JSON_DEPTH + 1) +
            "0" + "]".repeat(SettingsBackupCodec.MAX_BACKUP_JSON_DEPTH + 1)
        val failure = assertFailsWith<IllegalArgumentException> { SettingsBackupCodec.decode(deep) }
        assertTrue(failure.message.orEmpty().contains("вложенность"))
    }

    private fun sampleContent(): SettingsBackupContent = SettingsBackupContent(
        settings = BackupSettings(
            themeMode = "DARK",
            fontScalePercent = 100,
            messageDensity = "NORMAL",
            showAvatars = true,
            showBadges = true,
            showTimestamps = true,
            nameStyle = "DISPLAY_NAME",
            wrapMessageLines = true,
            showDeletedMessageContent = false,
            mentionColorArgb = 0xFFFFC857,
            autoScrollEnabled = true,
            animateEmotes = true,
            emoteScalePercent = 100,
            betterTtvEnabled = true,
            frankerFaceZEnabled = true,
            sevenTvEnabled = true,
            sendOnEnter = true,
            showComposerEmoteImages = true,
            replyNotificationsEnabled = true,
            autoModNotificationsEnabled = true,
            localHistoryEnabled = true,
            localHistoryLimit = 500,
            localHistoryRetentionDays = 30,
            localHistoryMaxSizeMb = 250,
            userCardTimeoutPresetsSeconds = listOf(10, 60, 600),
            userCardShowBanAction = true,
            userCardModerationActionOrder = listOf("timeout:10", "ban"),
            recentMessagesEnabled = true,
        ),
        channels = BackupChannels(
            logins = listOf("channel_a", "channel_b"),
            selectedLogin = "channel_a",
            favouriteChannelIds = listOf("1"),
            pinnedChannelIds = listOf("1"),
            recentChannelIds = listOf("2"),
        ),
        favouriteEmotes = listOf("provider:emote"),
    )
}
