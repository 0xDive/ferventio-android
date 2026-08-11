package io.ferventio.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalOpenUserProfileByLogin = staticCompositionLocalOf<(String, String) -> Unit> {
    { _, _ -> }
}

internal val LocalKnownPermanentlyBannedUserIds = staticCompositionLocalOf<Set<String>> {
    emptySet()
}
