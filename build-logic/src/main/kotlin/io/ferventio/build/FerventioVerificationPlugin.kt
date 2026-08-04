package io.ferventio.build

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Entry point for Ferventio's project-local verification convention plugin.
 *
 * The plugin intentionally keeps task registration in the consuming module so
 * every task can be wired to Android variant-specific providers without
 * reaching into AGP internals from the included build.
 */
class FerventioVerificationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Use an explicit Gradle Action instead of Kotlin DSL receiver syntax.
        // Convention-plugin sources are regular Kotlin code, and the explicit
        // Action remains stable across Kotlin Gradle Plugin SAM-receiver changes.
        target.tasks.configureEach(
            object : Action<Task> {
                override fun execute(task: Task) {
                    if (task.group == null && task.name.startsWith("verify")) {
                        task.group = "verification"
                    }
                }
            },
        )
    }
}
