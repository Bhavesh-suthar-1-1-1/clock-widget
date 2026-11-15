package com.app.dialclock

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Auto wallpaper detection (v3.0) — replaces the Studio's manual
 * wallpaper swatch picker, which Bhavesh asked to remove.
 *
 * The app reads the user's REAL home-screen wallpaper with
 * WallpaperManager.getDrawable() — no permission is needed for the
 * system wallpaper — downscales it, and measures its average relative
 * luminance (the same sRGB -> linear math DialRenderer.luminance uses).
 * The result is cached in the SAME prefs key the renderer already reads
 * (KEY_BG_LUM), so the existing auto-contrast path (readableTextOn)
 * just works off the detected value: dark wallpaper -> light text,
 * light wallpaper -> dark text, with nothing for the user to pick.
 *
 * Sampling runs off the main thread (bitmap decode + downscale) and
 * only on real triggers — Studio open, widget placed/updated, and the
 * system WALLPAPER_CHANGED broadcast. The widget never re-samples on
 * its per-second / per-minute ticks: it just reads the cached value.
 *
 * If the wallpaper can't be read (live wallpaper returns a
 * non-bitmap drawable, or getDrawable() is null), the last stored
 * value is kept; with no stored value the dark default (0.12, the
 * v2.9 Leaves value) is used.
 */
object WallpaperSense {

    private const val TAG = "WallpaperSense"
    private const val DEFAULT_LUM = 0.12f // dark, his flower photo (v2.9 Leaves)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** One reading: the luminance the renderer should use, and whether the
     *  wallpaper could actually be read. preview is ALWAYS null since
     *  v3.1: the v3.0 preview bitmap (a hardware bitmap from
     *  WallpaperManager on his vivo, drawn via asImageBitmap and
     *  recycled from a DisposableEffect) crashed Studio on open.
     *  The preview now uses a plain colour at the detected luminance. */
    data class Reading(
        val lum: Float,
        val readable: Boolean,
        val preview: Bitmap? = null
    )

    /** Brightness word for the Studio status line. */
    fun brightnessWord(lum: Float): String = when {
        lum < 0.25f -> "dark"
        lum < 0.6f -> "medium"
        else -> "light"
    }

    /** Last cached luminance, or the dark default if nothing was ever read. */
    fun cachedLum(ctx: Context): Float =
        DialPrefs.prefs(ctx).getFloat(DialPrefs.KEY_BG_LUM, DEFAULT_LUM).coerceIn(0f, 1f)

    /** Synchronous sampling — call OFF the main thread. Never throws. */
    fun sample(ctx: Context): Reading {
        return try {
            val drawable = WallpaperManager.getInstance(ctx).drawable
            val bmp = (drawable as? BitmapDrawable)?.bitmap
            if (bmp == null || bmp.isRecycled || bmp.width < 4 || bmp.height < 4) {
                // Live wallpaper (or no wallpaper): keep the last reading.
                Reading(cachedLum(ctx), readable = false, preview = null)
            } else {
                // Small copy for the average: 64x36 is plenty for luminance.
                // Hardware bitmaps (common on newer phones) cannot be read
                // with getPixels — copy to a software bitmap first.
                val soft = if (bmp.config == Bitmap.Config.HARDWARE)
                    bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
                val small = Bitmap.createScaledBitmap(soft, 64, 36, true)
                if (soft !== bmp) soft.recycle()
                var total = 0.0
                val pixels = IntArray(64 * 36)
                small.getPixels(pixels, 0, 64, 0, 0, 64, 36)
                for (px in pixels) total += DialRenderer.luminance(px)
                val lum = (total / pixels.size).toFloat().coerceIn(0f, 1f)
                small.recycle()
                // v3.1: NO preview bitmap here. v3.0 made a 360px copy of
                // the wallpaper for the Studio preview background — on his
                // vivo WallpaperManager returns a hardware bitmap, and
                // drawing it via asImageBitmap() then recycling it from a
                // DisposableEffect crashed Studio on open. The preview now
                // uses a plain colour at this luminance instead.
                Reading(lum, readable = true, preview = null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read wallpaper", e)
            Reading(cachedLum(ctx), readable = false, preview = null)
        }
    }

    /** Sample off the main thread, cache the result, and call [onDone]
     *  on the main thread. If the luminance changed, the widget is
     *  re-rendered with it — otherwise nothing happens. */
    fun sampleAsync(ctx: Context, onDone: ((Reading) -> Unit)? = null) {
        val appCtx = ctx.applicationContext
        Thread {
            val reading = sample(appCtx)
            val stored = cachedLum(appCtx)
            val changed = kotlin.math.abs(reading.lum - stored) > 0.01f
            if (reading.readable || changed) {
                DialPrefs.prefs(appCtx).edit()
                    .putFloat(DialPrefs.KEY_BG_LUM, reading.lum)
                    .apply()
            }
            mainHandler.post {
                if (changed) DialWidgetProvider.refreshAll(appCtx)
                // v3.1: a bad callback must never crash the app.
                try {
                    onDone?.invoke(reading)
                } catch (e: Exception) {
                    Log.w(TAG, "WallpaperSense callback failed", e)
                }
            }
        }.start()
    }

    /** Re-sample when the system wallpaper changes, then re-render.
     *  The action is deprecated (API 33) but still works; on newer
     *  Android the manifest receiver is unreliable, so opening the app
     *  (MainActivity.onResume) is the guaranteed re-check. */
    @Suppress("DEPRECATION")
    class WallpaperChangedReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_WALLPAPER_CHANGED) {
                sampleAsync(ctx)
            }
        }
    }
}
