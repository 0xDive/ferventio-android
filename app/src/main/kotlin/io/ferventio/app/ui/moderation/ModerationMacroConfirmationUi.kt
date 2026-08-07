package io.ferventio.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ConfirmedModerationCommand

internal data class PendingModerationMacro(
    val command: ConfirmedModerationCommand,
    val expandedCommand: String,
)

internal data class ModerationMacroConfirmationStrings(
    val title: String,
    val description: String,
    val execute: String,
    val cancel: String,
)

@Composable
internal fun ModerationMacroConfirmationDialog(
    expandedCommand: String,
    strings: ModerationMacroConfirmationStrings,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { LocalizedText(strings.title) },
        text = {
            Column {
                LocalizedText(
                    strings.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    VerbatimText(
                        expandedCommand,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                LocalizedText(strings.execute)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                LocalizedText(strings.cancel)
            }
        },
    )
}
