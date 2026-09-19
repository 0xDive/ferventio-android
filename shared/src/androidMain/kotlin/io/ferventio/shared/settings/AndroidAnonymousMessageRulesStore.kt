package io.ferventio.shared.settings

import android.content.Context

/** Reads the same message-rule preferences used by the legacy Android SettingsStore. */
class AndroidAnonymousMessageRulesStore(
    context: Context,
    fileName: String = FERVENTIO_ANDROID_SETTINGS_FILE_NAME,
) : AnonymousMessageRulesStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        fileName,
        Context.MODE_PRIVATE,
    )

    override fun load(): AnonymousMessageRulesRaw = AnonymousMessageRulesRaw(
        highlights = preferences.getString(ANONYMOUS_HIGHLIGHT_RULES_KEY, null),
        ignores = preferences.getString(ANONYMOUS_IGNORE_RULES_KEY, null),
    )

    override fun save(raw: AnonymousMessageRulesRaw) {
        check(
            preferences.edit()
                .putString(ANONYMOUS_HIGHLIGHT_RULES_KEY, raw.highlights)
                .putString(ANONYMOUS_IGNORE_RULES_KEY, raw.ignores)
                .commit(),
        ) { "Failed to persist anonymous message rules" }
    }
}
