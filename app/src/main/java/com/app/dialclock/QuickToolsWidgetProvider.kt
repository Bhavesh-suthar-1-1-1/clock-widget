package com.app.dialclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.RemoteViews

/**
 * Quick tools widget — one wide widget, 4 buttons:
 * Wi-Fi opens the system Wi-Fi panel (Settings.Panel.ACTION_WIFI);
 * Bluetooth opens the system Bluetooth settings screen (there is no
 * Settings.Panel Bluetooth panel, so ACTION_BLUETOOTH_SETTINGS is used),
 * Calculator opens the system calculator, Torch toggles the flashlight
 * (handled by QuickToolsReceiver; needs the Camera permission, granted
 * in the app — if it is missing, tapping Torch opens the app instead).
 */
class QuickToolsWidgetProvider : AppWidgetProvider() {
    companion object {
        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, QuickToolsWidgetProvider::class.java))
            updateAll(ctx, mgr, ids)
        }

        fun updateAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            for (id in ids) {
                val views = RemoteViews(ctx.packageName, R.layout.quicktools_widget)
                views.setOnClickPendingIntent(R.id.btn_wifi, panelIntent(ctx, 4001, Settings.Panel.ACTION_WIFI))
                views.setOnClickPendingIntent(R.id.btn_bluetooth, panelIntent(ctx, 4002, Settings.ACTION_BLUETOOTH_SETTINGS))
                views.setOnClickPendingIntent(R.id.btn_calc, calcIntent(ctx))
                views.setOnClickPendingIntent(
                    R.id.btn_torch,
                    PendingIntent.getBroadcast(
                        ctx, 4004, Intent(ctx, QuickToolsReceiver::class.java).apply { action = QuickToolsReceiver.ACTION_TOGGLE_TORCH },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                views.setTextViewText(
                    R.id.btn_torch,
                    if (DialPrefs.torchOn(ctx)) "Torch ●" else "Torch"
                )
                mgr.updateAppWidget(id, views)
            }
        }

        private fun panelIntent(ctx: Context, code: Int, panel: String): PendingIntent {
            val i = Intent(panel).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun calcIntent(ctx: Context): PendingIntent {
            val i = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALCULATOR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(ctx, 4003, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        updateAll(ctx, mgr, ids)
    }
}
