package com.app.dialclock

import android.content.Context
import android.content.SharedPreferences

enum class DialKind { ANALOG, DIGITAL, WORD_EN, WORD_ES, WIDE, BIG_DIGITAL, DAY, MOON, FRENCH_OVAL, BIG_HOUR, SEMPER, DUAL, DIGITAL_STYLE, HYBRID, SLATS, KLOKERS }
enum class Numerals { ARABIC, ROMAN, DOTS, FRENCH, KANJI, NONE }

/** One watch dial. Colors are ARGB ints. dateAt: 3 or 6 o'clock, 0 = no date window. */
data class Dial(
    val id: String,
    val name: String,
    val kind: DialKind,
    val face: Int,
    val hands: Int,
    val accent: Int,
    val numerals: Numerals,
    val dateAt: Int = 3,
    val squircle: Boolean = false,
    val hours24: Boolean = false,
    val glow: Boolean = false,
    val custom: Boolean = false,
    val aspect: Float = 1f,
    val style: String = ""   // DIGITAL_STYLE / HYBRID style id (dig01.., hyb01..); "" for the classic dials
)

object Dials {
    val BUILT_IN: List<Dial> = listOf(
        Dial("french", "Française", DialKind.ANALOG, 0xFFF2EAD8.toInt(), 0xFFB3261E.toInt(), 0xFFB3261E.toInt(), Numerals.FRENCH, 3),
        Dial("kanji", "Kanji", DialKind.ANALOG, 0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFF111111.toInt(), Numerals.KANJI, 6),
        Dial("h24", "24 Hour", DialKind.ANALOG, 0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFF111111.toInt(), Numerals.ARABIC, 3, hours24 = true),
        Dial("word_en", "Word · EN", DialKind.WORD_EN, 0xFF141414.toInt(), 0xFFF5F5F5.toInt(), 0xFFC9A86A.toInt(), Numerals.NONE, 0, squircle = true),
        Dial("word_es", "Word · ES", DialKind.WORD_ES, 0xFF141414.toInt(), 0xFFF5F5F5.toInt(), 0xFFC9A86A.toInt(), Numerals.NONE, 0, squircle = true),
        Dial("roman", "Roman", DialKind.ANALOG, 0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFF111111.toInt(), Numerals.ROMAN, 3),
        Dial("minimal", "Minimal", DialKind.ANALOG, 0xFFFAFAFA.toInt(), 0xFF111111.toInt(), 0xFF111111.toInt(), Numerals.DOTS, 3),
        Dial("neon", "Neon", DialKind.ANALOG, 0xFF0A0A12.toInt(), 0xFF00E5FF.toInt(), 0xFF00E5FF.toInt(), Numerals.ARABIC, 3, glow = true),
        Dial("moon", "Moon Phase", DialKind.MOON, 0xFF87A49C.toInt(), 0xFFFFFFFF.toInt(), 0xFFE53935.toInt(), Numerals.NONE, 0),
        Dial("frenchoval", "Française Oval", DialKind.FRENCH_OVAL, 0xFFF2EAD8.toInt(), 0xFFB3261E.toInt(), 0xFFB3261E.toInt(), Numerals.NONE, 0, squircle = true, aspect = 0.84f),
        Dial("bighour", "Big Hour", DialKind.BIG_HOUR, 0xFFFFFFFF.toInt(), 0xFF4A5560.toInt(), 0xFF4A5560.toInt(), Numerals.NONE, 0),
        Dial("semper", "Semper White", DialKind.SEMPER, 0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFF111111.toInt(), Numerals.NONE, 0, squircle = true, aspect = 0.84f),
        Dial("dual", "Dual Time", DialKind.DUAL, 0xFF101010.toInt(), 0xFFD4AF37.toInt(), 0xFFD4AF37.toInt(), Numerals.NONE, 0, squircle = true),
        Dial("wide", "Wide", DialKind.WIDE, 0xFF101010.toInt(), 0xFFFFFFFF.toInt(), 0xFFE53935.toInt(), Numerals.NONE, 0, squircle = true),
        Dial("bigdigital", "Big Digital", DialKind.BIG_DIGITAL, 0xFF101010.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), Numerals.NONE, 0, squircle = true),
        Dial("day", "Day", DialKind.DAY, 0xFF101010.toInt(), 0xFFFFFFFF.toInt(), 0xFFC9A86A.toInt(), Numerals.NONE, 0, squircle = true),
        // Red slat wall clock: round, red rim, white face, rotating number rings
        // behind vertical red slats, digital time in a capsule window.
        Dial("slats", "Slats", DialKind.SLATS, 0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFFD32F2F.toInt(), Numerals.NONE, 0, squircle = false),
        // Klokers KLOK-01 (v3.4, "Featured"): white slide-rule face, black
        // Arabic hours and 00/15/30/45 minute scale on discs that rotate
        // with the time, orange centre disc, fixed orange marker at 12.
        Dial("klokers", "Klokers", DialKind.KLOKERS, 0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFFE8590C.toInt(), Numerals.ARABIC, 0)
    ) + DigitalDials.STYLES.map { s ->
        Dial(s.id, s.name, DialKind.DIGITAL_STYLE, s.bg, s.fg, s.accent, Numerals.NONE, 0, squircle = true, style = s.id)
    } + HybridDials.STYLES.map { s ->
        Dial(s.id, s.name, DialKind.HYBRID, s.bg, s.fg, s.accent, Numerals.NONE, 0, squircle = true, style = s.id)
    }

    fun byId(id: String, customs: List<Dial>): Dial? =
        (BUILT_IN + customs).firstOrNull { it.id == id }

    fun all(customs: List<Dial>): List<Dial> = BUILT_IN + customs
}

