package io.ferventio.app.ui

internal const val PRIVACY_POLICY_EFFECTIVE_DATE = "01.08.2026"

internal data class PrivacyPolicySection(
    val id: String,
    val title: String,
    val paragraphs: List<String>,
)

internal data class OpenSourceNotice(
    val id: String,
    val name: String,
    val version: String,
    val licenseId: String,
    val description: String,
    val projectUrl: String,
    val playOnly: Boolean = false,
)

internal data class LicenseText(
    val id: String,
    val name: String,
    val text: String,
)

internal object FerventioLegalContent {
    const val APACHE_2_0 = "Apache-2.0"
    const val BSD_3_CLAUSE = "BSD-3-Clause"

    fun privacySections(
        localCrashReporting: Boolean,
        pushTransport: String,
        operatorName: String,
        privacyContact: String,
    ): List<PrivacyPolicySection> {
        val resolvedOperator = operatorName.trim().ifBlank { "Ferventio" }
        val resolvedContact = privacyContact.trim().ifBlank {
            "контакт, указанный на странице распространения этой сборки"
        }
        val crashParagraph = if (localCrashReporting) {
            "FOSS-сборка не отправляет crash reports автоматически. Redacted fatal и non-fatal отчёты хранятся только во внутреннем каталоге приложения: максимум 20 отчётов за 30 дней. Экспорт выполняется только по команде пользователя через системный picker."
        } else {
            "Production Play-сборка может передавать в Firebase Crashlytics sanitized сведения о сбоях, версию приложения, тип сборки, push transport и технические данные устройства/ОС. Debug, benchmark и FOSS-сборки автоматическую отправку Crashlytics не включают."
        }
        val pushParagraph = when (pushTransport) {
            "fcm" -> "Для push-уведомлений Play-сборка регистрирует FCM token и installation ID на настроенном Ferventio Server."
            "embedded_socket" -> "FOSS-сборка получает push через защищённое socket-соединение и не использует Firebase Cloud Messaging."
            else -> "Push transport зависит от варианта сборки и конфигурации сервера."
        }

        return listOf(
            PrivacyPolicySection(
                id = "scope",
                title = "1. Кто обрабатывает данные",
                paragraphs = listOf(
                    "Эта политика относится к Android-приложению Ferventio. Оператор сборки: $resolvedOperator. Контакт для privacy-запросов: $resolvedContact.",
                    "Ferventio не создаёт отдельный публичный профиль. При входе установка связывается с Twitch-аккаунтом через выбранный пользователем Ferventio Server.",
                ),
            ),
            PrivacyPolicySection(
                id = "local",
                title = "2. Данные на устройстве",
                paragraphs = listOf(
                    "Локально могут храниться список каналов, настройки, drafts, упоминания, правила фильтрации, позиции прокрутки, кэш изображений и история чата с авторами, badges, fragments и moderation state.",
                    "OAuth tokens не входят в пользовательский backup. Непрозрачная серверная сессия, device secret и текущий Twitch access token шифруются ключом Android Keystore; Twitch refresh token хранится только на настроенном сервере. Сохранённый access token позволяет продолжить прямую работу с Twitch при временной недоступности сервера до истечения или отзыва токена Twitch.",
                    crashParagraph,
                ),
            ),
            PrivacyPolicySection(
                id = "network",
                title = "3. Сетевые данные и назначение",
                paragraphs = listOf(
                    "Для чтения чата, отправки сообщений, модерации и metadata приложение обращается к Twitch IRC, EventSub и Helix. Twitch получает обычные сетевые данные запроса, включая IP-адрес.",
                    "Для emotes, badges и дополнительной публичной metadata приложение может обращаться к BTTV, FrankerFaceZ, 7TV, IVR.fi и их CDN. Эти провайдеры получают сетевые metadata запроса.",
                    pushParagraph,
                    "При включённой синхронизации настроек versioned backup передаётся на выбранный Ferventio Server. Сервер также обрабатывает installation ID, device-bound credentials, push registration, access-token leases и security/audit metadata.",
                ),
            ),
            PrivacyPolicySection(
                id = "sharing",
                title = "4. Передача третьим сторонам",
                paragraphs = listOf(
                    "Ferventio не продаёт персональные данные, не показывает рекламу и не использует Android Advertising ID.",
                    "Данные передаются только сервисам, необходимым для выбранных функций: Twitch, настроенному Ferventio Server, emote/metadata providers и, в Play release, Firebase Cloud Messaging/Crashlytics.",
                    "Файл, который пользователь вручную экспортирует через системный picker, передаётся выбранному пользователем приложению или storage provider; дальнейшая обработка регулируется правилами этого провайдера.",
                ),
            ),
            PrivacyPolicySection(
                id = "retention",
                title = "5. Хранение и защита",
                paragraphs = listOf(
                    "Срок хранения локальной истории задаётся пользователем. Кэш, история, настройки, локальные crash reports и данные приложения могут быть удалены средствами Ferventio или Android.",
                    "Передача к production Ferventio Server выполняется по HTTPS; release-сборка требует настроенные certificate pins. Tokens и credential-shaped значения редактируются в диагностических отчётах.",
                    "Срок хранения server-side settings snapshots, audit records и резервных копий определяется оператором выбранного Ferventio Server. Пользователь должен ознакомиться с политикой конкретного сервера.",
                ),
            ),
            PrivacyPolicySection(
                id = "controls",
                title = "6. Управление данными",
                paragraphs = listOf(
                    "В приложении доступны очистка истории и кэша, экспорт/импорт настроек, удаление локальных crash reports, выход, отзыв текущего устройства и отзыв всех серверных сессий Twitch-аккаунта.",
                    "Отзыв всех сессий удаляет серверные auth sessions, credentials, push registrations и pending deliveries. Локальные каналы, история и настройки остаются на каждом устройстве, пока пользователь не удалит их отдельно.",
                    "Для доступа, исправления или удаления данных, которые хранит конкретный Ferventio Server, следует обратиться к его оператору по privacy-контакту, указанному выше.",
                ),
            ),
            PrivacyPolicySection(
                id = "changes",
                title = "7. Изменения политики",
                paragraphs = listOf(
                    "Дата действующей редакции: $PRIVACY_POLICY_EFFECTIVE_DATE. Существенные изменения должны сопровождаться обновлением встроенного текста и опубликованной web-версии политики.",
                ),
            ),
        )
    }

