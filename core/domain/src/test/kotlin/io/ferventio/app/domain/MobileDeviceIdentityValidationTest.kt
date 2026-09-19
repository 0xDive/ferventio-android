package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileDeviceIdentityValidationTest {
    @Test
    fun acceptsPersistableIdentityShape() {
        assertTrue(
            MobileDeviceIdentityValidation.isValid(
                MobileDeviceIdentity(
                    installationId = "installation-id",
                    deviceSecret = "a".repeat(32),
                ),
            ),
        )
    }

    @Test
    fun rejectsBlankOrOversizedInstallationId() {
        assertFalse(
            MobileDeviceIdentityValidation.isValid(
                MobileDeviceIdentity(" ", "a".repeat(32)),
            ),
        )
        assertFalse(
            MobileDeviceIdentityValidation.isValid(
                MobileDeviceIdentity("a".repeat(129), "a".repeat(32)),
            ),
        )
    }

    @Test
    fun rejectsSecretOutsidePersistedBounds() {
        assertFalse(
            MobileDeviceIdentityValidation.isValid(
                MobileDeviceIdentity("installation", "a".repeat(31)),
            ),
        )
        assertFalse(
            MobileDeviceIdentityValidation.isValid(
                MobileDeviceIdentity("installation", "a".repeat(257)),
            ),
        )
    }
}
