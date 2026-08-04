package io.ferventio.app.domain

class RecentIdSet(
    private val maxSize: Int,
    private val ttlMillis: Long,
) {
    private val entries = LinkedHashMap<String, Long>()

    @Synchronized
    fun addIfNew(id: String, nowMillis: Long): Boolean {
        if (id.isBlank()) return true
        prune(nowMillis)
        if (entries.containsKey(id)) return false
        entries[id] = nowMillis
        trim()
        return true
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun size(nowMillis: Long): Int {
        prune(nowMillis)
        return entries.size
    }

    private fun prune(nowMillis: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value <= ttlMillis) break
            iterator.remove()
        }
    }

    private fun trim() {
        while (entries.size > maxSize) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }
}
