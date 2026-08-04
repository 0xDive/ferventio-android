package io.ferventio.app.domain

object ChatNoticeTextFormatter {
    fun title(message: ChatMessage): String {
        val notice = message.notice ?: return "Системное событие"
        val tier = tierLabel(notice.subTier)
        return when (message.type) {
            ChatMessageType.SUBSCRIPTION -> listOfNotNull(
                "Подписка",
                tier,
                notice.durationMonths?.takeIf { it > 0 }?.let { monthsLabel(it) },
            ).joinToString(" · ")

            ChatMessageType.RESUBSCRIPTION -> listOfNotNull(
                "Повторная подписка",
                tier,
                notice.cumulativeMonths?.takeIf { it > 0 }?.let { "всего ${monthsLabel(it)}" },
            ).joinToString(" · ")

            ChatMessageType.GIFT_SUBSCRIPTION -> when {
                notice.giftTotal != null && notice.giftTotal > 1 -> listOfNotNull(
                    "Подарено ${notice.giftTotal} ${subscriptionWord(notice.giftTotal)}",
                    tier,
                ).joinToString(" · ")

                !notice.recipientUserName.isNullOrBlank() -> listOfNotNull(
                    "Подарочная подписка для ${notice.recipientUserName}",
                    tier,
                ).joinToString(" · ")

                else -> listOfNotNull("Подарочная подписка", tier).joinToString(" · ")
            }

            ChatMessageType.RAID -> listOfNotNull(
                notice.raidUserName?.takeIf(String::isNotBlank)?.let { "Рейд от $it" } ?: "Рейд",
                notice.raidViewerCount?.takeIf { it >= 0 }?.let { "$it ${viewerWord(it)}" },
            ).joinToString(" · ")

            ChatMessageType.ANNOUNCEMENT -> "Объявление"
            else -> notice.systemMessage?.takeIf(String::isNotBlank)
                ?: notice.type.replace('_', ' ').ifBlank { "Системное событие" }
        }
    }

    fun body(message: ChatMessage): String? {
        val notice = message.notice ?: return null
        return notice.systemMessage
            ?.takeIf(String::isNotBlank)
            ?: message.text.takeIf(String::isNotBlank)
    }

    fun userMessage(message: ChatMessage): String? {
        val notice = message.notice ?: return null
        return notice.userMessage
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { it == notice.systemMessage }
    }

    fun tierLabel(rawTier: String?): String? = when (rawTier) {
        "1000" -> "Tier 1"
        "2000" -> "Tier 2"
        "3000" -> "Tier 3"
        else -> rawTier?.takeIf(String::isNotBlank)
    }

    private fun monthsLabel(value: Int): String = "$value ${russianWord(value, "месяц", "месяца", "месяцев")}"

    private fun subscriptionWord(value: Int): String = russianWord(value, "подписка", "подписки", "подписок")

    private fun viewerWord(value: Int): String = russianWord(value, "зритель", "зрителя", "зрителей")

    private fun russianWord(value: Int, one: String, few: String, many: String): String {
        val mod100 = value % 100
        val mod10 = value % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }
}
