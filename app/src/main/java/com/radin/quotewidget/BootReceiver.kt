package com.radin.quotewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms are cleared on reboot and app update, so re-arm the rotation. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        QuoteWidgetProvider.scheduleRotation(context)
        QuoteWidgetProvider.updateAll(context)
    }
}
