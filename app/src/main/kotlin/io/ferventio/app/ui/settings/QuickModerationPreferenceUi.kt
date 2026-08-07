package io.ferventio.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.ferventio.app.data.FERVENTIO_SETTINGS_FILE_NAME

internal const val QUICK_BAN_BUTTON_KEY = "quick_ban_button_enabled"
internal const val QUICK_DELETE_BUTTON_KEY = "quick_delete_button_enabled"

@Stable
internal class QuickModerationPreferenceState internal constructor(
    private val preferences: SharedPreferences,
) {
    private var mutableShowBan by mutableStateOf(preferences.getBoolean(QUICK_BAN_BUTTON_KEY, false))
    private var mutableShowDelete by mutableStateOf(preferences.getBoolean(QUICK_DELETE_BUTTON_KEY, false))

    val showBan: Boolean get() = mutableShowBan
    val showDelete: Boolean get() = mutableShowDelete

    fun setShowBan(value: Boolean) {
        preferences.edit().putBoolean(QUICK_BAN_BUTTON_KEY, value).apply()
        mutableShowBan = value
    }

    fun setShowDelete(value: Boolean) {
        preferences.edit().putBoolean(QUICK_DELETE_BUTTON_KEY, value).apply()
        mutableShowDelete = value
    }

    internal fun refresh() {
        mutableShowBan = preferences.getBoolean(QUICK_BAN_BUTTON_KEY, false)
        mutableShowDelete = preferences.getBoolean(QUICK_DELETE_BUTTON_KEY, false)
    }
}

@Composable
internal fun rememberQuickModerationPreferenceState(): QuickModerationPreferenceState {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember(appContext) {
        appContext.getSharedPreferences(FERVENTIO_SETTINGS_FILE_NAME, Context.MODE_PRIVATE)
    }
    val state = remember(preferences) { QuickModerationPreferenceState(preferences) }
    DisposableEffect(preferences, state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == QUICK_BAN_BUTTON_KEY || key == QUICK_DELETE_BUTTON_KEY) state.refresh()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        state.refresh()
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
