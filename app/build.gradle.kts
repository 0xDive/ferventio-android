import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import io.ferventio.build.ExportOsvDependencyInventoryTask
import io.ferventio.build.VerifyFerventioServerCertificatePinsTask
import io.ferventio.build.VerifyPlayCrashReportingConfigurationTask
import io.ferventio.build.VerifyPrivacyPolicyConfigurationTask
import io.ferventio.build.VerifyRuntimeClasspathTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("io.ferventio.android.verification")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val productionFerventioServerUrl = "https://ferventio.godive.dev"
val ferventioServerUrl = providers
    .gradleProperty("FERVENTIO_SERVER_URL")
    .orElse(providers.gradleProperty("FERVENTIO_PUSH_SERVER_URL"))
    .orElse(productionFerventioServerUrl)
    .get()
val ferventioServerCertificatePins = providers
    .gradleProperty("FERVENTIO_SERVER_CERTIFICATE_PINS")
    .orElse("")
    .get()
val firebaseApplicationId = providers.gradleProperty("FERVENTIO_FIREBASE_APPLICATION_ID").orElse("").get()
val firebaseProjectId = providers.gradleProperty("FERVENTIO_FIREBASE_PROJECT_ID").orElse("").get()
val firebaseApiKey = providers.gradleProperty("FERVENTIO_FIREBASE_API_KEY").orElse("").get()
val firebaseSenderId = providers.gradleProperty("FERVENTIO_FIREBASE_SENDER_ID").orElse("").get()
val crashlyticsMappingId = providers.gradleProperty("FERVENTIO_CRASHLYTICS_MAPPING_ID").orElse("").get()
val privacyOperatorName = providers.gradleProperty("FERVENTIO_PRIVACY_OPERATOR_NAME").orElse("").get()
val privacyContact = providers.gradleProperty("FERVENTIO_PRIVACY_CONTACT").orElse("").get()
val privacyPolicyUrl = providers.gradleProperty("FERVENTIO_PRIVACY_POLICY_URL").orElse("").get()
val showPrivacyPolicyInApp = providers
    .gradleProperty("FERVENTIO_SHOW_PRIVACY_POLICY_IN_APP")
    .orElse("false")
    .map { value ->
        require(value == "true" || value == "false") {
            "FERVENTIO_SHOW_PRIVACY_POLICY_IN_APP must be either true or false."
        }
        value.toBoolean()
    }
    .get()
val appWebsiteUrl = providers.gradleProperty("FERVENTIO_APP_WEBSITE_URL").orElse(productionFerventioServerUrl).get()
val appGithubUrl = providers.gradleProperty("FERVENTIO_APP_GITHUB_URL").orElse("https://github.com/0xDive/ferventio-android").get()
val appTelegramChannelUrl = providers.gradleProperty("FERVENTIO_APP_TELEGRAM_CHANNEL_URL").orElse("").get()
val appTelegramChatUrl = providers.gradleProperty("FERVENTIO_APP_TELEGRAM_CHAT_URL").orElse("").get()
val appTranslationsUrl = providers.gradleProperty("FERVENTIO_APP_TRANSLATIONS_URL").orElse("").get()

