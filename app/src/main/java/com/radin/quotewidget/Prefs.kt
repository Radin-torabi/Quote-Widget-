package com.radin.quotewidget

import android.content.Context
import android.content.SharedPreferences

/** All user settings plus the rotation state, stored in SharedPreferences. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("quote_widget", Context.MODE_PRIVATE)

    var quotesRaw: String
        get() = sp.getString("quotes_raw", null) ?: DEFAULT_QUOTES
        set(v) = sp.edit().putString("quotes_raw", v).apply()

    var split: QuoteParser.Split
        get() = runCatching { QuoteParser.Split.valueOf(sp.getString("split", "LINE")!!) }
            .getOrDefault(QuoteParser.Split.LINE)
        set(v) = sp.edit().putString("split", v.name).apply()

    /** Minutes between quote changes; 0 = only when tapped. */
    var intervalMinutes: Int
        get() = sp.getInt("interval", 60)
        set(v) = sp.edit().putInt("interval", v).apply()

    var shuffle: Boolean
        get() = sp.getBoolean("shuffle", true)
        set(v) = sp.edit().putBoolean("shuffle", v).apply()

    var tapAction: String   // "next" | "open"
        get() = sp.getString("tap", "next")!!
        set(v) = sp.edit().putString("tap", v).apply()

    var theme: String       // dynamic | auto | dark | light | clear_white | clear_black
        get() = sp.getString("theme", "dynamic")!!
        set(v) = sp.edit().putString("theme", v).apply()

    var bgOpacity: Int      // 0..100
        get() = sp.getInt("opacity", 100)
        set(v) = sp.edit().putInt("opacity", v).apply()

    var font: String        // serif | serif_italic | sans | sans_light | condensed | mono
        get() = sp.getString("font", "serif_italic")!!
        set(v) = sp.edit().putString("font", v).apply()

    /** 0 = auto-fit to widget, otherwise a fixed size in sp. */
    var textSize: Int
        get() = sp.getInt("text_size", 0)
        set(v) = sp.edit().putInt("text_size", v).apply()

    var align: String       // center | start
        get() = sp.getString("align", "center")!!
        set(v) = sp.edit().putString("align", v).apply()

    var showAuthor: Boolean
        get() = sp.getBoolean("show_author", true)
        set(v) = sp.edit().putBoolean("show_author", v).apply()

    var quoteMarks: Boolean
        get() = sp.getBoolean("quote_marks", false)
        set(v) = sp.edit().putBoolean("quote_marks", v).apply()

    fun quotes(): List<Quote> = QuoteParser.parse(quotesRaw, split)

    // ---- rotation state ----

    /** The quote that should be on screen right now (null when the bank is empty). */
    fun current(): Quote? {
        val quotes = quotes()
        if (quotes.isEmpty()) return null
        val order = ensureOrder(quotes.size)
        val pos = sp.getInt("pos", 0).coerceIn(0, order.lastIndex)
        return quotes[order[pos]]
    }

    fun advance() {
        val size = quotes().size
        if (size == 0) return
        val order = ensureOrder(size)
        var pos = sp.getInt("pos", 0) + 1
        if (pos >= order.size) {
            // Start a new cycle, avoiding showing the same quote twice in a row.
            val last = order.lastOrNull()
            val next = newOrder(size).toMutableList()
            if (size > 1 && next.first() == last) next.add(next.removeAt(0))
            saveOrder(next)
            pos = 0
        }
        sp.edit().putInt("pos", pos).apply()
    }

    private fun ensureOrder(size: Int): List<Int> {
        val signature = "${quotesRaw.hashCode()}:${split}:$shuffle"
        val stored = sp.getString("order", "")!!
            .split(',').mapNotNull { it.toIntOrNull() }
        if (sp.getString("signature", null) == signature && stored.size == size) return stored
        // Quote bank (or order mode) changed: start a fresh cycle.
        val order = newOrder(size)
        sp.edit().putString("signature", signature).putInt("pos", 0).apply()
        saveOrder(order)
        return order
    }

    private fun newOrder(size: Int): List<Int> =
        if (shuffle) (0 until size).shuffled() else (0 until size).toList()

    private fun saveOrder(order: List<Int>) {
        sp.edit().putString("order", order.joinToString(",")).apply()
    }

    companion object {
        val DEFAULT_QUOTES = """
            We suffer more often in imagination than in reality. — Seneca
            The impediment to action advances action. What stands in the way becomes the way. — Marcus Aurelius
            Do what you can, with what you have, where you are. — Theodore Roosevelt
            The best way out is always through. — Robert Frost
            Well done is better than well said. — Benjamin Franklin
            It does not matter how slowly you go as long as you do not stop. — Confucius
            Knowing is not enough; we must apply. — Johann Wolfgang von Goethe
            What we achieve inwardly will change outer reality. — Plutarch
        """.trimIndent()
    }
}
