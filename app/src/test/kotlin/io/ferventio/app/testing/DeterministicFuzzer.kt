package io.ferventio.app.testing

import kotlin.random.Random

/** Small reproducible input generator used instead of a native fuzzing runtime in JVM tests. */
internal class DeterministicFuzzer(seed: Long) {
    private val random = Random(seed)
    private val atoms = arrayOf(
        "a", "Z", "0", "_", "-", ":", ";", "=", "@", "#", "/", "\\", "\"", "'",
        " ", "\t", "\r", "\n", "\u0000", "\u0001", "?", "[", "]", "{", "}",
        "й", "漢", "é", "😀", "👩🏽‍💻",
    )

    fun nextBoolean(): Boolean = random.nextBoolean()

    fun nextInt(from: Int, until: Int): Int = random.nextInt(from, until)

    fun choose(values: List<String>): String = values[random.nextInt(values.size)]

    fun text(maxLength: Int): String {
        require(maxLength >= 0)
        if (maxLength == 0) return ""
        val targetLength = random.nextInt(maxLength + 1)
        return buildString(targetLength) {
            while (length < targetLength) {
                val atom = atoms[random.nextInt(atoms.size)]
                val remaining = targetLength - length
                if (atom.length <= remaining) append(atom) else append(atom.take(remaining))
            }
        }
    }

    fun mutate(source: String, maxMutations: Int = 8): String {
        if (source.isEmpty()) return text(32)
        val value = StringBuilder(source)
        repeat(random.nextInt(1, maxMutations.coerceAtLeast(1) + 1)) {
            when (random.nextInt(4)) {
                0 -> if (value.isNotEmpty()) value.deleteCharAt(random.nextInt(value.length))
                1 -> value.insert(random.nextInt(value.length + 1), text(6))
                2 -> if (value.isNotEmpty()) value.setCharAt(random.nextInt(value.length), text(1).firstOrNull() ?: 'x')
                else -> if (value.length > 1) {
                    val start = random.nextInt(value.length)
                    val end = random.nextInt(start + 1, value.length + 1)
                    value.delete(start, end)
                }
            }
        }
        return value.toString()
    }
}
