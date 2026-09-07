package io.ferventio.shared.push

import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.MobileDeviceIdentityValidation
import kotlinx.serialization.Serializable

/**
 * Backend-compatible push registration payload shared by Android and iOS.
 *
 * Nullable transport fields deliberately retain null defaults so adding APNs support does not
 * change the JSON emitted by existing Android FCM or embedded-socket registrations.
 */
@Serializable
data class PushRegistrationRequest(
    val installationId: String,
    val deviceSecret: String,
    val provider: String,
    val firebaseInstallationId: String? = null,
    val apnsDeviceToken: String? = null,
    val endpoint: String? = null,
    val p256dh: String? = null,
    val auth: String? = null,
    val appVersion: String,
    val platform: String,
    val userId: String? = null,
    val userLogin: String? = null,
    val channelIds: List<String> = emptyList(),
    val moderatorChannelIds: List<String> = emptyList(),
    val notificationRules: List<String> = emptyList(),
    val highlightPhrases: List<String> = emptyList(),
    val selectedUserLogins: List<String> = emptyList(),
)

data class PushRegistrationContext(
    val userId: String? = null,
    val userLogin: String? = null,
    val channelIds: List<String> = emptyList(),
    val moderatorChannelIds: List<String> = emptyList(),
    val notificationRules: List<String> = DEFAULT_NOTIFICATION_RULES,
    val highlightPhrases: List<String> = emptyList(),
    val selectedUserLogins: List<String> = emptyList(),
) {
    companion object {
        val DEFAULT_NOTIFICATION_RULES = listOf(
            "mention",
            "reply",
            "automod_hold",
            "ban",
            "timeout",
            "highlight",
            "selected_user",
            "stream_online",
            "title_change",
            "game_change",
            "raid",
            "reward",
            "subscription",
            "moderation_action",
        )
    }
}

/** Mirrors the server-side transport requirements before a request leaves the device. */
object PushRegistrationValidation {
    fun requireValid(request: PushRegistrationRequest) {
        require(request.installationId.isNotBlank() && request.deviceSecret.isNotBlank()) {
            "installationId and deviceSecret are required"
        }

        val platform = request.platform.trim().lowercase()
        val provider = request.provider.trim().lowercase()
        when (platform) {
            "android" -> when (provider) {
                "fcm" -> require(!request.firebaseInstallationId.isNullOrBlank()) {
                    "firebaseInstallationId is required for FCM"
                }

                "unifiedpush" -> require(
                    !request.endpoint.isNullOrBlank() &&
                        !request.p256dh.isNullOrBlank() &&
                        !request.auth.isNullOrBlank(),
                ) {
                    "endpoint, p256dh and auth are required for UnifiedPush"
                }

                "embedded_socket" -> Unit
                else -> error("android provider must be fcm, unifiedpush, or embedded_socket")
            }

            "ios" -> {
                require(provider == "apns") { "ios provider must be apns" }
                require(!request.apnsDeviceToken.isNullOrBlank()) {
                    "apnsDeviceToken is required for APNs"
                }
            }

            else -> error("platform must be android or ios")
        }
    }
}

/** Swift-friendly builder for the APNs registration shape accepted by the backend. */
class PushRegistrationRequestFactory {
    fun apns(
        identity: MobileDeviceIdentity,
        apnsDeviceToken: String,
        appVersion: String,
    ): PushRegistrationRequest = apns(
        identity = identity,
        apnsDeviceToken = apnsDeviceToken,
        appVersion = appVersion,
        context = PushRegistrationContext(),
    )

    fun apns(
        identity: MobileDeviceIdentity,
        apnsDeviceToken: String,
        appVersion: String,
        context: PushRegistrationContext,
    ): PushRegistrationRequest {
        MobileDeviceIdentityValidation.requireValid(identity)
        val normalizedToken = apnsDeviceToken.trim()
        require(normalizedToken.isNotEmpty()) { "APNs device token must not be blank" }

        return PushRegistrationRequest(
            installationId = identity.installationId,
            deviceSecret = identity.deviceSecret,
            provider = "apns",
            apnsDeviceToken = normalizedToken,
            appVersion = appVersion,
            platform = "ios",
            userId = context.userId,
            userLogin = context.userLogin,
            channelIds = context.channelIds,
            moderatorChannelIds = context.moderatorChannelIds,
            notificationRules = context.notificationRules,
            highlightPhrases = context.highlightPhrases,
            selectedUserLogins = context.selectedUserLogins,
        ).also(PushRegistrationValidation::requireValid)
    }
}
