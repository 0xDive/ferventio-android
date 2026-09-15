package io.ferventio.shared.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.image_cache_clear
import io.ferventio.shared.generated.resources.image_cache_clear_failed
import io.ferventio.shared.generated.resources.image_cache_cleared_amount
import io.ferventio.shared.generated.resources.image_cache_clearing
import io.ferventio.shared.generated.resources.image_cache_section_title
import io.ferventio.shared.generated.resources.image_cache_summary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FerventioImageCacheSettingsPage() {
    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext) { SingletonImageLoader.get(platformContext) }
    val scope = rememberCoroutineScope()
    var isClearing by remember { mutableStateOf(false) }
    var clearedBytes by remember { mutableStateOf<Long?>(null) }
    var clearFailed by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.image_cache_section_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.image_cache_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isClearing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(Res.string.image_cache_clearing),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else if (clearedBytes != null) {
                Text(
                    text = stringResource(
                        Res.string.image_cache_cleared_amount,
                        formatImageCacheBytes(clearedBytes ?: 0L),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (clearFailed) {
                Text(
                    text = stringResource(Res.string.image_cache_clear_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                enabled = !isClearing,
                onClick = {
                    if (isClearing) return@Button
                    isClearing = true
                    clearedBytes = null
                    clearFailed = false
                    scope.launch {
                        try {
                            val totalBytes = withContext(Dispatchers.Default) {
                                val memoryBytes = imageLoader.memoryCache?.size ?: 0L
                                val diskBytes = imageLoader.diskCache?.size ?: 0L
                                imageLoader.memoryCache?.clear()
                                imageLoader.diskCache?.clear()
                                memoryBytes + diskBytes
                            }
                            clearedBytes = totalBytes
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            clearFailed = true
                        } finally {
                            isClearing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.image_cache_clear))
            }
        }
    }
}

private fun formatImageCacheBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val kib = 1_024L
    val mib = kib * 1_024L
    val gib = mib * 1_024L
    return when {
        safeBytes < kib -> "$safeBytes B"
        safeBytes < mib -> "${(safeBytes + kib / 2) / kib} KB"
        safeBytes < gib -> "${(safeBytes + mib / 2) / mib} MB"
        else -> "${(safeBytes + gib / 2) / gib} GB"
    }
}
