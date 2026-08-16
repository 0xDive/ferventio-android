package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MobileAuthorizationCallbackParserTest {
    @Test
    fun `unrelated route is ignored`() {
        val result = MobileAuthorizationCallbackParser.parse(
            components = components(path = "/other"),
            expectedScheme = "io.ferventio.app",
        )

        assertIs<MobileAuthorizationCallbackParseResult.NotCallback>(result)
    }

    @Test
    fun `matching route extracts normalized callback values`() {
        val result = MobileAuthorizationCallbackParser.parse(
            components = components(
                codeValues = listOf(" code "),
                stateValues = listOf(" state "),
                errorValues = listOf(" "),
            ),
            expectedScheme = "io.ferventio.app",
        )

        assertEquals(
            MobileAuthorizationCallbackParseResult.Parsed(
                MobileAuthorizationCallbackPayload(
                    code = "code",
                    state = "state",
                    errorCode = null,
                ),
            ),
            result,
        )
    }

    @Test
    fun `missing callback values remain absent for policy validation`() {
        val result = MobileAuthorizationCallbackParser.parse(
            components = components(),
            expectedScheme = "io.ferventio.app",
        )

        assertEquals(
            MobileAuthorizationCallbackParseResult.Parsed(
                MobileAuthorizationCallbackPayload(
                    code = null,
                    state = null,
                    errorCode = null,
                ),
            ),
            result,
        )
    }

    @Test
    fun `duplicate security-sensitive query values are rejected`() {
        val result = MobileAuthorizationCallbackParser.parse(
            components = components(
                stateValues = listOf("state", "other"),
            ),
            expectedScheme = "io.ferventio.app",
        )

        assertIs<MobileAuthorizationCallbackParseResult.InvalidCallback>(result)
    }

    @Test
    fun `userinfo and fragments are rejected on callback route`() {
        assertIs<MobileAuthorizationCallbackParseResult.InvalidCallback>(
            MobileAuthorizationCallbackParser.parse(
                components = components(hasUserInfo = true),
                expectedScheme = "io.ferventio.app",
            ),
        )
        assertIs<MobileAuthorizationCallbackParseResult.InvalidCallback>(
            MobileAuthorizationCallbackParser.parse(
                components = components(fragment = "unexpected"),
                expectedScheme = "io.ferventio.app",
            ),
        )
    }

    private fun components(
        scheme: String? = "io.ferventio.app",
        host: String? = "oauth",
        path: String? = "/callback",
        hasUserInfo: Boolean = false,
        fragment: String? = null,
        codeValues: List<String?> = emptyList(),
        stateValues: List<String?> = emptyList(),
        errorValues: List<String?> = emptyList(),
    ) = MobileAuthorizationCallbackComponents(
        scheme = scheme,
        host = host,
        path = path,
        hasUserInfo = hasUserInfo,
        fragment = fragment,
        codeValues = codeValues,
        stateValues = stateValues,
        errorValues = errorValues,
    )
}
