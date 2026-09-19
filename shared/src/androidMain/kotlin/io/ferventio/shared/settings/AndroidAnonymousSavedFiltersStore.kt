package io.ferventio.shared.settings

import android.content.Context

/** Reads the same saved-filter preference used by the legacy Android SettingsStore. */
class AndroidAnonymousSavedFiltersStore(
    context: Context,
    fileName: String = FERVENTIO_ANDROID_SETTINGS_FILE_NAME,
) : AnonymousSavedFiltersStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        fileName,
        Context.MODE_PRIVATE,
    )

    override fun load(): String? = preferences.getString(ANONYMOUS_SAVED_FILTERS_KEY, null)

    override fun save(raw: String) {
        check(
            preferences.edit()
                .putString(ANONYMOUS_SAVED_FILTERS_KEY, raw)
                .commit(),
        ) { "Failed to persist anonymous saved filters" }
    }
}
