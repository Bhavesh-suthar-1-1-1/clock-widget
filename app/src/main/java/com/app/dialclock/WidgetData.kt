package com.app.dialclock

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock

/**
 * WidgetData — live device facts for the Device / Battery widgets.
 * Everything here comes from Android's own APIs. The device IP address is
 * deliberately never read and never shown on a widget.
 */
object WidgetData {

    data class Storage(val usedBytes: Long, val totalBytes: Long) {
        val usedGb: Double get() = usedBytes / 1_073_741_824.0
        val totalGb: Double get() = totalBytes / 1_073_741_824.0
        val freeGb: Double get() = (totalBytes - usedBytes) / 1_073_741_824.0
        val fraction: Float get() = if (totalBytes <= 0) 0f else (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }

    fun storage(): Storage {
        return try {
            val fs = StatFs(Environment.getDataDirectory().path)
            val total = fs.totalBytes
            val free = fs.availableBytes
            Storage(total - free, total)
        } catch (_: Exception) {
            Storage(0, 0)
        }
    }

    data class Ram(val usedBytes: Long, val totalBytes: Long) {
        val usedGb: Double get() = usedBytes / 1_073_741_824.0
        val totalGb: Double get() = totalBytes / 1_073_741_824.0
        val fraction: Float get() = if (totalBytes <= 0) 0f else (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }

    fun ram(ctx: Context): Ram {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            Ram(info.totalMem - info.availMem, info.totalMem)
        } catch (_: Exception) {
            Ram(0, 0)
        }
    }

    data class Battery(val level: Int, val charging: Boolean, val minutesLeft: Int) {
        val fraction: Float get() = (level / 100f).coerceIn(0f, 1f)
    }

    fun battery(ctx: Context): Battery {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            val sticky = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            var minutesLeft = -1
            if (charging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val ms = bm.computeChargeTimeRemaining()
                if (ms > 0) minutesLeft = (ms / 60_000L).toInt()
            }
            Battery(level, charging, minutesLeft)
        } catch (_: Exception) {
            Battery(0, false, -1)
        }
    }

    /** "Wi-Fi", "5G", "4G", "Mobile" or "Offline" — never an IP address. */
    fun networkType(ctx: Context): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return "Offline"
            val caps = cm.getNetworkCapabilities(net) ?: return "Offline"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Online"
            }
        } catch (_: Exception) {
            "Offline"
        }
    }

    fun uptimeText(): String {
        val mins = (SystemClock.elapsedRealtime() / 60_000L)
        val days = mins / 1440
        val hours = (mins % 1440) / 60
        val m = mins % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${m}m"
            else -> "${m}m"
        }
    }

    fun deviceName(): String = Build.MODEL ?: "Android"

    fun androidVersion(): String = "Android ${Build.VERSION.RELEASE ?: "?"}"

    fun gb(v: Double): String = "%.1f".format(v)
}
