package io.ferventio.app.domain

/**
 * Small synchronized access-order LRU used for metadata that can be reloaded from Twitch.
 * Snapshots are immutable copies, so callers never observe the internal mutable map.
 */
class BoundedLruCache<K : Any, V : Any>(
    private val maxEntries: Int,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val values = object : LinkedHashMap<K, V>(maxEntries + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    operator fun get(key: K): V? = values[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        values[key] = value
    }

    @Synchronized
    fun putAll(entries: Map<K, V>) {
        entries.forEach { (key, value) -> values[key] = value }
    }

    @Synchronized
    fun remove(key: K): V? = values.remove(key)

    @Synchronized
    fun retainKeys(keys: Set<K>) {
        values.keys.retainAll(keys)
    }

    @Synchronized
    fun clear() = values.clear()

    @Synchronized
    fun size(): Int = values.size

    @Synchronized
    fun snapshot(): Map<K, V> = LinkedHashMap(values)
}
