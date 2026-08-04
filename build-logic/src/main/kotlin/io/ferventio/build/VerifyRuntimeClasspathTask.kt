package io.ferventio.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Verifies required and forbidden external module prefixes without resolving
 * Android artifact variants for project dependencies.
 */
abstract class VerifyRuntimeClasspathTask : DefaultTask() {
    @get:Input
    abstract val resolvedModules: SetProperty<String>

    @get:Input
    abstract val requiredArtifactPrefixes: SetProperty<String>

    @get:Input
    abstract val forbiddenArtifactPrefixes: SetProperty<String>

    @TaskAction
    fun verifyClasspath() {
        val moduleNames = resolvedModules
            .getOrElse(emptySet())
            .sorted()

        val missing = requiredArtifactPrefixes
            .getOrElse(emptySet())
            .filterNot { prefix ->
                moduleNames.any { module -> module.startsWith(prefix) }
            }

        val forbidden = forbiddenArtifactPrefixes
            .getOrElse(emptySet())
            .flatMap { prefix ->
                moduleNames.filter { module -> module.startsWith(prefix) }
            }
            .distinct()
            .sorted()

        if (missing.isNotEmpty() || forbidden.isNotEmpty()) {
            val details = buildList {
                if (missing.isNotEmpty()) {
                    add(
                        "Missing required runtime module prefixes: " +
                            missing.joinToString(),
                    )
                }
                if (forbidden.isNotEmpty()) {
                    add(
                        "Forbidden runtime modules: " +
                            forbidden.joinToString(),
                    )
                }
            }.joinToString(System.lineSeparator())

            throw GradleException(details)
        }
    }
}
