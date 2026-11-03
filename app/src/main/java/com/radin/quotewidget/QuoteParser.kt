package com.radin.quotewidget

data class Quote(val text: String, val author: String?)

/**
 * Turns a pasted block of text into quotes. Understands:
 *   Quote text — Author          (em dash, en dash, ~, or " - " after closing punctuation)
 *   "Quote text" - Author
 *   Quote text                    followed by a line   — Author
 *   bulleted / numbered lists     (- quote, • quote, 1. quote, 1) quote)
 */
object QuoteParser {

    enum class Split { LINE, BLANK_LINE }

    private val bullet = Regex("""^\s*(?:[•*·▪►]|\d{1,4}[.)]|-(?=\s))\s*""")
    // Line that is only an attribution, e.g. "— Seneca", "~ Rumi", "-- Lao Tzu"
    private val authorLine = Regex("""^\s*(?:[—–~―]|--)\s*(.+?)\s*$""")
    private val shortHyphenAuthor = Regex("""^\s*-\s+(\S.{0,60})$""")
    // Attribution at the end of a line
    private val trailingStrong = Regex("""^(.*\S)\s*(?:[—–―~]|\s--)\s*([^—–―~]{1,80})$""")
    private val trailingHyphen = Regex("""^(.*[.!?…"”'’)])\s+-\s+([^-]{1,80})$""")

    private const val QUOTE_CHARS = "\"“”„«»'‘’"

    fun parse(raw: String, split: Split): List<Quote> {
        val result = mutableListOf<Quote>()
        val pending = StringBuilder()
        var pendingAuthor: String? = null
        var inHyphenList = false   // "- quote" bullets: don't mistake short ones for authors

        fun flush() {
            val text = pending.toString().trim()
            if (text.isNotEmpty()) result += build(text, pendingAuthor)
            pending.clear()
            pendingAuthor = null
        }

        for (rawLine in raw.replace("\r", "").lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) { flush(); continue }

            // Attribution on its own line attaches to the quote just above it.
            val authorMatch = authorLine.matchEntire(line)
                ?: shortHyphenAuthor.matchEntire(line)
                    ?.takeIf { !inHyphenList && it.groupValues[1].split(" ").size <= 5 }
            val previousHasNoAuthor = if (pending.isNotEmpty()) pendingAuthor == null
                else result.lastOrNull()?.author == null
            if (authorMatch != null && previousHasNoAuthor && (pending.isNotEmpty() || result.isNotEmpty())) {
                val name = authorMatch.groupValues[1].trim()
                if (pending.isNotEmpty()) {
                    pendingAuthor = name
                    flush()
                } else {
                    val last = result.removeAt(result.lastIndex)
                    result += last.copy(author = name)
                }
                continue
            }

            inHyphenList = line.startsWith("- ")
            val cleaned = line.replaceFirst(bullet, "")
            when (split) {
                Split.LINE -> { flush(); pending.append(cleaned); }
                Split.BLANK_LINE -> {
                    if (pending.isNotEmpty()) pending.append('\n')
                    pending.append(cleaned)
                }
            }
        }
        flush()
        return result
    }

    private fun build(text: String, explicitAuthor: String?): Quote {
        var body = text
        var author = explicitAuthor
        if (author == null) {
            val m = trailingStrong.matchEntire(body) ?: trailingHyphen.matchEntire(body)
            if (m != null && looksLikeAuthor(m.groupValues[2])) {
                body = m.groupValues[1]
                author = m.groupValues[2]
            }
        }
        body = body.trim().trimEnd(',', ' ')
        if (body.length >= 2 && body.first() in QUOTE_CHARS && body.last() in QUOTE_CHARS) {
            body = body.substring(1, body.length - 1).trim()
        }
        author = author?.trim()?.trim(*QUOTE_CHARS.toCharArray())?.trim()?.ifEmpty { null }
        return Quote(body, author)
    }

    private fun looksLikeAuthor(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || t.length > 80) return false
        // Authors are short; sentences that just happen to contain a dash are not.
        return t.split(Regex("\\s+")).size <= 8 && !t.endsWith("?")
    }
}
