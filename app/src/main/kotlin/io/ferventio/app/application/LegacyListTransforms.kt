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

internal inline fun <T, K> prependLegacyDistinctByKeyBounded(
    source: List<T>,
    value: T,
    maxSize: Int,
    key: (T) -> K,
): List<T> {
    if (maxSize <= 0) return emptyList()
    val valueKey = key(value)
    val first = source.firstOrNull()
    if (
        first != null &&
        key(first) == valueKey &&
        first == value &&
        source.size <= maxSize
    ) {
        return source
    }
    return buildList(minOf(maxSize, source.size + 1)) {
        add(value)
        source.forEach { item ->
            if (key(item) != valueKey && size < maxSize) add(item)
        }
    }
}

internal fun <K, V> removeLegacyMapKeyIfPresent(
    source: Map<K, V>,
    key: K,
): Map<K, V> = if (key in source) {
    source - key
} else {
    source
}

internal fun <T> prependLegacyBoundedAllowDuplicates(
    source: List<T>,
    value: T,
    maxSize: Int,
): List<T> {
    if (maxSize <= 0) return emptyList()
    val resultSize = minOf(maxSize, source.size + 1)
    return ArrayList<T>(resultSize).apply {
        add(value)
        val retained = (resultSize - 1).coerceAtLeast(0)
        for (index in 0 until retained) {
            add(source[index])
        }
    }
}
