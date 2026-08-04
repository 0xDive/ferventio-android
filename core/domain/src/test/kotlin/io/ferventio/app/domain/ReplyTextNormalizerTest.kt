package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyTextNormalizerTest {
    private val reply = ReplyContext(
        parentMessageId = "parent-message",
        parentUserLogin = "parent_user",
        parentUserName = "Parent_User",
    )

    @Test
    fun removesOnlyLeadingReplyMention() {
        val result = ReplyTextNormalizer.normalize(
            text = "@parent_user привет @friend",
            fragments = listOf(
                ChatFragment.Mention("@parent_user", "parent-id", "parent_user", "Parent_User"),
                ChatFragment.Text(" привет "),
                ChatFragment.Mention("@friend", "friend-id", "friend", "Friend"),
            ),
            reply = reply,
        )

        assertEquals("привет @friend", result.text)
        assertEquals("привет ", (result.fragments[0] as ChatFragment.Text).text)
        assertTrue(result.fragments[1] is ChatFragment.Mention)
        assertEquals("@friend", result.fragments[1].text)
    }

    @Test
    fun keepsSecondIntentionalMentionOfParent() {
        val result = ReplyTextNormalizer.normalizeText(
            text = "@parent_user @parent_user это уже настоящее упоминание",
            reply = reply,
        )

        assertEquals("@parent_user это уже настоящее упоминание", result)
    }

    @Test
    fun supportsCommaAndColonAfterGeneratedMention() {
        assertEquals(
            "привет",
            ReplyTextNormalizer.normalizeText("@Parent_User, привет", reply),
        )
        assertEquals(
            "привет",
            ReplyTextNormalizer.normalizeText("@parent_user: привет", reply),
        )
    }

    @Test
    fun doesNotRemoveAnotherUsersMention() {
        val text = "@another_user привет"
        assertEquals(text, ReplyTextNormalizer.normalizeText(text, reply))
    }

    @Test
    fun doesNotRemoveSimilarLongerLogin() {
        val text = "@parent_user_2 привет"
        assertEquals(text, ReplyTextNormalizer.normalizeText(text, reply))
    }

    @Test
    fun doesNothingWithoutReplyContext() {
        val text = "@parent_user обычное упоминание"
        assertEquals(text, ReplyTextNormalizer.normalizeText(text, null))
    }
}
