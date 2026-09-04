package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.PollDraft
import io.ferventio.app.domain.PollStatus
import io.ferventio.app.domain.PredictionStatus
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TwitchInteractiveMutationClientTest {
    @Test
    fun createPollUsesHelixContractAndParsesSnapshot() = runTest {
        var captured: HttpRequestData? = null
        val client = TwitchInteractiveMutationClient(
            HttpClient(
                MockEngine { request ->
                    captured = request
                    respondJson(POLL_RESPONSE)
                },
            ) { expectSuccess = false },
        )

        val poll = client.createPoll(
            authentication = authentication(POLL_SCOPE),
            broadcasterId = "broadcaster",
            draft = PollDraft(
                title = "Best color?",
                choices = listOf("Blue", "Pink"),
                durationSeconds = 60,
                channelPointsVotingEnabled = true,
                channelPointsPerVote = 100,
            ),
        )

        val request = requireNotNull(captured)
        assertEquals("POST", request.method.value)
        assertEquals("/helix/polls", request.url.encodedPath)
        assertEquals("Bearer twitch-token", request.headers[HttpHeaders.Authorization])
        assertEquals("client-id", request.headers["Client-Id"])
        val body = requestBody(request)
        assertTrue(body.contains("\"broadcaster_id\":\"broadcaster\""))
        assertTrue(body.contains("\"channel_points_per_vote\":100"))
        assertEquals("poll-1", poll.id)
        assertEquals(PollStatus.ACTIVE, poll.status)
        assertEquals(listOf("Blue", "Pink"), poll.choices.map { it.title })
    }

    @Test
    fun resolvePredictionSendsWinningOutcomeAndParsesResult() = runTest {
        var captured: HttpRequestData? = null
        val client = TwitchInteractiveMutationClient(
            HttpClient(
                MockEngine { request ->
                    captured = request
                    respondJson(PREDICTION_RESPONSE)
                },
            ) { expectSuccess = false },
        )

        val prediction = client.endPrediction(
            authentication = authentication(PREDICTION_SCOPE),
            broadcasterId = "broadcaster",
            predictionId = "prediction-1",
            status = PredictionStatus.RESOLVED,
            winningOutcomeId = "outcome-blue",
        )

        val request = requireNotNull(captured)
        assertEquals("PUT", request.method.value)
        assertEquals("/helix/predictions", request.url.encodedPath)
        val body = requestBody(request)
        assertTrue(body.contains("\"status\":\"resolved\""))
        assertTrue(body.contains("\"winning_outcome_id\":\"outcome-blue\""))
        assertEquals(PredictionStatus.RESOLVED, prediction.status)
        assertEquals("outcome-blue", prediction.winningOutcomeId)
    }

    @Test
    fun missingScopeAndForeignBroadcasterFailBeforeNetwork() = runTest {
        var calls = 0
        val client = TwitchInteractiveMutationClient(
            HttpClient(
                MockEngine {
                    calls += 1
                    respondJson(POLL_RESPONSE)
                },
            ) { expectSuccess = false },
        )
        val draft = PollDraft("Question", listOf("A", "B"), 30)

        assertFailsWith<TwitchInteractiveScopeException> {
            client.createPoll(authentication(), "broadcaster", draft)
        }
        assertFailsWith<IllegalArgumentException> {
            client.createPoll(authentication(POLL_SCOPE), "other-channel", draft)
        }
        assertEquals(0, calls)
    }

    @Test
    fun helixFailurePreservesStatusAndMessage() = runTest {
        val client = TwitchInteractiveMutationClient(
            HttpClient(
                MockEngine {
                    respond(
                        content = ByteReadChannel("{\"error\":\"Unauthorized\",\"message\":\"OAuth token is invalid\"}"),
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ) { expectSuccess = false },
        )

        val error = assertFailsWith<TwitchInteractiveMutationException> {
            client.createPoll(
                authentication = authentication(POLL_SCOPE),
                broadcasterId = "broadcaster",
                draft = PollDraft("Question", listOf("A", "B"), 30),
            )
        }

        assertEquals(401, error.statusCode)
        assertTrue(error.message.orEmpty().contains("OAuth token is invalid"))
    }

    private suspend fun requestBody(request: HttpRequestData): String =
        request.body.toByteArray().decodeToString()

    private fun authentication(vararg scopes: String) = StoredAuthentication(
        backendCredential = BackendSessionCredential(
            serverUrl = "https://example.test",
            token = "backend-token",
            expiresAtEpochMillis = 9_000_000L,
        ),
        accessLease = TwitchAccessLease(
            accessToken = "twitch-token",
            leaseExpiresAtEpochMillis = 2_000_000L,
            twitchExpiresAtEpochMillis = 8_000_000L,
            twitchValidatedAtEpochMillis = 1_000_000L,
            backendSessionExpiresAtEpochMillis = 9_000_000L,
            session = TwitchSession(
                clientId = "client-id",
                userId = "broadcaster",
                login = "broadcaster",
                scopes = scopes.toSet(),
                expiresInSeconds = 7_000L,
            ),
        ),
    )

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(body: String) = respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private companion object {
        const val POLL_SCOPE = "channel:manage:polls"
        const val PREDICTION_SCOPE = "channel:manage:predictions"
        val POLL_RESPONSE = """
            {"data":[{
              "id":"poll-1",
              "broadcaster_id":"broadcaster",
              "title":"Best color?",
              "choices":[
                {"id":"blue","title":"Blue","votes":3,"channel_points_votes":1,"bits_votes":0},
                {"id":"pink","title":"Pink","votes":2,"channel_points_votes":0,"bits_votes":0}
              ],
              "channel_points_voting_enabled":true,
              "channel_points_per_vote":100,
              "bits_voting_enabled":false,
              "bits_per_vote":0,
              "status":"ACTIVE",
              "duration":60,
              "started_at":"2026-08-17T18:00:00Z",
              "ends_at":"2026-08-17T18:01:00Z"
            }]}
        """.trimIndent()
        val PREDICTION_RESPONSE = """
            {"data":[{
              "id":"prediction-1",
              "broadcaster_id":"broadcaster",
              "title":"Will it happen?",
              "winning_outcome_id":"outcome-blue",
              "outcomes":[
                {"id":"outcome-blue","title":"Yes","users":4,"channel_points":1200,"color":"BLUE"},
                {"id":"outcome-pink","title":"No","users":2,"channel_points":400,"color":"PINK"}
              ],
              "prediction_window":120,
              "status":"RESOLVED",
              "created_at":"2026-08-17T18:00:00Z",
              "locked_at":"2026-08-17T18:02:00Z",
              "ended_at":"2026-08-17T18:03:00Z"
            }]}
        """.trimIndent()
    }
}
