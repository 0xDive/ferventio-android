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

internal inline fun <T, K> upsertLegacyListAtEnd(
    source: List<T>,
    value: T,
    maxSize: Int,
    key: (T) -> K,
): List<T> {
    val valueKey = key(value)
    val index = source.indexOfFirst { key(it) == valueKey }
    if (index == source.lastIndex && index >= 0 && source[index] == value) {
        return source
    }
    if (index < 0 && source.size >= maxSize) {
        return source
    }
    val result = ArrayList<T>(minOf(maxSize, if (index < 0) source.size + 1 else source.size))
    source.forEachIndexed { sourceIndex, item ->
        if (sourceIndex != index && result.size < maxSize - 1) {
            result += item
        }
    }
    if (result.size < maxSize) {
        result += value
    }
    return result
}

internal inline fun <T, K> removeLegacyListByKey(
    source: List<T>,
    keyValue: K,
    key: (T) -> K,
): List<T> {
    val index = source.indexOfFirst { key(it) == keyValue }
    if (index < 0) return source
    return source.toMutableList().apply { removeAt(index) }
}

internal fun prependLegacyDistinctBounded(
    source: List<String>,
    value: String,
    maxSize: Int,
): List<String> {
    if (maxSize <= 0) return emptyList()
    if (source.firstOrNull() == value && source.size <= maxSize) return source
    return buildList(minOf(maxSize, source.size + 1)) {
        add(value)
        source.forEach { item ->
            if (item != value && size < maxSize) add(item)
        }
    }
}
