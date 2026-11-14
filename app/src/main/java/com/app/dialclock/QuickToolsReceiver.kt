package com.app.dialclock

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat

/**
 * Flashlight toggle for the Quick tools widget.
 * If the Camera permission is not granted yet, tapping Torch opens the app
 * so the user can grant it there (the app asks once, on first launch).
 */
class QuickToolsReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TOGGLE_TORCH = "com.app.dialclock.TOGGLE_TORCH"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE_TORCH) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            val open = Intent(ctx, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ctx.startActivity(open)
            return
        }
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { camId ->
                cm.getCameraCharacteristics(camId)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            val next = !DialPrefs.torchOn(ctx)
            cm.setTorchMode(id, next)
            DialPrefs.setTorchOn(ctx, next)
        } catch (_: Exception) {
            // Camera busy or no flash on this device — leave the state as it is.
        }
        QuickToolsWidgetProvider.refreshAll(ctx)
    }
}
