package io.ferventio.app.domain

object ReplyThreadResolver {
    fun rootMessageId(message: ChatMessage): String =
        message.reply?.threadMessageId
            ?.takeIf(String::isNotBlank)
            ?: message.reply?.parentMessageId
                ?.takeIf(String::isNotBlank)
            ?: message.id

    fun resolve(
        target: ChatMessage,
        messages: List<ChatMessage>,
    ): List<ChatMessage> {
        val rootId = rootMessageId(target)
        val byId = messages.associateBy(ChatMessage::id)
        val selectedIds = linkedSetOf(rootId)
        var changed: Boolean
        do {
            changed = false
            messages.forEach { message ->
                val reply = message.reply ?: return@forEach
                val belongsToThread = reply.threadMessageId == rootId || reply.parentMessageId in selectedIds
                if (belongsToThread && selectedIds.add(message.id)) changed = true
            }
        } while (changed)

        if (target.id.isNotBlank()) selectedIds += target.id
        return selectedIds.asSequence()
            .mapNotNull(byId::get)
            .sortedWith(compareBy<ChatMessage> { it.timestampMillis }.thenBy(ChatMessage::id))
            .toList()
    }
}
