package io.ferventio.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

val LocalOpenUserProfileByLogin = staticCompositionLocalOf<(String, String) -> Unit> {
    { _, _ -> }
}

val LocalKnownPermanentlyBannedUserIds = staticCompositionLocalOf<Set<String>> {
    emptySet()
}
