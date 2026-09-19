package io.ferventio.shared.settings

import android.content.Context

/**
 * Android persistence for device-local UI preferences.
 *
 * The file name and keys intentionally match the legacy Android UI so moving the Android
 * surface to shared Compose keeps existing quick-moderation choices without migration.
 */
class AndroidSharedLocalUiPreferencesStore(
    context: Context,
    fileName: String = FERVENTIO_ANDROID_SETTINGS_FILE_NAME,
) : SharedLocalUiPreferencesStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        fileName,
        Context.MODE_PRIVATE,
    )

    override fun load(): SharedLocalUiPreferences = SharedLocalUiPreferences(
        showQuickBan = preferences.getBoolean(QUICK_BAN_BUTTON_KEY, false),
        showQuickDelete = preferences.getBoolean(QUICK_DELETE_BUTTON_KEY, false),
        confirmModerationActions = preferences.getBoolean(CONFIRM_MODERATION_ACTIONS_KEY, true),
        draftsByChannel = SharedComposerLocalStateCodec.decodeDrafts(
            preferences.getString(COMPOSER_DRAFTS_BY_CHANNEL_KEY, null),
        ),
        sentMessageHistoryByChannel = SharedComposerLocalStateCodec.decodeHistory(
            preferences.getString(SENT_MESSAGE_HISTORY_BY_CHANNEL_KEY, null),
        ),
    )

    override fun save(preferences: SharedLocalUiPreferences) {
        this.preferences.edit()
            .putBoolean(QUICK_BAN_BUTTON_KEY, preferences.showQuickBan)
            .putBoolean(QUICK_DELETE_BUTTON_KEY, preferences.showQuickDelete)
            .putBoolean(CONFIRM_MODERATION_ACTIONS_KEY, preferences.confirmModerationActions)
            .putString(
                COMPOSER_DRAFTS_BY_CHANNEL_KEY,
                SharedComposerLocalStateCodec.encodeDrafts(preferences.draftsByChannel),
            )
            .putString(
                SENT_MESSAGE_HISTORY_BY_CHANNEL_KEY,
                SharedComposerLocalStateCodec.encodeHistory(preferences.sentMessageHistoryByChannel),
            )
            .apply()
    }
}

const val FERVENTIO_ANDROID_SETTINGS_FILE_NAME = "ferventio_settings"

fun androidSharedLocalUiPreferencesStateHolder(
    context: Context,
): SharedLocalUiPreferencesStateHolder = SharedLocalUiPreferencesStateHolder(
    store = AndroidSharedLocalUiPreferencesStore(context),
)
