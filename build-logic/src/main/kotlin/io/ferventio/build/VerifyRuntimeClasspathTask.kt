package io.ferventio.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Verifies required and forbidden artifact filename prefixes on a resolved runtime classpath. */
abstract class VerifyRuntimeClasspathTask : DefaultTask() {
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:Input
    abstract val requiredArtifactPrefixes: SetProperty<String>

    @get:Input
    abstract val forbiddenArtifactPrefixes: SetProperty<String>

    @TaskAction
    fun verifyClasspath() {
        val artifactNames = runtimeClasspath.files
            .asSequence()
            .map { it.name }
            .sorted()
            .toList()

        val missing = requiredArtifactPrefixes.getOrElse(emptySet())
            .filterNot { prefix -> artifactNames.any { it.startsWith(prefix) } }
        val forbidden = forbiddenArtifactPrefixes.getOrElse(emptySet())
            .flatMap { prefix -> artifactNames.filter { it.startsWith(prefix) } }
            .distinct()
            .sorted()

        if (missing.isNotEmpty() || forbidden.isNotEmpty()) {
            val details = buildList {
                if (missing.isNotEmpty()) {
                    add("Missing required artifact prefixes: ${missing.joinToString()}")
                }
                if (forbidden.isNotEmpty()) {
                    add("Forbidden runtime artifacts: ${forbidden.joinToString()}")
                }
            }.joinToString(System.lineSeparator())
            throw GradleException(details)
        }
    }
}
