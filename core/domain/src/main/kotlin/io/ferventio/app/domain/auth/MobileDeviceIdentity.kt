package io.ferventio.app.domain

data class MobileDeviceIdentity(
    val installationId: String,
    val deviceSecret: String,
)

object MobileDeviceIdentityValidation {
    fun isValid(identity: MobileDeviceIdentity): Boolean =
        isValid(
            installationId = identity.installationId,
            deviceSecret = identity.deviceSecret,
        )

    fun isValid(
        installationId: String,
        deviceSecret: String,
    ): Boolean {
        val installationId = installationId.trim()
        val deviceSecret = deviceSecret.trim()
        return installationId.isNotEmpty() &&
            installationId.length <= 128 &&
            deviceSecret.length in 32..256
    }

    fun requireValid(identity: MobileDeviceIdentity) {
        require(isValid(identity)) { "Invalid mobile device identity" }
    }
}
