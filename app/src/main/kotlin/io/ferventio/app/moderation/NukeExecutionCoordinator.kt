package io.ferventio.app.moderation

import io.ferventio.app.domain.NukeExecutionPlan
import io.ferventio.app.domain.NukeTargetUser
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
) {
    val failedUsers: Int get() = failures.size
    val completed: Boolean get() = attemptedUsers > 0 && failedUsers == 0
}

fun interface NukeModerationAction {
    suspend fun timeout(
        user: NukeTargetUser,
        durationSeconds: Int,
        reason: String,
    )
}

/**
 * Executes only the immutable target set approved by NukePreviewSheet. The
 * coordinator never receives the original live messages or query and therefore
 * cannot silently expand the target set after confirmation.
 */
class NukeExecutionCoordinator(
    private val moderationAction: NukeModerationAction,
    private val delayAction: suspend (Long) -> Unit = { delay(it) },
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
        var succeeded = 0
        plan.targetUsers.forEachIndexed { index, user ->
            runCatching {
                moderationAction.timeout(
                    user = user,
                    durationSeconds = policy.timeoutSeconds,
                    reason = policy.reason,
                )
            }.onSuccess {
                succeeded += 1
            }.onFailure { throwable ->
                failures += NukeTargetFailure(
                    user = user,
                    message = throwable.message ?: throwable::class.java.simpleName,
                )
            }

            if (index < plan.targetUsers.lastIndex && policy.delayBetweenActionsMillis > 0L) {
                delayAction(policy.delayBetweenActionsMillis)
            }
        }

        return NukeExecutionResult(
            attemptedUsers = plan.targetUsers.size,
            succeededUsers = succeeded,
            failures = failures,
        )
    }
}
