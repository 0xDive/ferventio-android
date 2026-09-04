package io.ferventio.shared.workspace

import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import io.ferventio.shared.settings.SharedMessageRulesStateHolder
import io.ferventio.shared.settings.SharedSavedFiltersStateHolder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceSettingsBackupLocalApplyCoordinatorTest {
    @Test
    fun freshMetadataWinsCachedFallbackAndUnresolvedLoginsStayExplicit() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/helix/users" -> respond(
                    ByteReadChannel(
                        """{"data":[{"id":"1","login":"alpha","display_name":"Alpha fresh"}]}""",
                    ),
                    HttpStatusCode.OK,
                )
                "/helix/moderation/channels" -> respond(
                    ByteReadChannel("""{"data":[{"broadcaster_id":"2"}]}"""),
                    HttpStatusCode.OK,
                )
                else -> error("Unexpected Twitch request ${request.url}")
            }
        }
        val preparedBase = WorkspaceSettingsBackupImportPreparation.prepare(
            workspaceSettingsBackupTestPayload(themeMode = "LIGHT"),
        )
        val prepared = preparedBase.copy(
            channels = preparedBase.channels.copy(
                logins = listOf("alpha", "beta", "gamma"),
                selectedLogin = "gamma",
            ),
        )
        val state = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(
                    ChatChannel(id = "2", login = "beta", displayName = "Beta cached"),
                ),
                moderatorChannelIds = setOf("2"),
            ),
        ).apply { markLoadReady(9L) }
        val settingsState = SharedAppSettingsStateHolder().apply {
            restore(SharedAppPreferences(themeMode = AppThemeMode.DARK), 12L)
        }
        val rulesState = SharedMessageRulesStateHolder()
        val filtersState = SharedSavedFiltersStateHolder()
        val coordinator = WorkspaceSettingsBackupLocalApplyCoordinator(
            TwitchChannelDirectoryClient(HttpClient(engine) { expectSuccess = false }),
        )

        val outcome = coordinator.apply(
            prepared = prepared,
            authentication = authentication(),
            state = state,
            settingsState = settingsState,
            rulesState = rulesState,
            filtersState = filtersState,
        )

        assertEquals(listOf("1", "2"), state.channelIds)
        assertEquals("Alpha fresh", state.channels[0].displayName)
        assertEquals("Beta cached", state.channels[1].displayName)
        assertEquals("1", state.selectedChannelId)
        assertEquals(listOf("2"), state.pinnedChannelIds)
        assertEquals(setOf("2"), state.moderatorChannelIds)
        assertEquals(12L, state.settingsRevision)
        assertEquals(12L, settingsState.syncRevision)
        assertEquals(AppThemeMode.LIGHT, settingsState.preferences.themeMode)
        assertEquals(prepared.messageRules, rulesState.snapshot)
        assertEquals(prepared.savedFilters, filtersState.snapshot)
        assertEquals(prepared.workspaceLayout, state.workspaceLayout)
        assertEquals(2, outcome.resolvedChannelCount)
        assertEquals(listOf("gamma"), outcome.unresolvedLogins)
    }

    @Test
    fun directoryFailureKeepsCachedChannelsAndReportsMissingImportedLogins() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/helix/users", "/helix/moderation/channels" -> respond(
                    ByteReadChannel("temporary Twitch failure"),
                    HttpStatusCode.ServiceUnavailable,
                )
                else -> error("Unexpected Twitch request ${request.url}")
            }
        }
        val prepared = WorkspaceSettingsBackupImportPreparation.prepare(
            workspaceSettingsBackupTestPayload(themeMode = "LIGHT"),
        )
        val state = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(
                    ChatChannel(id = "2", login = "beta", displayName = "Beta cached"),
                ),
                moderatorChannelIds = setOf("2"),
            ),
        ).apply { markLoadReady(6L) }
        val settingsState = SharedAppSettingsStateHolder().apply {
            restore(SharedAppPreferences(), 6L)
        }
        val coordinator = WorkspaceSettingsBackupLocalApplyCoordinator(
            TwitchChannelDirectoryClient(HttpClient(engine) { expectSuccess = false }),
        )

        val outcome = coordinator.apply(
            prepared = prepared,
            authentication = authentication(),
            state = state,
            settingsState = settingsState,
            rulesState = SharedMessageRulesStateHolder(),
            filtersState = SharedSavedFiltersStateHolder(),
        )

        assertEquals(listOf("2"), state.channelIds)
        assertEquals("2", state.selectedChannelId)
        assertEquals(setOf("2"), state.moderatorChannelIds)
        assertEquals(6L, state.settingsRevision)
        assertEquals(1, outcome.resolvedChannelCount)
        assertEquals(listOf("alpha"), outcome.unresolvedLogins)
    }

    private fun authentication() = StoredAuthentication(
        backendCredential = BackendSessionCredential(
            serverUrl = "https://example.test",
            token = "backend-session",
            expiresAtEpochMillis = 4_600_000L,
        ),
        accessLease = TwitchAccessLease(
            accessToken = "access-token",
            leaseExpiresAtEpochMillis = 1_300_000L,
            twitchExpiresAtEpochMillis = 8_200_000L,
            twitchValidatedAtEpochMillis = 1_000_000L,
            backendSessionExpiresAtEpochMillis = 4_600_000L,
            session = TwitchSession(
                clientId = "client-id",
                userId = "viewer-id",
                login = "viewer",
                scopes = setOf("chat:read"),
                expiresInSeconds = 7_200L,
            ),
        ),
    )
}
