package com.app.dialclock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * HybridDials.kt — 7 original hybrid clock widgets.
 *
 * A hybrid combines a small analog face + big digital time + date/day chips
 * in ONE widget. Each of the 7 differs in size (square / wide / tall),
 * shape (squircle / round / wide rect), colours, and which element pulses.
 *
 * Animation note: home-screen widgets cannot do smooth 60fps animation —
 * a widget is a static picture the app re-draws. So the "pulse" here steps
 * once per second (the accent chip / dot / ring alternates between full and
 * dim alpha on each second tick, driven by the same 1-second tick as the
 * live second hand). The mini analog's second hand ticks every second too.
 */
object HybridDials {

    enum class Layout { SQUARE, WIDE, TALL }
    enum class Shape { SQUIRCLE, ROUND, WIDE_RECT }
    enum class Pulse { CHIP, DOT, RING }

    data class HybridStyle(
        val id: String,
        val name: String,
        val layout: Layout,
        val shape: Shape,
        val bg: Int,
        val bg2: Int? = null,   // second colour for a gradient background
        val fg: Int,
        val accent: Int,
        val pulse: Pulse,
        val analogScale: Float = 1f,  // size of the mini analog, relative
        val digitalScale: Float = 1f  // size of the big digital time, relative
    )

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    val STYLES: List<HybridStyle> = listOf(
        HybridStyle("hyb01", "Hybrid Square", Layout.SQUARE, Shape.SQUIRCLE, bg = BLACK, fg = WHITE, accent = 0xFFE53935.toInt(), pulse = Pulse.CHIP, analogScale = 1f, digitalScale = 1f),
        HybridStyle("hyb02", "Hybrid Wide", Layout.WIDE, Shape.WIDE_RECT, bg = BLACK, fg = WHITE, accent = 0xFFE53935.toInt(), pulse = Pulse.DOT, analogScale = 0.9f, digitalScale = 1.1f),
        HybridStyle("hyb03", "Hybrid Tall", Layout.TALL, Shape.SQUIRCLE, bg = WHITE, fg = 0xFF111111.toInt(), accent = 0xFF1E88E5.toInt(), pulse = Pulse.CHIP, analogScale = 1.1f, digitalScale = 0.95f),
        HybridStyle("hyb04", "Hybrid Gold", Layout.SQUARE, Shape.ROUND, bg = BLACK, fg = 0xFFD4AF37.toInt(), accent = 0xFFD4AF37.toInt(), pulse = Pulse.RING, analogScale = 1f, digitalScale = 1f),
        HybridStyle("hyb05", "Hybrid Blue", Layout.WIDE, Shape.WIDE_RECT, bg = 0xFF0A1E3C.toInt(), fg = WHITE, accent = 0xFF00E5FF.toInt(), pulse = Pulse.CHIP, analogScale = 0.85f, digitalScale = 1.05f),
        HybridStyle("hyb06", "Hybrid Mono", Layout.SQUARE, Shape.SQUIRCLE, bg = WHITE, fg = BLACK, accent = BLACK, pulse = Pulse.DOT, analogScale = 0.8f, digitalScale = 1.1f),
        HybridStyle("hyb07", "Hybrid Sunset", Layout.WIDE, Shape.ROUND, bg = 0xFFFF7A00.toInt(), bg2 = 0xFFFF3D7F.toInt(), fg = WHITE, accent = WHITE, pulse = Pulse.CHIP, analogScale = 0.95f, digitalScale = 1f)
    )

    fun byId(id: String): HybridStyle? = STYLES.firstOrNull { it.id == id }

    // ---- entry points ----------------------------------------------------
    /** Draw inside the square watch face (gallery previews + square widget). */
    fun draw(c: Canvas, dial: Dial, size: Int, faceR: Float, time: Calendar, opts: DialSettings) {
        val s = byId(dial.style) ?: return
        val cx = size / 2f
        val cy = size / 2f
        val rect = RectF(cx - faceR, cy - faceR, cx + faceR, cy + faceR)
        c.save()
        c.clipPath(android.graphics.Path().apply {
            addRoundRect(rect, faceR * 0.30f, faceR * 0.30f, android.graphics.Path.Direction.CW)
        })
        drawInto(c, rect, s, time, opts, size)
        c.restore()
    }

    /** Draw across a full rectangle (responsive wide/tall widget). */
    fun drawRect(c: Canvas, w: Int, h: Int, time: Calendar, opts: DialSettings, style: HybridStyle) {
        val pad = min(w, h) * 0.04f
        val rect = RectF(pad, pad, w - pad, h - pad)
        c.save()
        c.clipPath(android.graphics.Path().apply {
            addRoundRect(rect, cornerFor(style, rect), cornerFor(style, rect), android.graphics.Path.Direction.CW)
        })
        drawInto(c, rect, style, time, opts, min(w, h))
        c.restore()
    }

