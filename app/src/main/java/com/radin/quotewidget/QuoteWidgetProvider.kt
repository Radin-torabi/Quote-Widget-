package com.radin.quotewidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews

class QuoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
        scheduleRotation(context)
    }

    override fun onEnabled(context: Context) = scheduleRotation(context)

    override fun onDisabled(context: Context) = cancelRotation(context)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEXT -> {
                Prefs(context).advance()
                updateAll(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_NEXT = "com.radin.quotewidget.NEXT"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuoteWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val views = buildViews(context)
            manager.updateAppWidget(ids, views)
        }

        /** Builds the widget UI from the current settings. Also used for the in-app preview. */
        fun buildViews(context: Context): RemoteViews {
            val prefs = Prefs(context)
            val theme = prefs.theme
            val layout = if (theme == "clear_white") R.layout.widget_quote_shadow else R.layout.widget_quote
            val v = RemoteViews(context.packageName, layout)

            val quote = prefs.current()
                ?: Quote("Open Quote Widget and paste your quotes.", null)

            // ---- text ----
            val body = if (prefs.quoteMarks) "“${quote.text}”" else quote.text
            val styled = styledText(body, prefs.font)
            val auto = prefs.textSize == 0
            val quoteId = if (auto) R.id.quote_auto else R.id.quote_fixed
            v.setViewVisibility(R.id.quote_auto, if (auto) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.quote_fixed, if (auto) View.GONE else View.VISIBLE)
            v.setTextViewText(quoteId, styled)
            if (!auto) v.setTextViewTextSize(R.id.quote_fixed, TypedValue.COMPLEX_UNIT_SP, prefs.textSize.toFloat())

            val showAuthor = prefs.showAuthor && !quote.author.isNullOrBlank()
            v.setViewVisibility(R.id.author, if (showAuthor) View.VISIBLE else View.GONE)
            if (showAuthor) {
                v.setTextViewText(R.id.author, styledText("— ${quote.author}", prefs.font.removeSuffix("_italic")))
                val authorSize = if (auto) 13f else (prefs.textSize * 0.72f).coerceAtLeast(10f)
                v.setTextViewTextSize(R.id.author, TypedValue.COMPLEX_UNIT_SP, authorSize)
            }

            val gravity = if (prefs.align == "start") Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            v.setInt(quoteId, "setGravity", gravity)
            v.setInt(R.id.author, "setGravity", if (prefs.align == "start") Gravity.START else Gravity.CENTER)

            // ---- colours ----
            applyColors(context, v, theme, quoteId)
            v.setInt(R.id.bg, "setImageAlpha", (prefs.bgOpacity * 255 / 100).coerceIn(0, 255))

            // ---- tap ----
            val tapIntent = if (prefs.tapAction == "open") {
                PendingIntent.getActivity(
                    context, 1,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else nextIntent(context)
            v.setOnClickPendingIntent(R.id.root, tapIntent)
            return v
        }

        private fun applyColors(context: Context, v: RemoteViews, theme: String, quoteId: Int) {
            val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            when (theme) {
                // Resource-based: re-resolved by the launcher when light/dark mode or wallpaper changes.
                "dynamic", "auto" -> {
                    val bg = if (theme == "dynamic") R.color.dyn_bg else R.color.auto_bg
                    val text = if (theme == "dynamic") R.color.dyn_text else R.color.auto_text
                    val author = if (theme == "dynamic") R.color.dyn_author else R.color.auto_author
                    if (modern) {
                        v.setColorStateList(R.id.bg, "setImageTintList", bg)
                        v.setColorStateList(quoteId, "setTextColor", text)
                        v.setColorStateList(R.id.author, "setTextColor", author)
                    } else {
                        v.setInt(R.id.bg, "setColorFilter", context.getColor(bg))
                        v.setTextColor(quoteId, context.getColor(text))
                        v.setTextColor(R.id.author, context.getColor(author))
                    }
                }
                else -> {
                    val (bg, text, author) = when (theme) {
                        "light" -> Triple(Color.WHITE, 0xFF1B1B1F.toInt(), 0xFF5E5E66.toInt())
                        "clear_white" -> Triple(Color.BLACK, Color.WHITE, 0xE6FFFFFF.toInt())
                        "clear_black" -> Triple(Color.WHITE, 0xFF111111.toInt(), 0xCC111111.toInt())
                        else /* dark */ -> Triple(0xFF151517.toInt(), 0xFFF2F2F5.toInt(), 0xFFB4B4BE.toInt())
                    }
                    if (modern) v.setColorStateList(R.id.bg, "setImageTintList",
                        android.content.res.ColorStateList.valueOf(bg))
                    else v.setInt(R.id.bg, "setColorFilter", bg)
                    v.setTextColor(quoteId, text)
                    v.setTextColor(R.id.author, author)
                }
            }
        }

        private fun styledText(text: String, font: String): CharSequence {
            val family = when (font) {
                "serif", "serif_italic" -> "serif"
                "sans_light" -> "sans-serif-light"
                "condensed" -> "sans-serif-condensed"
                "mono" -> "monospace"
                "medium" -> "sans-serif-medium"
                else -> "sans-serif"
            }
            val s = SpannableString(text)
            s.setSpan(TypefaceSpan(family), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (font.endsWith("_italic")) s.setSpan(StyleSpan(Typeface.ITALIC), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (font == "bold") s.setSpan(StyleSpan(Typeface.BOLD), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return s
        }

        private fun nextIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, QuoteWidgetProvider::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /**
         * Inexact, non-wakeup repeating alarm: battery friendly and needs no special permission.
         * If the phone is asleep the change simply happens the next time the screen turns on.
         */
        fun scheduleRotation(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = nextIntent(context)
            am.cancel(pi)
            val minutes = Prefs(context).intervalMinutes
            if (minutes <= 0) return
            val manager = AppWidgetManager.getInstance(context)
            val hasWidgets = manager.getAppWidgetIds(ComponentName(context, QuoteWidgetProvider::class.java)).isNotEmpty()
            if (!hasWidgets) return
            val interval = minutes * 60_000L
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + interval,
                interval,
                pi
            )
        }

        fun cancelRotation(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(nextIntent(context))
        }
    }
}
