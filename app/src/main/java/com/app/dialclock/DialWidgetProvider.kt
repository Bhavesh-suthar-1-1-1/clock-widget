package com.app.dialclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import java.util.Calendar
import kotlin.math.min
import kotlin.math.sqrt

object TickScheduler {
    const val ACTION_TICK = "com.app.dialclock.ACTION_TICK"

    fun scheduleNextMinute(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, DialWidgetProvider::class.java).apply { action = ACTION_TICK }
        val pi = PendingIntent.getBroadcast(
            ctx, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Next wall-clock minute boundary
        val now = System.currentTimeMillis()
        val next = (now / 60_000L + 1) * 60_000L
        val triggerAt = SystemClock.elapsedRealtime() + (next - now)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        } catch (e: Exception) {
            try {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } catch (_: Exception) {
            }
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, DialWidgetProvider::class.java).apply { action = ACTION_TICK }
        val pi = PendingIntent.getBroadcast(
            ctx, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}

class DialWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        try {
            updateAll(ctx, mgr, ids)
        } finally {
            TickScheduler.scheduleNextMinute(ctx)
            ensureTickService(ctx)
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        // The user resized the widget: re-render at the new size.
        try {
            updateAll(ctx, mgr, intArrayOf(id))
        } finally {
            TickScheduler.scheduleNextMinute(ctx)
            ensureTickService(ctx)
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            TickScheduler.ACTION_TICK,
            android.content.Intent.ACTION_BOOT_COMPLETED,
            android.content.Intent.ACTION_TIME_CHANGED,
            android.content.Intent.ACTION_TIMEZONE_CHANGED -> {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(
                    android.content.ComponentName(ctx, DialWidgetProvider::class.java)
                )
                // The next tick is scheduled in a finally block: one bad
                // render must never kill the whole update chain (the v2.1
                // freeze — widget stuck at 10:20 — was exactly that).
                try {
                    updateAll(ctx, mgr, ids)
                } finally {
                    TickScheduler.scheduleNextMinute(ctx)
                    ensureTickService(ctx)
                }
            }
            else -> super.onReceive(ctx, intent)
        }
    }

    override fun onEnabled(ctx: Context) {
        TickScheduler.scheduleNextMinute(ctx)
        ensureTickService(ctx)
    }

    override fun onDisabled(ctx: Context) {
        TickScheduler.cancel(ctx)
        SecondTickService.stop(ctx)
    }

    companion object {
        /**
         * Real pixel size of one widget box. Responsive ON: the box's real
         * width x height (max size when the launcher reports it, else min).
         * Responsive OFF: today's fixed square (min dimension). The bitmap
         * is capped so the long side is at most 720px and the total stays
         * binder-safe (<= 720 x 720 pixels).
         */
        fun widgetPx(ctx: Context, opts: Bundle, settings: DialSettings, density: Float): Pair<Int, Int> {
            val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
            val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
            var w: Int
            var h: Int
            if (settings.responsive) {
                val maxW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW)
                val maxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH)
                w = (maxOf(minW, maxW) * density).toInt()
                h = (maxOf(minH, maxH) * density).toInt()
            } else {
                val px = (minOf(minW, minH) * density).toInt()
                w = px
                h = px
            }
            // Cap the long side at 720px and the total at 720x720 pixels.
            val longSide = maxOf(w, h).coerceAtLeast(1)
            var scale = min(720f / longSide, 1f)
            val total = w * h
            if (total * scale * scale > 720f * 720f) {
                scale = min(scale, sqrt(720f * 720f / total.toFloat()))
            }
            w = (w * scale).toInt().coerceIn(120, 720)
            h = (h * scale).toInt().coerceIn(120, 720)
            return Pair(w, h)
        }

        /** One RemoteViews holding a bitmap, tap opens the app (never changes the dial). */
        fun remoteViews(ctx: Context, bmp: Bitmap): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_preview)
            views.setImageViewBitmap(R.id.dial_image, bmp)
            val openIntent = Intent(ctx, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                ctx, 1003, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.dial_image, pi)
            return views
        }

        fun updateAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) {
                SecondTickService.stop(ctx)
                return
            }
            val customs = DialPrefs.customs(ctx)
            val dial = Dials.byId(DialPrefs.selectedId(ctx), customs) ?: Dials.BUILT_IN.first()
            val time = Calendar.getInstance()
            val settings = DialPrefs.settings(ctx)
            val density = ctx.resources.displayMetrics.density
            for (id in ids) {
                try {
                    val opts = mgr.getAppWidgetOptions(id)
                    val (wPx, hPx) = widgetPx(ctx, opts, settings, density)
                    val bmp = if (settings.responsive) {
                        DialRenderer.renderRect(dial, wPx, hPx, time, settings)
                    } else {
                        DialRenderer.render(dial, minOf(wPx, hPx), time, settings)
                    }
                    mgr.updateAppWidget(id, remoteViews(ctx, bmp))
                } catch (_: Exception) {
                    // One bad widget must not abort the rest.
                }
            }
            // Backup minute tick: keep the tick service running whenever any
            // Dial widget exists, even when the live second hand is not
            // eligible (battery optimisation on, digital dial, setting off).
            // The service re-renders every minute on its own, so the clock
            // no longer depends on the alarm chain alone.
            ensureTickService(ctx)
            // Battery widget has no live broadcast of its own — refresh it
            // here too, so its % stays fresh while the clock ticks.
            BatteryWidgetProvider.refreshAll(ctx)
        }

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(ctx, DialWidgetProvider::class.java)
            )
            try {
                updateAll(ctx, mgr, ids)
            } catch (_: Exception) {
                // Never crash the caller (e.g. MainActivity.onResume).
            } finally {
                TickScheduler.scheduleNextMinute(ctx)
                ensureTickService(ctx)
            }
        }

        /** (Re)start the tick service whenever any Dial widget exists. */
        fun ensureTickService(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(
                    android.content.ComponentName(ctx, DialWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) SecondTickService.start(ctx)
            } catch (_: Exception) {
            }
        }
    }
}
