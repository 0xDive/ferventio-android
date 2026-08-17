package io.ferventio.shared.moderation

import io.ferventio.app.domain.NukeExecutionPlan
import io.ferventio.app.domain.NukeMatchMode
import io.ferventio.app.domain.NukeTargetUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NukeExecutionCoordinatorTest {
    @Test
    fun executesFrozenTargetsWithPacingAndPolicy() = runTest {
        val calls = mutableListOf<Triple<String, Int, String>>()
        val delays = mutableListOf<Long>()
        val coordinator = NukeExecutionCoordinator(
            moderationAction = NukeModerationAction { user, durationSeconds, reason ->
                calls += Triple(user.userId, durationSeconds, reason)
            },
            delayAction = { millis -> delays += millis },
        )

        val result = coordinator.execute(plan("one", "two", "three"))

        assertEquals(listOf("one", "two", "three"), calls.map { it.first })
        assertTrue(calls.all { it.second == 600 && it.third == "Mass moderation" })
        assertEquals(listOf(175L, 175L), delays)
        assertEquals(3, result.attemptedUsers)
        assertEquals(3, result.succeededUsers)
        assertTrue(result.completed)
        assertFalse(result.stoppedEarly)
    }

    @Test
    fun recordsRecoverableFailureAndContinuesWithRemainingTargets() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = NukeExecutionCoordinator(
            moderationAction = NukeModerationAction { user, _, _ ->
                calls += user.userId
                if (user.userId == "two") error("temporary failure")
            },
            delayAction = {},
        )

        val result = coordinator.execute(plan("one", "two", "three"))

        assertEquals(listOf("one", "two", "three"), calls)
        assertEquals(3, result.attemptedUsers)
        assertEquals(2, result.succeededUsers)
        assertEquals(listOf("two"), result.failures.map { it.user.userId })
        assertFalse(result.completed)
        assertFalse(result.stoppedEarly)
    }

    @Test
    fun fatalFailureStopsBeforeRepeatingSameBrokenMutation() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = NukeExecutionCoordinator(
            moderationAction = NukeModerationAction { user, _, _ ->
                calls += user.userId
                if (user.userId == "two") {
                    throw TwitchModerationMutationException(
                        operation = "timeout user",
                        statusCode = 429,
                        twitchMessage = "Too Many Requests",
                    )
                }
            },
            delayAction = {},
            shouldStopAfterFailure = { error -> error.shouldStopNukeExecution() },
        )

        val result = coordinator.execute(plan("one", "two", "three"))

        assertEquals(listOf("one", "two"), calls)
        assertEquals(2, result.attemptedUsers)
        assertEquals(1, result.succeededUsers)
        assertEquals(1, result.failedUsers)
        assertTrue(result.stoppedEarly)
    }

    @Test
    fun rejectsOversizedPlanBeforeAnyModerationCall() = runTest {
        var calls = 0
        val coordinator = NukeExecutionCoordinator(
            moderationAction = NukeModerationAction { _, _, _ -> calls += 1 },
            delayAction = {},
        )
        val oversized = plan(*(1..101).map { it.toString() }.toTypedArray())

        assertFailsWith<IllegalArgumentException> {
            coordinator.execute(oversized)
        }
        assertEquals(0, calls)
    }

    @Test
    fun cancellationIsNeverConvertedIntoPartialFailure() = runTest {
        val coordinator = NukeExecutionCoordinator(
            moderationAction = NukeModerationAction { _, _, _ ->
                throw CancellationException("cancel")
            },
            delayAction = {},
        )

        assertFailsWith<CancellationException> {
            coordinator.execute(plan("one"))
        }
    }

    @Test
    fun classifiesOnlyNonRepeatableTwitchFailuresAsFatal() {
        assertTrue(TwitchModerationScopeException("moderator:manage:banned_users").shouldStopNukeExecution())
        assertTrue(
            TwitchModerationMutationException("timeout", 401, "Unauthorized")
                .shouldStopNukeExecution(),
        )
        assertTrue(
            TwitchModerationMutationException("timeout", 403, "Forbidden")
                .shouldStopNukeExecution(),
        )
        assertTrue(
            TwitchModerationMutationException("timeout", 429, "Too Many Requests")
                .shouldStopNukeExecution(),
        )
        assertFalse(
            TwitchModerationMutationException("timeout", 500, "Temporary")
                .shouldStopNukeExecution(),
        )
    }

    private fun plan(vararg userIds: String) = NukeExecutionPlan(
        query = "spam",
        matchMode = NukeMatchMode.PLAIN_TEXT,
        caseSensitive = false,
        previewedAtMillis = 1_000L,
        targetUsers = userIds.map { userId ->
            NukeTargetUser(
                userId = userId,
                userLogin = userId,
                userDisplayName = userId,
            )
        },
        targetMessageIds = userIds.map { "message-$it" },
    )
}
