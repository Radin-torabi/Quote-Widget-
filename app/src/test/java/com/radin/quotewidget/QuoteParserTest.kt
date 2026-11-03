package com.radin.quotewidget

import com.radin.quotewidget.QuoteParser.Split
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteParserTest {
    private fun p(s: String, split: Split = Split.LINE) = QuoteParser.parse(s, split)

    @Test fun trailingDashAuthors() {
        val q = p("""
            We suffer more often in imagination than in reality. — Seneca
            "Stay hungry, stay foolish." - Steve Jobs
            Simplicity is the ultimate sophistication ~ Leonardo da Vinci
            Life is short - enjoy it
        """.trimIndent())
        assertEquals(4, q.size)
        assertEquals(Quote("We suffer more often in imagination than in reality.", "Seneca"), q[0])
        assertEquals(Quote("Stay hungry, stay foolish.", "Steve Jobs"), q[1])
        assertEquals("Leonardo da Vinci", q[2].author)
        assertEquals(Quote("Life is short - enjoy it", null), q[3])
    }

    @Test fun authorOnNextLine() {
        val q = p("The obstacle is the way.\n— Marcus Aurelius\n\nAct well.\n- Epictetus\nNo author here")
        assertEquals(listOf(
            Quote("The obstacle is the way.", "Marcus Aurelius"),
            Quote("Act well.", "Epictetus"),
            Quote("No author here", null)), q)
    }

    @Test fun bulletsAndNumbers() {
        val q = p("- Be kind\n- Be brave\n1. First one\n2) Second one\n• Third")
        assertEquals(listOf("Be kind", "Be brave", "First one", "Second one", "Third"), q.map { it.text })
        assertEquals(null, q[1].author)
    }

    @Test fun blankLineMode() {
        val q = p("Line one\nline two\n— Someone\n\nAnother quote", Split.BLANK_LINE)
        assertEquals(listOf(Quote("Line one\nline two", "Someone"), Quote("Another quote", null)), q)
    }
}
