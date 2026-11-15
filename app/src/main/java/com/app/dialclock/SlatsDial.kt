package com.app.dialclock

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * SlatsDial.kt — the red slat wall clock (v2.4, "Featured" section).
 *
 * Round wall clock: red outer rim, white face, the whole face covered by
 * evenly spaced thin vertical red slats. Behind the slats, concentric rings
 * carry black numbers (hours on the outer ring, minutes on the inner ring)
 * and rotate with the time, so the numbers are partly hidden by the slats
 * and drift as the minutes pass. A horizontal white capsule window at
 * centre-right, with a thin red rim, shows the current time in crisp black
 * digits ("HH MM") — that window is the readable time.
 *
 * Pure Canvas bitmap rendering, like every other dial: the widget bitmap is
 * re-rendered once per second when the live tick is eligible (unrestricted
 * battery + live second hand on), so the rings spin at video speed in real
 * time; otherwise it re-renders on each minute tick and the rings jump by
 * the same visible step (6° minute ring, 0.5° hour ring). The in-app
 * preview re-renders every second on Canvas, or continuously through
 * Vulkan. No GL in the widget path, no extra engine.
 */
object SlatsDial {

    /**
     * Video-speed spin (v2.5): the reference video runs ~60x real time,
     * so the minute ring turns 6 degrees a second and the hour ring 0.5.
     * Both angles derive from wall-clock epoch seconds, so the widget
     * (re-rendered once a second) and the Vulkan preview (every frame)
     * stay in step. The rings are the moving spectacle — the capsule
     * window always shows the real current HH MM.
     */
    fun spinAngles(timeMillis: Long): Pair<Float, Float> {
        val s = timeMillis / 1000.0
        return Pair(
            ((s * 0.5) % 360.0).toFloat(),  // hour ring
            ((s * 6.0) % 360.0).toFloat()   // minute ring
        )
    }

    private val RED = 0xFFD32F2F.toInt()
    private val WHITE = 0xFFFFFFFF.toInt()
    private val INK = 0xFF16161A.toInt()

