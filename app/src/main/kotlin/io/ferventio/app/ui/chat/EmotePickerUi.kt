package io.ferventio.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.ferventio.app.domain.EmoteCatalogRanking
import io.ferventio.app.domain.EmoteScope
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.app.domain.usageKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class EmotePickerFilter(
    val providerId: String? = null,
    val shortLabel: String,
    val contentDescription: String,
) {
    ALL(shortLabel = "ALL", contentDescription = "Все эмоуты"),
    FREQUENT(shortLabel = "TOP", contentDescription = "Часто используемые"),
    RECENT(shortLabel = "", contentDescription = "Недавние эмоуты"),
    FAVORITES(shortLabel = "", contentDescription = "Избранные эмоуты"),
    TWITCH(providerId = "twitch", shortLabel = "T", contentDescription = "Twitch"),
    BETTER_TTV(providerId = "betterttv", shortLabel = "B", contentDescription = "BetterTTV"),
    FRANKER_FACE_Z(providerId = "frankerfacez", shortLabel = "F", contentDescription = "FrankerFaceZ"),
    SEVEN_TV(providerId = "7tv", shortLabel = "7", contentDescription = "7TV"),
}

private data class EmotePickerSection(
    val id: String,
    val title: String,
    val assets: List<ThirdPartyEmoteAsset>,
)

private data class ProviderScopeKey(
    val provider: String,
    val scope: EmoteScope,
)

