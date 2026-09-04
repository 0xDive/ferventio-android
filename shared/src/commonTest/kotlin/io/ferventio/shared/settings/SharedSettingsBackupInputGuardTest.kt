package io.ferventio.shared.settings

import kotlin.test.Test
import kotlin.test.assertFailsWith

class SharedSettingsBackupInputGuardTest {
    @Test
    fun acceptsBracketsInsideJsonStrings() {
        SharedSettingsBackupInputGuard.requireWithinLimits(
            """{"value":"[[[{{{\\\"}}}]]]"}""",
        )
    }

    @Test
    fun rejectsJsonNestingBeyondAndroidLimit() {
        val raw = "[".repeat(SharedSettingsBackupInputGuard.MAX_BACKUP_JSON_DEPTH + 1) +
            "]".repeat(SharedSettingsBackupInputGuard.MAX_BACKUP_JSON_DEPTH + 1)

        assertFailsWith<IllegalArgumentException> {
            SharedSettingsBackupInputGuard.requireWithinLimits(raw)
        }
    }

    @Test
    fun rejectsUtf8PayloadOverOneMiBEvenWhenCharacterCountFits() {
        val raw = "é".repeat(SharedSettingsBackupInputGuard.MAX_BACKUP_FILE_BYTES / 2 + 1)

        assertFailsWith<IllegalArgumentException> {
            SharedSettingsBackupInputGuard.requireWithinLimits(raw)
        }
    }

    @Test
    fun acceptsPayloadAtUtf8ByteLimit() {
        val raw = "a".repeat(SharedSettingsBackupInputGuard.MAX_BACKUP_FILE_BYTES)

        SharedSettingsBackupInputGuard.requireWithinLimits(raw)
    }
}
