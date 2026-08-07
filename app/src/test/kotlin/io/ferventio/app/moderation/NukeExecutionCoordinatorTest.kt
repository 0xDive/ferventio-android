package io.ferventio.app.moderation

import io.ferventio.app.domain.NukeExecutionPlan
import io.ferventio.app.domain.NukeMatchMode
import io.ferventio.app.domain.NukeTargetUser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NukeExecutionCoordinatorTest {
    @Test
    fun `executes only frozen users in order`() {
        runBlocking {
            val calls = mutableListOf<String>()
            val delays = mutableListOf<Long>()
            val coordinator = NukeExecutionCoordinator(
                moderationAction = NukeModerationAction { user, duration, reason ->
                    calls += "${user.userLogin}:$duration:$reason"
                },
                delayAction = delays::add,
            )

            val result = coordinator.execute(
                plan = plan("alpha", "beta"),
                policy = NukeExecutionPolicy(
                    timeoutSeconds = 300,
                    reason = "copypasta wave",
                    delayBetweenActionsMillis = 50L,
                ),
            )

            assertEquals(
                listOf("alpha:300:copypasta wave", "beta:300:copypasta wave"),
                calls,
            )
            assertEquals(listOf(50L), delays)
            assertEquals(2, result.attemptedUsers)
            assertEquals(2, result.succeededUsers)
            assertTrue(result.completed)
        }
    }

    @Test
    fun `failure for one user does not expand or abort target set`() {
        runBlocking {
            val calls = mutableListOf<String>()
            val coordinator = NukeExecutionCoordinator(
                moderationAction = NukeModerationAction { user, _, _ ->
                    calls += user.userLogin
                    if (user.userLogin == "beta") error("rate limited")
                },
                delayAction = {},
            )

            val result = coordinator.execute(plan("alpha", "beta", "gamma"))

            assertEquals(listOf("alpha", "beta", "gamma"), calls)
            assertEquals(3, result.attemptedUsers)
            assertEquals(2, result.succeededUsers)
            assertEquals(1, result.failedUsers)
            assertEquals("beta", result.failures.single().user.userLogin)
            assertFalse(result.completed)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects target set above safety limit`() {
        runBlocking {
            val coordinator = NukeExecutionCoordinator(
                moderationAction = NukeModerationAction { _, _, _ -> },
                delayAction = {},
            )

            coordinator.execute(
                plan = plan("alpha", "beta", "gamma"),
                policy = NukeExecutionPolicy(maxTargetUsers = 2),
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty frozen plan`() {
        runBlocking {
            val coordinator = NukeExecutionCoordinator(
                moderationAction = NukeModerationAction { _, _, _ -> },
                delayAction = {},
            )

            coordinator.execute(
                NukeExecutionPlan(
                    query = "spam",
                    matchMode = NukeMatchMode.PLAIN_TEXT,
                    caseSensitive = false,
                    previewedAtMillis = 1L,
                    targetUsers = emptyList(),
                    targetMessageIds = emptyList(),
                ),
            )
        }
    }

    private fun plan(vararg logins: String): NukeExecutionPlan = NukeExecutionPlan(
        query = "spam",
        matchMode = NukeMatchMode.PLAIN_TEXT,
        caseSensitive = false,
        previewedAtMillis = 10L,
        targetUsers = logins.map { login ->
            NukeTargetUser(
                userId = "id-$login",
                userLogin = login,
                userDisplayName = login.replaceFirstChar(Char::uppercase),
            )
        },
        targetMessageIds = logins.indices.map { "message-$it" },
    )
}
