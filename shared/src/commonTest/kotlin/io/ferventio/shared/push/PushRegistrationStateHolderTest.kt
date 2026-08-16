package io.ferventio.shared.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PushRegistrationStateHolderTest {
    @Test
    fun defaultsToUnknownAndIdle() {
        val holder = PushRegistrationStateHolder()

        assertEquals(PushAuthorizationStatus.UNKNOWN, holder.authorizationStatus)
        assertEquals(PushRegistrationStatus.IDLE, holder.registrationStatus)
        assertNull(holder.deviceToken)
        assertNull(holder.lastRegistrationError)
    }

    @Test
    fun tracksAuthorizationAndSuccessfulRegistration() {
        val holder = PushRegistrationStateHolder()

        holder.updateAuthorizationStatus(PushAuthorizationStatus.AUTHORIZED)
        holder.markRegistrationRequested()
        holder.markRegistered("  abc123  ")

        assertEquals(PushAuthorizationStatus.AUTHORIZED, holder.authorizationStatus)
        assertEquals(PushRegistrationStatus.REGISTERED, holder.registrationStatus)
        assertEquals("abc123", holder.deviceToken)
        assertNull(holder.lastRegistrationError)
    }

    @Test
    fun failedRegistrationClearsStaleToken() {
        val holder = PushRegistrationStateHolder()
        holder.markRegistered("abc123")

        holder.markRegistrationFailed("  unavailable  ")

        assertEquals(PushRegistrationStatus.FAILED, holder.registrationStatus)
        assertNull(holder.deviceToken)
        assertEquals("unavailable", holder.lastRegistrationError)
    }

    @Test
    fun clearRegistrationReturnsToIdleWithoutChangingAuthorization() {
        val holder = PushRegistrationStateHolder(PushAuthorizationStatus.PROVISIONAL)
        holder.markRegistered("abc123")

        holder.clearRegistration()

        assertEquals(PushAuthorizationStatus.PROVISIONAL, holder.authorizationStatus)
        assertEquals(PushRegistrationStatus.IDLE, holder.registrationStatus)
        assertNull(holder.deviceToken)
        assertNull(holder.lastRegistrationError)
    }

    @Test
    fun blankDeviceTokenIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            PushRegistrationStateHolder().markRegistered("   ")
        }
    }
}