/** Digital time arrangement: one row ("05:16") or stacked hours over minutes. */
enum class DigitalLayout { ONE_ROW, STACKED }

/** Render options the user can switch on/off in Settings. Defaults = the look v1.3 shipped with. */
data class DialSettings(
    val glass: Boolean = true,
    val shadow: Boolean = true,
    val secondHand: Boolean = true,
    val dateWindow: Boolean = true,
    val hour24: Boolean = true,
    val brand: Boolean = true,
    val minuteTrack: Boolean = true,
    val strap: Boolean = true,
    val shape: CaseShape = CaseShape.AUTO,
    val responsive: Boolean = true,
    // Custom overrides (null = keep the dial's own designed colours).
    // Persisted in the same "dial_prefs" file; the widget
    // provider reads them here, so Apply in Studio changes the real widget.
    val faceColor: Int? = null,
    val handsColor: Int? = null,
    val accentColor: Int? = null,
    val fontFamily: String? = null,
    val glassOpacity: Float = 0.6f,
    // Solid face: when on, the digital/hybrid face is drawn fully
    // opaque, so the preview colours ARE the widget colours on any
    // wallpaper. Off (default) keeps the frosted-glass look.
    val solidFace: Boolean = false,
    // Representative luminance (0..1) of the wallpaper the widget sits
    // on, as picked in the Studio (default dark = his flower photo).
    // The renderer blends the translucent face over this to decide the
    // text colour — it is what makes preview and widget agree.
    val backgroundLum: Float = 0.12f,
    // v3.8 Studio customisation for the kept digital + hybrid.
    // fontWeight null = the style's own weight (bold styles stay bold).
    val fontWeight: Float? = null, // 0..1, Thin -> Bold
    val digitalLayout: DigitalLayout = DigitalLayout.ONE_ROW,
    val showColon: Boolean = true,
    val digitalScale: Float = 1f, // digital text size multiplier, 0.7..1.3
    // Hybrid: null layout = the style's own arrangement; scales multiply
    // the style's own analog/digital sizes.
    val hybridLayout: String? = null, // "SQUARE" / "WIDE" / "TALL"
    val hybridAnalogScale: Float = 1f,
    val hybridDigitalScale: Float = 1f
)

