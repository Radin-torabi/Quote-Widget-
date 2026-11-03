package com.radin.quotewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var input: EditText
    private lateinit var count: TextView
    private lateinit var preview: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var loading = true

    private val saveQuotes = Runnable {
        prefs.quotesRaw = input.text.toString()
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        // Opened from "add widget" or long-press → Settings on the widget.
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            // If the user backs out, the widget is still added with the current settings.
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }

        input = findViewById(R.id.quotes_input)
        count = findViewById(R.id.quote_count)
        preview = findViewById(R.id.preview_container)

        input.setText(prefs.quotesRaw)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (loading) return
                handler.removeCallbacks(saveQuotes)
                handler.postDelayed(saveQuotes, 500)
                updateCount()
            }
        })

        findViewById<Button>(R.id.btn_paste).setOnClickListener { clipboardText()?.let { input.setText(it) } }
        findViewById<Button>(R.id.btn_append).setOnClickListener {
            clipboardText()?.let {
                val cur = input.text.toString().trimEnd()
                input.setText(if (cur.isEmpty()) it else "$cur\n$it")
            }
        }
        findViewById<Button>(R.id.btn_clear).setOnClickListener { input.setText("") }
        findViewById<Button>(R.id.btn_next).setOnClickListener {
            prefs.advance(); refresh()
        }

        spinner(R.id.sp_split,
            listOf("Each line is a quote" to QuoteParser.Split.LINE.name,
                   "Blank line between quotes (multi-line quotes)" to QuoteParser.Split.BLANK_LINE.name),
            prefs.split.name) { prefs.split = QuoteParser.Split.valueOf(it) }

        spinner(R.id.sp_interval,
            listOf("15 minutes" to "15", "30 minutes" to "30", "1 hour" to "60", "2 hours" to "120",
                   "3 hours" to "180", "6 hours" to "360", "12 hours" to "720", "Once a day" to "1440",
                   "Never — only when tapped" to "0"),
            prefs.intervalMinutes.toString()) {
            prefs.intervalMinutes = it.toInt()
            QuoteWidgetProvider.scheduleRotation(this)
        }

        spinner(R.id.sp_tap,
            listOf("Show the next quote" to "next", "Open these settings" to "open"),
            prefs.tapAction) { prefs.tapAction = it }

        spinner(R.id.sp_theme,
            listOf("Material You (matches wallpaper)" to "dynamic",
                   "Auto light / dark" to "auto",
                   "Dark" to "dark", "Light" to "light",
                   "Clear — white text" to "clear_white",
                   "Clear — black text" to "clear_black"),
            prefs.theme) { theme ->
            val wasClear = prefs.theme.startsWith("clear")
            prefs.theme = theme
            val opacity = findViewById<SeekBar>(R.id.sb_opacity)
            if (theme.startsWith("clear") && !wasClear) opacity.progress = 0
            else if (!theme.startsWith("clear") && wasClear) opacity.progress = 100
        }

        spinner(R.id.sp_font,
            listOf("Serif italic" to "serif_italic", "Serif" to "serif", "Sans" to "sans",
                   "Sans medium" to "medium", "Sans bold" to "bold", "Sans light" to "sans_light",
                   "Condensed" to "condensed", "Monospace" to "mono"),
            prefs.font) { prefs.font = it }

        spinner(R.id.sp_align,
            listOf("Centered" to "center", "Left" to "start"),
            prefs.align) { prefs.align = it }

        switch(R.id.sw_shuffle, prefs.shuffle) { prefs.shuffle = it }
        switch(R.id.sw_author, prefs.showAuthor) { prefs.showAuthor = it }
        switch(R.id.sw_marks, prefs.quoteMarks) { prefs.quoteMarks = it }

        val opacityLabel = findViewById<TextView>(R.id.lbl_opacity)
        seek(R.id.sb_opacity, prefs.bgOpacity) {
            opacityLabel.text = "Background opacity: $it%"
            prefs.bgOpacity = it
        }
        val sizeLabel = findViewById<TextView>(R.id.lbl_size)
        // Seek 0 = auto, 1..30 → 10..39 sp
        seek(R.id.sb_size, if (prefs.textSize == 0) 0 else prefs.textSize - 9) {
            val size = if (it == 0) 0 else it + 9
            sizeLabel.text = if (size == 0) "Text size: auto-fit to widget" else "Text size: $size sp"
            prefs.textSize = size
        }

        findViewById<Button>(R.id.btn_pin).setOnClickListener { pinWidget() }
        findViewById<Button>(R.id.btn_done).apply {
            text = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) "Done" else "Save"
            setOnClickListener {
                handler.removeCallbacks(saveQuotes)
                prefs.quotesRaw = input.text.toString()
                refresh()
                QuoteWidgetProvider.scheduleRotation(this@MainActivity)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) finish()
                else Toast.makeText(this@MainActivity, "Widget updated", Toast.LENGTH_SHORT).show()
            }
        }

        loading = false
        updateCount()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(saveQuotes)
        prefs.quotesRaw = input.text.toString()
        QuoteWidgetProvider.updateAll(this)
    }

    /** Re-render the preview and every placed widget. */
    private fun refresh() {
        if (loading) return
        val views = QuoteWidgetProvider.buildViews(this)
        preview.removeAllViews()
        val inflated = views.apply(this, preview)
        inflated.setOnClickListener { prefs.advance(); refresh() }   // tap preview = next
        preview.addView(inflated)
        QuoteWidgetProvider.updateAll(this)
    }

    private fun updateCount() {
        val n = QuoteParser.parse(input.text.toString(), prefs.split).size
        val withAuthor = QuoteParser.parse(input.text.toString(), prefs.split).count { it.author != null }
        count.text = when (n) {
            0 -> "No quotes found yet"
            1 -> "1 quote found"
            else -> "$n quotes found · $withAuthor with an author"
        }
    }

    private fun clipboardText(): String? {
        val cm = getSystemService(ClipboardManager::class.java)
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return null
        }
        return text.trim()
    }

    private fun pinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, QuoteWidgetProvider::class.java), null, null)
        } else {
            Toast.makeText(this, "Long-press your home screen › Widgets › Quote Widget", Toast.LENGTH_LONG).show()
        }
    }

    // ---- small UI helpers ----

    private fun spinner(id: Int, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
        val sp = findViewById<Spinner>(id)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options.map { it.first })
        sp.setSelection(options.indexOfFirst { it.second == current }.coerceAtLeast(0), false)
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, rowId: Long) {
                if (loading) return
                onPick(options[pos].second)
                if (id == R.id.sp_split) updateCount()
                refresh()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun switch(id: Int, current: Boolean, onChange: (Boolean) -> Unit) {
        val sw = findViewById<Switch>(id)
        sw.isChecked = current
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked); refresh() }
    }

    private fun seek(id: Int, current: Int, onChange: (Int) -> Unit) {
        val sb = findViewById<SeekBar>(id)
        sb.progress = current
        onChange(current)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, value: Int, fromUser: Boolean) { onChange(value); refresh() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }
}
