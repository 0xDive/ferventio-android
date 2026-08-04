package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatLinkParserTest {
    @Test
    fun findsHttpAndWwwLinksAndTrimsSentencePunctuation() {
        val links = ChatLinkParser.findAll(
            "docs: https://example.com/path?q=1, mirror www.example.org/test and bare example.net/help.",
        )

        assertEquals(
            listOf(
                "https://example.com/path?q=1",
                "https://www.example.org/test",
                "https://example.net/help",
            ),
            links.map(ChatLinkMatch::url),
        )
        assertEquals("https://example.com/path?q=1", links[0].let { link ->
            "docs: https://example.com/path?q=1, mirror www.example.org/test and bare example.net/help."
                .substring(link.start, link.endExclusive)
        })
    }

    @Test
    fun keepsOnlyUrlClickableWhenMessageStartsWithLink() {
        val source = "https://example.com/path this text must stay plain"
        val link = ChatLinkParser.findAll(source).single()

        assertEquals("https://example.com/path", source.substring(link.start, link.endExclusive))
        assertEquals("https://example.com/path", link.url)
    }

    @Test
    fun keepsBalancedClosingParenthesisInsideLink() {
        val source = "https://example.com/wiki/Test_(page)"
        assertEquals(source, ChatLinkParser.findAll(source).single().url)
    }

    @Test
    fun ignoresUnsupportedSchemes() {
        assertEquals(emptyList(), ChatLinkParser.findAll("javascript:alert(1) ftp://example.com"))
    }
}
