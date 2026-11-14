package com.app.dialclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import kotlin.math.cos
import kotlin.math.sin

/**
 * Battery widget — 3 designs, picked in the app (More widgets section).
 * 0 Ring % · 1 Big % + charging state · 2 Fill bar.
 *
 * Update note: ACTION_BATTERY_CHANGED is a sticky broadcast that Android
 * only delivers to receivers registered at runtime, never to manifest
 * receivers — so this provider refreshes on a 15-minute alarm, on boot,
 * whenever the app is opened, and on the clock widget's minute tick
 * (BatteryWidgetProvider.refreshAll is called from there when battery
 * widget ids exist). That keeps the % fresh without a background service.
 */
object BatteryWidgets {
    const val COUNT = 3
    val NAMES = listOf("Ring %", "Big % + charging", "Fill bar")

    fun render(ctx: Context, design: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF101014.toInt() }
        val corner = minOf(w, h) * 0.12f
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), corner, corner, bg)
        val bat = WidgetData.battery(ctx)
        val gold = 0xFFC9A86A.toInt()
        val green = 0xFF43A047.toInt()
        val white = Color.WHITE
        val grey = Color.argb(170, 255, 255, 255)
        val pad = w * 0.08f
        when (design) {
            0 -> {
                // Ring with % in the middle.
                val cx = w / 2f
                val cy = h / 2f
                val r = minOf(w, h) * 0.36f
                val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(50, 255, 255, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = r * 0.16f
                    strokeCap = Paint.Cap.ROUND
                }
                c.drawCircle(cx, cy, r, track)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (bat.charging) green else gold
                    style = Paint.Style.STROKE
                    strokeWidth = r * 0.16f
                    strokeCap = Paint.Cap.ROUND
                }
                val sweep = 360f * bat.fraction
                val oval = RectF(cx - r, cy - r, cx + r, cy + r)
                if (sweep > 0) c.drawArc(oval, -90f, sweep, false, fill)
                val pct = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = white; textSize = r * 0.62f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                c.drawText("${bat.level}%", cx, cy - (pct.fontMetrics.ascent + pct.fontMetrics.descent) / 2f, pct)
                val state = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = grey; textSize = r * 0.24f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                c.drawText(if (bat.charging) "CHARGING" else "BATTERY", cx, cy + r * 0.62f, state)
            }
            1 -> {
                val pct = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = white; textSize = h * 0.42f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                c.drawText("${bat.level}%", pad, h * 0.52f, pct)
                val state = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (bat.charging) green else gold; textSize = h * 0.15f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                val stateText = when {
                    bat.charging && bat.minutesLeft > 0 -> "Charging · ${bat.minutesLeft} min left"
                    bat.charging -> "Charging"
                    else -> "On battery"
                }
                c.drawText(stateText, pad, h * 0.80f, state)
            }
            else -> {
                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = grey; textSize = h * 0.14f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                c.drawText("BATTERY", pad, h * 0.26f, label)
                val pct = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = white; textSize = h * 0.26f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                c.drawText("${bat.level}%${if (bat.charging) " · Charging" else ""}", pad, h * 0.56f, pct)
                val barTop = h * 0.68f
                val barH = h * 0.09f
                val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (bat.charging) green else gold }
                c.drawRoundRect(RectF(pad, barTop, w - pad, barTop + barH), barH / 2, barH / 2, track)
                c.drawRoundRect(RectF(pad, barTop, pad + (w - pad * 2) * bat.fraction, barTop + barH), barH / 2, barH / 2, fill)
            }
        }
        return bmp
    }
}

class BatteryWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH = "com.app.dialclock.BATTERY_REFRESH"

        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                ctx, 3002, Intent(ctx, BatteryWidgetProvider::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 15 * 60_000L,
                15 * 60_000L, pi
            )
        }

        fun cancel(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                ctx, 3002, Intent(ctx, BatteryWidgetProvider::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, BatteryWidgetProvider::class.java))
            updateAll(ctx, mgr, ids)
        }

        fun updateAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val design = DialPrefs.widgetDesign(ctx, DialPrefs.KEY_BATTERY_DESIGN, BatteryWidgets.COUNT)
            val density = ctx.resources.displayMetrics.density
            for (id in ids) {
                val opts = mgr.getAppWidgetOptions(id)
                val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
                val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
                val w = (minW * density).toInt().coerceIn(200, 720)
                val h = (minH * density).toInt().coerceIn(120, 720)
                val bmp = BatteryWidgets.render(ctx, design, w, h)
                val views = DialWidgetProvider.remoteViews(ctx, bmp)
                mgr.updateAppWidget(id, views)
            }
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        updateAll(ctx, mgr, ids)
        schedule(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH, Intent.ACTION_BOOT_COMPLETED -> {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, BatteryWidgetProvider::class.java))
                updateAll(ctx, mgr, ids)
                schedule(ctx)
            }
            else -> super.onReceive(ctx, intent)
        }
    }

    override fun onEnabled(ctx: Context) = schedule(ctx)
    override fun onDisabled(ctx: Context) = cancel(ctx)
}
