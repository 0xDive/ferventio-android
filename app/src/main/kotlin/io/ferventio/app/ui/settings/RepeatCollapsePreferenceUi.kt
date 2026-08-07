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
import io.ferventio.app.data.FERVENTIO_REPEAT_COLLAPSE_KEY
import io.ferventio.app.data.FERVENTIO_SETTINGS_FILE_NAME

@Stable
internal class RepeatCollapsePreferenceState internal constructor(
    private val preferences: SharedPreferences,
) {
    private var mutableEnabled by mutableStateOf(
        preferences.getBoolean(FERVENTIO_REPEAT_COLLAPSE_KEY, true),
    )

    val enabled: Boolean
        get() = mutableEnabled

    fun setEnabled(value: Boolean) {
        if (value == mutableEnabled && preferences.getBoolean(FERVENTIO_REPEAT_COLLAPSE_KEY, true) == value) return
        preferences.edit().putBoolean(FERVENTIO_REPEAT_COLLAPSE_KEY, value).apply()
        mutableEnabled = value
    }

    internal fun refresh() {
        mutableEnabled = preferences.getBoolean(FERVENTIO_REPEAT_COLLAPSE_KEY, true)
    }
}

@Composable
internal fun rememberRepeatCollapsePreferenceState(): RepeatCollapsePreferenceState {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember(appContext) {
        appContext.getSharedPreferences(FERVENTIO_SETTINGS_FILE_NAME, Context.MODE_PRIVATE)
    }
    val state = remember(preferences) { RepeatCollapsePreferenceState(preferences) }
    DisposableEffect(preferences, state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == FERVENTIO_REPEAT_COLLAPSE_KEY) state.refresh()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        state.refresh()
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
