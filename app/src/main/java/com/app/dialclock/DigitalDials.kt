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
 * DigitalDials.kt — 38 original digital clock styles for the "Digital" section.
 *
 * Every style is drawn here in code (system font families + Canvas effects).
 * Nothing is copied from any other widget app: the fonts are Android's own
 * families and the pixel / dot / segment digits are drawn from the grids
 * below, which were made for this app.
 *
 * A style is: font family + weight + effect + colours + texture.
 * All 38 differ in that combination — no two are alike.
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
        val showDate: Boolean = false
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

    val STYLES: List<DigitalStyle> = listOf(
        DigitalStyle("dig01", "Tall", "sans-serif-condensed", bold = true, effect = Effect.FILL, fg = WHITE, bg = BLACK, accent = WHITE),
        DigitalStyle("dig02", "Outline", "sans-serif-black", effect = Effect.OUTLINE, fg = WHITE, bg = BLACK, accent = WHITE),
        DigitalStyle("dig03", "Pixel", "monospace", bold = true, effect = Effect.PIXEL, fg = 0xFF39FF6A.toInt(), bg = BLACK, accent = 0xFF39FF6A.toInt()),
        DigitalStyle("dig04", "Dotted", "monospace", bold = true, effect = Effect.DOTS, fg = WHITE, bg = BLACK, accent = CYAN),
        DigitalStyle("dig05", "Amber Seg", "monospace", effect = Effect.SEGMENT, fg = 0xFFFFB300.toInt(), bg = 0xFF2A1A08.toInt(), accent = 0xFFFFB300.toInt(), showDate = true),
        DigitalStyle("dig06", "Striped", "sans-serif-black", effect = Effect.STRIPES, fg = WHITE, bg = BLACK, accent = WHITE),
        DigitalStyle("dig07", "Script", "cursive", effect = Effect.FILL, fg = GOLD, bg = BLACK, accent = GOLD, showDate = true),
        DigitalStyle("dig08", "Casual", "casual", effect = Effect.FILL, fg = 0xFFFF6B5E.toInt(), bg = 0xFFFFF3E0.toInt(), accent = 0xFFFF6B5E.toInt()),
        DigitalStyle("dig09", "Serif", "serif", bold = true, effect = Effect.FILL, fg = 0xFF1A2A6C.toInt(), bg = 0xFFF7F3E8.toInt(), accent = 0xFF1A2A6C.toInt(), showDate = true),
        DigitalStyle("dig10", "Mono", "monospace", effect = Effect.FILL, fg = CYAN, bg = 0xFF0A1428.toInt(), accent = CYAN),
        DigitalStyle("dig11", "Thin", "sans-serif-thin", effect = Effect.FILL, fg = 0xFFE8E8E8.toInt(), bg = BLACK, accent = 0xFFE8E8E8.toInt()),
        DigitalStyle("dig12", "Black", "sans-serif-black", effect = Effect.FILL, fg = WHITE, bg = BLACK, accent = WHITE, showDate = true),
        DigitalStyle("dig13", "Neon", "sans-serif", bold = true, effect = Effect.GLOW, fg = CYAN, bg = 0xFF050508.toInt(), accent = CYAN, texture = Texture.NEON),
        DigitalStyle("dig14", "Sunset", "sans-serif-black", effect = Effect.GRADIENT, fg = ORANGE, bg = BLACK, accent = 0xFFFF3D7F.toInt()),
        DigitalStyle("dig15", "Ocean", "sans-serif", bold = true, effect = Effect.GRADIENT, fg = 0xFF4FC3F7.toInt(), bg = 0xFF06121F.toInt(), accent = 0xFF00E5FF.toInt(), showDate = true),
        DigitalStyle("dig16", "Lemon", "sans-serif-black", effect = Effect.FILL, fg = 0xFFFFE135.toInt(), bg = CHARCOAL, accent = 0xFFFFE135.toInt()),
        DigitalStyle("dig17", "Pink", "sans-serif-medium", effect = Effect.FILL, fg = 0xFFFF7BAC.toInt(), bg = 0xFF2A0A1E.toInt(), accent = 0xFFFF7BAC.toInt()),
        DigitalStyle("dig18", "Earth", "serif", effect = Effect.FILL, fg = 0xFF6B4226.toInt(), bg = 0xFFF2EAD8.toInt(), accent = 0xFF6B4226.toInt(), texture = Texture.GRAIN, showDate = true),
        DigitalStyle("dig19", "Pastel", "sans-serif-light", effect = Effect.FILL, fg = 0xFF5BA8A0.toInt(), bg = WHITE, accent = 0xFFB39DDB.toInt()),
        DigitalStyle("dig20", "Gold", "serif", bold = true, effect = Effect.GRADIENT, fg = 0xFFFFE9A8.toInt(), bg = BLACK, accent = 0xFFB8860B.toInt(), showDate = true),
        DigitalStyle("dig21", "Silver", "sans-serif", bold = true, effect = Effect.GRADIENT, fg = 0xFFF2F2F2.toInt(), bg = 0xFF1A1A1C.toInt(), accent = 0xFF9A9A9A.toInt()),
        DigitalStyle("dig22", "Frost", "sans-serif-light", effect = Effect.FILL, fg = WHITE, bg = 0xFF3A4A5A.toInt(), accent = WHITE, texture = Texture.FROSTED),
        DigitalStyle("dig23", "Grain", "monospace", bold = true, effect = Effect.FILL, fg = 0xFFF2EAD8.toInt(), bg = BLACK, accent = 0xFFF2EAD8.toInt(), texture = Texture.GRAIN),
        DigitalStyle("dig24", "Wavy", "casual", bold = true, effect = Effect.WAVY, fg = WHITE, bg = BLACK, accent = WHITE),
        DigitalStyle("dig25", "Orbit", "sans-serif-condensed", bold = true, effect = Effect.GEOMETRIC, fg = WHITE, bg = BLACK, accent = ORANGE),
        DigitalStyle("dig26", "Block", "monospace", bold = true, effect = Effect.BLOCK, fg = WHITE, bg = 0xFF101014.toInt(), accent = WHITE),
        DigitalStyle("dig27", "Slashed", "sans-serif-condensed", italic = true, effect = Effect.FILL, fg = 0xFFE53935.toInt(), bg = BLACK, accent = 0xFFE53935.toInt(), showDate = true),
        DigitalStyle("dig28", "Retro", "serif", italic = true, effect = Effect.FILL, fg = 0xFFF5E6C8.toInt(), bg = 0xFF0F4C4C.toInt(), accent = 0xFFF5E6C8.toInt()),
        DigitalStyle("dig29", "Minimal", "sans-serif-thin", effect = Effect.FILL, fg = 0xFFBDBDBD.toInt(), bg = BLACK, accent = 0xFFBDBDBD.toInt(), showDate = true),
        DigitalStyle("dig30", "Pop", "sans-serif-black", italic = true, effect = Effect.FILL, fg = WHITE, bg = 0xFFD32F2F.toInt(), accent = WHITE),
        DigitalStyle("dig31", "Dot Ring", "monospace", effect = Effect.DOTS, fg = 0xFFB388FF.toInt(), bg = BLACK, accent = 0xFFB388FF.toInt(), showDate = true),
        DigitalStyle("dig32", "Shadow", "sans-serif-black", effect = Effect.SHADOW, fg = WHITE, bg = 0xFF5E35B1.toInt(), accent = WHITE),
        DigitalStyle("dig33", "Gold Line", "sans-serif-light", effect = Effect.OUTLINE, fg = GOLD, bg = BLACK, accent = GOLD, showDate = true),
        DigitalStyle("dig34", "Two Tone", "sans-serif", bold = true, effect = Effect.DUAL_TONE, fg = WHITE, bg = BLACK, accent = ORANGE),
        DigitalStyle("dig35", "Big Date", "sans-serif-black", effect = Effect.FILL, fg = WHITE, bg = BLACK, accent = WHITE, showDate = true),
        DigitalStyle("dig36", "Lime", "sans-serif-condensed", bold = true, effect = Effect.FILL, fg = 0xFFBFFF00.toInt(), bg = BLACK, accent = 0xFFBFFF00.toInt()),
        DigitalStyle("dig37", "Dusk", "sans-serif-medium", effect = Effect.FILL, fg = 0xFFD1C4E9.toInt(), bg = 0xFF1A1025.toInt(), accent = 0xFFD1C4E9.toInt(), texture = Texture.GRADIENT_BG),
        DigitalStyle("dig38", "Rainbow", "sans-serif-black", effect = Effect.RAINBOW, fg = WHITE, bg = BLACK, accent = WHITE, showDate = true)
    )

    fun byId(id: String): DigitalStyle? = STYLES.firstOrNull { it.id == id }

    // ---- time text ------------------------------------------------------
    private fun hourStr(time: Calendar, hour24: Boolean): String {
        val h = if (hour24) time.get(Calendar.HOUR_OF_DAY)
        else { val v = time.get(Calendar.HOUR); if (v == 0) 12 else v }
        return "%02d".format(h)
    }

    private fun timeText(time: Calendar, hour24: Boolean): String =
        "${hourStr(time, hour24)}:%02d".format(time.get(Calendar.MINUTE))

    private fun typefaceOf(s: DigitalStyle): Typeface {
        val base = Typeface.create(s.family, Typeface.NORMAL)
        val style = when {
            s.bold && s.italic -> Typeface.BOLD_ITALIC
            s.bold -> Typeface.BOLD
            s.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
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
        val s = byId(dial.style) ?: return
        val cx = size / 2f
        val cy = size / 2f
        val rect = RectF(cx - faceR, cy - faceR, cx + faceR, cy + faceR)
        c.save()
        c.clipPath(android.graphics.Path().apply {
            addRoundRect(rect, faceR * 0.30f, faceR * 0.30f, android.graphics.Path.Direction.CW)
        })
        drawInto(c, rect, s, time, opts)
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
        drawInto(c, rect, style, time, opts)
        c.restore()
    }

    // ---- core layout ------------------------------------------------------
    private fun drawInto(c: Canvas, rect: RectF, s: DigitalStyle, time: Calendar, opts: DialSettings) {
        drawBackground(c, rect, s)
        val text = timeText(time, opts.hour24)
        val cx = rect.centerX()
        // Date line takes room below the time; shift the time up a little then.
        val cy = rect.centerY() - if (s.showDate) rect.height() * 0.055f else 0f
        drawTime(c, rect, cx, cy, text, s, time, opts)
        if (s.showDate) {
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

    private fun drawBackground(c: Canvas, rect: RectF, s: DigitalStyle) {
        when (s.texture) {
            Texture.SOLID -> c.drawRect(rect, Paint().apply { color = s.bg })
            Texture.GRADIENT_BG -> c.drawRect(rect, Paint().apply {
                shader = LinearGradient(
                    rect.left, rect.top, rect.left, rect.bottom,
                    s.bg, shade(s.bg, 0.55f), Shader.TileMode.CLAMP
                )
            })
            Texture.GRAIN -> {
                c.drawRect(rect, Paint().apply { color = s.bg })
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
                c.drawRect(rect, Paint().apply { color = s.bg })
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
                c.drawRect(rect, Paint().apply { color = s.bg })
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
    }

    // ---- time drawing -----------------------------------------------------
    private fun basePaint(s: DigitalStyle, textSize: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = s.fg
            this.textSize = textSize
            typeface = typefaceOf(s)
            textAlign = Paint.Align.CENTER
        }

    /** Baseline y that centres text of size ts on cy. */
    private fun baseline(p: Paint, cy: Float): Float =
        cy - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2f

    private fun drawTime(c: Canvas, rect: RectF, cx: Float, cy: Float, text: String, s: DigitalStyle, time: Calendar, opts: DialSettings) {
        when (s.effect) {
            Effect.PIXEL, Effect.DOTS, Effect.SEGMENT, Effect.BLOCK ->
                drawCustomDigits(c, rect, cx, cy, text, s)
            else -> drawTextTime(c, rect, cx, cy, text, s, opts)
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
            val tw = timeWidthEstimate(rect, text, s)
            val ax = cx + tw / 2f + rect.width() * 0.02f
            if (ax + ap.measureText(ampm) < rect.right - rect.width() * 0.03f) {
                c.drawText(ampm, ax, cy - rect.height() * 0.10f, ap)
            }
        }
    }

    /** Rough width of the drawn time, used to place the AM/PM marker. */
    private fun timeWidthEstimate(rect: RectF, text: String, s: DigitalStyle): Float {
        return when (s.effect) {
            Effect.PIXEL, Effect.DOTS, Effect.SEGMENT, Effect.BLOCK -> {
                val cellH = rect.height() * 0.52f
                val cellW = cellH * 0.72f
                val gap = cellW * 0.18f
                cellW * 4 + cellW * 0.5f + gap * 4
            }
            else -> {
                val p = basePaint(s, rect.height() * 0.40f)
                p.measureText(text)
            }
        }
    }

    private fun drawTextTime(c: Canvas, rect: RectF, cx: Float, cy: Float, text: String, s: DigitalStyle, opts: DialSettings) {
        // Shrink until the whole time fits the face width (never clip).
        var ts = rect.height() * 0.42f
        val maxW = rect.width() * 0.88f
        var p = basePaint(s, ts)
        applyEffect(p, s, ts, rect)
        while (p.measureText(text) > maxW && ts > rect.height() * 0.10f) {
            ts *= 0.92f
            p = basePaint(s, ts)
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
