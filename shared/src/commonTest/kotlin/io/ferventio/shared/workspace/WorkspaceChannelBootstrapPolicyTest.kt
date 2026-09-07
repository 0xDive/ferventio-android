package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspaceChannelBootstrapPolicyTest {
    private val cachedAlpha = ChatChannel(
        id = "1",
        login = "alpha",
        displayName = "Alpha cached",
    )
    private val cachedBeta = ChatChannel(
        id = "2",
        login = "beta",
        displayName = "Beta cached",
    )
    private val refreshedAlpha = cachedAlpha.copy(displayName = "Alpha live")
    private val refreshedGamma = ChatChannel(
        id = "3",
        login = "gamma",
        displayName = "Gamma live",
    )

    @Test
    fun refreshWinsWhileCacheFillsMissingSavedChannelsInPersistedOrder() {
        val result = WorkspaceChannelBootstrapPolicy.resolve(
            persistedLogins = listOf(" BETA ", "alpha", "gamma"),
            cachedChannels = listOf(cachedAlpha, cachedBeta),
            refreshedChannels = listOf(refreshedGamma, refreshedAlpha),
            selectedLogin = "ALPHA",
        )

        assertEquals(listOf("2", "1", "3"), result.channels.map { it.id })
        assertEquals("Beta cached", result.channels[0].displayName)
        assertEquals("Alpha live", result.channels[1].displayName)
        assertEquals("1", result.selectedChannelId)
    }

    @Test
    fun unavailableSavedChannelIsSkippedWithoutReorderingSurvivors() {
        val result = WorkspaceChannelBootstrapPolicy.resolve(
            persistedLogins = listOf("alpha", "missing", "beta"),
            cachedChannels = listOf(cachedAlpha, cachedBeta),
            refreshedChannels = emptyList(),
            selectedLogin = "missing",
        )

        assertEquals(listOf("1", "2"), result.channels.map { it.id })
        assertEquals("1", result.selectedChannelId)
    }

    @Test
    fun duplicateLoginsAndDuplicateResolvedIdsAreCollapsed() {
        val alias = ChatChannel(
            id = "1",
            login = "alias",
            displayName = "Alias",
        )
        val result = WorkspaceChannelBootstrapPolicy.resolve(
            persistedLogins = listOf("alpha", "ALPHA", "alias", "beta"),
            cachedChannels = listOf(cachedAlpha, cachedBeta, alias),
            refreshedChannels = emptyList(),
            selectedLogin = null,
        )

        assertEquals(listOf("1", "2"), result.channels.map { it.id })
        assertEquals("1", result.selectedChannelId)
    }

    @Test
    fun emptyPersistedWorkspaceDoesNotResurrectCachedChannels() {
        val result = WorkspaceChannelBootstrapPolicy.resolve(
            persistedLogins = emptyList(),
            cachedChannels = listOf(cachedAlpha, cachedBeta),
            refreshedChannels = listOf(refreshedAlpha),
            selectedLogin = "alpha",
        )

        assertEquals(emptyList(), result.channels)
        assertNull(result.selectedChannelId)
    }

    @Test
    fun loginNormalizationIsStableAndCaseInsensitive() {
        assertEquals(
            listOf("alpha", "beta"),
            WorkspaceChannelBootstrapPolicy.normalizeLogins(
                listOf(" Alpha ", "", "ALPHA", " beta ", "BETA"),
            ),
        )
    }
}
