package io.ferventio.app.emote

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.EmoteProviderCatalog
import io.ferventio.app.domain.EmoteScope
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.app.twitch.TwitchApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmoteRepositoryTest {
    private val channel = ChatChannel(id = "channel-1", login = "streamer", displayName = "Streamer")
    private val context = EmoteProviderContext(
        twitchClientId = "client",
        twitchAccessToken = "token",
        twitchUserId = "user",
    )

    @Test
    fun mergesGlobalAndChannelCatalogAndNormalizesProviderContract() = runBlocking {
        val provider = FakeProvider(
            id = "fake",
            textResolvable = false,
            global = mapOf("Global" to asset("global", "Global", provider = "wrong")),
            channel = mapOf(
                "Channel" to asset(
                    id = "channel",
                    code = "Channel",
                    provider = "wrong",
                    scope = EmoteScope.CHANNEL,
                    channelId = channel.id,
                ),
            ),
        )
        val repository = EmoteRepository(
            api = TwitchApiClient(),
            providers = listOf(provider),
        )

        val snapshot = repository.refresh(context, listOf(channel), enabledProviders = setOf("fake"))
        val loaded = snapshot.emotes("fake", channel.id)

        assertEquals(setOf("Global", "Channel"), loaded.keys)
        assertTrue(loaded.values.all { it.provider == "fake" })
        assertTrue(loaded.values.none(ThirdPartyEmoteAsset::textResolvable))
        assertEquals(2, snapshot.catalogByChannel[channel.id]?.size)
    }


    @Test
    fun anonymousRefreshSkipsTwitchButKeepsEnabledThirdPartyProviders() = runBlocking {
        val twitch = FakeProvider(
            id = EmoteRepository.TWITCH,
            global = mapOf("Kappa" to asset("twitch", "Kappa", EmoteRepository.TWITCH)),
        )
        val bttv = FakeProvider(
            id = EmoteRepository.BETTER_TTV,
            global = mapOf("OMEGALUL" to asset("bttv", "OMEGALUL", EmoteRepository.BETTER_TTV)),
        )
        val repository = EmoteRepository(
            api = TwitchApiClient(),
            providers = listOf(twitch, bttv),
        )

        val snapshot = repository.refresh(
            context = EmoteProviderContext("", "", ""),
            channels = listOf(channel),
            enabledProviders = setOf(EmoteRepository.BETTER_TTV),
            includeTwitch = false,
        )

        assertFalse(twitch.wasLoaded)
        assertTrue(bttv.wasLoaded)
        assertTrue(snapshot.emotes(EmoteRepository.TWITCH, channel.id).isEmpty())
        assertEquals(setOf("OMEGALUL"), snapshot.emotes(EmoteRepository.BETTER_TTV, channel.id).keys)
        assertEquals(listOf("OMEGALUL"), snapshot.catalogByChannel[channel.id].orEmpty().map { it.code })
    }


    @Test
    fun twitchChannelCatalogLoadsOnSelectionAndIsCached() = runBlocking {
        var clock = 1_000L
        val twitch = FakeProvider(
            id = EmoteRepository.TWITCH,
            global = mapOf(
                "Paid" to asset("paid", "Paid", EmoteRepository.TWITCH),
            ),
            channel = mapOf(
                "FollowFree" to asset(
                    id = "follow",
                    code = "FollowFree",
                    provider = EmoteRepository.TWITCH,
                    scope = EmoteScope.CHANNEL,
                    channelId = channel.id,
                ),
            ),
        )
        val repository = EmoteRepository(
            api = TwitchApiClient(),
            providers = listOf(twitch),
            nowMillis = { clock },
        )

        val snapshot = repository.refresh(
            context = context,
            channels = listOf(channel),
            enabledProviders = emptySet(),
        )
        assertEquals(listOf("Paid"), snapshot.catalogByChannel[channel.id].orEmpty().map { it.code })
        assertEquals(0, twitch.channelLoadCount)

        val first = repository.refreshTwitchChannel(
            context = context,
            channel = channel,
            currentCatalog = snapshot.catalogByChannel[channel.id].orEmpty(),
        ).getOrThrow()
        assertEquals(setOf("Paid", "FollowFree"), first.map { it.code }.toSet())
        assertEquals(1, twitch.channelLoadCount)

        clock += 30_000L
        repository.refreshTwitchChannel(
            context = context,
            channel = channel,
            currentCatalog = first,
        ).getOrThrow()
        assertEquals(1, twitch.channelLoadCount)
    }

    @Test
    fun pickerUsesSameConflictPrecedenceAsChatParser() = runBlocking {
        val sevenTv = FakeProvider(
            id = EmoteRepository.SEVEN_TV,
            global = mapOf("Same" to asset("7tv", "Same", EmoteRepository.SEVEN_TV)),
        )
        val ffz = FakeProvider(
            id = EmoteRepository.FRANKER_FACE_Z,
            global = mapOf("Same" to asset("ffz", "Same", EmoteRepository.FRANKER_FACE_Z)),
        )
        val bttv = FakeProvider(
            id = EmoteRepository.BETTER_TTV,
            global = mapOf("Same" to asset("bttv", "Same", EmoteRepository.BETTER_TTV)),
        )
        val repository = EmoteRepository(
            api = TwitchApiClient(),
            providers = listOf(sevenTv, ffz, bttv),
        )

        val snapshot = repository.refresh(
            context = context,
            channels = listOf(channel),
            enabledProviders = setOf(
                EmoteRepository.SEVEN_TV,
                EmoteRepository.FRANKER_FACE_Z,
                EmoteRepository.BETTER_TTV,
            ),
        )

        val selected = snapshot.catalogByChannel[channel.id].orEmpty().single { it.code == "Same" }
        assertEquals(EmoteRepository.BETTER_TTV, selected.provider)
        assertEquals("bttv", selected.id)
        assertFalse(snapshot.errorMessage?.isNotBlank() == true)
    }

    private fun asset(
        id: String,
        code: String,
        provider: String,
        scope: EmoteScope = EmoteScope.GLOBAL,
        channelId: String? = null,
    ) = ThirdPartyEmoteAsset(
        id = id,
        code = code,
        provider = provider,
        imageType = "webp",
        animated = false,
        imageUrl1x = "https://cdn/$id/1x.webp",
        imageUrl2x = "https://cdn/$id/2x.webp",
        imageUrl3x = "https://cdn/$id/3x.webp",
        scope = scope,
        channelId = channelId,
    )

    private class FakeProvider(
        override val id: String,
        override val textResolvable: Boolean = true,
        private val global: Map<String, ThirdPartyEmoteAsset> = emptyMap(),
        private val channel: Map<String, ThirdPartyEmoteAsset> = emptyMap(),
    ) : EmoteProvider {
        var wasLoaded: Boolean = false
            private set
        var channelLoadCount: Int = 0
            private set

        override suspend fun loadGlobal(context: EmoteProviderContext): EmoteProviderCatalog {
            wasLoaded = true
            return EmoteProviderCatalog(emotes = global)
        }

        override suspend fun loadChannel(
            context: EmoteProviderContext,
            channel: ChatChannel,
        ): EmoteProviderCatalog {
            wasLoaded = true
            channelLoadCount += 1
            return EmoteProviderCatalog(emotes = this.channel)
        }
    }
}
