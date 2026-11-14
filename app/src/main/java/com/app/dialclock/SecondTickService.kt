package com.app.dialclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * SecondTickService — the live second hand on analog widgets.
 *
 * A widget is a static picture; the only way its second hand can move is
 * for the app to re-draw it every second. Doing a FULL re-render every
 * second would be slow (case, grain, glass) and would flicker, because the
 * grain speckle is re-randomised on every render. So this service:
 *
 *   - renders the face ONCE per minute (DialRenderer.renderFace — no hands)
 *   - every second, copies that cached face and draws ONLY the hands on
 *     the copy (DialRenderer.drawHandsOnto — three lines + a centre cap),
 *     then pushes the bitmap to the widget.
 *
 * That per-second work is a bitmap copy + a few lines: a few milliseconds,
 * no lag. The service only runs while the screen is on, and stops itself
 * the moment the user picks a non-analog dial, turns the setting off, or
 * removes every widget.
 *
 * v2.2: the service ALSO runs as a backup minute tick whenever any Dial
 * widget exists, even when the second hand is not eligible (setting off,
 * battery optimisation on, digital/hybrid dial). In that mode it wakes
 * once a minute and does a full DialWidgetProvider.updateAll(), so the
 * clock can never freeze just because the alarm chain died.
 */
class SecondTickService : Service() {

    companion object {
        private const val CHANNEL_ID = "dial_live_seconds"
        private const val NOTIF_ID = 2001

        fun start(ctx: Context) {
            val i = Intent(ctx, SecondTickService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, SecondTickService::class.java))
        }

        /** True when the live tick is allowed to run right now. */
        fun eligible(ctx: Context): Boolean {
            val settings = DialPrefs.settings(ctx)
            if (!settings.secondHand) return false
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) return false
            val dial = Dials.byId(DialPrefs.selectedId(ctx), DialPrefs.customs(ctx)) ?: return false
            return DialRenderer.hasLiveHands(dial)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var screenOn = true
    private var lastMinute = -1
    private val faceCache = mutableMapOf<String, Bitmap>()

    private val tick = object : Runnable {
        override fun run() {
            try {
                doTick()
            } catch (_: Exception) {
                // One bad tick must never kill the schedule.
            } finally {
                scheduleNext()
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    handler.removeCallbacks(tick)
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    handler.removeCallbacks(tick)
                    handler.post(tick)
                }
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> {
                    faceCache.clear()
                    lastMinute = -1
                    handler.removeCallbacks(tick)
                    handler.post(tick)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, notification())
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleNext() {
        if (!screenOn) return
        val delay = if (eligible(this)) {
            // Live second hand: wake just after the next second boundary.
            1000L - (System.currentTimeMillis() % 1000L)
        } else {
            // Backup minute tick: wake just after the next minute boundary.
            // Cheap (one wake a minute) and independent of alarms, so the
            // widget keeps showing the right time even if the alarm chain
            // dies (exact alarms off, Doze, force-stop).
            60_000L - (System.currentTimeMillis() % 60_000L)
        }
        handler.postDelayed(tick, delay.coerceIn(50L, 60_000L))
    }

    private fun doTick() {
        if (!screenOn) return
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(ComponentName(this, DialWidgetProvider::class.java))
        if (ids.isEmpty()) {
            stopSelf()
            return
        }
        if (!eligible(this)) {
            // Backup minute tick: no live second hand (setting off, battery
            // optimisation on, or a non-analog dial) — just make sure the
            // widget re-renders every minute so the time can never freeze.
            val minute = Calendar.getInstance().get(Calendar.MINUTE)
            if (minute != lastMinute) {
                faceCache.clear()
                lastMinute = minute
                DialWidgetProvider.updateAll(this, mgr, ids)
            }
            return
        }
        val time = Calendar.getInstance()
        val settings = DialPrefs.settings(this)
        val dial = Dials.byId(DialPrefs.selectedId(this), DialPrefs.customs(this)) ?: return
        val density = resources.displayMetrics.density
        val minute = time.get(Calendar.MINUTE)
        if (minute != lastMinute) {
            faceCache.clear()
            lastMinute = minute
        }
        for (id in ids) {
            val opts = mgr.getAppWidgetOptions(id)
            val (wPx, hPx) = DialWidgetProvider.widgetPx(this, opts, settings, density)
            val side = minOf(wPx, hPx)
            val key = "${dial.id}|${settings.hashCode()}|${side}x$minute"
            var face = faceCache[key]
            if (face == null) {
                if (faceCache.size > 8) faceCache.clear()
                face = DialRenderer.renderFace(dial, side, time, settings)
                faceCache[key] = face
            }
            val frame = face.copy(Bitmap.Config.ARGB_8888, true)
            DialRenderer.drawHandsOnto(frame, dial, time, settings)
            val bmp = if (settings.responsive && wPx != hPx) {
                // Wide/tall widget: the square watch sits centred on a transparent rect.
                val full = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
                Canvas(full).drawBitmap(frame, (wPx - side) / 2f, (hPx - side) / 2f, null)
                full
            } else {
                frame
            }
            val views = DialWidgetProvider.remoteViews(this, bmp)
            try {
                mgr.updateAppWidget(id, views)
            } catch (_: Exception) {
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, "Dial widget updates", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keeps the Dial widgets showing the current time"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 2002, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle("Dial")
            .setContentText("Dial widget updates")
            .setContentIntent(open)
            .setOngoing(true)
            .setSound(null)
            .build()
    }
}
