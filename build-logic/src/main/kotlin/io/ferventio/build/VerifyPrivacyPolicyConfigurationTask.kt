package io.ferventio.build

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Validates release privacy metadata before an APK/AAB can be assembled. */
abstract class VerifyPrivacyPolicyConfigurationTask : DefaultTask() {
    @get:Input abstract val operatorName: Property<String>
    @get:Input abstract val privacyContact: Property<String>
    @get:Input abstract val privacyPolicyUrl: Property<String>

    @TaskAction
    fun verifyConfiguration() {
        requireConfigured("FERVENTIO_PRIVACY_OPERATOR_NAME", operatorName.get())
        requireConfigured("FERVENTIO_PRIVACY_CONTACT", privacyContact.get())
        requireHttpsUrl("FERVENTIO_PRIVACY_POLICY_URL", privacyPolicyUrl.get())
    }
}