private data class EmotePickerIndex(
    val catalog: List<ThirdPartyEmoteAsset>,
    val frequent: List<ThirdPartyEmoteAsset>,
    val recent: List<ThirdPartyEmoteAsset>,
    val favorites: List<ThirdPartyEmoteAsset>,
    val grouped: Map<ProviderScopeKey, List<ThirdPartyEmoteAsset>>,
) {
    fun sections(
        filter: EmotePickerFilter,
        query: String,
        recentEmoteKeys: List<String>,
        favoriteEmoteKeys: Set<String>,
        channelId: String,
        channelName: String,
    ): List<EmotePickerSection> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isNotEmpty()) {
            val searchable = filter.providerId?.let { providerId ->
                catalog.filter { it.provider == providerId }
            } ?: catalog
            val matches = EmoteCatalogRanking.search(
                query = normalizedQuery,
                catalog = searchable,
                recentEmoteKeys = recentEmoteKeys,
                favoriteEmoteKeys = favoriteEmoteKeys,
                limit = MAX_SEARCH_RESULTS,
            )
            return matches.takeIf { it.isNotEmpty() }
                ?.let { listOf(EmotePickerSection("search", "Подходящие эмоуты", matches)) }
                .orEmpty()
        }

        when (filter) {
            EmotePickerFilter.FREQUENT -> return frequent.toSingleSection("frequent", "Часто используемые")
            EmotePickerFilter.RECENT -> return recent.toSingleSection("recent", "Недавние")
            EmotePickerFilter.FAVORITES -> return favorites.toSingleSection("favorites", "Избранные")
            else -> Unit
        }

        val providers = filter.providerId?.let { listOf(it) }
            ?: listOf("twitch", "betterttv", "frankerfacez", "7tv")
        return buildList {
            if (filter == EmotePickerFilter.ALL && frequent.isNotEmpty()) {
                add(EmotePickerSection("frequent", "Часто используемые", frequent))
            }
            if ("twitch" in providers) {
                addAll(twitchSections(channelId = channelId, channelName = channelName))
            }
            listOf(EmoteScope.CHANNEL, EmoteScope.GLOBAL).forEach { scope ->
                providers.filterNot { it == "twitch" }.forEach { provider ->
                    val assets = grouped[ProviderScopeKey(provider, scope)].orEmpty()
                    if (assets.isNotEmpty()) {
                        add(
                            EmotePickerSection(
                                id = "$provider-${scope.name.lowercase()}",
                                title = "${providerDisplayName(provider)} · " +
                                    if (scope == EmoteScope.CHANNEL) "канал" else "глобальные",
                                assets = assets,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun twitchSections(
        channelId: String,
        channelName: String,
    ): List<EmotePickerSection> {
        val twitchAssets = catalog.filter { it.provider == "twitch" }
        if (twitchAssets.isEmpty()) return emptyList()

        val global = twitchAssets
            .filter { it.scope == EmoteScope.GLOBAL }
            .distinctBy { it.usageKey }
            .sortedBy { it.code.lowercase() }
        val byOwner = twitchAssets
            .filter { it.scope == EmoteScope.CHANNEL }
            .groupBy { it.ownerId ?: it.channelId ?: it.ownerName.orEmpty() }
            .filterKeys { it.isNotBlank() }

        return buildList {
            byOwner[channelId]?.takeIf { it.isNotEmpty() }?.let { current ->
                add(
                    EmotePickerSection(
                        id = "twitch-owner-$channelId",
                        title = "Twitch · $channelName",
                        assets = current.distinctBy { it.usageKey }
                            .sortedBy { it.code.lowercase() },
                    ),
                )
            }
            byOwner.entries
                .asSequence()
                .filterNot { (ownerId, _) -> ownerId == channelId }
                .map { (ownerId, assets) ->
                    val ownerName = assets.firstNotNullOfOrNull { it.ownerName?.takeIf(String::isNotBlank) }
                        ?: "Канал $ownerId"
                    EmotePickerSection(
                        id = "twitch-owner-$ownerId",
                        title = "Twitch · $ownerName",
                        assets = assets.distinctBy { it.usageKey }
                            .sortedBy { it.code.lowercase() },
                    )
                }
                .sortedBy { it.title.lowercase() }
                .forEach { section -> add(section) }
            if (global.isNotEmpty()) {
                add(
                    EmotePickerSection(
                        id = "twitch-global",
                        title = "Twitch · глобальные",
                        assets = global,
                    ),
                )
            }
        }
    }

    companion object {
        fun build(
            catalog: List<ThirdPartyEmoteAsset>,
            recentEmoteKeys: List<String>,
            favoriteEmoteKeys: Set<String>,
        ): EmotePickerIndex {
            val unique = catalog.asSequence().distinctBy { it.usageKey }.toList()
            val grouped = unique.groupBy { ProviderScopeKey(it.provider, it.scope) }
                .mapValues { (_, assets) -> assets.sortedBy { it.code.lowercase() } }
            return EmotePickerIndex(
                catalog = unique,
                frequent = EmoteCatalogRanking.frequent(unique, recentEmoteKeys, MAX_FREQUENT_EMOTES),
                recent = EmoteCatalogRanking.recent(unique, recentEmoteKeys, MAX_RECENT_EMOTES),
                favorites = unique.asSequence()
                    .filter { it.usageKey in favoriteEmoteKeys }
                    .sortedWith(
                        compareBy<ThirdPartyEmoteAsset> { if (it.scope == EmoteScope.CHANNEL) 0 else 1 }
                            .thenBy { it.code.lowercase() },
                    )
                    .toList(),
                grouped = grouped,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InlineEmoteAutocomplete(
    suggestions: List<ThirdPartyEmoteAsset>,
    onSelect: (ThirdPartyEmoteAsset) -> Unit,
    onOpenInfo: (ThirdPartyEmoteAsset) -> Unit,
) {
    if (suggestions.isEmpty()) return
    val stopHorizontalOverscroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(x = available.x, y = 0f)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(x = available.x, y = 0f)
        }
    }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(stopHorizontalOverscroll)
            .padding(start = 56.dp, end = 8.dp, top = 3.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items(
            items = suggestions,
            key = { it.usageKey },
            contentType = { if (it == suggestions.first()) "primary-emote" else "emote" },
        ) { asset ->
            val isPrimary = asset == suggestions.first()
            Surface(
                modifier = Modifier
                    .height(if (isPrimary) 66.dp else 54.dp)
                    .width(if (isPrimary) 184.dp else 58.dp)
                    .combinedClickable(
                        onClick = { onSelect(asset) },
                        onLongClick = { onOpenInfo(asset) },
                    ),
                shape = MaterialTheme.shapes.large,
                tonalElevation = if (isPrimary) 5.dp else 1.dp,
                color = if (isPrimary) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            ) {
                if (isPrimary) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = asset.bestImageUrl(),
                            contentDescription = asset.code,
                            modifier = Modifier.size(48.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                asset.code,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                asset.providerDisplayName(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = asset.bestImageUrl(),
                            contentDescription = asset.code,
                            modifier = Modifier.size(42.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TwitchStyleEmotePickerPanel(
    channelId: String,
    channelName: String,
    catalog: List<ThirdPartyEmoteAsset>,
    recentEmoteKeys: List<String>,
    favoriteEmoteKeys: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (ThirdPartyEmoteAsset) -> Unit,
    onOpenInfo: (ThirdPartyEmoteAsset) -> Unit,
    onToggleFavorite: (ThirdPartyEmoteAsset) -> Unit,
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var highlightedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val filters = EmotePickerFilter.entries
    val pagerState = rememberPagerState(
        initialPage = EmotePickerFilter.ALL.ordinal,
        pageCount = { filters.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val selectedFilter = filters.getOrElse(pagerState.currentPage) { EmotePickerFilter.ALL }
    val index = remember(catalog, recentEmoteKeys, favoriteEmoteKeys) {
        EmotePickerIndex.build(catalog, recentEmoteKeys, favoriteEmoteKeys)
    }
    val highlighted = remember(highlightedKey, index.catalog) {
        highlightedKey?.let { key -> index.catalog.firstOrNull { it.usageKey == key } }
    }
    val stopHorizontalOverscroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(x = available.x, y = 0f)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(x = available.x, y = 0f)
        }
    }
    val header = remember(selectedFilter, highlighted, channelName) {
        highlighted?.let { asset ->
            asset.code to "${asset.providerDisplayName()} · ${if (asset.scope == EmoteScope.CHANNEL) "эмоут канала" else "глобальный"}"
        } ?: when (selectedFilter) {
            EmotePickerFilter.ALL -> channelName to "Эмоуты канала и глобальные эмоуты"
            EmotePickerFilter.FREQUENT -> "Часто используемые" to "Сортировка по частоте"
            EmotePickerFilter.RECENT -> "Недавние" to "Последние использованные эмоуты"
            EmotePickerFilter.FAVORITES -> "Избранные" to "Сохранённые эмоуты"
            EmotePickerFilter.TWITCH -> "Twitch" to "Доступные твоему аккаунту эмоуты"
            else -> selectedFilter.contentDescription to "Канал и глобальный каталог"
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect {
                highlightedKey = null
            }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 250.dp, max = 370.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.InsertEmoticon,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        header.first,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        header.second,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                highlighted?.let { asset ->
                    IconButton(onClick = { onToggleFavorite(asset) }) {
                        Icon(
                            if (asset.usageKey in favoriteEmoteKeys) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Избранное",
                        )
                    }
                    IconButton(onClick = { onOpenInfo(asset) }) {
                        Icon(Icons.Default.Info, contentDescription = "Информация об эмоуте")
                    }
                }
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(Icons.Default.Search, contentDescription = "Поиск эмоутов")
                }
            }

            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("Поиск эмоутов") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                key = { page -> filters[page].name },
            ) { page ->
                val filter = filters[page]
                val sections = remember(
                    index,
                    filter,
                    query,
                    recentEmoteKeys,
                    favoriteEmoteKeys,
                    channelId,
                    channelName,
                ) {
                    index.sections(
                        filter = filter,
                        query = query,
                        recentEmoteKeys = recentEmoteKeys,
                        favoriteEmoteKeys = favoriteEmoteKeys,
                        channelId = channelId,
                        channelName = channelName,
                    )
                }
                EmotePickerGridPage(
                    sections = sections,
                    query = query,
                    highlightedKey = highlightedKey,
                    onSelect = { asset ->
                        highlightedKey = asset.usageKey
                        onSelect(asset)
                    },
                    onOpenInfo = { asset ->
                        highlightedKey = asset.usageKey
                        onOpenInfo(asset)
                    },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .nestedScroll(stopHorizontalOverscroll),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "close-picker", contentType = "picker-control") {
                    Surface(
                        modifier = Modifier
                            .height(42.dp)
                            .combinedClickable(onClick = onDismiss),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Закрыть каталог эмоутов",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
                items(
                    items = filters,
                    key = EmotePickerFilter::name,
                    contentType = { "picker-filter" },
                ) { filter ->
                    EmoteCategoryButton(
                        filter = filter,
                        selected = selectedFilter == filter,
                        onClick = {
                            highlightedKey = null
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(filter.ordinal)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmotePickerGridPage(
    sections: List<EmotePickerSection>,
    query: String,
    highlightedKey: String?,
    onSelect: (ThirdPartyEmoteAsset) -> Unit,
    onOpenInfo: (ThirdPartyEmoteAsset) -> Unit,
) {
    if (sections.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (query.isBlank()) "В этой категории пока нет эмоутов" else "Ничего не найдено",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 52.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 10.dp,
            top = 2.dp,
            end = 10.dp,
            bottom = 10.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sections.forEach { section ->
            item(
                key = "header-${section.id}",
                contentType = "emote-header",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Text(
                    section.title.uppercase(),
                    modifier = Modifier.padding(start = 4.dp, top = 9.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            gridItems(
                items = section.assets,
                key = { "${section.id}:${it.usageKey}" },
                contentType = { "emote" },
            ) { asset ->
                EmoteGridItem(
                    asset = asset,
                    highlighted = asset.usageKey == highlightedKey,
                    onClick = { onSelect(asset) },
                    onLongClick = { onOpenInfo(asset) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmoteCategoryButton(
    filter: EmotePickerFilter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (filter) {
                EmotePickerFilter.ALL -> Icon(
                    Icons.Default.InsertEmoticon,
                    contentDescription = filter.contentDescription,
                    modifier = Modifier.size(21.dp),
                )
                EmotePickerFilter.RECENT -> Icon(
                    Icons.Default.History,
                    contentDescription = filter.contentDescription,
                    modifier = Modifier.size(21.dp),
                )
                EmotePickerFilter.FAVORITES -> Icon(
                    Icons.Default.Star,
                    contentDescription = filter.contentDescription,
                    modifier = Modifier.size(21.dp),
                )
                else -> Text(
                    filter.shortLabel,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmoteGridItem(
    asset: ThirdPartyEmoteAsset,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = asset.bestImageUrl(),
            contentDescription = asset.code,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

private fun List<ThirdPartyEmoteAsset>.toSingleSection(
    id: String,
    title: String,
): List<EmotePickerSection> = takeIf { it.isNotEmpty() }
    ?.let { listOf(EmotePickerSection(id, title, this)) }
    .orEmpty()

private fun ThirdPartyEmoteAsset.bestImageUrl(): String = when {
    imageUrl2x.isNotBlank() -> imageUrl2x
    imageUrl1x.isNotBlank() -> imageUrl1x
    else -> imageUrl3x
}

private fun ThirdPartyEmoteAsset.providerDisplayName(): String = providerDisplayName(provider)

private fun providerDisplayName(providerId: String): String = when (providerId) {
    "twitch" -> "Twitch"
    "betterttv" -> "BetterTTV"
    "frankerfacez" -> "FrankerFaceZ"
    "7tv" -> "7TV"
    else -> providerId
}

private const val MAX_FREQUENT_EMOTES = 48
private const val MAX_RECENT_EMOTES = 48
private const val MAX_SEARCH_RESULTS = 240