val releaseKeystoreFile = providers.gradleProperty("FERVENTIO_KEYSTORE_FILE").orNull
val releaseKeystorePassword = providers.gradleProperty("FERVENTIO_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("FERVENTIO_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("FERVENTIO_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "io.ferventio.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.ferventio.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 89
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField(
            "String",
            "FERVENTIO_SERVER_URL",
            ferventioServerUrl.asBuildConfigString(),
        )
        // Compatibility alias retained for pre-public tooling and integrations.
        buildConfigField(
            "String",
            "DEFAULT_PUSH_SERVER_URL",
            ferventioServerUrl.asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "FERVENTIO_SERVER_CERTIFICATE_PINS",
            ferventioServerCertificatePins.asBuildConfigString(),
        )
        buildConfigField("boolean", "REQUIRE_FERVENTIO_SERVER_PINNING", "true")
        buildConfigField("boolean", "PERFORMANCE_TESTING", "false")
        buildConfigField("String", "PRIVACY_OPERATOR_NAME", privacyOperatorName.asBuildConfigString())
        buildConfigField("String", "PRIVACY_CONTACT", privacyContact.asBuildConfigString())
        buildConfigField("String", "PRIVACY_POLICY_URL", privacyPolicyUrl.asBuildConfigString())
        buildConfigField("boolean", "SHOW_PRIVACY_POLICY_IN_APP", showPrivacyPolicyInApp.toString())
        buildConfigField("String", "APP_WEBSITE_URL", appWebsiteUrl.asBuildConfigString())
        buildConfigField("String", "APP_GITHUB_URL", appGithubUrl.asBuildConfigString())
        buildConfigField("String", "APP_TELEGRAM_CHANNEL_URL", appTelegramChannelUrl.asBuildConfigString())
        buildConfigField("String", "APP_TELEGRAM_CHAT_URL", appTelegramChatUrl.asBuildConfigString())
        buildConfigField("String", "APP_TRANSLATIONS_URL", appTranslationsUrl.asBuildConfigString())
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystoreFile))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    flavorDimensions += "push"
    productFlavors {
        create("play") {
            dimension = "push"
            versionNameSuffix = "-play"
            buildConfigField("String", "PUSH_TRANSPORT", "fcm".asBuildConfigString())
            buildConfigField("boolean", "LOCAL_CRASH_REPORTING", "false")
            buildConfigField(
                "String",
                "FIREBASE_APPLICATION_ID",
                firebaseApplicationId.asBuildConfigString(),
            )
            buildConfigField(
                "String",
                "FIREBASE_PROJECT_ID",
                firebaseProjectId.asBuildConfigString(),
            )
            buildConfigField("String", "FIREBASE_API_KEY", firebaseApiKey.asBuildConfigString())
            buildConfigField("String", "FIREBASE_SENDER_ID", firebaseSenderId.asBuildConfigString())
            resValue(
                "string",
                "com.google.firebase.crashlytics.mapping_file_id",
                crashlyticsMappingId.ifBlank { "unconfigured" },
            )
        }

        create("foss") {
            dimension = "push"
            versionNameSuffix = "-foss"
            buildConfigField("String", "PUSH_TRANSPORT", "embedded_socket".asBuildConfigString())
            buildConfigField("boolean", "LOCAL_CRASH_REPORTING", "true")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "REQUIRE_FERVENTIO_SERVER_PINNING", "false")
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-benchmark"
        }
        // The Baseline Profile plugin creates benchmarkRelease and nonMinifiedRelease
        // after the normal Android DSL is configured. configureEach also receives those
        // future build types, while the production release keeps PERFORMANCE_TESTING=false.
        configureEach {
            if (name in setOf("benchmark", "benchmarkRelease", "nonMinifiedRelease")) {
                buildConfigField("boolean", "PERFORMANCE_TESTING", "true")
            }
        }

        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }


    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/INDEX.LIST",
        )
        resources.merges += setOf(
            "META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler",
            "META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory",
        )
    }
}

baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
}


kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:database"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.androidx.profileinstaller)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)

    add("playImplementation", libs.firebase.messaging)
    add("playImplementation", libs.firebase.crashlytics)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugRuntimeOnly(
        "org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}",
    )
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}",
    )
    baselineProfile(project(":benchmark"))
}

// Ferventio AndroidTest Espresso 3.7.0 pin
// Compose UI test currently requests Espresso 3.5.0 transitively. Android 16
// requires the 3.7.0 input-injection implementation, so pin it for every
// instrumentation-test compile/runtime configuration.
configurations.configureEach {
    if (
        name.endsWith("AndroidTestCompileClasspath") ||
        name.endsWith("AndroidTestRuntimeClasspath")
    ) {
        resolutionStrategy.force("androidx.test.espresso:espresso-core:3.7.0")
    }
}


