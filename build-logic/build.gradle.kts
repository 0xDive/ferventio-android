import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

tasks.withType<JavaCompile>().configureEach {
    // Compile with the Gradle Daemon JDK (25 in this repository) and emit Java 17 bytecode.
    options.release.set(17)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

gradlePlugin {
    plugins {
        create("ferventioVerification") {
            id = "io.ferventio.android.verification"
            implementationClass = "io.ferventio.build.FerventioVerificationPlugin"
            displayName = "Ferventio Android verification conventions"
            description = "Provides typed verification and dependency-inventory tasks."
        }
    }
}

val requiredVerificationClasses = listOf(
    "FerventioVerificationPlugin",
    "ExportOsvDependencyInventoryTask",
    "VerifyFerventioServerCertificatePinsTask",
    "VerifyPlayCrashReportingConfigurationTask",
    "VerifyPrivacyPolicyConfigurationTask",
    "VerifyRuntimeClasspathTask",
)

val verifyPluginImplementationClasses = tasks.register("verifyPluginImplementationClasses") {
    group = "verification"
    description = "Ensures every class referenced by the Ferventio convention plugin is packaged."
    dependsOn(tasks.named("classes"))

    doLast {
        val packageDirectory = layout.buildDirectory
            .dir("classes/kotlin/main/io/ferventio/build")
            .get()
            .asFile
        val missing = requiredVerificationClasses.filterNot { className ->
            packageDirectory.resolve("$className.class").isFile
        }
        check(missing.isEmpty()) {
            "Missing build-logic implementation classes: ${missing.joinToString()}"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyPluginImplementationClasses)
}
