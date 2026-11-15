package com.app.dialclock.vulkan

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.view.Surface

/**
 * JNI bindings for the Slats Vulkan preview (libdial_slats_vulkan.so:
 * Atmo's one-pass engine + the Slats fragment shader).
 *
 * nativeRender returns 0 = frame presented, 1 = swapchain out of date
 * (call nativeSetSurface again), -1 = error.
 */
object SlatsVulkanNative {
    private var loadTried = false
    private var loadOk = false

    /** True when the native library loads AND the device has a Vulkan runtime. */
    fun isAvailable(): Boolean {
        if (!loadTried) {
            loadTried = true
            loadOk = try {
                System.loadLibrary("dial_slats_vulkan")
                nativeProbeRuntime() > 0
            } catch (_: Throwable) {
                false
            }
        }
        return loadOk
    }

    external fun nativeProbeRuntime(): Int
    external fun nativeCreate(assetManager: AssetManager): Long
    external fun nativeSetSurface(handle: Long, surface: Surface, width: Int, height: Int): Boolean
    external fun nativeUploadAtlas(handle: Long, bitmap: Bitmap): Boolean
    external fun nativeSetParams(
        handle: Long,
        hourAngle: Float,
        minuteAngle: Float,
        capHour: Float,
        capMinute: Float,
        aspect: Float,
        resX: Float,
        resY: Float
    ): Boolean
    external fun nativeRender(handle: Long): Int
    external fun nativeDestroySurface(handle: Long)
    external fun nativeDestroy(handle: Long)
    external fun nativeDrainDiagnostics(): String
}
