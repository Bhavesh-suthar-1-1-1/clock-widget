package com.app.dialclock.vulkan

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.app.dialclock.DialPrefs
import com.app.dialclock.SlatsDial
import java.util.Calendar

/**
 * Slats preview rendered by Vulkan (Atmo's one-pass engine + the Slats
 * fragment shader) on a translucent SurfaceView, so the clock sits on the
 * frosted card exactly like the Canvas bitmap previews.
 *
 * A render thread drives it at ~30 fps: the ring angles come from
 * SlatsDial.spinAngles (video speed: minute ring 6 deg/s, hour ring
 * 0.5 deg/s) and the capsule always shows the real current HH MM.
 *
 * Any fatal Vulkan failure calls [onError] once; the caller then falls
 * back to the Canvas bitmap preview. The widget never uses this view —
 * widgets are bitmaps, and Canvas stays their renderer.
 */
class SlatsVulkanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    var onError: (() -> Unit)? = null

    @Volatile
    private var handle: Long = 0L

    @Volatile
    private var surfaceW: Int = 0

    @Volatile
    private var surfaceH: Int = 0

    @Volatile
    private var running: Boolean = false

    private var renderThread: Thread? = null
    private var errorReported: Boolean = false
    private val hour24: Boolean = DialPrefs.settings(context).hour24

    init {
        // Translucent format + on top: the Vulkan surface blends over the
        // frosted card (premultiplied alpha from the engine), and nothing
        // from the window covers it.
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        holder.addCallback(this)
    }

    private fun fail() {
        if (errorReported) return
        errorReported = true
        stopRender()
        onError?.invoke()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val engine = SlatsVulkanNative.nativeCreate(context.assets)
        if (engine == 0L) {
            fail()
            return
        }
        handle = engine
        startRender()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceW = width
        surfaceH = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopRender()
    }

    override fun onDetachedFromWindow() {
        stopRender()
        super.onDetachedFromWindow()
    }

    private fun startRender() {
        if (running) return
        running = true
        renderThread = Thread({
            var failures = 0
            while (running) {
                val h = handle
                val w = surfaceW
                val ht = surfaceH
                if (h == 0L || w <= 0 || ht <= 0) {
                    sleepQuietly(50)
                    continue
                }
                // (Re)bind the surface after creation or an out-of-date
                // swapchain; the atlas must be re-uploaded each time, as
                // destroying the surface destroys the engine's textures.
                if (!SlatsVulkanNative.nativeSetSurface(h, holder.surface, w, ht)) {
                    if (++failures >= 3) {
                        fail()
                        return@Thread
                    }
                    sleepQuietly(100)
                    continue
                }
                if (!SlatsVulkanNative.nativeUploadAtlas(h, SlatsAtlas.bitmap())) {
                    if (++failures >= 3) {
                        fail()
                        return@Thread
                    }
                    sleepQuietly(100)
                    continue
                }
                failures = 0
                // Frame loop for this surface binding.
                while (running && handle == h && surfaceW == w && surfaceH == ht) {
                    val now = System.currentTimeMillis()
                    val cal = Calendar.getInstance().apply { timeInMillis = now }
                    val (hourAngle, minuteAngle) = SlatsDial.spinAngles(now)
                    val hour = if (hour24) {
                        cal.get(Calendar.HOUR_OF_DAY)
                    } else {
                        val v = cal.get(Calendar.HOUR)
                        if (v == 0) 12 else v
                    }
                    val minute = cal.get(Calendar.MINUTE)
                    SlatsVulkanNative.nativeSetParams(
                        h, hourAngle, minuteAngle,
                        hour.toFloat(), minute.toFloat(),
                        w.toFloat() / ht.toFloat(), w.toFloat(), ht.toFloat()
                    )
                    when (SlatsVulkanNative.nativeRender(h)) {
                        0 -> failures = 0 // presented
                        1 -> break // swapchain out of date: rebind above
                        else -> {
                            if (++failures >= 5) {
                                fail()
                                return@Thread
                            }
                        }
                    }
                    sleepQuietly(33)
                }
            }
        }, "SlatsVulkanRender").also { it.start() }
    }

    private fun stopRender() {
        running = false
        val thread = renderThread
        // Never join ourselves: fail() can run on the render thread.
        if (thread != null && Thread.currentThread() != thread) thread.join(500)
        renderThread = null
        val h = handle
        handle = 0L
        if (h != 0L) {
            try {
                SlatsVulkanNative.nativeDestroySurface(h)
            } catch (_: Throwable) {
            }
            try {
                SlatsVulkanNative.nativeDestroy(h)
            } catch (_: Throwable) {
            }
        }
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }
}
