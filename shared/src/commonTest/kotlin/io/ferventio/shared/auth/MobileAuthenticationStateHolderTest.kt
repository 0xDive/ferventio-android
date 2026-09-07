package io.ferventio.shared.auth

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MobileAuthenticationStateHolderTest {
    @Test
    fun restoreWithoutSessionBecomesSignedOut() {
        val holder = MobileAuthenticationStateHolder()

        holder.restore(null)

        assertEquals(MobileAuthenticationStatus.SIGNED_OUT, holder.state.status)
        assertNull(holder.state.authentication)
    }

    @Test
    fun authorizationClearsOldSessionBeforeNewHandshake() {
        val holder = MobileAuthenticationStateHolder()
        holder.restore(authentication())

        holder.beginAuthorization()

        assertEquals(MobileAuthenticationStatus.AUTHORIZING, holder.state.status)
        assertNull(holder.state.authentication)
    }

    @Test
    fun successfulAuthorizationPublishesSession() {
        val holder = MobileAuthenticationStateHolder()
        val authentication = authentication()

        holder.markSignedIn(authentication)

        assertEquals(MobileAuthenticationStatus.SIGNED_IN, holder.state.status)
        assertEquals(authentication, holder.state.authentication)
        assertNull(holder.state.errorMessage)
    }

    @Test
    fun failureDropsAuthenticationAndNormalizesMessage() {
        val holder = MobileAuthenticationStateHolder()
        holder.markSignedIn(authentication())

        holder.markFailed(" unavailable ")

        assertEquals(MobileAuthenticationStatus.FAILED, holder.state.status)
        assertNull(holder.state.authentication)
        assertEquals("unavailable", holder.state.errorMessage)
    }

    @Test
    fun signOutClearsSessionAndFailure() {
        val holder = MobileAuthenticationStateHolder()
        holder.markFailed("failure")

        holder.signOut()

        assertEquals(MobileAuthenticationStatus.SIGNED_OUT, holder.state.status)
        assertNull(holder.state.authentication)
        assertNull(holder.state.errorMessage)
    }

    private fun authentication() = StoredAuthentication(
        backendCredential = BackendSessionCredential(
            serverUrl = "https://example.test",
            token = "backend-token",
            expiresAtEpochMillis = 1_000L,
        ),
        accessLease = null,
    )
}
