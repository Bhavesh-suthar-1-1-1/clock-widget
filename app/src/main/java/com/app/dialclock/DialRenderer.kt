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
import android.graphics.Typeface
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Draws every dial onto a Bitmap. One renderer is shared by the widget and the in-app previews. */
object DialRenderer {

    /** Full render, hands included — what the widget and the previews use. */
    fun render(dial: Dial, sizePx: Int, time: Calendar, opts: DialSettings = DialSettings()): Bitmap =
        renderInternal(dial, sizePx, time, opts, includeHands = true)

    /**
     * Face only: everything EXCEPT the hands (strap, case, face, numerals,
     * date, glass, grain). The live second-hand service caches this bitmap
     * and draws only the hands on top once per second — a full re-render
     * every second would re-randomise the grain and flicker.
     */
    fun renderFace(dial: Dial, sizePx: Int, time: Calendar, opts: DialSettings = DialSettings()): Bitmap =
        renderInternal(dial, sizePx, time, opts, includeHands = false)

    /**
     * Draw the hour/minute/second hands onto a face bitmap (same transforms
     * as render(), so the hands land exactly on the face).
     */
    fun drawHandsOnto(bmp: Bitmap, dial: Dial, time: Calendar, opts: DialSettings = DialSettings()) {
        val d = dial.withShape(opts.shape)
        val size = bmp.width
        val mid = size / 2f
        val c = Canvas(bmp)
        if (opts.strap) {
            c.save()
            c.scale(DialRealism.BODY_SCALE, DialRealism.BODY_SCALE, mid, mid)
        }
        c.save()
        c.scale(d.aspect, 1f, mid, mid)
        drawMainHands(c, d, size, size * 0.385f, time, opts)
        c.restore()
        if (opts.strap) c.restore()
    }

    /** Dials whose main hands can live-tick once per second. */
    fun hasLiveHands(dial: Dial): Boolean = when (dial.kind) {
        DialKind.ANALOG, DialKind.MOON, DialKind.FRENCH_OVAL, DialKind.SEMPER -> true
        else -> false
    }

    /**
     * Rectangular render for responsive widgets. Analog kinds draw the square
     * watch centred (never stretched); digital and hybrid kinds lay out
     * across the full rectangle. Long side is capped at 720px.
     */
    fun renderRect(dial: Dial, widthPx: Int, heightPx: Int, time: Calendar, opts: DialSettings = DialSettings()): Bitmap =
        renderRectInternal(dial, widthPx, heightPx, time, opts, includeHands = true)

    /** Rectangular face without hands (live-tick cache for wide/tall widgets). */
    fun renderRectFace(dial: Dial, widthPx: Int, heightPx: Int, time: Calendar, opts: DialSettings = DialSettings()): Bitmap =
        renderRectInternal(dial, widthPx, heightPx, time, opts, includeHands = false)

    private fun renderRectInternal(dial: Dial, widthPx: Int, heightPx: Int, time: Calendar, opts: DialSettings, includeHands: Boolean): Bitmap {
        val w = widthPx.coerceAtLeast(120)
        val h = heightPx.coerceAtLeast(120)
        val d = dial.withShape(opts.shape)
        return when (d.kind) {
            DialKind.DIGITAL_STYLE -> {
                val style = DigitalDials.byId(d.style) ?: return renderInternal(d, min(w, h), time, opts, includeHands)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                DigitalDials.drawRect(Canvas(bmp), w, h, time, opts, style)
                bmp
            }
            DialKind.HYBRID -> {
                val style = HybridDials.byId(d.style) ?: return renderInternal(d, min(w, h), time, opts, includeHands)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                HybridDials.drawRect(Canvas(bmp), w, h, time, opts, style)
                bmp
            }
            else -> {
                // Square watch, centred on a transparent rectangle.
                val side = min(w, h)
                val sq = renderInternal(d, side, time, opts, includeHands)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawBitmap(sq, (w - side) / 2f, (h - side) / 2f, null)
                bmp
            }
        }
    }

    private fun renderInternal(dial: Dial, sizePx: Int, time: Calendar, opts: DialSettings, includeHands: Boolean): Bitmap {
        val d = dial.withShape(opts.shape)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val mid = sizePx / 2f
        if (opts.strap) {
            DialRealism.drawStrap(c, sizePx, DialRealism.strapColor(d), d.aspect)
            c.save()
            c.scale(DialRealism.BODY_SCALE, DialRealism.BODY_SCALE, mid, mid)
        }
        c.save()
        c.scale(d.aspect, 1f, mid, mid)          // oval cases: squeeze the watch sideways
        if (opts.shadow) drawDropShadow(c, d, sizePx)
        drawCase(c, d, sizePx)
        val faceR = sizePx * 0.385f
        when (d.kind) {
            DialKind.ANALOG -> drawAnalog(c, d, sizePx, faceR, time, opts, includeHands)
            DialKind.DIGITAL -> drawDigital(c, d, sizePx, faceR, time, opts)
            DialKind.WORD_EN -> drawWord(c, d, sizePx, faceR, time, EN_GRID, ::wordsEn)
            DialKind.WORD_ES -> drawWord(c, d, sizePx, faceR, time, ES_GRID, ::wordsEs)
            DialKind.WIDE -> drawWide(c, d, sizePx, faceR, time, opts)
            DialKind.BIG_DIGITAL -> drawBigDigital(c, d, sizePx, faceR, time, opts)
            DialKind.DAY -> drawDay(c, d, sizePx, faceR, time)
            DialKind.MOON -> drawMoon(c, d, sizePx, faceR, time, opts, includeHands)
            DialKind.FRENCH_OVAL -> drawFrenchOval(c, d, sizePx, faceR, time, opts, includeHands)
            DialKind.BIG_HOUR -> drawBigHour(c, d, sizePx, faceR, time, opts)
            DialKind.SEMPER -> drawSemper(c, d, sizePx, faceR, time, opts, includeHands)
            DialKind.DUAL -> drawDual(c, d, sizePx, faceR, time)
            DialKind.DIGITAL_STYLE -> DigitalDials.draw(c, d, sizePx, faceR, time, opts)
            DialKind.HYBRID -> HybridDials.draw(c, d, sizePx, faceR, time, opts)
        }
        drawInnerShadow(c, d, sizePx, faceR)
        DialRealism.drawGrain(c, facePath(d, sizePx, faceR), sizePx)
        if (opts.glass) {
            drawGlass(c, d, sizePx, faceR)
            drawCaseGlass(c, d, sizePx)
        }
        c.restore()
        if (opts.strap) c.restore()
        return bmp
    }

    /**
     * The main hands for the four hand-drawing dials, in one place so the
     * full render and the live-tick service draw exactly the same hands.
     */
    private fun drawMainHands(c: Canvas, dial: Dial, size: Int, r: Float, time: Calendar, opts: DialSettings) {
        val cx = size / 2f
        val cy = size / 2f
        when (dial.kind) {
            DialKind.ANALOG -> {
                val thin = dial.numerals == Numerals.DOTS || dial.numerals == Numerals.KANJI
                DialRealism.drawHands(
                    c, cx, cy, r, size, time,
                    handColor = dial.hands,
                    secondColor = if (dial.accent == dial.hands) 0xFFC62828.toInt() else dial.accent,
                    showSecond = opts.secondHand,
                    hours24 = dial.hours24, thin = thin,
                    lume = !dial.glow && Color.luminance(dial.face) < 0.3f,
                    glow = dial.glow
                )
            }
            DialKind.MOON -> DialRealism.drawHands(c, cx, cy, r, size, time, Color.WHITE, dial.accent, opts.secondHand)
            DialKind.FRENCH_OVAL -> DialRealism.drawHands(c, cx, cy, r, size, time, dial.accent, dial.accent, opts.secondHand)
            DialKind.SEMPER -> DialRealism.drawHands(c, cx, cy, r, size, time, dial.hands, dial.accent, opts.secondHand)
            else -> {}
        }
    }

