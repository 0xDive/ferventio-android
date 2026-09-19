package io.ferventio.shared.ui.app

internal data class FerventioOpenSourceNotice(
    val id: String,
    val name: String,
    val version: String,
    val licenseId: String,
    val projectUrl: String,
)

internal data class FerventioLicenseText(
    val id: String,
    val name: String,
    val text: String,
)

internal object FerventioSharedLegalContent {
    const val APACHE_2_0 = "Apache-2.0"
    const val BSD_3_CLAUSE = "BSD-3-Clause"

    val openSourceNotices: List<FerventioOpenSourceNotice> = listOf(
        FerventioOpenSourceNotice(
            id = "compose-multiplatform",
            name = "Compose Multiplatform",
            version = "1.11.1",
            licenseId = APACHE_2_0,
            projectUrl = "https://github.com/JetBrains/compose-multiplatform",
        ),
        FerventioOpenSourceNotice(
            id = "kotlin",
            name = "Kotlin Standard Library",
            version = "2.4.10",
            licenseId = APACHE_2_0,
            projectUrl = "https://github.com/JetBrains/kotlin",
        ),
        FerventioOpenSourceNotice(
            id = "coroutines",
            name = "kotlinx.coroutines",
            version = "1.11.0",
            licenseId = APACHE_2_0,
            projectUrl = "https://github.com/Kotlin/kotlinx.coroutines",
        ),
        FerventioOpenSourceNotice(
            id = "serialization",
            name = "kotlinx.serialization",
            version = "1.11.0",
            licenseId = APACHE_2_0,
            projectUrl = "https://github.com/Kotlin/kotlinx.serialization",
        ),
        FerventioOpenSourceNotice(
            id = "ktor",
            name = "Ktor Client",
            version = "3.5.2",
            licenseId = APACHE_2_0,
            projectUrl = "https://github.com/ktorio/ktor",
        ),
        FerventioOpenSourceNotice(
            id = "coil",
            name = "Coil",
            version = "3.5.0",
            licenseId = APACHE_2_0,
            projectUrl = "https://github.com/coil-kt/coil",
        ),
        FerventioOpenSourceNotice(
            id = "skiko",
            name = "Skiko",
            version = "Compose Multiplatform 1.11.1",
            licenseId = APACHE_2_0,
            projectUrl = "https://github.com/JetBrains/skiko",
        ),
        FerventioOpenSourceNotice(
            id = "skia",
            name = "Skia",
            version = "Compose / Skiko",
            licenseId = BSD_3_CLAUSE,
            projectUrl = "https://skia.org/",
        ),
    )

    val licenseTexts: List<FerventioLicenseText> = listOf(
        FerventioLicenseText(
            id = APACHE_2_0,
            name = "Apache License 2.0",
            text = APACHE_LICENSE_2_0_TEXT,
        ),
        FerventioLicenseText(
            id = BSD_3_CLAUSE,
            name = "BSD 3-Clause License",
            text = BSD_3_CLAUSE_TEXT,
        ),
    )
}
