import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    android {
        namespace = "io.ferventio.core.domain"
        compileSdk = 36
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(compose.runtime)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        getByName("androidHostTest") {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(libs.junit)
                implementation(kotlin("test-junit"))
            }
        }
    }
}
