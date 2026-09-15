package io.ferventio.shared.push

import io.ferventio.app.domain.MobileDeviceIdentity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushBackendRegistrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun apnsFactoryBuildsBackendCompatibleRegistration() {
        val request = PushRegistrationRequestFactory().apns(
            identity = MobileDeviceIdentity(
                installationId = "installation-id",
                deviceSecret = "s".repeat(32),
            ),
            apnsDeviceToken = "  A1B2C3  ",
            appVersion = "1.0",
            context = PushRegistrationContext(
                userId = "user-id",
                userLogin = "viewer",
                channelIds = listOf("channel-id"),
            ),
        )

        assertEquals("ios", request.platform)
        assertEquals("apns", request.provider)
        assertEquals("A1B2C3", request.apnsDeviceToken)
        assertEquals("user-id", request.userId)
        assertEquals(listOf("channel-id"), request.channelIds)
        assertTrue(json.encodeToString(request).contains("\"apnsDeviceToken\":\"A1B2C3\""))
    }

    @Test
    fun androidFcmPayloadKeepsApnsFieldsOutOfJson() {
        val request = PushRegistrationRequest(
            installationId = "installation-id",
            deviceSecret = "secret",
            provider = "fcm",
            firebaseInstallationId = "firebase-id",
            appVersion = "1.0",
            platform = "android",
        )

        PushRegistrationValidation.requireValid(request)
        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"firebaseInstallationId\":\"firebase-id\""))
        assertFalse(encoded.contains("apnsDeviceToken"))
        assertFalse(encoded.contains("endpoint"))
        assertFalse(encoded.contains("p256dh"))
    }

    @Test
    fun rejectsApnsRegistrationWithoutDeviceToken() {
        assertFailsWith<IllegalArgumentException> {
            PushRegistrationValidation.requireValid(
                PushRegistrationRequest(
                    installationId = "installation-id",
                    deviceSecret = "secret",
                    provider = "apns",
                    appVersion = "1.0",
                    platform = "ios",
                ),
            )
        }
    }
}