    fun openSourceNotices(includePlayLibraries: Boolean): List<OpenSourceNotice> = buildList {
        add(
            OpenSourceNotice(
                id = "androidx",
                name = "AndroidX / Jetpack / Compose / Room",
                version = "Core 1.17.0 · Activity 1.13.0 · Lifecycle 2.11.0 · Compose 1.11.4 · Room 2.8.4",
                licenseId = APACHE_2_0,
                description = "Android application, UI, lifecycle, persistence and profile infrastructure.",
                projectUrl = "https://github.com/androidx/androidx",
            ),
        )
        add(
            OpenSourceNotice(
                id = "kotlin",
                name = "Kotlin Standard Library",
                version = "2.4.10",
                licenseId = APACHE_2_0,
                description = "Kotlin runtime used by the Android client.",
                projectUrl = "https://github.com/JetBrains/kotlin",
            ),
        )
        add(
            OpenSourceNotice(
                id = "coroutines",
                name = "kotlinx.coroutines",
                version = "1.10.2",
                licenseId = APACHE_2_0,
                description = "Structured concurrency and asynchronous streams.",
                projectUrl = "https://github.com/Kotlin/kotlinx.coroutines",
            ),
        )
        add(
            OpenSourceNotice(
                id = "serialization",
                name = "kotlinx.serialization",
                version = "1.9.0",
                licenseId = APACHE_2_0,
                description = "JSON encoding and decoding.",
                projectUrl = "https://github.com/Kotlin/kotlinx.serialization",
            ),
        )
        add(
            OpenSourceNotice(
                id = "ktor",
                name = "Ktor Client",
                version = "3.4.3",
                licenseId = APACHE_2_0,
                description = "HTTP and WebSocket client infrastructure.",
                projectUrl = "https://github.com/ktorio/ktor",
            ),
        )
        add(
            OpenSourceNotice(
                id = "okhttp",
                name = "OkHttp / Okio",
                version = "resolved transitively",
                licenseId = APACHE_2_0,
                description = "HTTP transport and buffered I/O used by Ktor and Coil.",
                projectUrl = "https://github.com/square/okhttp",
            ),
        )
        add(
            OpenSourceNotice(
                id = "coil",
                name = "Coil",
                version = "3.5.0",
                licenseId = APACHE_2_0,
                description = "Image loading for avatars, badges and emotes.",
                projectUrl = "https://github.com/coil-kt/coil",
            ),
        )
        add(
            OpenSourceNotice(
                id = "annotations",
                name = "javax.inject / JetBrains annotations",
                version = "resolved transitively",
                licenseId = APACHE_2_0,
                description = "Small runtime and metadata annotations used by the dependency graph.",
                projectUrl = "https://github.com/JetBrains/java-annotations",
            ),
        )
        if (includePlayLibraries) {
            add(
                OpenSourceNotice(
                    id = "firebase",
                    name = "Firebase Android SDK",
                    version = "Messaging 25.1.1 · Crashlytics 20.0.5",
                    licenseId = APACHE_2_0,
                    description = "Play-only push delivery and crash reporting.",
                    projectUrl = "https://github.com/firebase/firebase-android-sdk",
                    playOnly = true,
                ),
            )
            add(
                OpenSourceNotice(
                    id = "protobuf",
                    name = "Protocol Buffers Lite",
                    version = "resolved transitively by Play libraries",
                    licenseId = BSD_3_CLAUSE,
                    description = "Compact message serialization used by Firebase dependencies.",
                    projectUrl = "https://github.com/protocolbuffers/protobuf",
                    playOnly = true,
                ),
            )
        }
    }

    val licenseTexts: List<LicenseText> = listOf(
        LicenseText(
            id = APACHE_2_0,
            name = "Apache License 2.0",
            text = APACHE_LICENSE_2_0_TEXT,
        ),
        LicenseText(
            id = BSD_3_CLAUSE,
            name = "BSD 3-Clause License",
            text = BSD_3_CLAUSE_TEXT,
        ),
    )
}
