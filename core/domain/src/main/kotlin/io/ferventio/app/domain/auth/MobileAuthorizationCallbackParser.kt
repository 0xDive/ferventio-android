package io.ferventio.app.domain

/**
 * Platform-neutral representation of the URL components delivered by the mobile OAuth callback.
 * Platform URL types stay outside the domain layer; this model only carries already-decoded values.
 */
data class MobileAuthorizationCallbackComponents(
    val scheme: String?,
    val host: String?,
    val path: String?,
    val hasUserInfo: Boolean = false,
    val fragment: String? = null,
    val codeValues: List<String?> = emptyList(),
    val stateValues: List<String?> = emptyList(),
    val errorValues: List<String?> = emptyList(),
)

data class MobileAuthorizationCallbackPayload(
    val code: String?,
    val state: String?,
    val errorCode: String?,
)

sealed interface MobileAuthorizationCallbackParseResult {
    data object NotCallback : MobileAuthorizationCallbackParseResult
    data object InvalidCallback : MobileAuthorizationCallbackParseResult
    data class Parsed(
        val payload: MobileAuthorizationCallbackPayload,
    ) : MobileAuthorizationCallbackParseResult
}

/** Strict route and query-shape validation shared by Android and iOS OAuth adapters. */
object MobileAuthorizationCallbackParser {
    fun parse(
        components: MobileAuthorizationCallbackComponents,
        expectedScheme: String,
    ): MobileAuthorizationCallbackParseResult {
        require(expectedScheme.isNotBlank()) { "OAuth callback scheme must not be blank" }

        if (
            components.scheme != expectedScheme ||
            components.host != "oauth" ||
            components.path != "/callback"
        ) {
            return MobileAuthorizationCallbackParseResult.NotCallback
        }
        if (components.hasUserInfo || components.fragment != null) {
            return MobileAuthorizationCallbackParseResult.InvalidCallback
        }
        if (
            components.codeValues.size > 1 ||
            components.stateValues.size > 1 ||
            components.errorValues.size > 1
        ) {
            return MobileAuthorizationCallbackParseResult.InvalidCallback
        }

        return MobileAuthorizationCallbackParseResult.Parsed(
            MobileAuthorizationCallbackPayload(
                code = components.codeValues.singleOrNull().normalizedQueryValue(),
                state = components.stateValues.singleOrNull().normalizedQueryValue(),
                errorCode = components.errorValues.singleOrNull().normalizedQueryValue(),
            ),
        )
    }

    private fun String?.normalizedQueryValue(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)
}
