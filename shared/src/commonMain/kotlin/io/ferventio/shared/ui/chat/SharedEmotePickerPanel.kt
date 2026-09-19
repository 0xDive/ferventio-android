package io.ferventio.shared.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.ferventio.app.domain.EmoteScope
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.chat_emote_empty
import io.ferventio.shared.generated.resources.chat_emote_search
import io.ferventio.shared.generated.resources.chat_emotes
import io.ferventio.shared.generated.resources.settings_close
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SharedEmotePickerPanel(
    emotes: List<ThirdPartyEmoteAsset>,
    onSelect: (ThirdPartyEmoteAsset) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(emotes, query) {
        val normalized = query.trim().lowercase()
        emotes.asSequence()
            .distinctBy { asset -> asset.provider to asset.id }
            .filter { asset ->
                normalized.isEmpty() ||
                    asset.code.lowercase().contains(normalized) ||
                    asset.provider.lowercase().contains(normalized)
            }
            .sortedWith(
                compareByDescending<ThirdPartyEmoteAsset> { it.scope == EmoteScope.CHANNEL }
                    .thenBy { providerRank(it.provider) }
                    .thenBy { it.code.lowercase() },
            )
            .toList()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 360.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.chat_emotes),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.settings_close),
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.chat_emote_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.chat_emote_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 54.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = visible,
                        key = { asset -> "${asset.provider}:${asset.id}" },
                    ) { asset ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clickable { onSelect(asset) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = asset.bestPickerImageUrl(),
                                    contentDescription = asset.code,
                                    modifier = Modifier.size(44.dp).padding(3.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ThirdPartyEmoteAsset.bestPickerImageUrl(): String = when {
    imageUrl2x.isNotBlank() -> imageUrl2x
    imageUrl1x.isNotBlank() -> imageUrl1x
    else -> imageUrl3x
}

private fun providerRank(provider: String): Int = when (provider.lowercase()) {
    "twitch" -> 0
    "betterttv" -> 1
    "frankerfacez" -> 2
    "7tv" -> 3
    else -> 4
}