    /** 12-hour hour (1..12) for the 12h format setting. */
    private fun hour12(time: Calendar): Int {
        val h = time.get(Calendar.HOUR)
        return if (h == 0) 12 else h
    }

    private fun hourString(time: Calendar, hour24: Boolean): String =
        if (hour24) "%02d".format(time.get(Calendar.HOUR_OF_DAY)) else "%02d".format(hour12(time))

    // ---------- depth: drop shadow under the watch ----------

    private fun drawDropShadow(c: Canvas, dial: Dial, size: Int) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 0, 0, 0)
            maskFilter = BlurMaskFilter(size / 45f, BlurMaskFilter.Blur.NORMAL)
        }
        c.save()
        c.translate(size * 0.015f, size * 0.02f)
        if (dial.squircle) {
            c.drawRoundRect(RectF(size * 0.03f, size * 0.03f, size * 0.97f, size * 0.97f), size * 0.10f, size * 0.10f, shadow)
        } else {
            c.drawCircle(size / 2f, size / 2f, size * 0.455f, shadow)
        }
        c.restore()
    }

    // ---------- depth: dial sits below the bezel ----------

    private fun facePath(dial: Dial, size: Int, faceR: Float): Path {
        val cx = size / 2f
        val cy = size / 2f
        return Path().apply {
            when {
                dial.kind == DialKind.DIGITAL ->
                    addRoundRect(RectF(cx - faceR * 0.78f, cy - faceR * 0.72f, cx + faceR * 0.78f, cy + faceR * 0.72f), faceR * 0.16f, faceR * 0.16f, Path.Direction.CW)
                dial.squircle ->
                    addRoundRect(RectF(cx - faceR, cy - faceR, cx + faceR, cy + faceR), faceR * 0.30f, faceR * 0.30f, Path.Direction.CW)
                else -> addCircle(cx, cy, faceR, Path.Direction.CW)
            }
        }
    }

    private fun drawInnerShadow(c: Canvas, dial: Dial, size: Int, faceR: Float) {
        val cx = size / 2f
        val cy = size / 2f
        c.save()
        c.clipPath(facePath(dial, size, faceR))
        val grad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, faceR,
                intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(70, 0, 0, 0)),
                floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), grad)
        c.restore()
    }

    // ---------- glass: crystal dome light + streak + rim ----------

    private fun drawGlass(c: Canvas, dial: Dial, size: Int, faceR: Float) {
        val cx = size / 2f
        val cy = size / 2f
        c.save()
        c.clipPath(facePath(dial, size, faceR))
        // Soft dome light, top-left
        val dome = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size * 0.32f, size * 0.24f, size * 0.62f,
                intArrayOf(Color.argb(46, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), dome)
        // Diagonal crystal streak
        c.save()
        c.rotate(-28f, cx, cy)
        val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, size * 0.18f, 0f, size * 0.52f,
                intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(64, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(-size * 0.2f, size * 0.18f, size * 1.2f, size * 0.52f, streak)
        c.restore()
        // Bright rim light, top-left inside edge (round faces only)
        if (!dial.squircle && dial.kind != DialKind.DIGITAL) {
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = size / 220f
                strokeCap = Paint.Cap.ROUND
            }
            val arcRect = RectF(cx - faceR * 0.94f, cy - faceR * 0.94f, cx + faceR * 0.94f, cy + faceR * 0.94f)
            c.drawArc(arcRect, 200f, 90f, false, rim)
        }
        c.restore()
    }

    // ---------- glass over the whole watch: sheen across case + face ----------

    private fun drawCaseGlass(c: Canvas, dial: Dial, size: Int) {
        val cx = size / 2f
        val cy = size / 2f
        c.save()
        if (dial.squircle) {
            c.clipPath(Path().apply {
                addRoundRect(RectF(size * 0.03f, size * 0.03f, size * 0.97f, size * 0.97f), size * 0.10f, size * 0.10f, Path.Direction.CW)
            })
        } else {
            c.clipPath(Path().apply { addCircle(cx, cy, size * 0.455f, Path.Direction.CW) })
        }
        // Diagonal sheen across the whole watch
        c.save()
        c.rotate(-28f, cx, cy)
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, size * 0.10f, 0f, size * 0.42f,
                intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(26, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(-size * 0.2f, size * 0.10f, size * 1.2f, size * 0.42f, sheen)
        c.restore()
        // Top-left dome light over the case
        val dome = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size * 0.30f, size * 0.22f, size * 0.55f,
                intArrayOf(Color.argb(28, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), dome)
        c.restore()
    }

    // ---------- watch body: case, lugs, crown, shine ----------

    private fun drawCase(c: Canvas, dial: Dial, size: Int) {
        val cx = size / 2f
        val cy = size / 2f
        val caseR = size * 0.455f
        val gold = dial.kind == DialKind.DIGITAL || dial.kind == DialKind.DUAL
        val orange = dial.kind == DialKind.BIG_HOUR
        val dark = dial.kind == DialKind.WORD_EN || dial.kind == DialKind.WORD_ES ||
            dial.kind == DialKind.WIDE || dial.kind == DialKind.BIG_DIGITAL || dial.kind == DialKind.DAY ||
            dial.kind == DialKind.DIGITAL_STYLE || dial.kind == DialKind.HYBRID
        val colors = when {
            gold -> intArrayOf(0xFFFFE9A8.toInt(), 0xFFB8860B.toInt(), 0xFFF5D76E.toInt(), 0xFF8A6D1B.toInt())
            orange -> intArrayOf(0xFFFFCC80.toInt(), 0xFFF57C00.toInt(), 0xFFFFB74D.toInt(), 0xFFE65100.toInt())
            dark -> intArrayOf(0xFF3A3A3E.toInt(), 0xFF111114.toInt(), 0xFF2A2A2E.toInt(), 0xFF000000.toInt())
            else -> intArrayOf(0xFFF2F2F2.toInt(), 0xFF9A9A9A.toInt(), 0xFFE8E8E8.toInt(), 0xFF6E6E6E.toInt())
        }
        val metal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(), colors,
                floatArrayOf(0f, 0.45f, 0.7f, 1f), Shader.TileMode.CLAMP
            )
        }
        val brushLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(14, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = size / 700f
        }
        val brushDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(12, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = size / 700f
        }
        val lugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (gold) 0xFF8A6D1B.toInt() else if (orange) 0xFFE65100.toInt() else if (dark) 0xFF1C1C1F.toInt() else 0xFF8A8A8A.toInt()
        }
        val faceR = size * 0.385f
        DialRealism.drawLugs(c, size, lugPaint.color)
        if (dial.squircle) {
            val caseRect = RectF(size * 0.03f, size * 0.03f, size * 0.97f, size * 0.97f)
            c.drawRoundRect(caseRect, size * 0.10f, size * 0.10f, metal)
            // Brushed metal: fine concentric lines over the bezel
            var binset = size * 0.03f
            var bi = 0
            while (binset < size * 0.10f) {
                val corner = maxOf(size * 0.10f - (binset - size * 0.03f), size * 0.02f)
                c.drawRoundRect(RectF(binset, binset, size - binset, size - binset), corner, corner, if (bi % 2 == 0) brushLight else brushDark)
                binset += size / 160f
                bi++
            }
            // Shine: thin light edge top-left
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(110, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = size / 220f
            }
            val edgeRect = RectF(size * 0.045f, size * 0.045f, size * 0.955f, size * 0.955f)
            c.drawRoundRect(edgeRect, size * 0.09f, size * 0.09f, edge)
        } else {
            c.drawCircle(cx, cy, caseR, metal)
            // Brushed metal: fine concentric circles over the bezel ring
            var rr = faceR + size / 300f
            var bi = 0
            while (rr < caseR) {
                c.drawCircle(cx, cy, rr, if (bi % 2 == 0) brushLight else brushDark)
                rr += size / 240f
                bi++
            }
            // Polished shine arc, top-left
            val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = size / 130f
                strokeCap = Paint.Cap.ROUND
            }
            val arcRect = RectF(cx - caseR * 0.96f, cy - caseR * 0.96f, cx + caseR * 0.96f, cy + caseR * 0.96f)
            c.drawArc(arcRect, 195f, 110f, false, shine)
            // Inner bezel ring
            val bezel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 0, 0, 0)
                style = Paint.Style.STROKE
                strokeWidth = size / 300f
            }
            c.drawCircle(cx, cy, faceR + size / 220f, bezel)
        }
        // Crown on the right
        val crown = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (gold) 0xFFB8860B.toInt() else 0xFF777777.toInt()
        }
        c.drawRoundRect(
            RectF(size * 0.955f, cy - size * 0.028f, size * 0.995f, cy + size * 0.028f),
            size * 0.012f, size * 0.012f, crown
        )
    }

    // ---------- shared helpers ----------

    private fun paint(color: Int, size: Float = 0f, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (size > 0f) textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

    private fun point(cx: Float, cy: Float, r: Float, deg: Double): Pair<Float, Float> {
        val rad = Math.toRadians(deg - 90.0)
        return Pair((cx + r * cos(rad)).toFloat(), (cy + r * sin(rad)).toFloat())
    }

    // ---------- raised indices: light edge top-left, shadow bottom-right ----------
    // On glowing dials (Neon) the glow already gives depth, so the text is drawn plain.

    private fun drawRaisedText(c: Canvas, text: String, x: Float, y: Float, p: Paint, size: Int, glow: Boolean) {
        if (glow) {
            c.drawText(text, x, y, p)
            return
        }
        val dx = size / 600f
        val dy = size / 600f
        val light = Paint(p).apply { color = Color.argb(40, 255, 255, 255) }
        val dark = Paint(p).apply { color = Color.argb(70, 0, 0, 0) }
        c.drawText(text, x - dx, y - dy, light)
        c.drawText(text, x + dx, y + dy, dark)
        c.drawText(text, x, y, p)
    }

    private fun drawHand(c: Canvas, cx: Float, cy: Float, angleDeg: Double, len: Float, width: Float, color: Int, glow: Boolean = false, size: Int = 0) {
        val (x, y) = point(cx, cy, len, angleDeg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            if (glow) setShadowLayer(size / 50f, 0f, 0f, color)
        }
        c.drawLine(cx, cy, x, y, p)
    }

    // ---------- analog ----------

    private val ROMAN = arrayOf("XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI")
    private val KANJI = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    private val FRENCH = arrayOf("DOUZE", "UNE", "DEUX", "TROIS", "QUATRE", "CINQ", "SIX", "SEPT", "HUIT", "NEUF", "DIX", "ONZE")

    private fun drawAnalog(c: Canvas, dial: Dial, size: Int, r: Float, time: Calendar, opts: DialSettings = DialSettings(), includeHands: Boolean = true) {
        val cx = size / 2f
        val cy = size / 2f
        val faceRect = RectF(cx - r, cy - r, cx + r, cy + r)

        // Face
        val facePaint = paint(dial.face)
        if (dial.squircle) {
            c.drawRoundRect(faceRect, r * 0.30f, r * 0.30f, facePaint)
        } else {
            c.drawCircle(cx, cy, r, facePaint)
        }
        // Thin rim
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = size / 200f
        }
        if (dial.squircle) c.drawRoundRect(faceRect, r * 0.30f, r * 0.30f, rim)
        else c.drawCircle(cx, cy, r, rim)

        // Faint guilloche rings on the 24h dial (clipped to the face so they never spill onto the bezel)
        if (dial.hours24) {
            c.save()
            if (dial.squircle) {
                c.clipPath(Path().apply { addRoundRect(faceRect, r * 0.30f, r * 0.30f, Path.Direction.CW) })
            } else {
                c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
            }
            val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(22, 0, 0, 0)
                style = Paint.Style.STROKE
                strokeWidth = size / 400f
            }
            var rr = r * 0.15f
            while (rr < r * 0.9f) {
                c.drawCircle(cx, cy, rr, g)
                rr += r * 0.07f
            }
            c.restore()
        }

        // Minute track
        if (opts.minuteTrack) {
            val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (dial.numerals == Numerals.FRENCH) dial.accent else Color.argb(140, 0, 0, 0)
                strokeWidth = size / 220f
                strokeCap = Paint.Cap.ROUND
            }
            for (m in 0 until 60) {
                val long = m % 5 == 0
                val (x1, y1) = point(cx, cy, r * (if (long) 0.88f else 0.92f), m * 6.0)
                val (x2, y2) = point(cx, cy, r * 0.96f, m * 6.0)
                c.drawLine(x1, y1, x2, y2, tick)
            }
        }

        // Numerals / words / dots (glow on the Neon dial)
        val numR = r * 0.74f
        val numPaint = paint(dial.hands, r * 0.13f, bold = true).apply {
            if (dial.glow) setShadowLayer(size / 50f, 0f, 0f, dial.hands)
        }
        val smallPaint = paint(dial.hands, r * 0.085f, bold = true).apply {
            if (dial.glow) setShadowLayer(size / 50f, 0f, 0f, dial.hands)
        }
        when (dial.numerals) {
            Numerals.ARABIC -> {
                val count = if (dial.hours24) 24 else 12
                val p = paint(dial.hands, r * (if (dial.hours24) 0.10f else 0.13f), bold = true)
                for (i in 0 until count) {
                    // 24h dial: 24 at the top, then 1..23 clockwise. 12h dial: 12 at the top.
                    val v = if (dial.hours24) (if (i == 0) 24 else i) else if (i == 0) 12 else i
                    val (x, y) = point(cx, cy, numR, i * (360.0 / count))
                    drawRaisedText(c, v.toString(), x, y + p.textSize * 0.35f, p, size, dial.glow)
                }
            }
            Numerals.ROMAN -> for (i in 0 until 12) {
                val (x, y) = point(cx, cy, numR, i * 30.0)
                drawRaisedText(c, ROMAN[i], x, y + numPaint.textSize * 0.35f, numPaint, size, dial.glow)
            }
            Numerals.KANJI -> for (i in 0 until 12) {
                val (x, y) = point(cx, cy, numR, i * 30.0)
                drawRaisedText(c, KANJI[i], x, y + numPaint.textSize * 0.35f, numPaint, size, dial.glow)
            }
            Numerals.FRENCH -> {
                for (i in 0 until 12) {
                    val (x, y) = point(cx, cy, r * 0.82f, i * 30.0)
                    drawRaisedText(c, FRENCH[i], x, y + smallPaint.textSize * 0.35f, smallPaint, size, dial.glow)
                }
                // Red minute numbers on the track
                val mp = paint(dial.accent, r * 0.075f, bold = true)
                for (m in 1..12) {
                    if (dial.dateAt != 0 && opts.dateWindow && m == dial.dateAt) continue
                    val v = if (m == 12) "60" else "%02d".format(m * 5)
                    val (x, y) = point(cx, cy, r * 0.58f, m * 30.0)
                    drawRaisedText(c, v, x, y + mp.textSize * 0.35f, mp, size, dial.glow)
                }
            }
            Numerals.DOTS -> {
                val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dial.hands }
                val dshadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
                val dhi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
                for (i in 0 until 12) {
                    val (x, y) = point(cx, cy, numR, i * 30.0)
                    c.drawCircle(x + size / 600f, y + size / 600f, size / 110f, dshadow)
                    c.drawCircle(x, y, size / 110f, dp)
                    c.drawCircle(x - size / 900f, y - size / 900f, size / 260f, dhi)
                }
            }
            Numerals.NONE -> {}
        }

        // Brand, small, above centre
        if (opts.brand) {
            val brand = paint(dial.accent, r * 0.09f, bold = true)
            c.drawText("DIAL", cx, cy - r * 0.32f, brand)
        }

        // Date window
        if (dial.dateAt != 0 && opts.dateWindow) {
            val day = time.get(Calendar.DAY_OF_MONTH).toString()
            val w = r * 0.30f
            val h = r * 0.21f
            val rect = if (dial.dateAt == 6) {
                RectF(cx - w / 2, cy + r * 0.46f - h / 2, cx + w / 2, cy + r * 0.46f + h / 2)
            } else {
                RectF(cx + r * 0.46f - w / 2, cy - h / 2, cx + r * 0.46f + w / 2, cy + h / 2)
            }
            val bg = paint(Color.WHITE)
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 0, 0, 0)
                style = Paint.Style.STROKE
                strokeWidth = size / 400f
            }
            c.drawRoundRect(rect, h * 0.2f, h * 0.2f, bg)
            c.drawRoundRect(rect, h * 0.2f, h * 0.2f, border)
            // Bevel: dark shade on the top inside edge, light on the bottom
            val topShade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(70, 0, 0, 0)
                strokeWidth = size / 500f
                strokeCap = Paint.Cap.ROUND
            }
            val botLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(110, 255, 255, 255)
                strokeWidth = size / 500f
                strokeCap = Paint.Cap.ROUND
            }
            c.drawLine(rect.left + h * 0.25f, rect.top + size / 500f, rect.right - h * 0.25f, rect.top + size / 500f, topShade)
            c.drawLine(rect.left + h * 0.25f, rect.bottom - size / 500f, rect.right - h * 0.25f, rect.bottom - size / 500f, botLight)
            val dp = paint(Color.BLACK, h * 0.62f, bold = true)
            c.drawText(day, rect.centerX(), rect.centerY() + dp.textSize * 0.35f, dp)
        }

        if (includeHands) drawMainHands(c, dial, size, r, time, opts)
    }

    // ---------- digital (seven segment) ----------

    // Segments lit for each digit: a,b,c,d,e,f,g
    private val SEG = mapOf(
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

    private fun drawSegDigit(c: Canvas, d: Char, left: Float, top: Float, w: Float, h: Float, on: Paint, off: Paint) {
        val t = w * 0.20f
        val segs = SEG[d] ?: return
        fun hSeg(x: Float, y: Float, lit: Boolean) {
            c.drawRect(RectF(x, y, x + w, y + t), if (lit) on else off)
        }
        fun vSeg(x: Float, y: Float, lit: Boolean) {
            c.drawRect(RectF(x, y, x + t, y + h / 2), if (lit) on else off)
        }
        val hw = w
        hSeg(left, top, segs[0]) // a
        vSeg(left + hw - t, top, segs[1]) // b
        vSeg(left + hw - t, top + h / 2, segs[2]) // c
        hSeg(left, top + h - t, segs[3]) // d
        vSeg(left, top + h / 2, segs[4]) // e
        vSeg(left, top, segs[5]) // f
        hSeg(left, top + h / 2 - t / 2, segs[6]) // g
    }

    private fun drawDigital(c: Canvas, dial: Dial, size: Int, faceR: Float, time: Calendar, opts: DialSettings = DialSettings()) {
        val cx = size / 2f
        val cy = size / 2f
        val r = faceR
        // Brown inner, smaller than the face so the gold case shows around it
        val inner = RectF(cx - r * 0.78f, cy - r * 0.72f, cx + r * 0.78f, cy + r * 0.72f)
        c.drawRoundRect(inner, r * 0.16f, r * 0.16f, paint(0xFF4A2E1B.toInt()))
        // Brand
        val brand = paint(0xFFD8C49A.toInt(), r * 0.11f, bold = true)
        c.drawText("DIAL", cx, inner.top + r * 0.22f, brand)
        // LCD
        val lcd = RectF(inner.left + r * 0.10f, inner.top + r * 0.34f, inner.right - r * 0.10f, inner.bottom - r * 0.14f)
        c.drawRoundRect(lcd, r * 0.08f, r * 0.08f, paint(0xFFCBB98A.toInt()))
        // Time HH:MM (12h or 24h per Settings)
        val hh = hourString(time, opts.hour24)
        val mm = "%02d".format(time.get(Calendar.MINUTE))
        val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1A1A.toInt() }
        val off = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(28, 0, 0, 0)
        }
        val dw = lcd.width() * 0.17f
        val dh = lcd.height() * 0.72f
        val gap = lcd.width() * 0.045f
        val total = dw * 4 + gap * 3
        var x = lcd.centerX() - total / 2
        val y = lcd.centerY() - dh / 2
        for (ch in (hh + mm)) {
            drawSegDigit(c, ch, x, y, dw, dh, on, off)
            x += dw + gap
        }
        // Colon
        val colonX = lcd.centerX()
        val dotR = dw * 0.09f
        c.drawCircle(colonX, lcd.centerY() - dh * 0.22f, dotR, on)
        c.drawCircle(colonX, lcd.centerY() + dh * 0.22f, dotR, on)
    }

    // ---------- word clocks ----------

    private data class WordPos(val row: Int, val col: Int, val len: Int)

    private val EN_GRID = listOf(
        "ITLISASTIME",
        "ACQUARTERDC",
        "TWENTYFIVE.",
        "HALFSTENFTO",
        "PASTERUNINE",
        "ONESIXTHREE",
        "FOURFIVETWO",
        "EIGHTELEVEN",
        "SEVENTWELVE",
        "TENSEOCLOCK"
    )
    private val EN_WORDS = mapOf(
        "IT" to WordPos(0, 0, 2), "IS" to WordPos(0, 3, 2),
        "A" to WordPos(1, 0, 1), "QUARTER" to WordPos(1, 2, 7),
        "TWENTY" to WordPos(2, 0, 6), "FIVE_M" to WordPos(2, 6, 4),
        "HALF" to WordPos(3, 0, 4), "TEN_M" to WordPos(3, 5, 3), "TO" to WordPos(3, 9, 2),
        "PAST" to WordPos(4, 0, 4), "NINE" to WordPos(4, 7, 4),
        "ONE" to WordPos(5, 0, 3), "SIX" to WordPos(5, 3, 3), "THREE" to WordPos(5, 6, 5),
        "FOUR" to WordPos(6, 0, 4), "FIVE_H" to WordPos(6, 4, 4), "TWO" to WordPos(6, 8, 3),
        "EIGHT" to WordPos(7, 0, 5), "ELEVEN" to WordPos(7, 5, 6),
        "SEVEN" to WordPos(8, 0, 5), "TWELVE" to WordPos(8, 5, 6),
        "TEN_H" to WordPos(9, 0, 3), "OCLOCK" to WordPos(9, 5, 6)
    )
    private val EN_HOURS = arrayOf(
        "TWELVE", "ONE", "TWO", "THREE", "FOUR", "FIVE_H", "SIX",
        "SEVEN", "EIGHT", "NINE", "TEN_H", "ELEVEN"
    )

    private fun wordsEn(time: Calendar): Set<Pair<Int, Int>> {
        val lit = mutableSetOf<Pair<Int, Int>>()
        fun add(name: String) {
            val w = EN_WORDS[name] ?: return
            for (i in 0 until w.len) lit.add(Pair(w.row, w.col + i))
        }
        val h12 = time.get(Calendar.HOUR) % 12
        val m = time.get(Calendar.MINUTE)
        val step = m / 5
        add("IT"); add("IS")
        var hour = h12
        when (step) {
            0 -> { add(EN_HOURS[hour]); add("OCLOCK") }
            1 -> { add("FIVE_M"); add("PAST"); add(EN_HOURS[hour]) }
            2 -> { add("TEN_M"); add("PAST"); add(EN_HOURS[hour]) }
            3 -> { add("A"); add("QUARTER"); add("PAST"); add(EN_HOURS[hour]) }
            4 -> { add("TWENTY"); add("PAST"); add(EN_HOURS[hour]) }
            5 -> { add("TWENTY"); add("FIVE_M"); add("PAST"); add(EN_HOURS[hour]) }
            6 -> { add("HALF"); add("PAST"); add(EN_HOURS[hour]) }
            else -> {
                hour = (hour + 1) % 12
                when (step) {
                    7 -> { add("TWENTY"); add("FIVE_M"); add("TO"); add(EN_HOURS[hour]) }
                    8 -> { add("TWENTY"); add("TO"); add(EN_HOURS[hour]) }
                    9 -> { add("A"); add("QUARTER"); add("TO"); add(EN_HOURS[hour]) }
                    10 -> { add("TEN_M"); add("TO"); add(EN_HOURS[hour]) }
                    11 -> { add("FIVE_M"); add("TO"); add(EN_HOURS[hour]) }
                }
            }
        }
        return lit
    }

    private val ES_GRID = listOf(
        "ESLASON.....",
        "UNA.........",
        "DOS.TRES....",
        "CUATRO......",
        "CINCOSEIS...",
        "SIETEOCHO...",
        "NUEVEDIEZ...",
        "ONCEDOCE....",
        "YVEINTICINCO",
        "MENOSCUARTO.",
        "QUINCEMEDIA."
    )
    private val ES_WORDS = mapOf(
        "ES" to WordPos(0, 0, 2), "LA" to WordPos(0, 2, 2),
        "SON" to WordPos(0, 4, 3), "LAS" to WordPos(0, 2, 3),
        "UNA" to WordPos(1, 0, 3),
        "DOS" to WordPos(2, 0, 3), "TRES" to WordPos(2, 4, 4),
        "CUATRO" to WordPos(3, 0, 6),
        "CINCO" to WordPos(4, 0, 5), "SEIS" to WordPos(4, 5, 4),
        "SIETE" to WordPos(5, 0, 5), "OCHO" to WordPos(5, 5, 4),
        "NUEVE" to WordPos(6, 0, 5), "DIEZ" to WordPos(6, 5, 4),
        "ONCE" to WordPos(7, 0, 4), "DOCE" to WordPos(7, 4, 4),
        "Y" to WordPos(8, 0, 1), "VEINTE" to WordPos(8, 1, 6), "VEINTICINCO" to WordPos(8, 1, 11),
        "MENOS" to WordPos(9, 0, 5), "CUARTO" to WordPos(9, 5, 6),
        "QUINCE" to WordPos(10, 0, 6), "MEDIA" to WordPos(10, 6, 5)
    )
    private val ES_HOURS = arrayOf(
        "DOCE", "UNA", "DOS", "TRES", "CUATRO", "CINCO", "SEIS",
        "SIETE", "OCHO", "NUEVE", "DIEZ", "ONCE"
    )

    private fun wordsEs(time: Calendar): Set<Pair<Int, Int>> {
        val lit = mutableSetOf<Pair<Int, Int>>()
        fun add(name: String) {
            val w = ES_WORDS[name] ?: return
            for (i in 0 until w.len) lit.add(Pair(w.row, w.col + i))
        }
        val h12 = time.get(Calendar.HOUR) % 12
        val m = time.get(Calendar.MINUTE)
        val step = m / 5
        var hour = h12
        if (step == 0) {
            if (hour == 1) { add("ES"); add("LA") } else { add("SON"); add("LAS") }
            add(ES_HOURS[hour])
            return lit
        }
        if (hour == 1) { add("ES"); add("LA") } else { add("SON"); add("LAS") }
        when (step) {
            1 -> { add(ES_HOURS[hour]); add("Y"); add("CINCO") }
            2 -> { add(ES_HOURS[hour]); add("Y"); add("DIEZ") }
            3 -> { add(ES_HOURS[hour]); add("Y"); add("CUARTO") }
            4 -> { add(ES_HOURS[hour]); add("Y"); add("VEINTE") }
            5 -> { add(ES_HOURS[hour]); add("Y"); add("VEINTICINCO") }
            6 -> { add(ES_HOURS[hour]); add("Y"); add("MEDIA") }
            else -> {
                hour = (hour + 1) % 12
                // rebuild the hour phrase for the next hour
                lit.clear()
                if (hour == 1) { add("ES"); add("LA") } else { add("SON"); add("LAS") }
                when (step) {
                    7 -> { add(ES_HOURS[hour]); add("MENOS"); add("VEINTICINCO") }
                    8 -> { add(ES_HOURS[hour]); add("MENOS"); add("VEINTE") }
                    9 -> { add(ES_HOURS[hour]); add("MENOS"); add("CUARTO") }
                    10 -> { add(ES_HOURS[hour]); add("MENOS"); add("DIEZ") }
                    11 -> { add(ES_HOURS[hour]); add("MENOS"); add("CINCO") }
                }
            }
        }
        return lit
    }

    private fun drawWord(
        c: Canvas, dial: Dial, size: Int, faceR: Float, time: Calendar,
        grid: List<String>, words: (Calendar) -> Set<Pair<Int, Int>>
    ) {
        val cx = size / 2f
        val cy = size / 2f
        val r = faceR
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        c.drawRoundRect(rect, r * 0.30f, r * 0.30f, paint(dial.face))
        val lit = words(time)
        val rows = grid.size
        val cols = grid[0].length
        // Grid takes the top ~76% of the face; period + date live below it.
        val gridTop = cy - r * 0.94f
        val gridHeight = r * 1.46f
        val cell = min(gridHeight / rows, (r * 1.84f) / cols)
        val litPaint = paint(dial.hands, cell * 0.62f, bold = true).apply {
            setShadowLayer(cell * 0.45f, 0f, 0f, 0xFFB388FF.toInt())
        }
        val dimPaint = paint(Color.argb(70, 255, 255, 255), cell * 0.62f, bold = true)
        val left = cx - (cols * cell) / 2
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val ch = grid[row][col]
                if (ch == '.') continue
                val x = left + col * cell + cell / 2
                val y = gridTop + row * cell + cell / 2
                c.drawText(
                    ch.toString(), x, y + litPaint.textSize * 0.35f,
                    if (lit.contains(Pair(row, col))) litPaint else dimPaint
                )
            }
        }
        // Divider, then period of day + date, glowing like the lit words.
        val divY = cy + r * 0.60f
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 255, 255)
            strokeWidth = size / 500f
        }
        c.drawLine(cx - r * 0.78f, divY, cx + r * 0.78f, divY, divPaint)
        val hour = time.get(Calendar.HOUR_OF_DAY)
        val period = when (hour) {
            in 5..11 -> "MORNING"
            in 12..16 -> "AFTERNOON"
            in 17..20 -> "EVENING"
            else -> "NIGHT"
        }
        val periodPaint = paint(dial.hands, r * 0.13f, bold = true).apply {
            setShadowLayer(r * 0.06f, 0f, 0f, 0xFFB388FF.toInt())
            letterSpacing = 0.28f
        }
        c.drawText(period, cx, cy + r * 0.76f, periodPaint)
        val dateText = java.text.SimpleDateFormat("EEE dd MMM", java.util.Locale.ENGLISH)
            .format(time.time).uppercase(java.util.Locale.ENGLISH)
        val datePaint = paint(Color.argb(160, 255, 255, 255), r * 0.085f, bold = true).apply {
            letterSpacing = 0.18f
        }
        c.drawText(dateText, cx, cy + r * 0.92f, datePaint)
    }

    // ---------- wide digital + big digital + day ----------

    private fun drawWide(c: Canvas, dial: Dial, size: Int, faceR: Float, time: Calendar, opts: DialSettings = DialSettings()) {
        val cx = size / 2f
        val cy = size / 2f
        val r = faceR
        // Same squircle face as every other dial
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        c.drawRoundRect(rect, r * 0.30f, r * 0.30f, paint(dial.face))
        // Minute ticks along the top edge (kept inside the flat part of the top)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 255, 255)
            strokeWidth = size / 300f
            strokeCap = Paint.Cap.ROUND
        }
        for (m in 0 until 60) {
            val x = cx - r * 0.65f + (m / 59f) * r * 1.30f
            val long = m % 5 == 0
            c.drawLine(x, rect.top + r * 0.06f, x, rect.top + r * (if (long) 0.16f else 0.11f), tick)
        }
        // Big serif hour + minute, side by side
        val serif = Typeface.SERIF
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dial.hands
            textSize = r * 0.52f
            typeface = Typeface.create(serif, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val hh = hourString(time, opts.hour24)
        val mm = "%02d".format(time.get(Calendar.MINUTE))
        c.drawText(hh, cx - r * 0.45f, cy - r * 0.02f + numPaint.textSize * 0.35f, numPaint)
        c.drawText(mm, cx + r * 0.45f, cy - r * 0.02f + numPaint.textSize * 0.35f, numPaint)
        // Centre mini analog dial below the digits
        val mcx = cx
        val mcy = cy + r * 0.45f
        val mr = r * 0.24f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = size / 400f
        }
        c.drawCircle(mcx, mcy, mr, ring)
        val hour = time.get(Calendar.HOUR_OF_DAY)
        val min = time.get(Calendar.MINUTE)
        val sec = time.get(Calendar.SECOND)
        drawHand(c, mcx, mcy, ((hour % 12) + min / 60.0) / 12.0 * 360.0, mr * 0.55f, size / 110f, dial.hands)
        drawHand(c, mcx, mcy, min / 60.0 * 360.0, mr * 0.80f, size / 130f, dial.hands)
        // Red second hand
        drawHand(c, mcx, mcy, sec / 60.0 * 360.0, mr * 0.85f, size / 160f, dial.accent)
        c.drawCircle(mcx, mcy, size / 130f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dial.accent })
    }

    private fun drawBigDigital(c: Canvas, dial: Dial, size: Int, faceR: Float, time: Calendar, opts: DialSettings = DialSettings()) {
        val cx = size / 2f
        val cy = size / 2f
        val r = faceR
        // Same squircle face as every other dial
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        c.drawRoundRect(rect, r * 0.30f, r * 0.30f, paint(dial.face))
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dial.hands
            textSize = r * 0.62f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val text = "${hourString(time, opts.hour24)}:%02d".format(time.get(Calendar.MINUTE))
        c.drawText(text, cx, cy - r * 0.02f + numPaint.textSize * 0.18f, numPaint)
        val dateText = (java.text.SimpleDateFormat("EEEE, dd MMM", java.util.Locale.ENGLISH)
            .format(time.time).uppercase(java.util.Locale.ENGLISH) +
            if (!opts.hour24) " · " + if (time.get(Calendar.HOUR_OF_DAY) < 12) "AM" else "PM" else "")
        val datePaint = paint(Color.argb(170, 255, 255, 255), r * 0.10f, bold = true).apply {
            letterSpacing = 0.22f
        }
        c.drawText(dateText, cx, cy + r * 0.52f, datePaint)
    }

    private fun drawDay(c: Canvas, dial: Dial, size: Int, faceR: Float, time: Calendar) {
        val cx = size / 2f
        val cy = size / 2f
        val r = faceR
        // Same squircle face as every other dial
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        c.drawRoundRect(rect, r * 0.30f, r * 0.30f, paint(dial.face))
        val dayName = java.text.SimpleDateFormat("EEEE", java.util.Locale.ENGLISH)
            .format(time.time).uppercase(java.util.Locale.ENGLISH)
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dial.hands
            textSize = r * 0.42f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.22f
        }
        // Shrink until the whole day name fits inside the face (never clipped)
        var ts = r * 0.42f
        while (dayPaint.measureText(dayName) > r * 1.72f && ts > r * 0.16f) {
            ts *= 0.92f
            dayPaint.textSize = ts
        }
        c.drawText(dayName, cx, cy - r * 0.02f + dayPaint.textSize * 0.18f, dayPaint)
        val dateText = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.ENGLISH)
            .format(time.time).uppercase(java.util.Locale.ENGLISH)
        val datePaint = paint(dial.accent, r * 0.10f, bold = true).apply {
            letterSpacing = 0.20f
        }
        c.drawText(dateText, cx, cy + r * 0.52f, datePaint)
    }

    // ---------- the five photo dials (each keeps its own shape from its photo) ----------

    /** Real moon phase from the date: 0 = new moon, 0.5 = full moon. */
    private fun moonPhase(time: Calendar): Double {
        val synodic = 29.530588853
        val newMoon = 947182440000L // 2000-01-06 18:14 UTC
        val days = (time.timeInMillis - newMoon) / 86400000.0
        val p = (days % synodic) / synodic
        return if (p < 0) p + 1 else p
    }

    /** Curved brand text: top line arched over, bottom line smiling under. */
    private fun drawCurvedBrand(c: Canvas, cx: Float, cy: Float, r: Float, top: String, bottom: String, p: Paint) {
        val arcR = r * 0.40f
        val sweep = 130f
        val arcLen = arcR * (sweep * Math.PI / 180f).toFloat()
        val topArc = Path().apply {
            addArc(RectF(cx - arcR, cy - arcR, cx + arcR, cy + arcR), 205f, sweep)
        }
        c.drawTextOnPath(top, topArc, (arcLen - p.measureText(top)) / 2, 0f, p)
        val botArc = Path().apply {
            addArc(RectF(cx - arcR, cy - arcR, cx + arcR, cy + arcR), 155f, -sweep)
        }
        c.drawTextOnPath(bottom, botArc, (arcLen - p.measureText(bottom)) / 2, 0f, p)
    }

    private fun drawMoonShadow(c: Canvas, cx: Float, cy: Float, R: Float, phase: Double) {
        val night = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF12224A.toInt() }
        val waxing = phase < 0.5
        val k = cos(2 * Math.PI * phase).toFloat()      // 1 = new, -1 = full
        val disc = RectF(cx - R, cy - R, cx + R, cy + R)
        // unlit half: left side when waxing, right side when waning
        val dark = Path().apply {
            addArc(disc, if (waxing) 90f else -90f, 180f)
            close()
        }
        val rx = (R * kotlin.math.abs(k)).coerceAtLeast(0.5f)
        val ell = Path().apply {
            addOval(RectF(cx - rx, cy - R, cx + rx, cy + R), Path.Direction.CW)
        }
        // crescent: terminator bulges into the lit side; gibbous: it cuts into the dark side
        dark.op(ell, if (k > 0) Path.Op.UNION else Path.Op.DIFFERENCE)
        c.drawPath(dark, night)
    }

    private fun drawMoon(c: Canvas, dial: Dial, size: Int, r: Float, time: Calendar, opts: DialSettings = DialSettings(), includeHands: Boolean = true) {
        val cx = size / 2f
        val cy = size / 2f
        val faceRect = RectF(cx - r, cy - r, cx + r, cy + r)
        if (dial.squircle) c.drawRoundRect(faceRect, r * 0.30f, r * 0.30f, paint(dial.face))
        else c.drawCircle(cx, cy, r, paint(dial.face))
        // Faint guilloche rings + night half, clipped to the face
        c.save()
        if (dial.squircle) c.clipPath(Path().apply { addRoundRect(faceRect, r * 0.30f, r * 0.30f, Path.Direction.CW) })
        else c.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
        val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(26, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = size / 500f
        }
        var rr = r * 0.18f
        while (rr < r * 0.92f) {
            c.drawCircle(cx, cy, rr, g)
            rr += r * 0.055f
        }
        val night = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF12224A.toInt() }
        c.drawCircle(cx - r * 0.30f, cy + r * 0.08f, r * 0.52f, night)
        c.restore()
        // Moon disc with craters
        val moonR = r * 0.30f
        val moonCx = cx - r * 0.30f
        val moonCy = cy + r * 0.08f
        c.drawCircle(moonCx, moonCy, moonR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDCE8E4.toInt() })
        val crater = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 90, 120, 110) }
        c.drawCircle(moonCx - moonR * 0.3f, moonCy - moonR * 0.25f, moonR * 0.16f, crater)
        c.drawCircle(moonCx + moonR * 0.35f, moonCy + moonR * 0.1f, moonR * 0.12f, crater)
        c.drawCircle(moonCx + moonR * 0.05f, moonCy + moonR * 0.4f, moonR * 0.10f, crater)
        // Real phase shadow, clipped to the disc
        val phase = moonPhase(time)
        c.save()
        c.clipPath(Path().apply { addCircle(moonCx, moonCy, moonR, Path.Direction.CW) })
        drawMoonShadow(c, moonCx, moonCy, moonR, phase)
        c.restore()
        // Minute track
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 255, 255, 255)
            strokeWidth = size / 260f
            strokeCap = Paint.Cap.ROUND
        }
        for (m in 0 until 60) {
            val long = m % 5 == 0
            val (x1, y1) = point(cx, cy, r * (if (long) 0.86f else 0.91f), m * 6.0)
            val (x2, y2) = point(cx, cy, r * 0.95f, m * 6.0)
            c.drawLine(x1, y1, x2, y2, tick)
        }
        // Hour numerals 12, 2, 4, 6, 8, 10
        val numPaint = paint(Color.WHITE, r * 0.11f, bold = true)
        for (i in 0 until 6) {
            val v = if (i == 0) 12 else i * 2
            val (x, y) = point(cx, cy, r * 0.74f, i * 60.0)
            c.drawText(v.toString(), x, y + numPaint.textSize * 0.35f, numPaint)
        }
        // DAY & NIGHT label, top
        val label = paint(Color.argb(200, 255, 255, 255), r * 0.07f, bold = true).apply { letterSpacing = 0.18f }
        c.drawText("DAY & NIGHT", cx, cy - r * 0.52f, label)
        if (includeHands) drawMainHands(c, dial, size, r, time, opts)
    }

    private fun drawFrenchOval(c: Canvas, dial: Dial, size: Int, r: Float, time: Calendar, opts: DialSettings = DialSettings(), includeHands: Boolean = true) {
        val cx = size / 2f
        val cy = size / 2f
        val faceRect = RectF(cx - r, cy - r, cx + r, cy + r)
        c.drawRoundRect(faceRect, r * 0.30f, r * 0.30f, paint(dial.face))
        // Red oval track
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dial.accent
            style = Paint.Style.STROKE
            strokeWidth = size / 300f
        }
        c.drawRoundRect(RectF(cx - r * 0.86f, cy - r * 0.86f, cx + r * 0.86f, cy + r * 0.86f), r * 0.26f, r * 0.26f, track)
        // French hour words around the edge, each kept readable
        val wordPaint = paint(dial.accent, r * 0.085f, bold = true).apply { letterSpacing = 0.08f }
        val words = arrayOf("DOUZE", "UNE", "DEUX", "TROIS", "QUATRE", "CINQ", "SIX", "SEPT", "HUIT", "NEUF", "DIX", "ONZE")
        for (i in 0 until 12) {
            val (x, y) = point(cx, cy, r * 0.95f, i * 30.0)
            val deg = i * 30f
            val rot = if (deg > 90f && deg < 270f) deg - 180f else deg
            c.save()
            c.translate(x, y)
            c.rotate(rot)
            c.drawText(words[i], 0f, wordPaint.textSize * 0.35f, wordPaint)
            c.restore()
        }
        // Red minute numbers on the inner track
        val mp = paint(dial.accent, r * 0.07f, bold = true)
        for (m in 1..12) {
            val v = if (m == 12) "60" else "%02d".format(m * 5)
            val (x, y) = point(cx, cy, r * 0.62f, m * 30.0)
            c.drawText(v, x, y + mp.textSize * 0.35f, mp)
        }
        // Curved brand in the middle
        val brand = paint(dial.accent, r * 0.075f, bold = true).apply { letterSpacing = 0.10f }
        drawCurvedBrand(c, cx, cy, r, "SEMPER &", "ADHUC", brand)
        // Atelier line at the bottom
        val sub = paint(dial.accent, r * 0.055f, bold = true).apply { letterSpacing = 0.16f }
        c.drawText("ATELIER FRANÇAIS", cx, cy + r * 0.78f, sub)
        if (includeHands) drawMainHands(c, dial, size, r, time, opts)
    }

    private fun drawBigHour(c: Canvas, dial: Dial, size: Int, r: Float, time: Calendar, opts: DialSettings = DialSettings()) {
        val cx = size / 2f
        val cy = size / 2f
        val faceRect = RectF(cx - r, cy - r, cx + r, cy + r)
        if (dial.squircle) c.drawRoundRect(faceRect, r * 0.30f, r * 0.30f, paint(dial.face))
        else c.drawCircle(cx, cy, r, paint(dial.face))
        val hh = hourString(time, opts.hour24)
        val mm = "%02d".format(time.get(Calendar.MINUTE))
        val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dial.hands
            textSize = r * 0.78f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val minPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dial.hands
            textSize = r * 0.42f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        c.drawText(hh, cx - r * 0.32f, cy + hourPaint.textSize * 0.28f, hourPaint)
        c.drawText(mm, cx + r * 0.46f, cy + r * 0.18f, minPaint)
        // Cloud icon, divider, day + date
        val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = size / 260f
            strokeCap = Paint.Cap.ROUND
        }
        val ccx = cx - r * 0.40f
        val ccy = cy + r * 0.66f
        val cr = r * 0.09f
        c.drawCircle(ccx - cr * 0.5f, ccy, cr * 0.55f, cloud)
        c.drawCircle(ccx + cr * 0.4f, ccy - cr * 0.2f, cr * 0.7f, cloud)
        c.drawLine(ccx - cr, ccy + cr * 0.5f, ccx + cr, ccy + cr * 0.5f, cloud)
        val div = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 0, 0, 0)
            strokeWidth = size / 300f
        }
        c.drawLine(cx - r * 0.16f, cy + r * 0.55f, cx - r * 0.16f, cy + r * 0.77f, div)
        val dayText = java.text.SimpleDateFormat("EEE d", java.util.Locale.ENGLISH).format(time.time).uppercase(java.util.Locale.ENGLISH)
        val dayPaint = paint(Color.argb(170, 0, 0, 0), r * 0.11f, bold = true).apply { letterSpacing = 0.10f }
        c.drawText(dayText, cx + r * 0.12f, cy + r * 0.70f, dayPaint)
    }

    private fun drawSemper(c: Canvas, dial: Dial, size: Int, r: Float, time: Calendar, opts: DialSettings = DialSettings(), includeHands: Boolean = true) {
        val cx = size / 2f
        val cy = size / 2f
        val faceRect = RectF(cx - r, cy - r, cx + r, cy + r)
        c.drawRoundRect(faceRect, r * 0.30f, r * 0.30f, paint(dial.face))
        // Arabic numerals upright around the edge
        val numPaint = paint(dial.hands, r * 0.13f, bold = false)
        for (i in 0 until 12) {
            val v = if (i == 0) 12 else i
            val (x, y) = point(cx, cy, r * 0.78f, i * 30.0)
            c.drawText(v.toString(), x, y + numPaint.textSize * 0.35f, numPaint)
        }
        // Inner minute track: ticks + 00/05..55 numbers
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 0, 0, 0)
            strokeWidth = size / 300f
            strokeCap = Paint.Cap.ROUND
        }
        for (m in 0 until 60) {
            val long = m % 5 == 0
            val (x1, y1) = point(cx, cy, r * (if (long) 0.62f else 0.66f), m * 6.0)
            val (x2, y2) = point(cx, cy, r * 0.70f, m * 6.0)
            c.drawLine(x1, y1, x2, y2, tick)
        }
        val mp = paint(dial.hands, r * 0.065f, bold = true)
        for (m in 0 until 12) {
            val v = "%02d".format(m * 5)
            val (x, y) = point(cx, cy, r * 0.54f, m * 30.0)
            c.drawText(v, x, y + mp.textSize * 0.35f, mp)
        }
        // Curved brand in the middle
        val brand = paint(dial.hands, r * 0.075f, bold = true).apply { letterSpacing = 0.10f }
        drawCurvedBrand(c, cx, cy, r, "SEMPER &", "ADHUC", brand)
        val sub = paint(dial.hands, r * 0.05f, bold = true).apply { letterSpacing = 0.16f }
        c.drawText("ATELIER FRANÇAIS", cx, cy + r * 0.92f, sub)
        if (includeHands) drawMainHands(c, dial, size, r, time, opts)
    }

    private fun drawSubdial(c: Canvas, cx: Float, cy: Float, sr: Float, size: Int, gold: Int, angleDeg: Double) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 212, 175, 55)
            style = Paint.Style.STROKE
            strokeWidth = size / 400f
        }
        c.drawCircle(cx, cy, sr, ring)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 212, 175, 55)
            strokeWidth = size / 300f
            strokeCap = Paint.Cap.ROUND
        }
        for (m in 0 until 12) {
            val (x1, y1) = point(cx, cy, sr * 0.82f, m * 30.0)
            val (x2, y2) = point(cx, cy, sr * 0.94f, m * 30.0)
            c.drawLine(x1, y1, x2, y2, tick)
        }
        drawHand(c, cx, cy, angleDeg, sr * 0.75f, size / 160f, gold)
        c.drawCircle(cx, cy, size / 200f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold })
    }

    private fun drawDual(c: Canvas, dial: Dial, size: Int, r: Float, time: Calendar) {
        val cx = size / 2f
        val cy = size / 2f
        val faceRect = RectF(cx - r, cy - r, cx + r, cy + r)
        c.drawRoundRect(faceRect, r * 0.30f, r * 0.30f, paint(dial.face))
        // Gold dotted minute track around the edge
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dial.accent }
        for (m in 0 until 60) {
            val (x, y) = point(cx, cy, r * 0.88f, m * 6.0)
            c.drawCircle(x, y, if (m % 5 == 0) size / 150f else size / 220f, dot)
        }
        // Brand
        val brand = paint(dial.accent, r * 0.075f, bold = true).apply { letterSpacing = 0.14f }
        c.drawText("RAYMOND WEIL", cx, cy - r * 0.62f, brand)
        val sub = paint(Color.argb(170, 212, 175, 55), r * 0.055f, bold = true).apply { letterSpacing = 0.20f }
        c.drawText("GENEVE", cx, cy - r * 0.50f, sub)
        // Left subdial: hours. Right subdial: minutes.
        drawSubdial(c, cx - r * 0.38f, cy + r * 0.12f, r * 0.28f, size, dial.accent, ((time.get(Calendar.HOUR) % 12) + time.get(Calendar.MINUTE) / 60.0) / 12.0 * 360.0)
        drawSubdial(c, cx + r * 0.38f, cy + r * 0.12f, r * 0.28f, size, dial.accent, time.get(Calendar.MINUTE) / 60.0 * 360.0)
    }
}
