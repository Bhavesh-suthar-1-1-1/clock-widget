package com.app.dialclock

/*
 * DialShape.kt  -  round / square / oval watch cases.
 *
 * Today the code only knows two shapes: round, and a rounded square
 * ("squircle"). There is no real oval, not even "Française Oval".
 *
 * This adds an OVAL case (tall and narrow, like the Semper & Adhuc photo)
 * and a "Case shape" setting: Auto / Round / Square / Oval.
 * The whole watch (case, dial, hands, glass) is drawn normally and then
 * squeezed sideways, so every dial can be oval without changing its code.
 *
 * Add this file next to DialRenderer.kt, then make these 6 edits.
 * (Do EDITS from DialRealism.kt first; this file replaces its EDIT 3.)
 *
 * ---------------------------------------------------------------
 * EDIT A  Dial.kt  ->  data class Dial: add one field at the end
 * ---------------------------------------------------------------
 *     val custom: Boolean = false,
 *     val aspect: Float = 1f          // width / height of the case. 1 = not squeezed
 *
 *   and in Dials.BUILT_IN make these two dials oval by default:
 *     "frenchoval"  ...  squircle = true, aspect = 0.84f)
 *     "semper"      ...  squircle = true, aspect = 0.84f)
 *
 * ---------------------------------------------------------------
 * EDIT B  Dial.kt  ->  DialSettings: add (after `strap`)
 * ---------------------------------------------------------------
 *     val shape: CaseShape = CaseShape.AUTO
 *
 * ---------------------------------------------------------------
 * EDIT C  Dial.kt  ->  DialPrefs
 * ---------------------------------------------------------------
 *     private const val KEY_SHAPE = "case_shape"
 *
 *   inside settings(ctx), add to the DialSettings(...) call:
 *     shape = try { CaseShape.valueOf(p.getString(KEY_SHAPE, "AUTO") ?: "AUTO") }
 *             catch (e: Exception) { CaseShape.AUTO }
 *
 *   and add this function:
 *     fun setShape(ctx: Context, shape: CaseShape) {
 *         prefs(ctx).edit().putString(KEY_SHAPE, shape.name).apply()
 *     }
 *
 * ---------------------------------------------------------------
 * EDIT D  MainActivity.kt  ->  SettingsScreen, in the "Look" column
 * ---------------------------------------------------------------
 *     Text("Case shape", style = MaterialTheme.typography.titleSmall)
 *     Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
 *         CaseShape.values().forEach { s ->
 *             val on = settings.shape == s
 *             Button(
 *                 onClick = { DialPrefs.setShape(ctx, s); onChanged() },
 *                 colors = ButtonDefaults.buttonColors(
 *                     containerColor = if (on) MaterialTheme.colorScheme.primary
 *                                      else MaterialTheme.colorScheme.surfaceVariant,
 *                     contentColor = if (on) MaterialTheme.colorScheme.onPrimary
 *                                    else MaterialTheme.colorScheme.onSurfaceVariant
 *                 ),
 *                 contentPadding = PaddingValues(horizontal = 12.dp)
 *             ) { Text(s.label) }
 *         }
 *     }
 *   (add any missing imports: Button, ButtonDefaults, PaddingValues, Arrangement, Row)
 *
 * ---------------------------------------------------------------
 * EDIT E  DialRenderer.kt  ->  REPLACE the whole render() function with this
 * ---------------------------------------------------------------
 *     fun render(dial: Dial, sizePx: Int, time: Calendar, opts: DialSettings = DialSettings()): Bitmap {
 *         val d = dial.withShape(opts.shape)
 *         val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
 *         val c = Canvas(bmp)
 *         val mid = sizePx / 2f
 *         if (opts.strap) {
 *             DialRealism.drawStrap(c, sizePx, DialRealism.strapColor(d), d.aspect)
 *             c.save()
 *             c.scale(DialRealism.BODY_SCALE, DialRealism.BODY_SCALE, mid, mid)
 *         }
 *         c.save()
 *         c.scale(d.aspect, 1f, mid, mid)          // oval cases: squeeze the watch sideways
 *         if (opts.shadow) drawDropShadow(c, d, sizePx)
 *         drawCase(c, d, sizePx)
 *         val faceR = sizePx * 0.385f
 *         when (d.kind) {
 *             DialKind.ANALOG -> drawAnalog(c, d, sizePx, faceR, time, opts)
 *             DialKind.DIGITAL -> drawDigital(c, d, sizePx, faceR, time, opts)
 *             DialKind.WORD_EN -> drawWord(c, d, sizePx, faceR, time, EN_GRID, ::wordsEn)
 *             DialKind.WORD_ES -> drawWord(c, d, sizePx, faceR, time, ES_GRID, ::wordsEs)
 *             DialKind.WIDE -> drawWide(c, d, sizePx, faceR, time, opts)
 *             DialKind.BIG_DIGITAL -> drawBigDigital(c, d, sizePx, faceR, time, opts)
 *             DialKind.DAY -> drawDay(c, d, sizePx, faceR, time)
 *             DialKind.MOON -> drawMoon(c, d, sizePx, faceR, time, opts)
 *             DialKind.FRENCH_OVAL -> drawFrenchOval(c, d, sizePx, faceR, time, opts)
 *             DialKind.BIG_HOUR -> drawBigHour(c, d, sizePx, faceR, time, opts)
 *             DialKind.SEMPER -> drawSemper(c, d, sizePx, faceR, time, opts)
 *             DialKind.DUAL -> drawDual(c, d, sizePx, faceR, time)
 *         }
 *         drawInnerShadow(c, d, sizePx, faceR)
 *         DialRealism.drawGrain(c, facePath(d, sizePx, faceR), sizePx)
 *         if (opts.glass) {
 *             drawGlass(c, d, sizePx, faceR)
 *             drawCaseGlass(c, d, sizePx)
 *         }
 *         c.restore()
 *         if (opts.strap) c.restore()
 *         return bmp
 *     }
 *
 * ---------------------------------------------------------------
 * EDIT F  nothing else. The widget and the app previews already call
 *         DialRenderer.render(...) with the saved settings.
 * ---------------------------------------------------------------
 *
 * NOTES
 *  - Round / Square / Oval only change the classic analog dials and the Moon
 *    dial. Word clocks, digital and the others keep their own shape, because
 *    their layouts are built for a square face. (Oval aspect is still set
 *    on Semper and Française Oval by EDIT A.)
 *  - Text on an oval case is squeezed sideways by about 16%, so letters look
 *    a bit condensed. Hands still point exactly at their ticks.
 */

enum class CaseShape(val label: String) {
    AUTO("Auto"),
    ROUND("Round"),
    SQUARE("Square"),
    OVAL("Oval")
}

/** Width / height of an oval case. Smaller = thinner. 0.84 matches the Semper & Adhuc photo. */
const val OVAL_ASPECT = 0.84f

/** Applies the user's case-shape choice. Only dials that can handle round or square faces change. */
fun Dial.withShape(shape: CaseShape): Dial {
    if (shape == CaseShape.AUTO) return this
    val flexible = kind == DialKind.ANALOG || kind == DialKind.MOON
    if (!flexible) return this
    return when (shape) {
        CaseShape.ROUND -> copy(squircle = false, aspect = 1f)
        CaseShape.SQUARE -> copy(squircle = true, aspect = 1f)
        CaseShape.OVAL -> copy(squircle = true, aspect = OVAL_ASPECT)
        CaseShape.AUTO -> this
    }
}
