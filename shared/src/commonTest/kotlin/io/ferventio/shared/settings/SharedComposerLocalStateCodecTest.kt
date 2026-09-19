package io.ferventio.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedComposerLocalStateCodecTest {
    @Test
    fun draftCodecMatchesLegacyJsonShape() {
        val encoded = SharedComposerLocalStateCodec.encodeDrafts(
            mapOf("channel-1" to "hello", "empty" to ""),
        )

        assertEquals(mapOf("channel-1" to "hello"), SharedComposerLocalStateCodec.decodeDrafts(encoded))
    }

    @Test
    fun historyCodecNormalizesDuplicatesAndBounds() {
        val encoded = SharedComposerLocalStateCodec.encodeHistory(
            mapOf("channel-1" to listOf(" first ", "second", "first")),
        )

        assertEquals(
            mapOf("channel-1" to listOf("first", "second")),
            SharedComposerLocalStateCodec.decodeHistory(encoded),
        )
    }

    @Test
    fun malformedLegacyValuesFailClosed() {
        assertEquals(emptyMap(), SharedComposerLocalStateCodec.decodeDrafts("{"))
        assertEquals(emptyMap(), SharedComposerLocalStateCodec.decodeHistory("not-json"))
    }
}
