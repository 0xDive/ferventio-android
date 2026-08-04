package io.ferventio.app.security

/**
 * Performs cheap structural checks before a JSON parser allocates a full object graph.
 *
 * This is intentionally not a JSON validator. Syntax remains the responsibility of
 * kotlinx.serialization; the guard only bounds input size and structural nesting while
 * correctly ignoring brackets inside JSON strings.
 */
internal object JsonInputGuard {
    fun requireWithinLimits(
        raw: String,
        maxChars: Int,
        maxNestingDepth: Int,
        inputName: String,
    ) {
        require(maxChars > 0) { "maxChars должен быть положительным" }
        require(maxNestingDepth > 0) { "maxNestingDepth должен быть положительным" }
        require(raw.length <= maxChars) {
            "$inputName слишком большой: максимум $maxChars символов"
        }

        var depth = 0
        var inString = false
        var escaping = false

        raw.forEach { char ->
            if (inString) {
                when {
                    escaping -> escaping = false
                    char == '\\' -> escaping = true
                    char == '"' -> inString = false
                }
                return@forEach
            }

            when (char) {
                '"' -> inString = true
                '{', '[' -> {
                    depth += 1
                    require(depth <= maxNestingDepth) {
                        "$inputName имеет слишком глубокую JSON-вложенность: максимум $maxNestingDepth"
                    }
                }
                '}', ']' -> if (depth > 0) depth -= 1
            }
        }
    }
}
