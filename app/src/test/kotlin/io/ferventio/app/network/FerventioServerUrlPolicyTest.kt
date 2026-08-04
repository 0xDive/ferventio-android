package io.ferventio.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FerventioServerUrlPolicyTest {
    @Test
    fun acceptsHttpsAndRemovesTrailingSlash() {
        val result = FerventioServerUrlPolicy.validate(
            "https://ferventio.godive.dev/",
        )

        assertEquals(
            "https://ferventio.godive.dev",
            result.baseUrl,
        )
        assertEquals(
            "ferventio.godive.dev",
            result.host,
        )
    }

    @Test
    fun rejectsRemoteCleartextServer() {
        assertThrows(IllegalArgumentException::class.java) {
            FerventioServerUrlPolicy.validate(
                "http://ferventio.godive.dev",
            )
        }
    }

    @Test
    fun rejectsCredentialsQueryAndFragment() {
        assertThrows(IllegalArgumentException::class.java) {
            FerventioServerUrlPolicy.validate(
                "https://user@ferventio.godive.dev?token=value#fragment",
            )
        }
    }
}
