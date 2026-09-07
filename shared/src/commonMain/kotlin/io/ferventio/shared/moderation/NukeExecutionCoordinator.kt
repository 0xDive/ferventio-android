package io.ferventio.shared.moderation

import io.ferventio.app.domain.NukeExecutionPlan
import io.ferventio.app.domain.NukeTargetUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class NukeExecutionPolicy(
    val timeoutSeconds: Int = 600,
    val reason: String = "Mass moderation",
    val maxTargetUsers: Int = 100,
    val delayBetweenActionsMillis: Long = 175L,
)

data class NukeTargetFailure(
    val user: NukeTargetUser,
    val message: String,
)

data class NukeExecutionResult(
    val attemptedUsers: Int,
    val succeededUsers: Int,
    val failures: List<NukeTargetFailure>,
    val stoppedEarly: Boolean,
) {
    val failedUsers: Int get() = failures.size
    val completed: Boolean
        get() = attemptedUsers > 0 &&
            attemptedUsers == succeededUsers &&
            failures.isEmpty() &&
            !stoppedEarly
}

fun interface NukeModerationAction {
    suspend fun timeout(
        user: NukeTargetUser,
        durationSeconds: Int,
        reason: String,
    )
}

/**
 * Executes only the immutable target set frozen by NukeExecutionPlanner.
 *
 * The executor never receives live messages or the original matcher, so a confirmation cannot
 * silently expand to users who appeared after the preview. Target count and pacing limits are
 * enforced again at execution time even if a caller bypasses the UI.
 */
class NukeExecutionCoordinator(
    private val moderationAction: NukeModerationAction,
    private val delayAction: suspend (Long) -> Unit = { delay(it) },
    private val shouldStopAfterFailure: (Throwable) -> Boolean = { false },
) {
    suspend fun execute(
        plan: NukeExecutionPlan,
        policy: NukeExecutionPolicy = NukeExecutionPolicy(),
    ): NukeExecutionResult {
        require(policy.timeoutSeconds > 0) { "Nuke timeout duration must be positive" }
        require(policy.maxTargetUsers > 0) { "Nuke target limit must be positive" }
        require(policy.delayBetweenActionsMillis >= 0L) { "Nuke delay must not be negative" }
        require(plan.targetUsers.isNotEmpty()) { "Nuke execution plan has no target users" }
        require(plan.targetUsers.size <= policy.maxTargetUsers) {
            "Nuke execution plan exceeds the ${policy.maxTargetUsers}-user safety limit"
        }

        val failures = mutableListOf<NukeTargetFailure>()
        var attempted = 0
        var succeeded = 0
        var stoppedEarly = false

        for ((index, user) in plan.targetUsers.withIndex()) {
            attempted += 1
            try {
                moderationAction.timeout(
                    user = user,
                    durationSeconds = policy.timeoutSeconds,
                    reason = policy.reason,
                )
                succeeded += 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                failures += NukeTargetFailure(
                    user = user,
                    message = throwable.message
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?: "Moderation action failed",
                )
                if (shouldStopAfterFailure(throwable)) {
                    stoppedEarly = index < plan.targetUsers.lastIndex
                    break
                }
            }

            if (index < plan.targetUsers.lastIndex && policy.delayBetweenActionsMillis > 0L) {
                delayAction(policy.delayBetweenActionsMillis)
            }
        }

        return NukeExecutionResult(
            attemptedUsers = attempted,
            succeededUsers = succeeded,
            failures = failures,
            stoppedEarly = stoppedEarly,
        )
    }
}

internal fun Throwable.shouldStopNukeExecution(): Boolean = when (this) {
    is TwitchModerationScopeException -> true
    is TwitchModerationMutationException -> statusCode in NUKE_FATAL_HTTP_CODES
    else -> false
}

private val NUKE_FATAL_HTTP_CODES = setOf(401, 403, 429)
