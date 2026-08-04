package io.ferventio.app.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupFileIoTest {
    @Test
    fun utf8RoundTripPreservesNonAsciiContent() {
        val expected = "Ferventio резервная копия — канал_1 🎉"
        val output = ByteArrayOutputStream()

        BackupFileIo.writeUtf8(output, expected)
        val actual = BackupFileIo.readUtf8Limited(
            ByteArrayInputStream(output.toByteArray()),
            maxBytes = output.size(),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun exactByteLimitIsAccepted() {
        val bytes = "абв".toByteArray(Charsets.UTF_8)

        val decoded = BackupFileIo.readUtf8Limited(
            ByteArrayInputStream(bytes),
            maxBytes = bytes.size,
        )

        assertEquals("абв", decoded)
    }

    @Test
    fun oversizedStreamIsRejectedBeforeReturningPartialJson() {
        val bytes = ByteArray(17) { 'x'.code.toByte() }

        assertFailsWith<IllegalArgumentException> {
            BackupFileIo.readUtf8Limited(
                ByteArrayInputStream(bytes),
                maxBytes = 16,
            )
        }
    }

    @Test
    fun nonPositiveLimitIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            BackupFileIo.readUtf8Limited(ByteArrayInputStream(byteArrayOf()), maxBytes = 0)
        }
    }
}