    private fun shade(color: Int, f: Float): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255)
    )

    private fun lighten(color: Int, f: Float): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) + (255 - Color.red(color)) * f).toInt().coerceIn(0, 255),
        (Color.green(color) + (255 - Color.green(color)) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) + (255 - Color.blue(color)) * f).toInt().coerceIn(0, 255)
    )

    /** Full render of the clock onto a square bitmap. [accent] is the slat /
     *  rim colour — the Studio's Accent colour recolours the whole clock. */
    fun render(sizePx: Int, time: Calendar, opts: DialSettings, accent: Int = RED): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val rimR = sizePx * 0.46f    // outer edge of the rim
        val faceR = sizePx * 0.395f  // white face

        drawShadow(c, cx, cy, rimR, sizePx)
        drawRim(c, cx, cy, rimR, faceR, accent, sizePx)

        // Face: soft radial paper-white with a darker edge, so it reads as a
        // dish set into the rim instead of a flat disc.
        c.drawCircle(cx, cy, faceR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - faceR * 0.25f, cy - faceR * 0.30f, faceR * 1.35f,
                intArrayOf(WHITE, 0xFFF6F3F1.toInt(), 0xFFE3DEDB.toInt()),
                floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
            )
        })

        // Everything inside the face is clipped to the face circle.
        c.save()
        c.clipPath(Path().apply { addCircle(cx, cy, faceR, Path.Direction.CW) })
        drawBezelTicks(c, cx, cy, faceR)
        drawRings(c, cx, cy, faceR, time)
        drawSlats(c, cx, cy, faceR, accent)
        drawFaceDepth(c, cx, cy, faceR)
        c.restore()

        drawCapsule(c, cx, cy, faceR, time, opts, accent)
        return bmp
    }

    private fun drawShadow(c: Canvas, cx: Float, cy: Float, rimR: Float, sizePx: Int) {
        val wide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 0, 0, 0)
            maskFilter = BlurMaskFilter(sizePx / 34f, BlurMaskFilter.Blur.NORMAL)
        }
        c.save()
        c.translate(sizePx * 0.012f, sizePx * 0.026f)
        c.drawCircle(cx, cy, rimR, wide)
        c.restore()
        // Tight contact shadow right under the rim.
        val tight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            maskFilter = BlurMaskFilter(sizePx / 120f, BlurMaskFilter.Blur.NORMAL)
        }
        c.save()
        c.translate(0f, sizePx * 0.006f)
        c.drawCircle(cx, cy, rimR, tight)
        c.restore()
    }

    /** Lacquered rim: a sweep gradient gives it a lit side and a shaded side. */
    private fun drawRim(c: Canvas, cx: Float, cy: Float, rimR: Float, faceR: Float, accent: Int, sizePx: Int) {
        val light = lighten(accent, 0.30f)
        val dark = shade(accent, 0.62f)
        c.drawCircle(cx, cy, rimR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(
                cx, cy,
                intArrayOf(light, accent, dark, accent, light),
                floatArrayOf(0f, 0.25f, 0.55f, 0.8f, 1f)
            )
        })
        // Outer edge line and a soft highlight just inside it.
        c.drawCircle(cx, cy, rimR - sizePx / 400f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shade(accent, 0.55f)
            style = Paint.Style.STROKE
            strokeWidth = sizePx / 230f
        })
        c.drawCircle(cx, cy, rimR - sizePx / 95f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = sizePx / 300f
        })
        // Groove where the rim meets the face.
        c.drawCircle(cx, cy, faceR + sizePx / 220f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = sizePx / 190f
        })
    }

    /** Static minute ticks just inside the face edge (behind the slats). */
    private fun drawBezelTicks(c: Canvas, cx: Float, cy: Float, faceR: Float) {
        val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 0, 0)
            strokeWidth = faceR * 0.006f
            strokeCap = Paint.Cap.ROUND
        }
        val thick = Paint(thin).apply {
            color = Color.argb(120, 0, 0, 0)
            strokeWidth = faceR * 0.012f
        }
        for (i in 0 until 60) {
            val big = i % 5 == 0
            val rad = Math.toRadians((i * 6.0) - 90.0)
            val r1 = faceR * 0.975f
            val r2 = faceR * (if (big) 0.925f else 0.945f)
            c.drawLine(
                (cx + r1 * cos(rad)).toFloat(), (cy + r1 * sin(rad)).toFloat(),
                (cx + r2 * cos(rad)).toFloat(), (cy + r2 * sin(rad)).toFloat(),
                if (big) thick else thin
            )
        }
    }

    /** Concentric rings with rotating numbers, behind the slats. */
    private fun drawRings(c: Canvas, cx: Float, cy: Float, faceR: Float, time: Calendar) {
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = faceR * 0.007f
        }
        c.drawCircle(cx, cy, faceR * 0.88f, ringPaint)
        c.drawCircle(cx, cy, faceR * 0.66f, ringPaint)
        c.drawCircle(cx, cy, faceR * 0.42f, ringPaint)

        val (hourAngle, minuteAngle) = spinAngles(time.timeInMillis)

        // Outer ring: hours 1..12.
        val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = faceR * 0.135f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        drawNumberRing(c, cx, cy, faceR * 0.77f, hourAngle, hourPaint) { i ->
            if (i == 0) "12" else i.toString()
        }
        // Inner ring: minutes 05..60 in steps of 5.
        val minPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = faceR * 0.105f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        drawNumberRing(c, cx, cy, faceR * 0.54f, minuteAngle, minPaint) { i ->
            // v3.2: the old expression bound `.let` to the literal 60, so the
            // last label read "00" instead of "60".
            val m = (i + 1) * 5
            "%02d".format(m)
        }
    }

    /** Twelve numbers around a ring, rotated with the ring. Each digit is
     * turned so its top points outward, like the numbers on the real clock. */
    private fun drawNumberRing(
        c: Canvas, cx: Float, cy: Float, r: Float, baseAngle: Float,
        paint: Paint, label: (Int) -> String
    ) {
        for (i in 0 until 12) {
            val angle = i * 30f + baseAngle
            val rad = Math.toRadians((angle - 90f).toDouble())
            val x = (cx + r * cos(rad)).toFloat()
            val y = (cy + r * sin(rad)).toFloat()
            c.save()
            c.translate(x, y)
            c.rotate(angle)
            c.drawText(label(i), 0f, paint.textSize * 0.35f, paint)
            c.restore()
        }
    }

    /** Evenly spaced vertical slats: each is a lit-left / shaded-right
     * round bar that also throws a soft shadow onto the numbers behind it. */
    private fun drawSlats(c: Canvas, cx: Float, cy: Float, faceR: Float, accent: Int) {
        val slatCount = 30
        val pitch = (2f * faceR) / slatCount
        val barW = pitch * 0.46f
        val light = lighten(accent, 0.28f)
        val dark = shade(accent, 0.66f)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 0, 0, 0) }
        val slatPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until slatCount) {
            val x = cx - faceR + i * pitch + (pitch - barW) / 2f
            // Shadow cast to the right.
            c.drawRoundRect(
                RectF(x + barW * 0.55f, cy - faceR, x + barW * 1.75f, cy + faceR),
                barW / 2f, barW / 2f, shadowPaint
            )
            slatPaint.shader = LinearGradient(
                x, 0f, x + barW, 0f,
                intArrayOf(light, accent, dark),
                floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRoundRect(
                RectF(x, cy - faceR, x + barW, cy + faceR),
                barW / 2f, barW / 2f, slatPaint
            )
        }
    }

    /** Glass-like light over the whole face plus an inner edge shadow. */
    private fun drawFaceDepth(c: Canvas, cx: Float, cy: Float, faceR: Float) {
        c.drawRect(cx - faceR, cy - faceR, cx + faceR, cy + faceR, Paint().apply {
            shader = LinearGradient(
                cx - faceR * 0.6f, cy - faceR, cx + faceR * 0.2f, cy + faceR * 0.2f,
                intArrayOf(Color.argb(70, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        })
        c.drawCircle(cx, cy, faceR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = faceR * 0.10f
            shader = RadialGradient(
                cx, cy, faceR * 1.05f,
                intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(70, 0, 0, 0)),
                floatArrayOf(0f, 0.90f, 1f), Shader.TileMode.CLAMP
            )
        })
    }

    /** White capsule window at centre-right with the digital time. */
    private fun drawCapsule(
        c: Canvas, cx: Float, cy: Float, faceR: Float, time: Calendar,
        opts: DialSettings, accent: Int
    ) {
        val h = faceR * 0.40f
        val rect = RectF(cx - faceR * 0.10f, cy - h / 2f, cx + faceR * 0.95f, cy + h / 2f)

        // Soft drop shadow under the window.
        c.save()
        c.translate(0f, h * 0.10f)
        c.drawRoundRect(rect, h / 2f, h / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 0, 0, 0)
            maskFilter = BlurMaskFilter(h * 0.16f, BlurMaskFilter.Blur.NORMAL)
        })
        c.restore()

        c.drawRoundRect(rect, h / 2f, h / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(WHITE, 0xFFF1EEEC.toInt()), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        })
        c.drawRoundRect(rect, h / 2f, h / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            style = Paint.Style.STROKE
            strokeWidth = faceR * 0.024f
        })

        val hour = if (opts.hour24) time.get(Calendar.HOUR_OF_DAY)
        else { val v = time.get(Calendar.HOUR); if (v == 0) 12 else v }
        val hh = "%02d".format(hour)
        val mm = "%02d".format(time.get(Calendar.MINUTE))

        var ts = h * 0.60f
        val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        digitPaint.textSize = ts
        // Colon made of two accent dots, so it reads as a clock and never
        // as a gap; the whole group is shrunk to fit the capsule.
        fun groupWidth(): Float = digitPaint.measureText(hh) + digitPaint.measureText(mm) + ts * 0.42f
        while (groupWidth() > rect.width() * 0.80f && ts > h * 0.2f) {
            ts *= 0.92f
            digitPaint.textSize = ts
        }
        val total = groupWidth()
        var x = rect.centerX() - total / 2f
        val baseY = cy - (digitPaint.fontMetrics.ascent + digitPaint.fontMetrics.descent) / 2f
        c.drawText(hh, x, baseY, digitPaint)
        x += digitPaint.measureText(hh)
        val dotR = ts * 0.065f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        c.drawCircle(x + ts * 0.21f, cy - ts * 0.15f, dotR, dotPaint)
        c.drawCircle(x + ts * 0.21f, cy + ts * 0.15f, dotR, dotPaint)
        x += ts * 0.42f
        c.drawText(mm, x, baseY, digitPaint)

        // 12-hour mode: a small AM / PM tag above the right end of the digits.
        if (!opts.hour24) {
            val ap = if (time.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"
            val tag = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                textSize = h * 0.17f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            c.drawText(ap, rect.right - h * 0.42f, rect.top + h * 0.26f, tag)
        }
    }
}
