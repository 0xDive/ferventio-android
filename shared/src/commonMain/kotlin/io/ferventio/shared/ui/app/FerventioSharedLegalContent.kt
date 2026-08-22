package io.ferventio.shared.ui.app

internal data class FerventioOpenSourceNotice(
    val id: String,
    val name: String,
    val version: String,
    val licenseId: String,
    val description: String,
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
            description = "Shared UI, resources, and iOS Compose hosting infrastructure.",
            projectUrl = "https://github.com/JetBrains/compose-multiplatform",
        ),
        FerventioOpenSourceNotice(
            id = "kotlin",
            name = "Kotlin Standard Library",
            version = "2.4.10",
            licenseId = APACHE_2_0,
            description = "Kotlin runtime used by the shared Android and iOS client.",
            projectUrl = "https://github.com/JetBrains/kotlin",
        ),
        FerventioOpenSourceNotice(
            id = "coroutines",
            name = "kotlinx.coroutines",
            version = "1.11.0",
            licenseId = APACHE_2_0,
            description = "Structured concurrency, flows, and asynchronous runtime work.",
            projectUrl = "https://github.com/Kotlin/kotlinx.coroutines",
        ),
        FerventioOpenSourceNotice(
            id = "serialization",
            name = "kotlinx.serialization",
            version = "1.11.0",
            licenseId = APACHE_2_0,
            description = "JSON encoding and decoding for shared network and persistence contracts.",
            projectUrl = "https://github.com/Kotlin/kotlinx.serialization",
        ),
        FerventioOpenSourceNotice(
            id = "ktor",
            name = "Ktor Client",
            version = "3.5.2",
            licenseId = APACHE_2_0,
            description = "HTTP and WebSocket transport, including the Darwin engine on iOS.",
            projectUrl = "https://github.com/ktorio/ktor",
        ),
        FerventioOpenSourceNotice(
            id = "coil",
            name = "Coil",
            version = "3.5.0",
            licenseId = APACHE_2_0,
            description = "Multiplatform image loading for avatars, badges, and emotes.",
            projectUrl = "https://github.com/coil-kt/coil",
        ),
        FerventioOpenSourceNotice(
            id = "skiko",
            name = "Skiko",
            version = "bundled with Compose Multiplatform 1.11.1",
            licenseId = APACHE_2_0,
            description = "Kotlin Multiplatform bindings and rendering bridge used by Compose on iOS.",
            projectUrl = "https://github.com/JetBrains/skiko",
        ),
        FerventioOpenSourceNotice(
            id = "skia",
            name = "Skia",
            version = "bundled by the Compose/Skiko graphics runtime",
            licenseId = BSD_3_CLAUSE,
            description = "Native 2D graphics engine used by the Compose rendering stack.",
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
