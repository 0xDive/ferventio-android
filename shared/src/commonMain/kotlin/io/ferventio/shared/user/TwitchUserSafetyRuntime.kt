package io.ferventio.shared.user

import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.chat.ChatRuntimeStateHolder

class TwitchUserSafetyRuntime(
    private val chatState: ChatRuntimeStateHolder,
    private val gateway: TwitchUserSafetyGateway = TwitchUserSafetyClient(),
) {
    suspend fun blockUser(
        authentication: StoredAuthentication,
        targetUserId: String,
    ) {
        try {
            gateway.blockUser(authentication, targetUserId)
        } catch (error: TwitchUserSafetyMutationException) {
            if (error.statusCode == AUTHENTICATION_FAILURE_CODE) {
                chatState.markAuthenticationRequired(error.message)
            }
            throw error
        }
    }

    private companion object {
        const val AUTHENTICATION_FAILURE_CODE = 401
    }
}