    private fun cornerFor(s: HybridStyle, rect: RectF): Float = when (s.shape) {
        Shape.SQUIRCLE -> min(rect.width(), rect.height()) * 0.14f
        Shape.ROUND -> min(rect.width(), rect.height()) * 0.30f
        Shape.WIDE_RECT -> min(rect.width(), rect.height()) * 0.06f
    }

    // ---- core layout ------------------------------------------------------
    private fun drawInto(c: Canvas, rect: RectF, s: HybridStyle, time: Calendar, opts: DialSettings, size: Int) {
        // Background: solid or two-colour gradient.
        if (s.bg2 != null) {
            c.drawRect(rect, Paint().apply {
                shader = LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    s.bg, s.bg2, Shader.TileMode.CLAMP
                )
            })
        } else {
            c.drawRect(rect, Paint().apply { color = s.bg })
        }

        val sec = time.get(Calendar.SECOND)
        // Pulse steps once per second: full alpha on even seconds, dim on odd.
        val pulseAlpha = if (sec % 2 == 0) 255 else 150

        val hour24 = opts.hour24
        val hh = if (hour24) "%02d".format(time.get(Calendar.HOUR_OF_DAY))
        else { val v = time.get(Calendar.HOUR); "%02d".format(if (v == 0) 12 else v) }
        val mm = "%02d".format(time.get(Calendar.MINUTE))
        val digital = "$hh:$mm"
        val dayNum = time.get(Calendar.DAY_OF_MONTH).toString()
        val dayName = SimpleDateFormat("EEE", Locale.ENGLISH).format(time.time).uppercase(Locale.ENGLISH)

