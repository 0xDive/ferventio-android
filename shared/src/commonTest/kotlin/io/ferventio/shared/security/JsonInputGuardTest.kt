package io.ferventio.shared.security

import kotlin.test.Test
import kotlin.test.assertFailsWith

class JsonInputGuardTest {
    @Test
    fun acceptsNestedJsonWithinConfiguredLimits() {
        JsonInputGuard.requireWithinLimits(
            raw = "{\"items\":[{\"value\":1}]}",
            maxChars = 100,
            maxNestingDepth = 3,
            inputName = "payload",
        )
    }

    @Test
    fun rejectsOversizedInputBeforeParsing() {
        assertFailsWith<IllegalArgumentException> {
            JsonInputGuard.requireWithinLimits(
                raw = "{\"value\":12345}",
                maxChars = 5,
                maxNestingDepth = 4,
                inputName = "payload",
            )
        }
    }

    @Test
    fun rejectsExcessiveStructuralNesting() {
        assertFailsWith<IllegalArgumentException> {
            JsonInputGuard.requireWithinLimits(
                raw = "{\"a\":[[{\"b\":1}]]}",
                maxChars = 100,
                maxNestingDepth = 3,
                inputName = "payload",
            )
        }
    }

    @Test
    fun ignoresBracketsAndEscapedQuotesInsideStrings() {
        JsonInputGuard.requireWithinLimits(
            raw = "{\"text\":\"[[[ { \\\"nested-looking\\\" } ]]]\"}",
            maxChars = 100,
            maxNestingDepth = 1,
            inputName = "payload",
        )
    }
}
