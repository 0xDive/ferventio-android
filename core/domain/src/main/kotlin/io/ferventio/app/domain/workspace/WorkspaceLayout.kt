package io.ferventio.app.domain

import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val CURRENT_WORKSPACE_LAYOUT_SCHEMA = 2
const val MAX_SPLITS_PER_TAB = 4
const val MAX_RECENT_CHANNELS = 12

enum class MainSection {
    CHATS,
    MENTIONS,
    MODERATION,
    SEARCH,
    SETTINGS,
}

data class ChannelAttention(
    val unreadCount: Int = 0,
    val mentionCount: Int = 0,
    val firstUnreadMessageId: String? = null,
) {
    val hasUnread: Boolean get() = unreadCount > 0
}

data class WorkspaceLayout(
    val schemaVersion: Int = CURRENT_WORKSPACE_LAYOUT_SCHEMA,
    val workspaces: List<Workspace> = emptyList(),
    val activeWorkspaceId: String? = null,
) {
    val activeWorkspace: Workspace?
        get() = workspaces.firstOrNull { it.id == activeWorkspaceId } ?: workspaces.firstOrNull()

    val activeTab: WorkspaceTab?
        get() = activeWorkspace?.activeTab

    fun normalized(knownChannelIds: Set<String>): WorkspaceLayout {
        val normalizedWorkspaces = workspaces
            .map { it.normalized(knownChannelIds) }
            .distinctBy(Workspace::id)
            .ifEmpty { listOf(Workspace.default(knownChannelIds.firstOrNull())) }
        val selectedWorkspaceId = activeWorkspaceId
            ?.takeIf { id -> normalizedWorkspaces.any { it.id == id } }
            ?: normalizedWorkspaces.first().id
        return copy(
            schemaVersion = CURRENT_WORKSPACE_LAYOUT_SCHEMA,
            workspaces = normalizedWorkspaces,
            activeWorkspaceId = selectedWorkspaceId,
        )
    }

    companion object {
        fun default(channelId: String? = null): WorkspaceLayout {
            val workspace = Workspace.default(channelId)
            return WorkspaceLayout(
                workspaces = listOf(workspace),
                activeWorkspaceId = workspace.id,
            )
        }
    }
}

data class Workspace(
    val id: String,
    val name: String,
    val tabs: List<WorkspaceTab>,
    val activeTabId: String?,
) {
    val activeTab: WorkspaceTab?
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()

    fun normalized(knownChannelIds: Set<String>): Workspace {
        val normalizedTabs = tabs
            .map { it.normalized(knownChannelIds) }
            .distinctBy(WorkspaceTab::id)
            .ifEmpty { listOf(WorkspaceTab.default(knownChannelIds.firstOrNull())) }
        return copy(
            name = name.trim().ifEmpty { "Workspace" }.take(40),
            tabs = normalizedTabs,
            activeTabId = activeTabId
                ?.takeIf { id -> normalizedTabs.any { it.id == id } }
                ?: normalizedTabs.first().id,
        )
    }

    companion object {
        fun default(channelId: String? = null): Workspace {
            val tab = WorkspaceTab.default(channelId)
            return Workspace(
                id = newLayoutId("workspace"),
                name = "Основной",
                tabs = listOf(tab),
                activeTabId = tab.id,
            )
        }
    }
}

