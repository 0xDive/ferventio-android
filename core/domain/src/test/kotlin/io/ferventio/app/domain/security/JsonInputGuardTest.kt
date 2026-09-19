package io.ferventio.app.domain.security

import org.junit.Assert.assertThrows
import org.junit.Test

class JsonInputGuardTest {
    @Test
    fun ignoresStructuralCharactersInsideStrings() {
        JsonInputGuard.requireWithinLimits(
            raw = """{"value":"[{still-string}]"}""",
            maxChars = 128,
            maxNestingDepth = 1,
            inputName = "test JSON",
        )
    }

    @Test
    fun rejectsOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) {
            JsonInputGuard.requireWithinLimits(
                raw = "12345",
                maxChars = 4,
                maxNestingDepth = 2,
                inputName = "test JSON",
            )
        }
    }

    @Test
    fun rejectsExcessiveNesting() {
        assertThrows(IllegalArgumentException::class.java) {
            JsonInputGuard.requireWithinLimits(
                raw = "{\"value\":[{}]}",
                maxChars = 128,
                maxNestingDepth = 2,
                inputName = "test JSON",
            )
        }
    }
}
