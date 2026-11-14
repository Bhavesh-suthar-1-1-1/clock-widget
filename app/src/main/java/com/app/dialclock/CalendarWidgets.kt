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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Calendar widget — 2 designs, picked in the app (More widgets section).
 * 0 Month grid with today highlighted · 1 Big date + week strip.
 * Updates at midnight (alarm) + on date / time / timezone change.
 */
object CalendarWidgets {
    const val COUNT = 2
    val NAMES = listOf("Month grid", "Big date")

    fun render(ctx: Context, design: Int, w: Int, h: Int, time: Calendar = Calendar.getInstance()): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF101014.toInt() }
        val corner = minOf(w, h) * 0.10f
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), corner, corner, bg)
        val gold = 0xFFC9A86A.toInt()
        val white = Color.WHITE
        val grey = Color.argb(170, 255, 255, 255)
        val dim = Color.argb(80, 255, 255, 255)
        val pad = w * 0.07f
        when (design) {
            0 -> {
                val month = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(time.time).uppercase(Locale.ENGLISH)
                val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = white; textSize = h * 0.075f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                c.drawText(month, pad, h * 0.13f, title)
                // Weekday header (Monday first).
                val days = arrayOf("M", "T", "W", "T", "F", "S", "S")
                val cellW = (w - pad * 2) / 7f
                val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = grey; textSize = h * 0.055f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val gridTop = h * 0.20f
                for (i in 0 until 7) {
                    c.drawText(days[i], pad + cellW * (i + 0.5f), gridTop, head)
                }
                // Month grid.
                val first = (time.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                val today = time.get(Calendar.DAY_OF_MONTH)
                val monthLen = time.getActualMaximum(Calendar.DAY_OF_MONTH)
                // Monday-first offset: Sunday=1 .. Saturday=7 in Calendar.
                val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
                val cellH = (h - gridTop - h * 0.05f) / 6f
                val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = white; textSize = cellH * 0.42f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val numDim = Paint(num).apply { color = dim }
                val todayBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }
                val todayTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK; textSize = cellH * 0.42f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                for (day in 1..monthLen) {
                    val pos = offset + day - 1
                    val col = pos % 7
                    val row = pos / 7
                    val cx = pad + cellW * (col + 0.5f)
                    val cy = gridTop + h * 0.045f + cellH * (row + 0.5f)
                    if (day == today) {
                        c.drawCircle(cx, cy, cellH * 0.34f, todayBg)
                        c.drawText(day.toString(), cx, cy - (todayTxt.fontMetrics.ascent + todayTxt.fontMetrics.descent) / 2f, todayTxt)
                    } else {
                        c.drawText(day.toString(), cx, cy - (num.fontMetrics.ascent + num.fontMetrics.descent) / 2f, num)
                    }
                }
            }
            else -> {
                val dayNum = time.get(Calendar.DAY_OF_MONTH).toString()
                val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = white; textSize = h * 0.44f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                c.drawText(dayNum, pad, h * 0.52f, big)
                val weekday = SimpleDateFormat("EEEE", Locale.ENGLISH).format(time.time).uppercase(Locale.ENGLISH)
                val monthYear = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(time.time).uppercase(Locale.ENGLISH)
                val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = gold; textSize = h * 0.085f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                c.drawText(weekday, pad, h * 0.68f, sub)
                val sub2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = grey; textSize = h * 0.075f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                }
                c.drawText(monthYear, pad, h * 0.80f, sub2)
                // Week strip: the 7 days of this week, today in gold.
                val stripY = h * 0.94f
                val cal = time.clone() as Calendar
                val todayDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
                cal.add(Calendar.DAY_OF_MONTH, -todayDow)
                val cellW = (w - pad * 2) / 7f
                val strip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = grey; textSize = h * 0.06f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val stripToday = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = gold; textSize = h * 0.06f
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                for (i in 0 until 7) {
                    val d = cal.get(Calendar.DAY_OF_MONTH)
                    val cx = pad + cellW * (i + 0.5f)
                    c.drawText(d.toString(), cx, stripY, if (i == todayDow) stripToday else strip)
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
        }
        return bmp
    }
}

class CalendarWidgetProvider : AppWidgetProvider() {
    companion object {
        const val ACTION_MIDNIGHT = "com.app.dialclock.CALENDAR_MIDNIGHT"

        fun scheduleMidnight(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val next = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5)
            }.timeInMillis
            val pi = PendingIntent.getBroadcast(
                ctx, 3003, Intent(ctx, CalendarWidgetProvider::class.java).apply { action = ACTION_MIDNIGHT },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } catch (_: Exception) {
                am.set(AlarmManager.RTC_WAKEUP, next, pi)
            }
        }

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, CalendarWidgetProvider::class.java))
            updateAll(ctx, mgr, ids)
        }

        fun updateAll(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val design = DialPrefs.widgetDesign(ctx, DialPrefs.KEY_CALENDAR_DESIGN, CalendarWidgets.COUNT)
            val density = ctx.resources.displayMetrics.density
            for (id in ids) {
                val opts = mgr.getAppWidgetOptions(id)
                val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250)
                val w = (minW * density).toInt().coerceIn(200, 720)
                val h = (minH * density).toInt().coerceIn(200, 720)
                val bmp = CalendarWidgets.render(ctx, design, w, h)
                val views = DialWidgetProvider.remoteViews(ctx, bmp)
                mgr.updateAppWidget(id, views)
            }
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        updateAll(ctx, mgr, ids)
        scheduleMidnight(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MIDNIGHT, Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BOOT_COMPLETED -> {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, CalendarWidgetProvider::class.java))
                updateAll(ctx, mgr, ids)
                scheduleMidnight(ctx)
            }
            else -> super.onReceive(ctx, intent)
        }
    }

    override fun onEnabled(ctx: Context) = scheduleMidnight(ctx)
}