object DialPrefs {
    private const val NAME = "dial_prefs"
    private const val KEY_SELECTED = "selected_dial"
    private const val KEY_USES = "uses_"
    private const val KEY_FAVS = "favs"
    private const val KEY_CUSTOMS = "customs"
    private const val KEY_TAP = "tap_action" // "open" or "next"
    const val KEY_GLASS = "glass"
    const val KEY_SHADOW = "shadow"
    const val KEY_SECOND = "second_hand"
    const val KEY_DATEWIN = "date_window"
    const val KEY_HOUR24 = "hour24"
    const val KEY_BRAND = "brand"
    const val KEY_TICKS = "minute_track"
    const val KEY_STRAP = "strap"
    private const val KEY_SHAPE = "case_shape"
    const val KEY_RESPONSIVE = "responsive"
    const val KEY_TORCH = "torch_on"
    private const val KEY_HIDDEN_UPDATE = "hidden_update"
    // Custom overrides (Studio writes these keys).
    // v3.2: stored as Long (ARGB in the low 32 bits, NONE_COLOR = no override).
    // The old Int keys used -1 for "none", but -1 is also opaque white, so a
    // white face/text colour picked in the Studio was silently dropped on Apply.
    const val NONE_COLOR = -1L
    const val KEY_STUDIO_FACE = "studio_face_v2"
    const val KEY_STUDIO_HANDS = "studio_hands_v2"
    const val KEY_STUDIO_ACCENT = "studio_accent_v2"
    const val KEY_STUDIO_FONT = "studio_font"       // family string, "" = none
    const val KEY_STUDIO_OPACITY = "studio_opacity" // Float 0.3..1.0
    const val KEY_SOLID_FACE = "solid_face"         // Boolean
    const val KEY_BG_LUM = "bg_lum"                 // Float 0..1, wallpaper luminance
    const val KEY_WALLPAPER = "studio_wallpaper"    // StudioWallpaper name
    // v3.8 Studio customisation (kept digital + hybrid).
    const val KEY_STUDIO_WEIGHT = "studio_weight"   // Float 0..1, -1 = style's own
    const val KEY_DIGITAL_LAYOUT = "digital_layout" // "ONE_ROW" / "STACKED"
    const val KEY_SHOW_COLON = "show_colon"         // Boolean
    const val KEY_DIGITAL_SCALE = "digital_scale"   // Float 0.7..1.3
    const val KEY_HYB_LAYOUT = "hyb_layout"         // "" = style's own, else SQUARE/WIDE/TALL
    const val KEY_HYB_ANALOG_SCALE = "hyb_analog_scale"   // Float 0.7..1.3
    const val KEY_HYB_DIGITAL_SCALE = "hyb_digital_scale" // Float 0.7..1.3

    /** All render settings in one read, for the widget and the previews. */
    fun settings(ctx: Context): DialSettings {
        val p = prefs(ctx)
        return DialSettings(
            glass = p.getBoolean(KEY_GLASS, true),
            shadow = p.getBoolean(KEY_SHADOW, true),
            secondHand = p.getBoolean(KEY_SECOND, true),
            dateWindow = p.getBoolean(KEY_DATEWIN, true),
            hour24 = p.getBoolean(KEY_HOUR24, true),
            brand = p.getBoolean(KEY_BRAND, true),
            minuteTrack = p.getBoolean(KEY_TICKS, true),
            strap = p.getBoolean(KEY_STRAP, true),
            shape = try { CaseShape.valueOf(p.getString(KEY_SHAPE, "AUTO") ?: "AUTO") }
                    catch (e: Exception) { CaseShape.AUTO },
            responsive = p.getBoolean(KEY_RESPONSIVE, true),
            faceColor = readColor(p, KEY_STUDIO_FACE),
            handsColor = readColor(p, KEY_STUDIO_HANDS),
            accentColor = readColor(p, KEY_STUDIO_ACCENT),
            fontFamily = p.getString(KEY_STUDIO_FONT, "")?.takeIf { it.isNotEmpty() },
            glassOpacity = p.getFloat(KEY_STUDIO_OPACITY, 0.6f).coerceIn(0.3f, 1f),
            solidFace = p.getBoolean(KEY_SOLID_FACE, false),
            backgroundLum = p.getFloat(KEY_BG_LUM, 0.12f).coerceIn(0f, 1f),
            fontWeight = p.getFloat(KEY_STUDIO_WEIGHT, -1f).takeIf { it >= 0f }?.coerceIn(0f, 1f),
            digitalLayout = try { DigitalLayout.valueOf(p.getString(KEY_DIGITAL_LAYOUT, "ONE_ROW") ?: "ONE_ROW") }
                    catch (e: Exception) { DigitalLayout.ONE_ROW },
            showColon = p.getBoolean(KEY_SHOW_COLON, true),
            digitalScale = p.getFloat(KEY_DIGITAL_SCALE, 1f).coerceIn(0.7f, 1.3f),
            hybridLayout = p.getString(KEY_HYB_LAYOUT, "")?.takeIf { it.isNotEmpty() },
            hybridAnalogScale = p.getFloat(KEY_HYB_ANALOG_SCALE, 1f).coerceIn(0.7f, 1.3f),
            hybridDigitalScale = p.getFloat(KEY_HYB_DIGITAL_SCALE, 1f).coerceIn(0.7f, 1.3f)
        )
    }

    private fun readColor(p: SharedPreferences, key: String): Int? = try {
        p.getLong(key, NONE_COLOR).takeIf { it != NONE_COLOR }?.toInt()
    } catch (e: ClassCastException) { null }

    fun colorToStore(color: Int?): Long = color?.let { it.toLong() and 0xFFFFFFFFL } ?: NONE_COLOR

    fun setSetting(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(key, value).apply()
    }

    /** Version the user hid the update popup for — it must not re-show for that version. */
    fun hiddenUpdateVersion(ctx: Context): String =
        prefs(ctx).getString(KEY_HIDDEN_UPDATE, "") ?: ""

