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
import android.os.Build
import android.os.SystemClock

/**
 * Device widget — 4 designs, picked in the app (More widgets section).
 * 0 Name + Android · 1 Storage bar · 2 RAM bar · 3 Network + Battery + Uptime.
 * Never shows the device IP address. Updates every 15 minutes + on boot.
 */
object DeviceWidgets {
    const val COUNT = 4
    val NAMES = listOf("Name + Android", "Storage bar", "RAM bar", "Network + Battery")

    fun render(ctx: Context, design: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF101014.toInt() }
        val corner = minOf(w, h) * 0.12f
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), corner, corner, bg)
        val gold = 0xFFC9A86A.toInt()
        val white = Color.WHITE
        val grey = Color.argb(170, 255, 255, 255)
        val pad = w * 0.07f
        when (design) {
            0 -> {
                val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = white; textSize = h * 0.26f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                var ts = h * 0.26f
                while (name.measureText(WidgetData.deviceName()) > w - pad * 2 && ts > h * 0.10f) {
                    ts *= 0.92f; name.textSize = ts
                }
                c.drawText(WidgetData.deviceName(), pad, h * 0.48f, name)
                val ver = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = gold; textSize = h * 0.16f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                c.drawText(WidgetData.androidVersion(), pad, h * 0.78f, ver)
            }
            1 -> {
                val st = WidgetData.storage()
                drawBarWidget(c, w, h, pad, "Storage", "${WidgetData.gb(st.usedGb)} / ${WidgetData.gb(st.totalGb)} GB",
                    "${WidgetData.gb(st.freeGb)} GB free", st.fraction, gold, white, grey)
            }
            2 -> {
                val ram = WidgetData.ram(ctx)
                drawBarWidget(c, w, h, pad, "RAM", "${WidgetData.gb(ram.usedGb)} / ${WidgetData.gb(ram.totalGb)} GB",
                    "${(ram.fraction * 100).toInt()}% in use", ram.fraction, gold, white, grey)
            }
            else -> {
                val bat = WidgetData.battery(ctx)
                val rows = listOf(
                    "Network" to WidgetData.networkType(ctx),
                    "Battery" to "${bat.level}%${if (bat.charging) " · Charging" else ""}",
                    "Uptime" to WidgetData.uptimeText()
                )
                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = grey; textSize = h * 0.13f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = white; textSize = h * 0.16f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                var y = h * 0.30f
                for ((k, v) in rows) {
                    c.drawText(k, pad, y, label)
                    c.drawText(v, w - pad, y, value)
                    y += h * 0.26f
                }
            }
        }
        return bmp
    }

    private fun drawBarWidget(
        c: Canvas, w: Int, h: Int, pad: Float,
        title: String, main: String, sub: String, frac: Float,
        gold: Int, white: Int, grey: Int
    ) {
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = grey; textSize = h * 0.14f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        c.drawText(title.uppercase(), pad, h * 0.26f, titleP)
        val mainP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white; textSize = h * 0.24f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        c.drawText(main, pad, h * 0.56f, mainP)
        val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gold; textSize = h * 0.14f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        c.drawText(sub, pad, h * 0.82f, subP)
        val barTop = h * 0.64f
        val barH = h * 0.07f
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }
        c.drawRoundRect(RectF(pad, barTop, w - pad, barTop + barH), barH / 2, barH / 2, track)
        c.drawRoundRect(RectF(pad, barTop, pad + (w - pad * 2) * frac, barTop + barH), barH / 2, barH / 2, fill)
    }
}

class DeviceWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH = "com.app.dialclock.DEVICE_REFRESH"

        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                ctx, 3001, Intent(ctx, DeviceWidgetProvider::class.java).apply { action = ACTION_REFRESH },
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
                ctx, 3001, Intent(ctx, DeviceWidgetProvider::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, DeviceWidgetProvider::class.java))
            updateAll(ctx, mgr, ids)
        }

        fun updateAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val design = DialPrefs.widgetDesign(ctx, DialPrefs.KEY_DEVICE_DESIGN, DeviceWidgets.COUNT)
            val density = ctx.resources.displayMetrics.density
            for (id in ids) {
                val opts = mgr.getAppWidgetOptions(id)
                val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                val w = (minW * density).toInt().coerceIn(200, 720)
                val h = (minH * density).toInt().coerceIn(120, 720)
                val bmp = DeviceWidgets.render(ctx, design, w, h)
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
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, DeviceWidgetProvider::class.java))
                updateAll(ctx, mgr, ids)
                schedule(ctx)
            }
            else -> super.onReceive(ctx, intent)
        }
    }

    override fun onEnabled(ctx: Context) = schedule(ctx)
    override fun onDisabled(ctx: Context) = cancel(ctx)
}
