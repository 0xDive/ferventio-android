package io.ferventio.app.application

import io.ferventio.app.twitch.TwitchApiException
import io.ferventio.app.twitch.TwitchChannelPointsContext
import io.ferventio.app.twitch.TwitchChannelPointsRedemption
import io.ferventio.app.twitch.TwitchChannelPointsRedeemException
import io.ferventio.app.twitch.TwitchChannelPointsReward
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPointsCoordinatorTest {
    private val auth = ChannelPointsAuth(clientId = "client", accessToken = "token")
    private val reward = TwitchChannelPointsReward(
        id = "reward",
        title = "Reward",
        prompt = "",
        cost = 5,
        enabled = true,
        userInputRequired = false,
        imageUrl = null,
    )

    @Test
    fun `slow refresh in one channel does not block another channel`() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val gateway = FakeGateway(
            contextCall = { channelLogin ->
                if (channelLogin == "one") {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                } else {
                    secondEntered.complete(Unit)
                }
                context()
            },
        )
        val coordinator = ChannelPointsCoordinator(gateway)

        val first = launch { coordinator.refresh(auth, channelId = "1", channelLogin = "one") }
        firstEntered.await()
        val second = launch { coordinator.refresh(auth, channelId = "2", channelLogin = "two") }

        withTimeout(1_000L) { secondEntered.await() }
        releaseFirst.complete(Unit)
        joinAll(first, second)

        assertEquals(10, coordinator.state.value.channel("1")?.balance)
        assertEquals(10, coordinator.state.value.channel("2")?.balance)
    }

    @Test
    fun `requests for the same channel remain serialized`() = runBlocking {
        val calls = AtomicInteger(0)
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val gateway = FakeGateway(
            contextCall = {
                if (calls.incrementAndGet() == 1) {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                }
                context()
            },
        )
        val coordinator = ChannelPointsCoordinator(gateway)

        val first = launch { coordinator.refresh(auth, channelId = "1", channelLogin = "one") }
        firstEntered.await()
        val second = launch { coordinator.refresh(auth, channelId = "1", channelLogin = "one") }
        repeat(10) { yield() }

        assertEquals(1, calls.get())
        releaseFirst.complete(Unit)
        joinAll(first, second)
        assertEquals(2, calls.get())
    }

    @Test
    fun `session reset clears visible state`() = runBlocking {
        val coordinator = ChannelPointsCoordinator(FakeGateway(contextCall = { context() }))

        coordinator.refresh(auth, channelId = "1", channelLogin = "one")
        assertEquals(10, coordinator.state.value.channel("1")?.balance)

        coordinator.resetSession()

        assertNull(coordinator.state.value.channel("1"))
    }

    @Test
    fun `session reset prevents stale in flight refresh from restoring state`() = runBlocking {
        val refreshEntered = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val coordinator = ChannelPointsCoordinator(
            FakeGateway(
                contextCall = {
                    refreshEntered.complete(Unit)
                    releaseRefresh.await()
                    context()
                },
            ),
        )

        val refresh = launch { coordinator.refresh(auth, channelId = "1", channelLogin = "one") }
        refreshEntered.await()
        coordinator.resetSession()
        releaseRefresh.complete(Unit)
        refresh.join()

        assertNull(coordinator.state.value.channel("1"))
    }

    @Test
    fun `cancelled redemption blocks retry until successful refresh`() = runBlocking {
        val redeemEntered = CompletableDeferred<Unit>()
        val holdRedemption = CompletableDeferred<Unit>()
        val gateway = FakeGateway(
            contextCall = { context() },
            redeemCall = {
                redeemEntered.complete(Unit)
                holdRedemption.await()
                TwitchChannelPointsRedemption("never")
            },
        )
        val coordinator = ChannelPointsCoordinator(gateway)

        val redemption = launch {
            coordinator.redeem(auth, "1", "one", reward, textInput = null)
        }
        redeemEntered.await()
        redemption.cancelAndJoin()

        val uncertain = coordinator.state.value.channel("1")
        assertTrue(uncertain?.redemptionOutcomeUncertain == true)
        assertNull(uncertain?.redeemingRewardId)
        assertNull(uncertain?.errorMessage)

        val blockedRetry = runCatching {
            coordinator.redeem(auth, "1", "one", reward, textInput = null)
        }.exceptionOrNull()
        assertTrue(blockedRetry is IllegalStateException)

        coordinator.refresh(auth, channelId = "1", channelLogin = "one")
        assertFalse(coordinator.state.value.channel("1")?.redemptionOutcomeUncertain ?: true)
    }

    @Test
    fun `transport failure leaves redemption outcome uncertain`() = runBlocking {
        val coordinator = ChannelPointsCoordinator(
            FakeGateway(
                redeemCall = { throw IOException("connection reset") },
            ),
        )

        val error = runCatching {
            coordinator.redeem(auth, "1", "one", reward, textInput = null)
        }.exceptionOrNull()

        assertTrue(error is IOException)
        val state = coordinator.state.value.channel("1")
        assertTrue(state?.redemptionOutcomeUncertain == true)
        assertNull(state?.errorMessage)
    }

    @Test
    fun `unauthorized redemption stays retryable for refreshed authentication`() = runBlocking {
        val coordinator = ChannelPointsCoordinator(
            FakeGateway(
                redeemCall = { throw TwitchApiException(401, "Channel Points: invalid token") },
            ),
        )

        val error = runCatching {
            coordinator.redeem(auth, "1", "one", reward, textInput = null)
        }.exceptionOrNull()

        assertTrue(error is TwitchApiException)
        val state = coordinator.state.value.channel("1")
        assertFalse(state?.redemptionOutcomeUncertain ?: true)
        assertNull(state?.redeemingRewardId)
        assertEquals("Twitch API 401: Channel Points: invalid token", state?.errorMessage)
    }

    @Test
    fun `definitive Twitch rejection stays retryable after fixing its cause`() = runBlocking {
        val coordinator = ChannelPointsCoordinator(
            FakeGateway(
                redeemCall = { throw TwitchChannelPointsRedeemException("NOT_ENOUGH_POINTS") },
            ),
        )

        val error = runCatching {
            coordinator.redeem(auth, "1", "one", reward, textInput = null)
        }.exceptionOrNull()

        assertTrue(error is TwitchChannelPointsRedeemException)
        val state = coordinator.state.value.channel("1")
        assertFalse(state?.redemptionOutcomeUncertain ?: true)
        assertEquals(
            "Channel Points redemption failed: NOT_ENOUGH_POINTS",
            state?.errorMessage,
        )
    }

    @Test
    fun `confirmed redemption stays successful when follow-up refresh fails`() = runBlocking {
        val contextCalls = AtomicInteger(0)
        val coordinator = ChannelPointsCoordinator(
            FakeGateway(
                contextCall = {
                    if (contextCalls.incrementAndGet() == 1) context()
                    else throw IOException("read-back failed")
                },
                redeemCall = { TwitchChannelPointsRedemption("confirmed") },
            ),
        )

        coordinator.refresh(auth, channelId = "1", channelLogin = "one")
        val redemption = coordinator.redeem(auth, "1", "one", reward, textInput = null)

        assertEquals("confirmed", redemption.id)
        val state = coordinator.state.value.channel("1")
        assertFalse(state?.redemptionOutcomeUncertain ?: true)
        assertEquals("confirmed", state?.lastRedemptionId)
        assertEquals(5, state?.balance)
        assertNull(state?.errorMessage)
    }

    @Test
    fun `cancelled refresh clears spinner without inventing an error`() = runBlocking {
        val refreshEntered = CompletableDeferred<Unit>()
        val holdRefresh = CompletableDeferred<Unit>()
        val coordinator = ChannelPointsCoordinator(
            FakeGateway(
                contextCall = {
                    refreshEntered.complete(Unit)
                    holdRefresh.await()
                    context()
                },
            ),
        )

        val refresh = launch { coordinator.refresh(auth, "1", "one") }
        refreshEntered.await()
        refresh.cancelAndJoin()

        val state = coordinator.state.value.channel("1")
        assertFalse(state?.loading ?: true)
        assertNull(state?.errorMessage)
    }

    private fun context() = TwitchChannelPointsContext(
        balance = 10,
        rewards = listOf(reward),
    )

    private class FakeGateway(
        private val contextCall: suspend (channelLogin: String) -> TwitchChannelPointsContext = {
            TwitchChannelPointsContext(balance = 10, rewards = emptyList())
        },
        private val redeemCall: suspend (channelId: String) -> TwitchChannelPointsRedemption = {
            TwitchChannelPointsRedemption("redemption")
        },
    ) : ChannelPointsGateway {
        override suspend fun getContext(
            clientId: String,
            accessToken: String,
            channelLogin: String,
        ): TwitchChannelPointsContext = contextCall(channelLogin)

        override suspend fun redeem(
            clientId: String,
            accessToken: String,
            channelId: String,
            reward: TwitchChannelPointsReward,
            transactionId: String,
            textInput: String?,
        ): TwitchChannelPointsRedemption = redeemCall(channelId)

        override fun close() = Unit
    }
}