    fun hideUpdate(ctx: Context, version: String) {
        prefs(ctx).edit().putString(KEY_HIDDEN_UPDATE, version).apply()
    }

    fun setShape(ctx: Context, shape: CaseShape) {
        prefs(ctx).edit().putString(KEY_SHAPE, shape.name).apply()
    }

    fun getSetting(ctx: Context, key: String, def: Boolean): Boolean =
        prefs(ctx).getBoolean(key, def)

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun selectedId(ctx: Context): String {
        val id = prefs(ctx).getString(KEY_SELECTED, "french") ?: "french"
        // v3.8: all digital styles but dig12 and all hybrids but hyb01
        // were removed, and the classic Seiko Gold digital with them. A
        // phone that had one of those selected opens on the kept digital
        // instead. Customs are checked first, so a custom dial whose id
        // happens to start with "dig" is never redirected.
        if (Dials.byId(id, customs(ctx)) != null) return id
        return when {
            id == "digital" || id.startsWith("dig") -> "dig12"
            id.startsWith("hyb") -> "hyb01"
            else -> id
        }
    }

    fun select(ctx: Context, id: String) {
        val p = prefs(ctx)
        p.edit().putString(KEY_SELECTED, id).apply()
        p.edit().putInt(KEY_USES + id, uses(ctx, id) + 1).apply()
    }

    fun uses(ctx: Context, id: String): Int = prefs(ctx).getInt(KEY_USES + id, 0)

    fun favorites(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_FAVS, emptySet())?.toSet() ?: emptySet()

    fun isFav(ctx: Context, id: String): Boolean =
        prefs(ctx).getStringSet(KEY_FAVS, emptySet())?.contains(id) == true

    fun toggleFav(ctx: Context, id: String) {
        val p = prefs(ctx)
        val set = (p.getStringSet(KEY_FAVS, emptySet()) ?: emptySet()).toMutableSet()
        if (set.contains(id)) set.remove(id) else set.add(id)
        p.edit().putStringSet(KEY_FAVS, set).apply()
    }

    /** Custom dials stored as lines:
     *  id|name|kind|face|hands|accent|numerals|dateAt|squircle|hours24|glow|aspect|style
     *  Old 8-field lines (v2.7 and earlier) still parse — the five newer
     *  fields default, so nothing saved before is lost. */
    fun customs(ctx: Context): List<Dial> {
        val raw = prefs(ctx).getString(KEY_CUSTOMS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.lines().mapNotNull { line ->
            val p = line.split("|")
            if (p.size < 8) return@mapNotNull null
            try {
                Dial(
                    id = p[0], name = p[1],
                    kind = DialKind.valueOf(p[2]),
                    face = p[3].toInt(), hands = p[4].toInt(), accent = p[5].toInt(),
                    numerals = Numerals.valueOf(p[6]), dateAt = p[7].toInt(),
                    squircle = p.getOrNull(8)?.toBooleanStrictOrNull() ?: false,
                    hours24 = p.getOrNull(9)?.toBooleanStrictOrNull() ?: false,
                    glow = p.getOrNull(10)?.toBooleanStrictOrNull() ?: false,
                    aspect = p.getOrNull(11)?.toFloatOrNull() ?: 1f,
                    style = p.getOrNull(12) ?: "",
                    custom = true
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /** One custom dial as a 13-field line. `custom` is not stored — the
     *  parser always sets it, since only customs come from this file. */
    private fun Dial.toLine(): String = listOf(
        id, name, kind.name, face.toString(), hands.toString(), accent.toString(),
        numerals.name, dateAt.toString(), squircle.toString(), hours24.toString(),
        glow.toString(), aspect.toString(), style
    ).joinToString("|")

    fun addCustom(ctx: Context, d: Dial) {
        val list = customs(ctx).toMutableList()
        list.removeAll { it.id == d.id }
        list.add(d)
        val raw = list.joinToString("\n") { it.toLine() }
        prefs(ctx).edit().putString(KEY_CUSTOMS, raw).apply()
    }

    fun deleteCustom(ctx: Context, id: String) {
        val list = customs(ctx).filter { it.id != id }
        val raw = list.joinToString("\n") { it.toLine() }
        prefs(ctx).edit().putString(KEY_CUSTOMS, raw).apply()
        if (selectedId(ctx) == id) select(ctx, "french")
    }

    /** Gallery order: most used first, then the rest in built-in order. */
    fun ordered(ctx: Context): List<Dial> {
        val all = Dials.all(customs(ctx))
        return all.sortedByDescending { uses(ctx, it.id) }
    }
}