val verifyPlayCrashReportingConfiguration =
    tasks.register<VerifyPlayCrashReportingConfigurationTask>("verifyPlayCrashReportingConfiguration") {
        group = "verification"
        description = "Validates Firebase Crashlytics configuration for the production Play release."
        configuredFirebaseApplicationId.set(firebaseApplicationId)
        configuredFirebaseProjectId.set(firebaseProjectId)
        configuredFirebaseApiKey.set(firebaseApiKey)
        configuredFirebaseSenderId.set(firebaseSenderId)
        configuredCrashlyticsMappingId.set(crashlyticsMappingId)
    }

val verifyPrivacyPolicyConfiguration =
    tasks.register<VerifyPrivacyPolicyConfigurationTask>("verifyPrivacyPolicyConfiguration") {
        group = "verification"
        description = "Validates operator, contact and published HTTPS privacy-policy URL for release builds."
        operatorName.set(privacyOperatorName)
        privacyContact.set(privacyContact)
        privacyPolicyUrl.set(privacyPolicyUrl)
    }

val verifyFerventioServerCertificatePins =
    tasks.register<VerifyFerventioServerCertificatePinsTask>("verifyFerventioServerCertificatePins") {
        group = "verification"
        description = "Validates build-time SPKI pins for the default Ferventio server."
        configuredDefaultPushServerUrl.set(ferventioServerUrl)
        configuredCertificatePins.set(ferventioServerCertificatePins)
    }

tasks.configureEach {
    if (name.matches(Regex("pre(?:Foss|Play)(?:Release|Benchmark|BenchmarkRelease|NonMinifiedRelease)Build"))) {
        dependsOn(verifyFerventioServerCertificatePins)
    }
    if (name in setOf("preFossReleaseBuild", "prePlayReleaseBuild")) {
        dependsOn(verifyPrivacyPolicyConfiguration)
    }
    if (name == "prePlayReleaseBuild") {
        dependsOn(verifyPlayCrashReportingConfiguration)
    }
}

tasks.register<VerifyRuntimeClasspathTask>("verifyPlayCrashReportingDependency") {
    group = "verification"
    description = "Fails if the Play release runtime does not contain Firebase Crashlytics."
    runtimeClasspath.from(
        providers.provider { configurations.getByName("playReleaseRuntimeClasspath") },
    )
    requiredArtifactPrefixes.set(setOf("firebase-crashlytics-"))
    forbiddenArtifactPrefixes.set(emptySet())
}

tasks.register<VerifyRuntimeClasspathTask>("verifyFossNoGooglePushDependencies") {
    group = "verification"
    description = "Fails if the FOSS runtime contains Firebase or Google Play Services artifacts."
    runtimeClasspath.from(
        providers.provider { configurations.getByName("fossReleaseRuntimeClasspath") },
    )
    requiredArtifactPrefixes.set(emptySet())
    forbiddenArtifactPrefixes.set(setOf("firebase-", "play-services-"))
}

val securityRuntimeConfigurationNames = listOf(
    "fossReleaseRuntimeClasspath",
    "playReleaseRuntimeClasspath",
    "fossDebugUnitTestRuntimeClasspath",
    "playDebugUnitTestRuntimeClasspath",
)

tasks.register<ExportOsvDependencyInventoryTask>("exportOsvDependencyInventory") {
    group = "verification"
    description = "Exports resolved Android/JVM dependencies as an OSV-Scanner custom inventory."
    coordinates.set(providers.provider {
        securityRuntimeConfigurationNames
            .flatMap { configurationName ->
                configurations.getByName(configurationName)
                    .incoming
                    .resolutionResult
                    .allComponents
            }
            .mapNotNull { component -> component.id as? ModuleComponentIdentifier }
            .map { identifier ->
                "${identifier.group}:${identifier.module}:${identifier.version}"
            }
            .distinct()
            .sorted()
    })
    outputFile.set(layout.buildDirectory.file("reports/security/osv-scanner.json"))
}
