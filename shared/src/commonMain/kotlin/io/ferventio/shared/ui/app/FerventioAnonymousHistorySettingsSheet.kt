package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.settings_close
import io.ferventio.shared.generated.resources.settings_history
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import org.jetbrains.compose.resources.stringResource

/** Signed-out settings intentionally expose only device-local History controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FerventioAnonymousHistorySettingsSheet(
    state: SharedAppSettingsStateHolder,
    onSave: (SharedAppPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_history),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            FerventioHistorySettingsPage(
                preferences = state.preferences,
                update = { transform ->
                    val updated = state.updateLocally(transform)
                    onSave(updated)
                },
            )
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.settings_close))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
