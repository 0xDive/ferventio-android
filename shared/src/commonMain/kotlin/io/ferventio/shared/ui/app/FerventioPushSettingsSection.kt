package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ferventio.shared.generated.resources.*
import io.ferventio.shared.push.PushBackendRegistrationStatus
import io.ferventio.shared.push.PushRegistrationStatus
import io.ferventio.shared.push.PushSelfTestStatus
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FerventioPushSettingsSection() {
    val push = LocalFerventioRuntimeState.current.pushRegistration
    val actions = currentPlatformPushSettingsActions()
    val platformError = push.lastRegistrationError
    val backendError = push.lastBackendRegistrationError

    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.notifications_delivery_section),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = when {
                push.registrationStatus == PushRegistrationStatus.FAILED ->
                    stringResource(Res.string.notifications_status_failed)
                push.backendRegistrationStatus == PushBackendRegistrationStatus.FAILED ->
                    stringResource(Res.string.notifications_status_failed)
                push.registrationStatus == PushRegistrationStatus.REQUESTED ->
                    stringResource(Res.string.notifications_status_registering)
                push.backendRegistrationStatus == PushBackendRegistrationStatus.REGISTERING ->
                    stringResource(Res.string.notifications_status_syncing)
                push.backendRegistrationStatus == PushBackendRegistrationStatus.REGISTERED ->
                    stringResource(Res.string.notifications_status_active)
                push.registrationStatus == PushRegistrationStatus.REGISTERED ->
                    stringResource(Res.string.notifications_status_syncing)
                else -> stringResource(Res.string.notifications_status_waiting)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (
                push.registrationStatus == PushRegistrationStatus.FAILED ||
                push.backendRegistrationStatus == PushBackendRegistrationStatus.FAILED
            ) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        (backendError ?: platformError)?.takeIf(String::isNotBlank)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { actions.onReconnect?.invoke() },
                enabled = actions.onReconnect != null &&
                    push.registrationStatus == PushRegistrationStatus.REGISTERED &&
                    push.backendRegistrationStatus != PushBackendRegistrationStatus.REGISTERING,
            ) {
                Text(stringResource(Res.string.notifications_reconnect))
            }
            TextButton(
                onClick = { actions.onSelfTest?.invoke() },
                enabled = actions.onSelfTest != null &&
                    push.backendRegistrationStatus == PushBackendRegistrationStatus.REGISTERED &&
                    push.selfTestStatus != PushSelfTestStatus.SENDING,
            ) {
                Text(stringResource(Res.string.notifications_send_test))
            }
        }
        when (push.selfTestStatus) {
            PushSelfTestStatus.IDLE -> Unit
            PushSelfTestStatus.SENDING -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(Res.string.notifications_self_test_sending),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            PushSelfTestStatus.SENT -> Text(
                text = stringResource(Res.string.notifications_self_test_sent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PushSelfTestStatus.FAILED -> Text(
                text = stringResource(
                    Res.string.notifications_self_test_failed,
                    push.lastSelfTestError.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
