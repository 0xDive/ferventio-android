package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerAutocompleteTest {
    @Test
    fun userAutocompleteRanksFrequentlySeenUsers() {
        val frequent = message("m1", "u1", "alice", "Alice")
        val other = message("m2", "u2", "alex", "Alex")
        val result = ComposerAutocomplete.suggestions(
            input = "@a",
            messages = listOf(frequent, frequent.copy(id = "m3"), other),
            profilesById = emptyMap(),
            catalog = emptyList(),
            recentEmoteKeys = emptyList(),
            favoriteEmoteKeys = emptySet(),
            currentUserId = null,
        )

        assertEquals("alice", (result.first() as ComposerSuggestion.User).login)
    }


    @Test
    fun prebuiltUserIndexAvoidsRescanningMessagesWhileTyping() {
        val index = ComposerAutocomplete.buildUserIndex(
            messages = listOf(
                message("m1", "u1", "alice", "Alice"),
                message("m2", "u1", "alice", "Alice"),
                message("m3", "u2", "alex", "Alex"),
            ),
            profilesById = emptyMap(),
            currentUserId = null,
        )

        val result = ComposerAutocomplete.suggestions(
            input = "@al",
            messages = emptyList(),
            profilesById = emptyMap(),
            catalog = emptyList(),
            recentEmoteKeys = emptyList(),
            favoriteEmoteKeys = emptySet(),
            currentUserId = null,
            userIndex = index,
        )

        assertEquals("alice", (result.first() as ComposerSuggestion.User).login)
        assertEquals(2, (result.first() as ComposerSuggestion.User).useCount)
    }

    @Test
    fun prebuiltEmoteIndexAvoidsCatalogRescanWhileTyping() {
        val regular = emote("1", "CatWave")
        val frequent = emote("2", "CatJam")
        val index = EmoteCatalogRanking.buildSearchIndex(listOf(regular, frequent))

        val usage = EmoteCatalogRanking.buildUsageRanking(
            listOf(frequent.usageKey, frequent.usageKey),
        )
        val result = ComposerAutocomplete.suggestions(
            input = "hello Cat",
            messages = emptyList(),
            profilesById = emptyMap(),
            catalog = emptyList(),
            recentEmoteKeys = emptyList(),
            favoriteEmoteKeys = emptySet(),
            currentUserId = null,
            emoteSearchIndex = index,
            emoteUsageRanking = usage,
        )

        assertEquals(frequent, (result.first() as ComposerSuggestion.Emote).asset)
    }

    @Test
    fun slashInputDoesNotShowACommandWhitelist() {
        val result = ComposerAutocomplete.suggestions(
            input = "/anything twitch may support",
            messages = emptyList(),
            profilesById = emptyMap(),
            catalog = emptyList(),
            recentEmoteKeys = emptyList(),
            favoriteEmoteKeys = emptySet(),
            currentUserId = null,
        )

        assertEquals(emptyList<ComposerSuggestion>(), result)
    }

    @Test
    fun applySuggestionReplacesOnlyCurrentToken() {
        val result = ComposerAutocomplete.applySuggestion(
            input = "hello @al",
            suggestion = ComposerSuggestion.User("u1", "alice", "Alice", null, 2),
        )

        assertEquals("hello @alice ", result)
    }

    private fun emote(
        id: String,
        code: String,
    ) = ThirdPartyEmoteAsset(
        id = id,
        code = code,
        provider = "7tv",
        imageType = "webp",
        animated = false,
        imageUrl1x = "https://cdn/$id/1",
        imageUrl2x = "https://cdn/$id/2",
        imageUrl3x = "https://cdn/$id/3",
    )

    private fun message(id: String, userId: String, login: String, displayName: String) = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(userId, login, displayName),
        text = "hello",
        timestamp = "2026-07-22T10:00:00Z",
    )
}
