package io.ferventio.app.domain

import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object MessageFilterCodec {
    private const val SCHEMA_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(filters: List<SavedMessageFilter>): String = buildJsonObject {
        put("schemaVersion", JsonPrimitive(SCHEMA_VERSION))
        put("filters", buildJsonArray {
            filters.take(MAX_SAVED_FILTERS).forEach { filter ->
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(filter.id))
                        put("name", JsonPrimitive(filter.name))
                        put("expression", JsonPrimitive(filter.expression))
                    },
                )
            }
        })
    }.toString()

    fun decode(raw: String?): Result<List<SavedMessageFilter>> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val root = json.parseToJsonElement(raw).jsonObject
        val schema = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
        require(schema in 1..SCHEMA_VERSION) { "Неподдерживаемая версия фильтров: $schema" }
        val filters = root["filters"]?.jsonArray ?: JsonArray(emptyList())
        filters.mapNotNull { element ->
            val item = element.jsonObject
            val id = item["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val name = item["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val expression = item["expression"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (name.isBlank() || expression.isBlank()) return@mapNotNull null
            SavedMessageFilter(
                id = id.ifBlank { Uuid.random().toString() },
                name = name.take(80),
                expression = expression.take(MAX_FILTER_EXPRESSION_LENGTH),
            )
        }
            .distinctBy(SavedMessageFilter::id)
            .take(MAX_SAVED_FILTERS)
    }

    fun merge(
        existing: List<SavedMessageFilter>,
        imported: List<SavedMessageFilter>,
    ): List<SavedMessageFilter> {
        val merged = LinkedHashMap<String, SavedMessageFilter>()
        existing.forEach { filter -> merged[filter.id] = filter }
        imported.forEach { filter ->
            val uniqueId = if (filter.id !in merged) filter.id else Uuid.random().toString()
            val existingNames = merged.values.map { it.name.lowercase() }.toSet()
            val name = uniqueName(filter.name, existingNames)
            merged[uniqueId] = filter.copy(id = uniqueId, name = name)
        }
        return merged.values.take(MAX_SAVED_FILTERS)
    }

    private fun uniqueName(base: String, existing: Set<String>): String {
        val normalized = base.trim().take(80).ifBlank { "Импортированный фильтр" }
        if (normalized.lowercase() !in existing) return normalized
        for (suffix in 2..999) {
            val candidate = "$normalized ($suffix)".take(80)
            if (candidate.lowercase() !in existing) return candidate
        }
        return "$normalized ${Clock.System.now().toEpochMilliseconds()}".take(80)
    }
}
