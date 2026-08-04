package io.ferventio.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Validates the values embedded into the production Play/Crashlytics build. */
abstract class VerifyPlayCrashReportingConfigurationTask : DefaultTask() {
    @get:Input abstract val configuredFirebaseApplicationId: Property<String>
    @get:Input abstract val configuredFirebaseProjectId: Property<String>
    @get:Input abstract val configuredFirebaseApiKey: Property<String>
    @get:Input abstract val configuredFirebaseSenderId: Property<String>
    @get:Input abstract val configuredCrashlyticsMappingId: Property<String>

    @TaskAction
    fun verifyConfiguration() {
        val applicationId = requireConfigured(
            "FERVENTIO_FIREBASE_APPLICATION_ID",
            configuredFirebaseApplicationId.get(),
        )
        requireConfigured("FERVENTIO_FIREBASE_PROJECT_ID", configuredFirebaseProjectId.get())
        requireConfigured("FERVENTIO_FIREBASE_API_KEY", configuredFirebaseApiKey.get())
        val senderId = requireConfigured(
            "FERVENTIO_FIREBASE_SENDER_ID",
            configuredFirebaseSenderId.get(),
        )
        requireConfigured(
            "FERVENTIO_CRASHLYTICS_MAPPING_ID",
            configuredCrashlyticsMappingId.get(),
        )

        if (!applicationId.contains(":android:")) {
            throw GradleException(
                "FERVENTIO_FIREBASE_APPLICATION_ID must be an Android Firebase application ID.",
            )
        }
        if (senderId.any { !it.isDigit() }) {
            throw GradleException("FERVENTIO_FIREBASE_SENDER_ID must contain digits only.")
        }
    }
}
