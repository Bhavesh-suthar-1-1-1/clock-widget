package com.app.dialclock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

/**
 * DigitalDials.kt — 19 digital clock styles — 10 in the gallery, all 19 in the Studio.
 *
 * Every style is drawn here in code (system font families + Canvas effects).
 * Nothing is copied from any other widget app: the fonts are Android's own
 * families and the pixel / dot / segment digits are drawn from the grids
 * below, which were made for this app.
 *
 * A style is: font family + weight + effect + colours + texture.
 * All 19 differ in that combination — no two are alike.
 */
object DigitalDials {

    enum class Effect {
        FILL, OUTLINE, GLOW, GRADIENT, PIXEL, DOTS, SEGMENT, STRIPES,
        WAVY, GEOMETRIC, BLOCK, DUAL_TONE, RAINBOW, SHADOW
    }

    enum class Texture { SOLID, GRADIENT_BG, GRAIN, FROSTED, NEON }

    data class DigitalStyle(
        val id: String,
        val name: String,
        val family: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val effect: Effect,
        val fg: Int,
        val bg: Int,
        val accent: Int,
        val texture: Texture = Texture.SOLID,
        val showDate: Boolean = false,
        /** v3.2: false = hidden from the main gallery. The design is still a
         *  full dial — it stays in the Studio's Digital list, so it can be
         *  picked there and applied to the widget. */
        val featured: Boolean = true
    )