        when (s.layout) {
            Layout.SQUARE -> {
                // Two zones that never touch: analog left, digital right, chip under the digital.
                val gap = rect.width() * 0.04f
                val azLeft = rect.left + gap
                val azRight = rect.left + rect.width() * 0.44f
                val dzLeft = rect.left + rect.width() * 0.48f
                val dzRight = rect.right - gap
                val azCx = (azLeft + azRight) / 2f
                val dzCx = (dzLeft + dzRight) / 2f
                val cy = rect.centerY()
                // Biggest disc that fits the analog zone (the pulse ring adds 4% around it).
                val ar = min((azRight - azLeft) / 2f, rect.height() * 0.42f) * 0.96f * s.analogScale
                drawMiniAnalog(c, azCx, cy, ar, size, time, opts, s, pulseAlpha)
                val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = s.fg
                    textSize = rect.height() * 0.22f * s.digitalScale
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                var ts = rect.height() * 0.22f * s.digitalScale
                while (dp.measureText(digital) > (dzRight - dzLeft) * 0.96f && ts > rect.height() * 0.09f) {
                    ts *= 0.92f
                    dp.textSize = ts
                }
                val dy = cy - rect.height() * 0.02f
                c.drawText(digital, dzCx, dy - (dp.fontMetrics.ascent + dp.fontMetrics.descent) / 2f, dp)
                drawDateChip(c, dzCx, dy + rect.height() * 0.20f, rect.height() * 0.12f, dayNum, dayName, s, pulseAlpha, (dzRight - dzLeft) * 0.96f)
            }
            Layout.WIDE -> {
                // Two zones that never touch: big digital left, mini analog right,
                // date chip under the analog inside the right zone.
                val gap = rect.width() * 0.03f
                val dzLeft = rect.left + gap
                val dzRight = rect.left + rect.width() * 0.56f
                val azLeft = rect.left + rect.width() * 0.60f
                val azRight = rect.right - gap
                val dzCx = (dzLeft + dzRight) / 2f
                val azCx = (azLeft + azRight) / 2f
                val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = s.fg
                    textSize = rect.height() * 0.40f * s.digitalScale
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                var ts = rect.height() * 0.40f * s.digitalScale
                while (dp.measureText(digital) > (dzRight - dzLeft) * 0.96f && ts > rect.height() * 0.14f) {
                    ts *= 0.92f
                    dp.textSize = ts
                }
                val dy = rect.centerY() - rect.height() * 0.02f
                c.drawText(digital, dzCx, dy - (dp.fontMetrics.ascent + dp.fontMetrics.descent) / 2f, dp)
                // Chip sits at the bottom of the right zone; the analog takes the space above it.
                val chipH = rect.height() * 0.13f
                val chipCy = rect.bottom - chipH / 2f - rect.height() * 0.03f
                val analogTop = rect.top + rect.height() * 0.04f
                val analogBottom = chipCy - chipH / 2f - rect.height() * 0.04f
                val ar = min((azRight - azLeft) / 2f, (analogBottom - analogTop) / 2f) * 0.96f * s.analogScale
                val ay = (analogTop + analogBottom) / 2f
                drawMiniAnalog(c, azCx, ay, ar, size, time, opts, s, pulseAlpha)
                drawDateChip(c, azCx, chipCy, chipH, dayNum, dayName, s, pulseAlpha, (azRight - azLeft) * 0.98f)
            }
            Layout.TALL -> {
                // Digital on top, mini analog in the middle, date chip at the bottom.
                val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = s.fg
                    textSize = rect.width() * 0.34f * s.digitalScale
                    typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                var ts = rect.width() * 0.34f * s.digitalScale
                while (dp.measureText(digital) > rect.width() * 0.86f && ts > rect.width() * 0.12f) {
                    ts *= 0.92f
                    dp.textSize = ts
                }
                val dy = rect.top + rect.height() * 0.20f
                c.drawText(digital, rect.centerX(), dy - (dp.fontMetrics.ascent + dp.fontMetrics.descent) / 2f, dp)
                val ar = min(rect.width(), rect.height()) * 0.17f * s.analogScale
                drawMiniAnalog(c, rect.centerX(), rect.centerY() + rect.height() * 0.08f, ar, size, time, opts, s, pulseAlpha)
                drawDateChip(c, rect.centerX(), rect.bottom - rect.height() * 0.10f, rect.height() * 0.075f, dayNum, dayName, s, pulseAlpha)
            }
        }
    }

    /** Red date chip with the day number in accent and the weekday beside it. */
    private fun drawDateChip(c: Canvas, cx: Float, cy: Float, h: Float, dayNum: String, dayName: String, s: HybridStyle, pulseAlpha: Int, maxW: Float = Float.MAX_VALUE) {
        val chipAlpha = if (s.pulse == Pulse.CHIP) pulseAlpha else 255
        val text = "$dayNum  $dayName"
        // Chip text must contrast with the chip colour (the accent): dark text on light chips.
        val textColor = if (Color.luminance(s.accent) > 0.5f) BLACK else WHITE
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = h * 0.58f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        var ts = h * 0.58f
        while (tp.measureText(text) + h * 0.9f > maxW && ts > h * 0.30f) {
            ts *= 0.92f
            tp.textSize = ts
        }
        val w = tp.measureText(text) + h * 0.9f
        val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(chipAlpha, Color.red(s.accent), Color.green(s.accent), Color.blue(s.accent))
        }
        c.drawRoundRect(rect, h * 0.5f, h * 0.5f, bg)
        c.drawText(text, cx, cy - (tp.fontMetrics.ascent + tp.fontMetrics.descent) / 2f, tp)
    }

    /** Small analog face with hour/minute/second hands. */
    private fun drawMiniAnalog(c: Canvas, cx: Float, cy: Float, r: Float, size: Int, time: Calendar, opts: DialSettings, s: HybridStyle, pulseAlpha: Int) {
        // Face: a slightly darker/lighter disc than the panel.
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (Color.luminance(s.bg) > 0.5f) Color.argb(255, 235, 235, 235) else Color.argb(255, 22, 22, 26)
        }
        c.drawCircle(cx, cy, r, face)
        // Pulsing accent ring (only on styles whose pulse target is the ring).
        if (s.pulse == Pulse.RING) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(pulseAlpha, Color.red(s.accent), Color.green(s.accent), Color.blue(s.accent))
                style = Paint.Style.STROKE
                strokeWidth = r * 0.07f
            }
            c.drawCircle(cx, cy, r * 1.04f, ring)
        }
        // 12 tick marks.
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.fg
            strokeWidth = max(r * 0.04f, 1.5f)
            strokeCap = Paint.Cap.ROUND
        }
        for (m in 0 until 12) {
            val a = Math.toRadians(m * 30.0 - 90.0)
            val long = m % 3 == 0
            val r1 = r * if (long) 0.78f else 0.85f
            c.drawLine(
                (cx + r1 * cos(a)).toFloat(), (cy + r1 * sin(a)).toFloat(),
                (cx + r * 0.94f * cos(a)).toFloat(), (cy + r * 0.94f * sin(a)).toFloat(), tick
            )
        }
        // Hands (hour + minute + live second hand + centre cap).
        DialRealism.drawHands(
            c, cx, cy, r, size, time,
            handColor = s.fg,
            secondColor = s.accent,
            showSecond = opts.secondHand,
            hours24 = false, thin = true, lume = false, glow = false
        )
        // Pulsing centre dot (only on styles whose pulse target is the dot).
        if (s.pulse == Pulse.DOT) {
            c.drawCircle(cx, cy, r * 0.10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(pulseAlpha, Color.red(s.accent), Color.green(s.accent), Color.blue(s.accent))
            })
        }
    }

    fun renderBitmap(w: Int, h: Int, time: Calendar, opts: DialSettings, style: HybridStyle): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawRect(c, w, h, time, opts, style)
        return bmp
    }
}
