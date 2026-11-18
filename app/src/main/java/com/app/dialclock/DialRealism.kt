package com.app.dialclock

/*
 * DialRealism.kt  -  makes the Dial widget look like a real watch.
 *
 * ADD this file next to DialRenderer.kt, then make the 7 small edits below.
 * Do not change anything else.
 *
 * ---------------------------------------------------------------
 * EDIT 1  Dial.kt  ->  DialSettings: add one field at the end
 * ---------------------------------------------------------------
 *     val minuteTrack: Boolean = true,
 *     val strap: Boolean = true
 *
 * Dial.kt -> DialPrefs: add a key and read it
 *     const val KEY_STRAP = "strap"
 *     ...inside settings():   strap = p.getBoolean(KEY_STRAP, true)
 *
 * ---------------------------------------------------------------
 * EDIT 2  MainActivity.kt  ->  SettingsScreen, inside the "Look" Column,
 *         add one more toggle:
 * ---------------------------------------------------------------
 *     SettingToggle("Watch strap", "Leather strap above and below the case", settings.strap) {
 *         DialPrefs.setSetting(ctx, DialPrefs.KEY_STRAP, it); onChanged()
 *     }
 *
 * ---------------------------------------------------------------
 * EDIT 3  SKIP THIS ONE. The new render() is in DialShape.kt (EDIT E) and already
 *         includes the strap and grain. Use that instead.
 *         (old text, ignore:) render(): wrap the drawing
 * ---------------------------------------------------------------
 *     val c = Canvas(bmp)
 *     if (opts.strap) {                                   // NEW
 *         DialRealism.drawStrap(c, sizePx, DialRealism.strapColor(dial))
 *         c.save()
 *         c.scale(DialRealism.BODY_SCALE, DialRealism.BODY_SCALE, sizePx / 2f, sizePx / 2f)
 *     }
 *     if (opts.shadow) drawDropShadow(c, dial, sizePx)
 *     ... (everything stays the same) ...
 *     drawInnerShadow(c, dial, sizePx, faceR)
 *     DialRealism.drawGrain(c, facePath(dial, sizePx, faceR), sizePx)   // NEW
 *     if (opts.glass) { ... }
 *     if (opts.strap) c.restore()                         // NEW (just before "return bmp")
 *
 * ---------------------------------------------------------------
 * EDIT 4  DialRenderer.kt  ->  drawCase(): real lugs
 * ---------------------------------------------------------------
 *  a) DELETE the block that starts with the comment
 *       // Lugs, top and bottom (no strap, just the case lugs)
 *     (the 5 lines: lugW, lugH, lugR and the two c.drawRoundRect calls).
 *     Keep the crown code below it.
 *  b) INSERT this line right after the `val faceR = size * FACE_R_FRAC` line (v3.5: was 0.385f)
 *     in drawCase (so it is drawn BEFORE the case and the case covers the lug roots):
 *       DialRealism.drawLugs(c, size, lugPaint.color)
 *
 * ---------------------------------------------------------------
 * EDIT 5  DialRenderer.kt  ->  drawAnalog(): hands
 * ---------------------------------------------------------------
 *  DELETE everything from the comment "// Hands" down to and including
 *     val dot = Paint(...); c.drawCircle(cx, cy, size / 90f, dot)
 *  and put this instead:
 *
 *     val thin = dial.numerals == Numerals.DOTS || dial.numerals == Numerals.KANJI
 *     DialRealism.drawHands(
 *         c, cx, cy, r, size, time,
 *         handColor = dial.hands,
 *         secondColor = if (dial.accent == dial.hands) 0xFFC62828.toInt() else dial.accent,
 *         showSecond = opts.secondHand,
 *         hours24 = dial.hours24, thin = thin,
 *         lume = !dial.glow && Color.luminance(dial.face) < 0.3f,
 *         glow = dial.glow
 *     )
 *
 * ---------------------------------------------------------------
 * EDIT 6  DialRenderer.kt  ->  same idea in the other 3 round/photo dials
 *         (delete their "// Hands" block + the final centre c.drawCircle, add one call)
 * ---------------------------------------------------------------
 *   drawMoon:        DialRealism.drawHands(c, cx, cy, r, size, time, Color.WHITE, dial.accent, opts.secondHand)
 *   drawFrenchOval:  DialRealism.drawHands(c, cx, cy, r, size, time, dial.accent, dial.accent, opts.secondHand)
 *   drawSemper:      DialRealism.drawHands(c, cx, cy, r, size, time, dial.hands, dial.accent, opts.secondHand)
 *
 * ---------------------------------------------------------------
 * EDIT 7  DialRenderer.kt  ->  drawAnalog(): date window no longer hits the numbers
 * ---------------------------------------------------------------
 *  a) In the "Date window" block, change the three size lines to:
 *         val w = r * 0.30f
 *         val h = r * 0.21f
 *     and replace both  r * 0.52f  in that block with  r * 0.46f
 *  b) In the Numerals.FRENCH branch, the red minute numbers loop:
 *         for (m in 1..12) {
 *             if (dial.dateAt != 0 && opts.dateWindow && m == dial.dateAt) continue   // NEW
 *             ...
 *
 * ---------------------------------------------------------------
 * ALSO (not code): in widget updateAll() change
 *     .coerceIn(240, 480)   ->   .coerceIn(360, 720)
 * so the picture is sharper on big widgets.
 *
 * NOTE: the red second hand can only show the time at the moment of each
 * minute update, so on the widget it will always sit at 12. Turn "Second hand"
 * OFF in Settings (or set its default to false) until the widget can tick faster.
 */

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object DialRealism {

    /** The watch is drawn at this scale when the strap is on, so the strap can run off the widget edge. */
    const val BODY_SCALE = 0.78f

    private fun shade(color: Int, f: Float): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255)
    )

    // ---------------------------------------------------------------- hands

    /** One tapered hand (0 deg = 12 o'clock) with a short counterweight tail and a soft shadow. */
    private fun drawTapered(
        c: Canvas, cx: Float, cy: Float, angleDeg: Double,
        length: Float, baseW: Float, tail: Float,
        color: Int, size: Int, lume: Boolean, glow: Boolean
    ) {
        val m = Matrix().apply { setRotate(angleDeg.toFloat(), cx, cy) }
        val tipW = baseW * 0.28f
        val body = Path().apply {
            moveTo(cx - baseW * 0.42f, cy + tail)
            lineTo(cx - baseW / 2f, cy)
            lineTo(cx - tipW / 2f, cy - length)
            lineTo(cx + tipW / 2f, cy - length)
            lineTo(cx + baseW / 2f, cy)
            lineTo(cx + baseW * 0.42f, cy + tail)
            close()
        }
        body.transform(m)

        // Shadow always falls bottom-right (light from top-left). Glow dials get a coloured halo instead.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (glow) {
                this.color = Color.argb(170, Color.red(color), Color.green(color), Color.blue(color))
                maskFilter = BlurMaskFilter(size / 45f, BlurMaskFilter.Blur.NORMAL)
            } else {
                this.color = Color.argb(80, 0, 0, 0)
                maskFilter = BlurMaskFilter(max(size / 300f, 1f), BlurMaskFilter.Blur.NORMAL)
            }
        }
        c.save()
        if (!glow) c.translate(size / 260f, size / 220f)
        c.drawPath(body, shadow)
        c.restore()

        c.drawPath(body, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })

        if (lume) {
            // Luminous strip on the outer part of the hand
            fun widthAt(t: Float) = (baseW + (tipW - baseW) * t) * 0.45f
            val w0 = widthAt(0.30f)
            val w1 = widthAt(0.92f)
            val strip = Path().apply {
                moveTo(cx - w0 / 2f, cy - length * 0.30f)
                lineTo(cx - w1 / 2f, cy - length * 0.92f)
                lineTo(cx + w1 / 2f, cy - length * 0.92f)
                lineTo(cx + w0 / 2f, cy - length * 0.30f)
                close()
            }
            strip.transform(m)
            c.drawPath(strip, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFEAF5C8.toInt() })
        } else {
            // Thin light ridge down the middle gives the hand a bevelled look
            val ridge = Path().apply {
                moveTo(cx, cy - length * 0.05f)
                lineTo(cx, cy - length * 0.97f)
            }
            ridge.transform(m)
            c.drawPath(ridge, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(baseW * 0.12f, 1f)
                this.color = Color.argb(45, 255, 255, 255)
            })
        }
    }

    /** Thin seconds hand with a counterweight disc on its tail. */
    private fun drawSecond(c: Canvas, cx: Float, cy: Float, angleDeg: Double, length: Float, size: Int, color: Int) {
        val rad = Math.toRadians(angleDeg)
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val tail = length * 0.22f
        val line = Path().apply {
            moveTo(cx - dx * tail, cy - dy * tail)
            lineTo(cx + dx * length, cy + dy * length)
        }
        val sw = max(size / 260f, 1.2f)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sw
            this.color = Color.argb(70, 0, 0, 0)
            maskFilter = BlurMaskFilter(max(size / 300f, 1f), BlurMaskFilter.Blur.NORMAL)
        }
        c.save()
        c.translate(size / 200f, size / 170f)
        c.drawPath(line, shadow)
        c.restore()
        c.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sw
            strokeCap = Paint.Cap.ROUND
            this.color = color
        })
        c.drawCircle(cx - dx * tail * 0.8f, cy - dy * tail * 0.8f, size / 170f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    /** Centre cap with a little shadow and highlight. */
    private fun drawCenterCap(c: Canvas, cx: Float, cy: Float, size: Int, color: Int) {
        c.drawCircle(cx + size / 300f, cy + size / 260f, size / 80f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(90, 0, 0, 0)
            maskFilter = BlurMaskFilter(max(size / 250f, 1f), BlurMaskFilter.Blur.NORMAL)
        })
        c.drawCircle(cx, cy, size / 80f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        c.drawCircle(cx - size / 400f, cy - size / 400f, size / 200f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(110, 255, 255, 255)
        })
    }

    /** Hour + minute (+ optional second) hands and the centre cap, in one call. */
    fun drawHands(
        c: Canvas, cx: Float, cy: Float, r: Float, size: Int, time: Calendar,
        handColor: Int, secondColor: Int, showSecond: Boolean,
        hours24: Boolean = false, thin: Boolean = false,
        lume: Boolean = false, glow: Boolean = false
    ) {
        val hour = time.get(Calendar.HOUR_OF_DAY)
        val min = time.get(Calendar.MINUTE)
        val sec = time.get(Calendar.SECOND)
        val hourAngle = if (hours24) (hour + min / 60.0) / 24.0 * 360.0
        else ((hour % 12) + min / 60.0) / 12.0 * 360.0
        val minAngle = (min + sec / 60.0) / 60.0 * 360.0
        val k = if (thin) 0.75f else 1f
        drawTapered(c, cx, cy, hourAngle, r * 0.52f, size / 52f * k, r * 0.14f, handColor, size, lume, glow)
        drawTapered(c, cx, cy, minAngle, r * 0.76f, size / 72f * k, r * 0.16f, handColor, size, lume, glow)
        if (showSecond) drawSecond(c, cx, cy, sec / 60.0 * 360.0, r * 0.82f, size, secondColor)
        drawCenterCap(c, cx, cy, size, secondColor)
    }

    // ---------------------------------------------------------------- dial grain

    /** Tiny light/dark speckle tile, built once. Gives the dial a printed / sand-grain surface. */
    private val grainTile: Bitmap by lazy {
        val n = 128
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val rnd = java.util.Random(7L)
        for (y in 0 until n) for (x in 0 until n) {
            val v = rnd.nextInt(100)
            b.setPixel(x, y, when {
                v < 9 -> Color.argb(22, 255, 255, 255)
                v < 18 -> Color.argb(16, 0, 0, 0)
                else -> Color.TRANSPARENT
            })
        }
        b
    }

    fun drawGrain(c: Canvas, facePath: Path, size: Int) {
        c.save()
        c.clipPath(facePath)
        val p = Paint().apply { shader = BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        c.restore()
    }

    // ---------------------------------------------------------------- strap

    fun strapColor(dial: Dial): Int = when {
        dial.kind == DialKind.KLOKERS -> 0xFF6E6E6E.toInt()            // grey fabric NATO (Klokers original)
        dial.kind == DialKind.BIG_HOUR -> 0xFFEDEDED.toInt()           // white rubber
        dial.kind == DialKind.DIGITAL -> 0xFF5A3A1E.toInt()            // dark brown leather
        Color.luminance(dial.face) < 0.2f -> 0xFF222225.toInt()        // black leather
        else -> 0xFF9C6232.toInt()                                     // tan leather
    }

    /** Leather strap with stitching, fading out at the top and bottom edge. Draw FIRST, unscaled. */
    fun drawStrap(c: Canvas, size: Int, base: Int, aspect: Float = 1f) {
        val cx = size / 2f
        val w = size * 0.34f * BODY_SCALE * aspect * 0.97f
        val left = cx - w / 2f
        val right = cx + w / 2f
        val s = size.toFloat()

        c.drawRect(left, 0f, right, s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                left, 0f, right, 0f,
                intArrayOf(shade(base, 0.72f), shade(base, 1.08f), shade(base, 0.72f)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        })

        val stitch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size / 320f
            color = shade(base, 1.55f)
            pathEffect = DashPathEffect(floatArrayOf(size / 55f, size / 80f), 0f)
        }
        val inset = w * 0.10f
        c.drawLine(left + inset, 0f, left + inset, s, stitch)
        c.drawLine(right - inset, 0f, right - inset, s, stitch)

        // Fade the strap out at the widget's top and bottom edge
        val fadeLen = s * 0.16f
        c.drawRect(left, 0f, right, fadeLen, Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            shader = LinearGradient(0f, 0f, 0f, fadeLen, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
        c.drawRect(left, s - fadeLen, right, s, Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            shader = LinearGradient(0f, s - fadeLen, 0f, s, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        })
    }
}
