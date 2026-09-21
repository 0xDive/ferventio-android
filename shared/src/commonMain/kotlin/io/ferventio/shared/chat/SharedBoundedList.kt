package io.ferventio.shared.chat

import kotlin.collections.AbstractList

private const val SHARED_BOUNDED_LIST_CHUNK_SIZE = 64

internal class SharedBoundedList<T> private constructor(
    private val chunks: Array<Array<Any?>>,
    private val firstOffset: Int,
    override val size: Int,
    internal val limit: Int,
) : AbstractList<T>() {
    override fun get(index: Int): T {
        if (index !in 0 until size) {
            throw IndexOutOfBoundsException("index=$index, size=$size")
        }
        val absoluteIndex = firstOffset + index
        val chunkIndex = absoluteIndex / SHARED_BOUNDED_LIST_CHUNK_SIZE
        val itemIndex = absoluteIndex % SHARED_BOUNDED_LIST_CHUNK_SIZE
        @Suppress("UNCHECKED_CAST")
        return chunks[chunkIndex][itemIndex] as T
    }

    internal fun append(value: T): SharedBoundedList<T> {
        val writeIndex = firstOffset + size
        val chunkIndex = writeIndex / SHARED_BOUNDED_LIST_CHUNK_SIZE
        val itemIndex = writeIndex % SHARED_BOUNDED_LIST_CHUNK_SIZE

        var nextChunks = if (chunkIndex < chunks.size) {
            Array(chunks.size) { index ->
                if (index == chunkIndex) chunks[index].copyOf() else chunks[index]
            }
        } else {
            Array(chunks.size + 1) { index ->
                if (index < chunks.size) {
                    chunks[index]
                } else {
                    arrayOfNulls(SHARED_BOUNDED_LIST_CHUNK_SIZE)
                }
            }
        }
        nextChunks[chunkIndex][itemIndex] = value

        var nextFirstOffset = firstOffset
        var nextSize = size + 1
        if (nextSize > limit) {
            nextFirstOffset += 1
            nextSize = limit
            if (nextFirstOffset >= SHARED_BOUNDED_LIST_CHUNK_SIZE) {
                nextFirstOffset -= SHARED_BOUNDED_LIST_CHUNK_SIZE
                nextChunks = nextChunks.copyOfRange(1, nextChunks.size)
            }
        }

        return SharedBoundedList(
            chunks = nextChunks,
            firstOffset = nextFirstOffset,
            size = nextSize,
            limit = limit,
        )
    }

    internal companion object {
        fun <T> append(
            source: List<T>,
            value: T,
            limit: Int,
        ): List<T> {
            if (limit <= 0) return emptyList()
            if (source is SharedBoundedList<*> && source.limit == limit) {
                @Suppress("UNCHECKED_CAST")
                return (source as SharedBoundedList<T>).append(value)
            }

            val retainedSourceCount = minOf(source.size, (limit - 1).coerceAtLeast(0))
            val resultSize = retainedSourceCount + 1
            val chunkCount =
                (resultSize + SHARED_BOUNDED_LIST_CHUNK_SIZE - 1) /
                    SHARED_BOUNDED_LIST_CHUNK_SIZE
            val chunks = Array(chunkCount) {
                arrayOfNulls<Any?>(SHARED_BOUNDED_LIST_CHUNK_SIZE)
            }
            var targetIndex = 0
            val sourceStart = source.size - retainedSourceCount
            for (sourceIndex in sourceStart until source.size) {
                val chunkIndex = targetIndex / SHARED_BOUNDED_LIST_CHUNK_SIZE
                val itemIndex = targetIndex % SHARED_BOUNDED_LIST_CHUNK_SIZE
                chunks[chunkIndex][itemIndex] = source[sourceIndex]
                targetIndex += 1
            }
            val valueChunkIndex = targetIndex / SHARED_BOUNDED_LIST_CHUNK_SIZE
            val valueItemIndex = targetIndex % SHARED_BOUNDED_LIST_CHUNK_SIZE
            chunks[valueChunkIndex][valueItemIndex] = value

            return SharedBoundedList(
                chunks = chunks,
                firstOffset = 0,
                size = resultSize,
                limit = limit,
            )
        }
    }
}

internal fun <T> appendSharedBounded(
    source: List<T>,
    value: T,
    limit: Int,
): List<T> = SharedBoundedList.append(source, value, limit)
