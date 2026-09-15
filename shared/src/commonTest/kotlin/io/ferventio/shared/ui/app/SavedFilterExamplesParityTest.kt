package io.ferventio.shared.ui.app

import io.ferventio.app.domain.MessageFilterLanguage
import kotlin.test.Test
import kotlin.test.assertTrue

class SavedFilterExamplesParityTest {
    @Test
    fun sharedFilterExamplesRemainValid() {
        val examples = MessageFilterLanguage.examples()

        assertTrue(examples.isNotEmpty())
        examples.forEach { (_, expression) ->
            assertTrue(
                actual = MessageFilterLanguage.compile(expression).isValid,
                message = "Expected example to compile: $expression",
            )
        }
    }
}
