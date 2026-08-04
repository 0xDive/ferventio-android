package io.ferventio.app.domain

/**
 * Defines the minimum successful EventSub setup needed to expose chat as connected.
 *
 * `channel.chat.message` is the mandatory read transport. Notice and moderation
 * subscriptions are optional enrichments and must never keep a readable chat in the
 * `CREATING_SUBSCRIPTIONS` state.
 */
object EventSubBootstrapPolicy {
    fun acceptPrimaryChat(primaryError: Throwable?): Boolean = primaryError == null

    fun connectedDetail(channelLogin: String, noticeReady: Boolean): String =
        if (noticeReady) {
            "Чат и системные события #$channelLogin подключены; остальные подписки настраиваются…"
        } else {
            "Чат #$channelLogin подключён; системные события и остальные подписки настраиваются…"
        }
}
