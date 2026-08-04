package io.ferventio.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCertificatePinPolicyTest {
    @Test
    fun parsesExactHostWithPrimaryAndBackupPins() {
        val policy = ServerCertificatePinPolicy.parse(
            "Api.Example.com=$PIN_A;api.example.com=$PIN_B",
        )

        assertEquals(setOf(PIN_A, PIN_B), policy.pinsForHost("API.EXAMPLE.COM"))
        assertFalse(policy.hasPinsForHost("other.example.com"))
    }

    @Test
    fun pinnedHttpsServerIsAcceptedWhenPinningIsRequired() {
        val policy = ServerCertificatePinPolicy.parse("api.example.com=$PIN_A")

        val validated = policy.validateServerUrl(
            value = "https://api.example.com/",
            requirePinning = true,
            allowLocalCleartext = false,
        )

        assertEquals("https://api.example.com", validated.baseUrl)
        assertEquals("api.example.com", validated.host)
        assertTrue(validated.pinned)
    }

    @Test
    fun unpinnedHttpsServerIsRejectedInReleaseMode() {
        val policy = ServerCertificatePinPolicy.parse("api.example.com=$PIN_A")

        val error = assertThrows(IllegalArgumentException::class.java) {
            policy.validateServerUrl(
                value = "https://other.example.com",
                requirePinning = true,
                allowLocalCleartext = false,
            )
        }

        assertTrue(error.message.orEmpty().contains("нет certificate pin"))
    }

    @Test
    fun debugModeAllowsUnpinnedHttpsAndLocalEmulatorHttp() {
        val policy = ServerCertificatePinPolicy.parse("")

        assertFalse(
            policy.validateServerUrl(
                value = "https://dev.example.com",
                requirePinning = false,
                allowLocalCleartext = true,
            ).pinned,
        )
        assertEquals(
            "http://10.0.2.2:8080",
            policy.validateServerUrl(
                value = "http://10.0.2.2:8080",
                requirePinning = false,
                allowLocalCleartext = true,
            ).baseUrl,
        )
    }

    @Test
    fun rejectsInsecureRemoteServerAndDecoratedBaseUrl() {
        val policy = ServerCertificatePinPolicy.parse("")

        assertThrows(IllegalArgumentException::class.java) {
            policy.validateServerUrl(
                value = "http://api.example.com",
                requirePinning = false,
                allowLocalCleartext = true,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            policy.validateServerUrl(
                value = "https://user@api.example.com?token=value#fragment",
                requirePinning = false,
                allowLocalCleartext = true,
            )
        }
    }

    @Test
    fun rejectsWildcardsSha1AndMalformedSha256Pins() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerCertificatePinPolicy.parse("*.example.com=$PIN_A")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerCertificatePinPolicy.parse("api.example.com=sha1/AAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerCertificatePinPolicy.parse("api.example.com=sha256/not-base64")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ServerCertificatePinPolicy.parse("https://api.example.com=$PIN_A")
        }
    }

    private companion object {
        const val PIN_A = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        const val PIN_B = "sha256/AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    }
}
