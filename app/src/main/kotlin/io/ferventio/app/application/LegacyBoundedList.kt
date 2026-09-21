package io.ferventio.app.application

import java.util.RandomAccess
import kotlin.collections.AbstractList

private const val LEGACY_BOUNDED_LIST_CHUNK_SIZE = 64

/**
 * Immutable bounded list optimized for the live-chat append path.
 *
 * Appending copies the small chunk table plus one 64-element chunk instead of copying the entire
 * live-message window. Existing chunks are shared safely because they are never mutated.
 */
internal class LegacyBoundedList<T> private constructor(
    private val chunks: Array<Array<Any?>>,
    private val firstOffset: Int,
    override val size: Int,
    internal val limit: Int,
) : AbstractList<T>(), RandomAccess {
    override fun get(index: Int): T {
        if (index !in 0 until size) {
            throw IndexOutOfBoundsException("index=$index, size=$size")
        }
        val absoluteIndex = firstOffset + index
        val chunkIndex = absoluteIndex / LEGACY_BOUNDED_LIST_CHUNK_SIZE
        val itemIndex = absoluteIndex % LEGACY_BOUNDED_LIST_CHUNK_SIZE
        @Suppress("UNCHECKED_CAST")
        return chunks[chunkIndex][itemIndex] as T
    }

    internal fun append(value: T): LegacyBoundedList<T> {
        val writeIndex = firstOffset + size
        val chunkIndex = writeIndex / LEGACY_BOUNDED_LIST_CHUNK_SIZE
        val itemIndex = writeIndex % LEGACY_BOUNDED_LIST_CHUNK_SIZE

        var nextChunks = if (chunkIndex < chunks.size) {
            Array(chunks.size) { index ->
                if (index == chunkIndex) chunks[index].copyOf() else chunks[index]
            }
        } else {
            Array(chunks.size + 1) { index ->
                if (index < chunks.size) {
                    chunks[index]
                } else {
                    arrayOfNulls(LEGACY_BOUNDED_LIST_CHUNK_SIZE)
                }
            }
        }
        nextChunks[chunkIndex][itemIndex] = value

        var nextFirstOffset = firstOffset
        var nextSize = size + 1
        if (nextSize > limit) {
            nextFirstOffset += 1
            nextSize = limit
            if (nextFirstOffset >= LEGACY_BOUNDED_LIST_CHUNK_SIZE) {
                nextFirstOffset -= LEGACY_BOUNDED_LIST_CHUNK_SIZE
                nextChunks = nextChunks.copyOfRange(1, nextChunks.size)
            }
        }

        return LegacyBoundedList(
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
            if (source is LegacyBoundedList<*> && source.limit == limit) {
                @Suppress("UNCHECKED_CAST")
                return (source as LegacyBoundedList<T>).append(value)
            }

            val retainedSourceCount = minOf(source.size, (limit - 1).coerceAtLeast(0))
            val resultSize = retainedSourceCount + 1
            val chunkCount =
                (resultSize + LEGACY_BOUNDED_LIST_CHUNK_SIZE - 1) / LEGACY_BOUNDED_LIST_CHUNK_SIZE
            val chunks = Array(chunkCount) {
                arrayOfNulls<Any?>(LEGACY_BOUNDED_LIST_CHUNK_SIZE)
            }
            var targetIndex = 0
            val sourceStart = source.size - retainedSourceCount
            for (sourceIndex in sourceStart until source.size) {
                val chunkIndex = targetIndex / LEGACY_BOUNDED_LIST_CHUNK_SIZE
                val itemIndex = targetIndex % LEGACY_BOUNDED_LIST_CHUNK_SIZE
                chunks[chunkIndex][itemIndex] = source[sourceIndex]
                targetIndex += 1
            }
            val valueChunkIndex = targetIndex / LEGACY_BOUNDED_LIST_CHUNK_SIZE
            val valueItemIndex = targetIndex % LEGACY_BOUNDED_LIST_CHUNK_SIZE
            chunks[valueChunkIndex][valueItemIndex] = value

            return LegacyBoundedList(
                chunks = chunks,
                firstOffset = 0,
                size = resultSize,
                limit = limit,
            )
        }
    }
}

internal fun <T> appendLegacyBounded(
    source: List<T>,
    value: T,
    limit: Int,
): List<T> = LegacyBoundedList.append(source, value, limit)