    // ---- colour helpers -------------------------------------------------
    private fun shade(color: Int, f: Float): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255)
    )

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val CHARCOAL = 0xFF1C1C1E.toInt()
    private const val GOLD = 0xFFD4AF37.toInt()
    private const val ORANGE = 0xFFFF7A00.toInt()
    private const val CYAN = 0xFF00E5FF.toInt()

    // v3.8: only one digital clock is kept — "Black" (dig12). The other
    // 18 styles were removed at Bhavesh's word ("remove all digital clocks
    // but keep 1 digital clock"). The Studio customises this one instead.
    val STYLES: List<DigitalStyle> = listOf(
        DigitalStyle("dig12", "Black", "sans-serif-black", effect = Effect.FILL, fg = WHITE, bg = BLACK, accent = WHITE, showDate = true)
    )

    fun byId(id: String): DigitalStyle? = STYLES.firstOrNull { it.id == id }

    /** Styles shown in the main gallery (the rest live in the Studio). */
    val FEATURED_IDS: Set<String> = STYLES.filter { it.featured }.map { it.id }.toSet()

    /** Custom overrides: palette accent / face / hands and the chosen
     * numeral typeface, resolved onto a copy so the built-in styles
     * themselves never change. The text colours are then run through
     * the contrast check: the face is translucent over the wallpaper,
     * so the text must be readable on the face the eye actually sees
     * (face colour at its opacity, over the Studio's background
     * luminance) — not just on the designed face. A style's own colours
     * survive whenever they already contrast; a saved override that
     * would be invisible is flipped to white or near-black instead. */
    fun resolved(s: DigitalStyle, opts: DialSettings): DigitalStyle {
        val bg = opts.faceColor ?: s.bg
        val alpha = DialRenderer.faceAlpha(opts)
        val faceLum = DialRenderer.luminance(bg)
        return s.copy(
            bg = bg,
            fg = DialRenderer.readableTextOn(opts.handsColor ?: s.fg, faceLum, alpha, opts.backgroundLum),
            accent = DialRenderer.readableTextOn(opts.accentColor ?: s.accent, faceLum, alpha, opts.backgroundLum),
            family = opts.fontFamily ?: s.family
        )
    }

    // ---- time text ------------------------------------------------------
    private fun hourStr(time: Calendar, hour24: Boolean): String {
        val h = if (hour24) time.get(Calendar.HOUR_OF_DAY)
        else { val v = time.get(Calendar.HOUR); if (v == 0) 12 else v }
        return "%02d".format(h)
    }

    private fun timeText(time: Calendar, hour24: Boolean, showColon: Boolean = true): String =
        "${hourStr(time, hour24)}${if (showColon) ":" else " "}%02d".format(time.get(Calendar.MINUTE))

    /** The style's typeface at the Studio weight. No weight picked yet =
     * the style's own weight (bold styles stay bold, light ones stay light). */
    private fun typefaceOf(s: DigitalStyle, opts: DialSettings): Typeface {
        val weight = opts.fontWeight ?: if (s.bold) 0.8f else 0.45f
        return DialRenderer.weightedTypeface(s.family, weight, s.italic)
    }

    // ---- 5x7 pixel font (digits only; ':' is two dots) -------------------
    // Each digit is 7 rows of 5 cells, '#' = on.
    private val PIXEL_FONT: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to arrayOf("01110", "10001", "00001", "00110", "01000", "10000", "11111"),
        '3' to arrayOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
        '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100")
    )

    // ---- seven-segment map (same layout as the classic Seiko LCD) --------
    private val SEG: Map<Char, BooleanArray> = mapOf(
        '0' to booleanArrayOf(true, true, true, true, true, true, false),
        '1' to booleanArrayOf(false, true, true, false, false, false, false),
        '2' to booleanArrayOf(true, true, false, true, true, false, true),
        '3' to booleanArrayOf(true, true, true, true, false, false, true),
        '4' to booleanArrayOf(false, true, true, false, false, true, true),
        '5' to booleanArrayOf(true, false, true, true, false, true, true),
        '6' to booleanArrayOf(true, false, true, true, true, true, true),
        '7' to booleanArrayOf(true, true, true, false, false, false, false),
        '8' to booleanArrayOf(true, true, true, true, true, true, true),
        '9' to booleanArrayOf(true, true, true, true, false, true, true)
    )

    // ---- entry points ----------------------------------------------------
    /** Draw inside the square watch face (gallery previews + square widget). */
    fun draw(c: Canvas, dial: Dial, size: Int, faceR: Float, time: Calendar, opts: DialSettings) {
        val s = byId(dial.style)?.let { resolved(it, opts) } ?: return
        val cx = size / 2f
        val cy = size / 2f
        val rect = RectF(cx - faceR, cy - faceR, cx + faceR, cy + faceR)
        c.save()
        c.clipPath(android.graphics.Path().apply {
            addRoundRect(rect, faceR * 0.30f, faceR * 0.30f, android.graphics.Path.Direction.CW)
        })
        drawInto(c, rect, s, time, opts, faceR * 0.30f)
        c.restore()
    }

    /** Draw across a full rectangle (responsive wide/tall widget). */
    fun drawRect(c: Canvas, w: Int, h: Int, time: Calendar, opts: DialSettings, style: DigitalStyle) {
        val pad = min(w, h) * 0.04f
        val rect = RectF(pad, pad, w - pad, h - pad)
        c.save()
        c.clipPath(android.graphics.Path().apply {
            addRoundRect(rect, min(w, h) * 0.12f, min(w, h) * 0.12f, android.graphics.Path.Direction.CW)
        })
        drawInto(c, rect, resolved(style, opts), time, opts, min(w, h) * 0.12f)
        c.restore()
    }

    // ---- core layout ------------------------------------------------------
    private fun drawInto(c: Canvas, rect: RectF, s: DigitalStyle, time: Calendar, opts: DialSettings, corner: Float) {
        drawBackground(c, rect, s, corner, opts)
        val text = timeText(time, opts.hour24, opts.showColon)
        val cx = rect.centerX()
        // Date line takes room below the time; shift the time up a little then.
        // The date only shows when the style draws one AND the user wants dates.
        val showDate = s.showDate && opts.dateWindow
        val cy = rect.centerY() - if (showDate) rect.height() * 0.055f else 0f
        drawTime(c, rect, cx, cy, text, s, time, opts)
        if (showDate) {
            val date = SimpleDateFormat("EEE d MMM", Locale.ENGLISH)
                .format(time.time).uppercase(Locale.ENGLISH)
            val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, Color.red(s.fg), Color.green(s.fg), Color.blue(s.fg))
                textSize = rect.height() * 0.062f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.14f
            }
            c.drawText(date, cx, rect.centerY() + rect.height() * 0.30f, dp)
        }
    }

    // Frosted-glass background: the style's own colour at ~60% opacity, so
    // the home-screen wallpaper faintly shows through the widget bitmap.
    // With Solid face on, the face is fully opaque instead — the preview
    // colours are then the widget colours on any wallpaper.
    // The time text is drawn opaque on top, in a contrast-checked colour,
    // so it stays readable on the face the eye actually sees.
    private fun drawBackground(c: Canvas, rect: RectF, s: DigitalStyle, corner: Float, opts: DialSettings) {
        val glass = DialRenderer.glassColor(s.bg, DialRenderer.faceAlpha(opts))
        when (s.texture) {
            Texture.SOLID -> c.drawRect(rect, Paint().apply { color = glass })
            Texture.GRADIENT_BG -> c.drawRect(rect, Paint().apply {
                shader = LinearGradient(
                    rect.left, rect.top, rect.left, rect.bottom,
                    glass, shade(glass, 0.55f), Shader.TileMode.CLAMP
                )
            })
            Texture.GRAIN -> {
                c.drawRect(rect, Paint().apply { color = glass })
                val rnd = java.util.Random(s.id.hashCode().toLong())
                val dot = Paint().apply { color = Color.argb(26, 255, 255, 255) }
                val dot2 = Paint().apply { color = Color.argb(20, 0, 0, 0) }
                val n = (rect.width() * rect.height() / 260f).toInt().coerceIn(120, 700)
                repeat(n) {
                    val x = rect.left + rnd.nextFloat() * rect.width()
                    val y = rect.top + rnd.nextFloat() * rect.height()
                    val r = rect.width() * 0.0035f
                    c.drawCircle(x, y, r, if (rnd.nextBoolean()) dot else dot2)
                }
            }
            Texture.FROSTED -> {
                c.drawRect(rect, Paint().apply { color = glass })
                c.drawRect(rect, Paint().apply { color = Color.argb(70, 255, 255, 255) })
                c.save()
                c.rotate(-28f, rect.centerX(), rect.centerY())
                c.drawRect(
                    rect.left - rect.width() * 0.2f, rect.top + rect.height() * 0.15f,
                    rect.right + rect.width() * 0.2f, rect.top + rect.height() * 0.45f,
                    Paint().apply {
                        shader = LinearGradient(
                            0f, rect.top + rect.height() * 0.15f, 0f, rect.top + rect.height() * 0.45f,
                            intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(60, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                        )
                    }
                )
                c.restore()
            }
            Texture.NEON -> {
                c.drawRect(rect, Paint().apply { color = glass })
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = s.accent
                    style = Paint.Style.STROKE
                    strokeWidth = rect.height() * 0.02f
                    setShadowLayer(rect.height() * 0.10f, 0f, 0f, s.accent)
                }
                c.drawRoundRect(
                    RectF(rect.left + rect.height() * 0.04f, rect.top + rect.height() * 0.04f,
                        rect.right - rect.height() * 0.04f, rect.bottom - rect.height() * 0.04f),
                    rect.height() * 0.10f, rect.height() * 0.10f, glow
                )
            }
        }
        // Frosted finish: faint speckle, top light, bright rim.
        DialRenderer.drawGlassFinish(c, rect, corner, s.id.hashCode().toLong())
    }

    // ---- time drawing -----------------------------------------------------
    private fun basePaint(s: DigitalStyle, textSize: Float, opts: DialSettings): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.fg
            this.textSize = textSize
            typeface = typefaceOf(s, opts)
            textAlign = Paint.Align.CENTER
        }

    /** Baseline y that centres text of size ts on cy. */
    private fun baseline(p: Paint, cy: Float): Float =
        cy - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2f

    private fun drawTime(c: Canvas, rect: RectF, cx: Float, cy: Float, text: String, s: DigitalStyle, time: Calendar, opts: DialSettings) {
        // Stacked layout returns where the minutes line sits, so the
        // seconds suffix and AM/PM can hang off it instead of the centre.
        val stackedInfo: StackedInfo? = when (s.effect) {
            Effect.PIXEL, Effect.DOTS, Effect.SEGMENT, Effect.BLOCK -> {
                drawCustomDigits(c, rect, cx, cy, text, s)
                null
            }
            else -> drawTextTime(c, rect, cx, cy, text, s, opts)
        }
        // Small live seconds suffix (":SS") when the user wants a ticking
        // digital. Drawn right after the time at ~40% size, in the same
        // (contrast-checked) text colour — this is the visible tick on
        // the widget, driven by the second-tick service. Skipped when
        // there is no room for it.
        var secondsW = 0f
        if (opts.secondHand) {
            val ss = ":%02d".format(time.get(Calendar.SECOND))
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = s.fg
                textSize = rect.height() * 0.16f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textAlign = Paint.Align.LEFT
            }
            if (stackedInfo != null) {
                val sx = cx + stackedInfo.mmWidth / 2f + rect.width() * 0.02f
                if (sx + sp.measureText(ss) < rect.right - rect.width() * 0.02f) {
                    c.drawText(ss, sx, stackedInfo.mmBaseline, sp)
                    secondsW = sp.measureText(ss) + rect.width() * 0.02f
                }
            } else {
                val tw = timeWidthEstimate(rect, text, s, opts)
                val sx = cx + tw / 2f + rect.width() * 0.02f
                if (sx + sp.measureText(ss) < rect.right - rect.width() * 0.02f) {
                    c.drawText(ss, sx, baseline(sp, cy), sp)
                    secondsW = sp.measureText(ss) + rect.width() * 0.02f
                }
            }
        }
        // Small AM / PM marker for 12-hour mode (drawn after the time, top-right).
        if (!opts.hour24) {
            val ampm = if (time.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM"
            val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, Color.red(s.accent), Color.green(s.accent), Color.blue(s.accent))
                textSize = rect.height() * 0.075f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textAlign = Paint.Align.LEFT
            }
            if (stackedInfo != null) {
                val ax = cx + stackedInfo.mmWidth / 2f + rect.width() * 0.02f + secondsW
                if (ax + ap.measureText(ampm) < rect.right - rect.width() * 0.03f) {
                    c.drawText(ampm, ax, stackedInfo.mmBaseline, ap)
                }
            } else {
                val tw = timeWidthEstimate(rect, text, s, opts)
                val ax = cx + tw / 2f + rect.width() * 0.02f + secondsW
                if (ax + ap.measureText(ampm) < rect.right - rect.width() * 0.03f) {
                    c.drawText(ampm, ax, cy - rect.height() * 0.10f, ap)
                }
            }
        }
    }

    /** Rough width of the drawn time, used to place the AM/PM marker. */
    private fun timeWidthEstimate(rect: RectF, text: String, s: DigitalStyle, opts: DialSettings): Float {
        return when (s.effect) {
            Effect.PIXEL, Effect.DOTS, Effect.SEGMENT, Effect.BLOCK -> {
                val cellH = rect.height() * 0.52f
                val cellW = cellH * 0.72f
                val gap = cellW * 0.18f
                cellW * 4 + cellW * 0.5f + gap * 4
            }
            else -> {
                val p = basePaint(s, rect.height() * 0.40f, opts)
                p.measureText(text)
            }
        }
    }

    /** Where the minutes line sits in a stacked layout — the seconds
     * suffix and AM/PM hang off it. */
    private data class StackedInfo(val mmWidth: Float, val mmBaseline: Float)

    /** Hours over minutes, like the stacked clock in the reference
     * screenshots. Plain lines — the fancy per-effect paths are one-row
     * only, and the kept style (Black, FILL) draws plain anyway. */
    private fun drawStackedTime(c: Canvas, rect: RectF, cx: Float, cy: Float, text: String, s: DigitalStyle, opts: DialSettings): StackedInfo {
        val parts = text.split(':', ' ').filter { it.isNotEmpty() }
        val hh = parts.getOrElse(0) { "--" }
        val mm = parts.getOrElse(1) { "--" }
        var ts = rect.height() * 0.30f * opts.digitalScale
        val maxW = rect.width() * 0.80f
        var p = basePaint(s, ts, opts)
        applyEffect(p, s, ts, rect)
        while (maxOf(p.measureText(hh), p.measureText(mm)) > maxW && ts > rect.height() * 0.10f) {
            ts *= 0.92f
            p = basePaint(s, ts, opts)
            applyEffect(p, s, ts, rect)
        }
        val lineH = ts * 1.08f
        val hhCy = cy - lineH / 2f
        val mmCy = cy + lineH / 2f
        c.drawText(hh, cx, baseline(p, hhCy), p)
        c.drawText(mm, cx, baseline(p, mmCy), p)
        return StackedInfo(p.measureText(mm), baseline(p, mmCy))
    }

    private fun drawTextTime(c: Canvas, rect: RectF, cx: Float, cy: Float, text: String, s: DigitalStyle, opts: DialSettings): StackedInfo? {
        if (opts.digitalLayout == DigitalLayout.STACKED) {
            return drawStackedTime(c, rect, cx, cy, text, s, opts)
        }
        // Shrink until the whole time fits the face width (never clip).
        var ts = rect.height() * 0.42f * opts.digitalScale
        val maxW = rect.width() * 0.88f
        var p = basePaint(s, ts, opts)
        applyEffect(p, s, ts, rect)
        while (p.measureText(text) > maxW && ts > rect.height() * 0.10f) {
            ts *= 0.92f
            p = basePaint(s, ts, opts)
            applyEffect(p, s, ts, rect)
        }
        when (s.effect) {
            Effect.WAVY -> {
                val w = p.measureText(text)
                val path = Path().apply {
                    val x0 = cx - w / 2f
                    val amp = ts * 0.09f
                    val seg = w / 6f
                    moveTo(x0, cy)
                    var x = x0
                    var up = true
                    while (x < cx + w / 2f) {
                        quadTo(x + seg / 2f, cy + if (up) -amp else amp, x + seg, cy)
                        x += seg
                        up = !up
                    }
                }
                c.drawTextOnPath(text, path, 0f, 0f, p)
            }
            Effect.SHADOW -> {
                val sh = Paint(p).apply { color = Color.argb(120, 0, 0, 0); shader = null; setShadowLayer(0f, 0f, 0f, 0) }
                c.drawText(text, cx + ts * 0.06f, baseline(p, cy) + ts * 0.08f, sh)
                c.drawText(text, cx, baseline(p, cy), p)
            }
            Effect.DUAL_TONE -> {
                val hh = text.substring(0, 2)
                val rest = text.substring(2)
                val wHh = p.measureText(hh)
                val wRest = p.measureText(rest)
                val total = wHh + wRest
                var x = cx - total / 2f
                val y = baseline(p, cy)
                val pHh = Paint(p).apply { textAlign = Paint.Align.LEFT }
                c.drawText(hh, x, y, pHh)
                x += wHh
                val pRest = Paint(p).apply { textAlign = Paint.Align.LEFT; color = s.accent; shader = null }
                c.drawText(rest, x, y, pRest)
            }
            Effect.RAINBOW -> {
                val palette = intArrayOf(
                    0xFFE53935.toInt(), 0xFFFF7A00.toInt(), 0xFFFFD600.toInt(),
                    0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt()
                )
                var total = 0f
                for (ch in text) total += p.measureText(ch.toString())
                var x = cx - total / 2f
                val y = baseline(p, cy)
                text.forEachIndexed { i, ch ->
                    val pc = Paint(p).apply {
                        textAlign = Paint.Align.LEFT
                        color = if (ch == ':') s.fg else palette[i % palette.size]
                        shader = null
                    }
                    c.drawText(ch.toString(), x, y, pc)
                    x += p.measureText(ch.toString())
                }
            }
            Effect.GEOMETRIC -> {
                c.drawText(text, cx, baseline(p, cy), p)
                // Three accent dots floating over the digits — the "orbit" look.
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.accent }
                val w = p.measureText(text)
                c.drawCircle(cx - w * 0.30f, cy - ts * 0.42f, ts * 0.085f, dot)
                c.drawCircle(cx + w * 0.08f, cy + ts * 0.42f, ts * 0.085f, dot)
                c.drawCircle(cx + w * 0.38f, cy - ts * 0.30f, ts * 0.085f, dot)
            }
            else -> c.drawText(text, cx, baseline(p, cy), p)
        }
        return null
    }

    /** Per-effect paint setup (outline / glow / gradient / stripes). */
    private fun applyEffect(p: Paint, s: DigitalStyle, ts: Float, rect: RectF) {
        when (s.effect) {
            Effect.OUTLINE -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = ts * 0.055f
                p.strokeJoin = Paint.Join.ROUND
            }
            Effect.GLOW -> p.setShadowLayer(ts * 0.35f, 0f, 0f, s.fg)
            Effect.GRADIENT -> {
                p.shader = LinearGradient(
                    0f, rect.centerY() - ts * 0.5f, 0f, rect.centerY() + ts * 0.5f,
                    s.fg, s.accent, Shader.TileMode.CLAMP
                )
            }
            Effect.STRIPES -> {
                val stripeH = ts / 5f
                p.shader = LinearGradient(
                    0f, 0f, 0f, stripeH,
                    intArrayOf(s.fg, s.fg, Color.TRANSPARENT, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.45f, 0.55f, 1f), Shader.TileMode.REPEAT
                )
            }
            else -> {}
        }
    }

    // ---- custom-drawn digits (pixel / dots / seven-seg / block) ----------
    private fun drawCustomDigits(c: Canvas, rect: RectF, cx: Float, cy: Float, text: String, s: DigitalStyle) {
        val cellH = rect.height() * 0.52f
        val cellW = cellH * 0.72f
        val gap = cellW * 0.18f
        val colonW = cellW * 0.5f
        val total = cellW * 4 + colonW + gap * 4
        var scale = 1f
        val maxW = rect.width() * 0.88f
        if (total > maxW) scale = maxW / total
        val cw = cellW * scale
        val ch = cellH * scale
        val gp = gap * scale
        val colW = colonW * scale
        val top = cy - ch / 2f
        var x = cx - (cw * 4 + colW + gp * 4) / 2f
        val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = s.fg }
        val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(22, Color.red(s.fg), Color.green(s.fg), Color.blue(s.fg)) }
        for (ch2 in text) {
            when (ch2) {
                ':' -> {
                    drawColon(c, x, top, colW, ch, s, on)
                    x += colW + gp
                }
                else -> {
                    when (s.effect) {
                        Effect.PIXEL -> drawPixelDigit(c, ch2, x, top, cw, ch, on, off)
                        Effect.DOTS -> drawDotDigit(c, ch2, x, top, cw, ch, on, off)
                        Effect.SEGMENT -> drawSegDigit(c, ch2, x, top, cw, ch, on, off)
                        Effect.BLOCK -> drawBlockDigit(c, ch2, x, top, cw, ch, on, off)
                        else -> {}
                    }
                    x += cw + gp
                }
            }
        }
    }

    private fun drawColon(c: Canvas, x: Float, top: Float, w: Float, h: Float, s: DigitalStyle, on: Paint) {
        val r = w * 0.22f
        val cx = x + w / 2f
        c.drawCircle(cx, top + h * 0.32f, r, on)
        c.drawCircle(cx, top + h * 0.68f, r, on)
    }

    private fun drawPixelDigit(c: Canvas, d: Char, left: Float, top: Float, w: Float, h: Float, on: Paint, off: Paint) {
        val grid = PIXEL_FONT[d] ?: return
        val cw = w / 5f
        val ch = h / 7f
        for (row in 0 until 7) for (col in 0 until 5) {
            val lit = grid[row][col] == '1'
            if (!lit) continue
            c.drawRect(RectF(left + col * cw + cw * 0.06f, top + row * ch + ch * 0.06f,
                left + (col + 1) * cw - cw * 0.06f, top + (row + 1) * ch - ch * 0.06f), on)
        }
    }

    private fun drawDotDigit(c: Canvas, d: Char, left: Float, top: Float, w: Float, h: Float, on: Paint, off: Paint) {
        val grid = PIXEL_FONT[d] ?: return
        val cw = w / 5f
        val ch = h / 7f
        for (row in 0 until 7) for (col in 0 until 5) {
            if (grid[row][col] != '1') continue
            c.drawCircle(left + col * cw + cw / 2f, top + row * ch + ch / 2f, min(cw, ch) * 0.42f, on)
        }
    }

    private fun drawSegDigit(c: Canvas, d: Char, left: Float, top: Float, w: Float, h: Float, on: Paint, off: Paint) {
        val t = w * 0.20f
        val segs = SEG[d] ?: return
        fun hSeg(x: Float, y: Float, lit: Boolean) {
            c.drawRect(RectF(x, y, x + w, y + t), if (lit) on else off)
        }
        fun vSeg(x: Float, y: Float, lit: Boolean) {
            c.drawRect(RectF(x, y, x + t, y + h / 2), if (lit) on else off)
        }
        hSeg(left, top, segs[0])
        vSeg(left + w - t, top, segs[1])
        vSeg(left + w - t, top + h / 2, segs[2])
        hSeg(left, top + h - t, segs[3])
        vSeg(left, top + h / 2, segs[4])
        vSeg(left, top, segs[5])
        hSeg(left, top + h / 2 - t / 2, segs[6])
    }

    private fun drawBlockDigit(c: Canvas, d: Char, left: Float, top: Float, w: Float, h: Float, on: Paint, off: Paint) {
        // Chunky square-corner segments with a visible gap between them.
        val t = w * 0.24f
        val g = t * 0.16f
        val segs = SEG[d] ?: return
        fun hSeg(x: Float, y: Float, lit: Boolean) {
            if (!lit) return
            c.drawRect(RectF(x + g, y + g, x + w - g, y + t - g), on)
        }
        fun vSeg(x: Float, y: Float, lit: Boolean) {
            if (!lit) return
            c.drawRect(RectF(x + g, y + g, x + t - g, y + h / 2 - g), on)
        }
        hSeg(left, top, segs[0])
        vSeg(left + w - t, top, segs[1])
        vSeg(left + w - t, top + h / 2, segs[2])
        hSeg(left, top + h - t, segs[3])
        vSeg(left, top + h / 2, segs[4])
        vSeg(left, top, segs[5])
        hSeg(left, top + h / 2 - t / 2, segs[6])
    }

    // ---- small bitmap helper (used by renderRect) ------------------------
    fun renderBitmap(w: Int, h: Int, time: Calendar, opts: DialSettings, style: DigitalStyle): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawRect(c, w, h, time, opts, style)
        return bmp
    }
}
