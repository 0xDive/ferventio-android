package io.ferventio.shared.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushRegistrationStateHolderTest {
    @Test
    fun defaultsToUnknownAndIdle() {
        val holder = PushRegistrationStateHolder()

        assertEquals(PushAuthorizationStatus.UNKNOWN, holder.authorizationStatus)
        assertEquals(PushRegistrationStatus.IDLE, holder.registrationStatus)
        assertNull(holder.deviceToken)
        assertNull(holder.lastRegistrationError)
        assertEquals(PushBackendRegistrationStatus.IDLE, holder.backendRegistrationStatus)
        assertNull(holder.backendRegisteredDeviceToken)
        assertNull(holder.lastBackendRegistrationError)
        assertEquals(PushSelfTestStatus.IDLE, holder.selfTestStatus)
        assertNull(holder.lastSelfTestError)
        assertFalse(holder.needsBackendRegistration)
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
        assertTrue(holder.needsBackendRegistration)
    }

    @Test
    fun backendRegistrationTracksTheTokenItSynchronized() {
        val holder = PushRegistrationStateHolder()
        holder.markRegistered("abc123")

        holder.markBackendRegistrationStarted()
        holder.markBackendRegistered()

        assertEquals(PushBackendRegistrationStatus.REGISTERED, holder.backendRegistrationStatus)
        assertEquals("abc123", holder.backendRegisteredDeviceToken)
        assertNull(holder.lastBackendRegistrationError)
        assertFalse(holder.needsBackendRegistration)
    }

    @Test
    fun receivingSameDeviceTokenKeepsBackendRegistrationCurrent() {
        val holder = PushRegistrationStateHolder()
        holder.markRegistered("abc123")
        holder.markBackendRegistrationStarted()
        holder.markBackendRegistered()

        holder.markRegistered("  abc123  ")

        assertEquals(PushBackendRegistrationStatus.REGISTERED, holder.backendRegistrationStatus)
        assertEquals("abc123", holder.backendRegisteredDeviceToken)
        assertFalse(holder.needsBackendRegistration)
    }

    @Test
    fun rotatedDeviceTokenInvalidatesBackendRegistrationAndSelfTestState() {
        val holder = PushRegistrationStateHolder()
        holder.markRegistered("old-token")
        holder.markBackendRegistrationStarted()
        holder.markBackendRegistered()
        holder.markSelfTestStarted()
        holder.markSelfTestSent()

        holder.markRegistered("new-token")

        assertEquals("new-token", holder.deviceToken)
        assertEquals(PushBackendRegistrationStatus.IDLE, holder.backendRegistrationStatus)
        assertNull(holder.backendRegisteredDeviceToken)
        assertEquals(PushSelfTestStatus.IDLE, holder.selfTestStatus)
        assertNull(holder.lastSelfTestError)
        assertTrue(holder.needsBackendRegistration)
    }

    @Test
    fun backendFailureDoesNotClearPlatformDeviceToken() {
        val holder = PushRegistrationStateHolder()
        holder.markRegistered("abc123")
        holder.markBackendRegistrationStarted()

        holder.markBackendRegistrationFailed("  backend unavailable  ")

        assertEquals(PushRegistrationStatus.REGISTERED, holder.registrationStatus)
        assertEquals("abc123", holder.deviceToken)
        assertEquals(PushBackendRegistrationStatus.FAILED, holder.backendRegistrationStatus)
        assertEquals("backend unavailable", holder.lastBackendRegistrationError)
        assertTrue(holder.needsBackendRegistration)
    }

    @Test
    fun selfTestTracksProgressSuccessAndFailureSeparatelyFromRegistration() {
        val holder = PushRegistrationStateHolder()
        holder.markRegistered("abc123")
        holder.markBackendRegistrationStarted()
        holder.markBackendRegistered()

        holder.markSelfTestStarted()
        assertEquals(PushSelfTestStatus.SENDING, holder.selfTestStatus)
        assertNull(holder.lastSelfTestError)
        assertEquals(PushBackendRegistrationStatus.REGISTERED, holder.backendRegistrationStatus)

        holder.markSelfTestSent()
        assertEquals(PushSelfTestStatus.SENT, holder.selfTestStatus)
        assertNull(holder.lastSelfTestError)
        assertEquals(PushBackendRegistrationStatus.REGISTERED, holder.backendRegistrationStatus)

        holder.markSelfTestStarted()
        holder.markSelfTestFailed("  delivery unavailable  ")
        assertEquals(PushSelfTestStatus.FAILED, holder.selfTestStatus)
        assertEquals("delivery unavailable", holder.lastSelfTestError)
        assertEquals(PushBackendRegistrationStatus.REGISTERED, holder.backendRegistrationStatus)
        assertFalse(holder.needsBackendRegistration)
    }

    @Test
    fun backendRegistrationRestartClearsPreviousSelfTestResult() {
        val holder = PushRegistrationStateHolder()
        holder.markRegistered("abc123")
        holder.markBackendRegistrationStarted()
        holder.markBackendRegistered()
        holder.markSelfTestFailed("old failure")

        holder.markBackendRegistrationStarted()

        assertEquals(PushSelfTestStatus.IDLE, holder.selfTestStatus)
        assertNull(holder.lastSelfTestError)
    }

    @Test
    fun failedRegistrationClearsStaleTokenAndBackendState() {
        val holder = PushRegistrationStateHolder()
        holder.markRegistered("abc123")
        holder.markBackendRegistrationStarted()
        holder.markBackendRegistered()
        holder.markSelfTestStarted()
        holder.markSelfTestSent()

        holder.markRegistrationFailed("  unavailable  ")

        assertEquals(PushRegistrationStatus.FAILED, holder.registrationStatus)
        assertNull(holder.deviceToken)
        assertEquals("unavailable", holder.lastRegistrationError)
        assertEquals(PushBackendRegistrationStatus.IDLE, holder.backendRegistrationStatus)
        assertNull(holder.backendRegisteredDeviceToken)
        assertEquals(PushSelfTestStatus.IDLE, holder.selfTestStatus)
        assertFalse(holder.needsBackendRegistration)
    }

    @Test
    fun clearRegistrationReturnsToIdleWithoutChangingAuthorization() {
        val holder = PushRegistrationStateHolder(PushAuthorizationStatus.PROVISIONAL)
        holder.markRegistered("abc123")
        holder.markBackendRegistrationStarted()
        holder.markBackendRegistered()
        holder.markSelfTestStarted()
        holder.markSelfTestSent()

        holder.clearRegistration()

        assertEquals(PushAuthorizationStatus.PROVISIONAL, holder.authorizationStatus)
        assertEquals(PushRegistrationStatus.IDLE, holder.registrationStatus)
        assertNull(holder.deviceToken)
        assertNull(holder.lastRegistrationError)
        assertEquals(PushBackendRegistrationStatus.IDLE, holder.backendRegistrationStatus)
        assertEquals(PushSelfTestStatus.IDLE, holder.selfTestStatus)
        assertFalse(holder.needsBackendRegistration)
    }

    @Test
    fun backendRegistrationCannotStartBeforePlatformTokenExists() {
        assertFailsWith<IllegalArgumentException> {
            PushRegistrationStateHolder().markBackendRegistrationStarted()
        }
    }

    @Test
    fun blankDeviceTokenIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            PushRegistrationStateHolder().markRegistered("   ")
        }
    }
}
