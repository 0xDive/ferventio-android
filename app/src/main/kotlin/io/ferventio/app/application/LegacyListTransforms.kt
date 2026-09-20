package io.ferventio.app.application

internal inline fun <T> mapLegacyListIfChanged(
    source: List<T>,
    transform: (T) -> T,
): List<T>? {
    var updated: MutableList<T>? = null
    for (index in source.indices) {
        val current = source[index]
        val replacement = transform(current)
        if (replacement != current) {
            val target = updated ?: source.toMutableList().also { updated = it }
            target[index] = replacement
        }
    }
    return updated
}
