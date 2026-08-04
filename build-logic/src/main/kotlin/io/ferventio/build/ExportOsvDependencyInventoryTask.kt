package io.ferventio.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Exports resolved Maven coordinates using OSV-Scanner's custom lockfile JSON shape. */
@CacheableTask
abstract class ExportOsvDependencyInventoryTask : DefaultTask() {
    @get:Input
    abstract val coordinates: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun exportInventory() {
        val packages = coordinates.get()
            .distinct()
            .sorted()
            .map(::parseMavenCoordinate)

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(buildJson(packages), Charsets.UTF_8)
    }

    private fun parseMavenCoordinate(coordinate: String): MavenPackage {
        val parts = coordinate.split(':')
        if (parts.size != 3 || parts.any(String::isBlank)) {
            throw GradleException(
                "Invalid Maven coordinate '$coordinate'; expected group:artifact:version.",
            )
        }
        return MavenPackage(
            name = "${parts[0]}:${parts[1]}",
            version = parts[2],
        )
    }

    private fun buildJson(packages: List<MavenPackage>): String = buildString {
        appendLine("{")
        appendLine("  \"results\": [")
        appendLine("    {")
        appendLine("      \"packages\": [")
        packages.forEachIndexed { index, dependency ->
            appendLine("        {")
            appendLine("          \"package\": {")
            appendLine("            \"name\": \"${dependency.name.jsonEscape()}\",")
            appendLine("            \"version\": \"${dependency.version.jsonEscape()}\",")
            appendLine("            \"ecosystem\": \"Maven\"")
            appendLine("          }")
            append("        }")
            if (index != packages.lastIndex) append(',')
            appendLine()
        }
        appendLine("      ]")
        appendLine("    }")
        appendLine("  ]")
        appendLine("}")
    }

    private fun String.jsonEscape(): String = buildString(length) {
        this@jsonEscape.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u%04x".format(character.code))
                    } else {
                        append(character)
                    }
                }
            }
        }
    }

    private data class MavenPackage(
        val name: String,
        val version: String,
    )
}