data class WorkspaceTab(
    val id: String,
    val title: String,
    val splits: List<SplitLayout>,
    val activeSplitId: String?,
    val primaryFraction: Float = 0.5f,
) {
    val activeSplit: SplitLayout?
        get() = splits.firstOrNull { it.id == activeSplitId } ?: splits.firstOrNull()

    fun normalized(knownChannelIds: Set<String>): WorkspaceTab {
        val normalizedSplits = splits
            .take(MAX_SPLITS_PER_TAB)
            .map { split -> split.withChannelId(split.channelId?.takeIf(knownChannelIds::contains)) }
            .distinctBy(SplitLayout::id)
            .ifEmpty { listOf(ChatSplit(newLayoutId("split"), knownChannelIds.firstOrNull())) }
        return copy(
            title = title.trim().ifEmpty { "Вкладка" }.take(40),
            splits = normalizedSplits,
            activeSplitId = activeSplitId
                ?.takeIf { id -> normalizedSplits.any { it.id == id } }
                ?: normalizedSplits.first().id,
            primaryFraction = primaryFraction.coerceIn(0.25f, 0.75f),
        )
    }

    companion object {
        fun default(channelId: String? = null): WorkspaceTab {
            val split = ChatSplit(newLayoutId("split"), channelId)
            return WorkspaceTab(
                id = newLayoutId("tab"),
                title = "Чаты",
                splits = listOf(split),
                activeSplitId = split.id,
            )
        }
    }
}

sealed interface SplitLayout {
    val id: String
    val channelId: String?
    val filterQuery: String

    fun withChannelId(channelId: String?): SplitLayout
    fun withFilterQuery(query: String): SplitLayout
}

data class ChatSplit(
    override val id: String,
    override val channelId: String?,
) : SplitLayout {
    override val filterQuery: String = ""

    override fun withChannelId(channelId: String?): SplitLayout = copy(channelId = channelId)

    override fun withFilterQuery(query: String): SplitLayout {
        val normalized = query.trim().take(MAX_FILTER_EXPRESSION_LENGTH)
        return if (normalized.isEmpty()) this else FilteredSplit(id, channelId, normalized)
    }
}

data class FilteredSplit(
    override val id: String,
    override val channelId: String?,
    override val filterQuery: String,
) : SplitLayout {
    override fun withChannelId(channelId: String?): SplitLayout = copy(channelId = channelId)

    override fun withFilterQuery(query: String): SplitLayout {
        val normalized = query.trim().take(MAX_FILTER_EXPRESSION_LENGTH)
        return if (normalized.isEmpty()) ChatSplit(id, channelId) else copy(filterQuery = normalized)
    }
}

fun newLayoutId(prefix: String): String = "$prefix-${Uuid.random()}"

object WorkspaceLayoutCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(layout: WorkspaceLayout): String = buildJsonObject {
        put("schemaVersion", JsonPrimitive(CURRENT_WORKSPACE_LAYOUT_SCHEMA))
        put("activeWorkspaceId", JsonPrimitive(layout.activeWorkspaceId.orEmpty()))
        put("workspaces", buildJsonArray {
            layout.workspaces.forEach { workspace ->
                add(buildJsonObject {
                    put("id", JsonPrimitive(workspace.id))
                    put("name", JsonPrimitive(workspace.name))
                    put("activeTabId", JsonPrimitive(workspace.activeTabId.orEmpty()))
                    put("tabs", buildJsonArray {
                        workspace.tabs.forEach { tab ->
                            add(buildJsonObject {
                                put("id", JsonPrimitive(tab.id))
                                put("title", JsonPrimitive(tab.title))
                                put("activeSplitId", JsonPrimitive(tab.activeSplitId.orEmpty()))
                                put("primaryFraction", JsonPrimitive(tab.primaryFraction))
                                put("splits", buildJsonArray {
                                    tab.splits.forEach { split ->
                                        add(buildJsonObject {
                                            put("type", JsonPrimitive(if (split is FilteredSplit) "filtered" else "chat"))
                                            put("id", JsonPrimitive(split.id))
                                            put("channelId", JsonPrimitive(split.channelId.orEmpty()))
                                            put("filterQuery", JsonPrimitive(split.filterQuery))
                                        })
                                    }
                                })
                            })
                        }
                    })
                })
            }
        })
    }.toString()

    fun decodeOrDefault(raw: String?, fallbackChannelId: String? = null): WorkspaceLayout {
        if (raw.isNullOrBlank()) return WorkspaceLayout.default(fallbackChannelId)
        return runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            when (root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1) {
                1 -> migrateV1(root, fallbackChannelId)
                CURRENT_WORKSPACE_LAYOUT_SCHEMA -> decodeV2(root, fallbackChannelId)
                else -> WorkspaceLayout.default(fallbackChannelId)
            }
        }.getOrElse { WorkspaceLayout.default(fallbackChannelId) }
    }

    private fun decodeV2(root: JsonObject, fallbackChannelId: String?): WorkspaceLayout {
        val workspaces = root.array("workspaces").mapNotNull(::decodeWorkspace)
        return WorkspaceLayout(
            schemaVersion = CURRENT_WORKSPACE_LAYOUT_SCHEMA,
            workspaces = workspaces,
            activeWorkspaceId = root.string("activeWorkspaceId"),
        ).let { layout ->
            if (layout.workspaces.isEmpty()) WorkspaceLayout.default(fallbackChannelId) else layout
        }
    }

    private fun decodeWorkspace(element: kotlinx.serialization.json.JsonElement): Workspace? {
        val obj = element as? JsonObject ?: return null
        val id = obj.string("id") ?: return null
        val tabs = obj.array("tabs").mapNotNull(::decodeTab)
        return Workspace(
            id = id,
            name = obj.string("name") ?: "Workspace",
            tabs = tabs,
            activeTabId = obj.string("activeTabId"),
        )
    }

    private fun decodeTab(element: kotlinx.serialization.json.JsonElement): WorkspaceTab? {
        val obj = element as? JsonObject ?: return null
        val id = obj.string("id") ?: return null
        val splits = obj.array("splits").mapNotNull(::decodeSplit).take(MAX_SPLITS_PER_TAB)
        return WorkspaceTab(
            id = id,
            title = obj.string("title") ?: "Вкладка",
            splits = splits,
            activeSplitId = obj.string("activeSplitId"),
            primaryFraction = obj["primaryFraction"]?.jsonPrimitive?.floatOrNull ?: 0.5f,
        )
    }

    private fun decodeSplit(element: kotlinx.serialization.json.JsonElement): SplitLayout? {
        val obj = element as? JsonObject ?: return null
        val id = obj.string("id") ?: return null
        val channelId = obj.string("channelId")
        val query = obj.string("filterQuery").orEmpty()
        return if (obj.string("type") == "filtered" && query.isNotBlank()) {
            FilteredSplit(id, channelId, query)
        } else {
            ChatSplit(id, channelId)
        }
    }

    /**
     * Schema 1 was the short-lived prototype that stored only an ordered channelIds array.
     * It is accepted so future builds can migrate early development installs without reset.
     */
    private fun migrateV1(root: JsonObject, fallbackChannelId: String?): WorkspaceLayout {
        val channelIds = root.array("channelIds")
            .mapNotNull { element ->
                (element as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            }
            .take(MAX_SPLITS_PER_TAB)
            .ifEmpty { listOfNotNull(fallbackChannelId) }
        val splits = channelIds.map { ChatSplit(newLayoutId("split"), it) }
            .ifEmpty { listOf(ChatSplit(newLayoutId("split"), fallbackChannelId)) }
        val tab = WorkspaceTab(
            id = newLayoutId("tab"),
            title = root.string("title") ?: "Чаты",
            splits = splits,
            activeSplitId = splits.first().id,
        )
        val workspace = Workspace(
            id = newLayoutId("workspace"),
            name = root.string("workspaceName") ?: "Основной",
            tabs = listOf(tab),
            activeTabId = tab.id,
        )
        return WorkspaceLayout(
            workspaces = listOf(workspace),
            activeWorkspaceId = workspace.id,
        )
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

    private fun JsonObject.array(name: String): JsonArray =
        (get(name) as? JsonArray) ?: JsonArray(emptyList())
}
