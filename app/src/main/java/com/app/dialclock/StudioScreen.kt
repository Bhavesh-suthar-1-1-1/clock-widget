package com.app.dialclock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.pow

private val StudioGold = Ui.Gold
private val StudioBorder = Ui.Hairline

/** Preview aspect, in pixels — the same sizes the widget uses on the home screen. */
private enum class StudioSize(val label: String, val w: Int, val h: Int) {
    SMALL("Small 2×2", 520, 520),
    MEDIUM("Medium 4×2", 720, 360),
    LARGE("Large 4×4", 720, 720)
}

/** Everything the Studio can change, in memory until Save & Apply. */
private data class StudioDraft(
    val dialId: String,
    val size: StudioSize = StudioSize.MEDIUM,
    // Representative luminance (0..1) of the user's REAL wallpaper, as
    // measured by WallpaperSense when the Studio opens (v3.0 — the
    // manual swatch picker is gone). The renderer blends the translucent
    // face over this to decide the text colour.
    val backgroundLum: Float = 0.12f,
    val faceColor: Int? = null,
    val handsColor: Int? = null,
    val accentColor: Int? = null,
    val glass: Boolean = true,
    val glassOpacity: Float = 0.6f,
    val solidFace: Boolean = false,
    val font: String? = null, // null = the dial's own typeface
    val showDate: Boolean = true,
    val showSeconds: Boolean = true,
    val hour24: Boolean = true,
    val shape: CaseShape = CaseShape.AUTO,
    // Responsive is the one size choice Save can really apply: Android never
    // lets an app force a placed widget to an exact cell size, so the size
    // chips above only change this preview. Responsive on = the widget fills
    // whatever box it is placed in; off = it stays a square watch.
    val responsive: Boolean = true
) {
    /** The exact settings object the home-screen widget renders with. */
    fun toSettings(base: DialSettings): DialSettings = base.copy(
        faceColor = faceColor,
        handsColor = handsColor,
        accentColor = accentColor,
        fontFamily = font,
        glass = glass,
        glassOpacity = glassOpacity,
        solidFace = solidFace,
        backgroundLum = backgroundLum,
        dateWindow = showDate,
        secondHand = showSeconds,
        hour24 = hour24,
        shape = shape,
        responsive = responsive
    )
}

private fun loadDraft(ctx: Context): StudioDraft {
    val s = DialPrefs.settings(ctx)
    return StudioDraft(
        dialId = DialPrefs.selectedId(ctx),
        backgroundLum = s.backgroundLum,
        faceColor = s.faceColor,
        handsColor = s.handsColor,
        accentColor = s.accentColor,
        glass = s.glass,
        glassOpacity = s.glassOpacity,
        solidFace = s.solidFace,
        font = s.fontFamily,
        showDate = s.dateWindow,
        showSeconds = s.secondHand,
        hour24 = s.hour24,
        shape = s.shape,
        responsive = s.responsive
    )
}

/** Write the draft to the same prefs the widget reads, then re-render the widget. */
private fun applyDraft(ctx: Context, draft: StudioDraft) {
    DialPrefs.prefs(ctx).edit()
        .putLong(DialPrefs.KEY_STUDIO_FACE, DialPrefs.colorToStore(draft.faceColor))
        .putLong(DialPrefs.KEY_STUDIO_HANDS, DialPrefs.colorToStore(draft.handsColor))
        .putLong(DialPrefs.KEY_STUDIO_ACCENT, DialPrefs.colorToStore(draft.accentColor))
        .putString(DialPrefs.KEY_STUDIO_FONT, draft.font ?: "")
        .putFloat(DialPrefs.KEY_STUDIO_OPACITY, draft.glassOpacity)
        .putFloat(DialPrefs.KEY_BG_LUM, draft.backgroundLum)
        .putBoolean(DialPrefs.KEY_SOLID_FACE, draft.solidFace)
        .apply()
    DialPrefs.select(ctx, draft.dialId)
    DialPrefs.setShape(ctx, draft.shape)
    DialPrefs.setSetting(ctx, DialPrefs.KEY_GLASS, draft.glass)
    DialPrefs.setSetting(ctx, DialPrefs.KEY_DATEWIN, draft.showDate)
    DialPrefs.setSetting(ctx, DialPrefs.KEY_SECOND, draft.showSeconds)
    DialPrefs.setSetting(ctx, DialPrefs.KEY_HOUR24, draft.hour24)
    DialPrefs.setSetting(ctx, DialPrefs.KEY_RESPONSIVE, draft.responsive)
    DialWidgetProvider.refreshAll(ctx)
}

private fun hasPlacedWidget(ctx: Context): Boolean {
    val mgr = AppWidgetManager.getInstance(ctx)
    val ids = mgr.getAppWidgetIds(ComponentName(ctx, DialWidgetProvider::class.java))
    return ids.isNotEmpty()
}

// ---------- which controls are real for a given dial ----------
// Every control below only appears when the widget renderer actually
// honours it for the selected dial. A hidden control is one that would
// do nothing — showing it would be a lie.

/** Face/hands/accent overrides: the renderer applies them to every dial
 * except Slats, which draws its own white face and black numbers — Slats
 * takes only the accent (v3.2: it recolours the rim, slats and capsule). */
private fun canColor(dial: Dial): Boolean = dial.kind != DialKind.SLATS

private fun canAccentOnly(dial: Dial): Boolean = dial.kind == DialKind.SLATS

/** Glass shine: the renderer draws it on every dial except Slats. */
private fun canGlass(dial: Dial): Boolean = dial.kind != DialKind.SLATS

/** Glass opacity: only the digital and hybrid backgrounds are drawn at
 * the chosen opacity; analog glass is a fixed shine. */
private fun canGlassOpacity(dial: Dial): Boolean =
    dial.kind == DialKind.DIGITAL_STYLE || dial.kind == DialKind.HYBRID

/** Typeface override: only the digital styles read fontFamily. */
private fun canFont(dial: Dial): Boolean = dial.kind == DialKind.DIGITAL_STYLE

/** Date: analog dials with a date window, digital styles that draw a
 * date line, and hybrids (date chip) — all gated on the date setting. */
private fun canDate(dial: Dial): Boolean = when (dial.kind) {
    DialKind.ANALOG -> dial.dateAt != 0
    DialKind.DIGITAL_STYLE -> DigitalDials.byId(dial.style)?.showDate == true
    DialKind.HYBRID -> true
    else -> false
}

/** Seconds: dials with a live second hand (analog) and hybrids (mini analog). */
private fun canSeconds(dial: Dial): Boolean =
    DialRenderer.hasLiveHands(dial) || dial.kind == DialKind.HYBRID ||
    dial.kind == DialKind.DIGITAL_STYLE

/** Live second-tick status: one compact line under the preview — Running,
 * or Not running with the exact reason and a Fix button when there is one.
 * The Studio preview ticks inside the app no matter what; the widget only
 * ticks when the service is alive AND eligible (second hand on, battery
 * optimisation off, dial has second motion). Re-checked on every tick. */
@Composable
private fun TickStatusRow(draft: StudioDraft, dial: Dial, tick: Int, settings: DialSettings) {
    val ctx = LocalContext.current
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    val batteryOk = remember(tick) { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
    val serviceAlive = remember(tick) { SecondTickService.isRunning }
    val motion = DialRenderer.hasSecondMotion(dial, settings)
    val running = motion && draft.showSeconds && batteryOk && serviceAlive
    val reason = when {
        !motion -> "This dial updates once a minute."
        !draft.showSeconds -> "Live seconds are off — enable Show seconds in the Text tab."
        !batteryOk -> "Battery optimisation is on, so the widget can't tick every second."
        !serviceAlive -> "The tick service isn't running."
        else -> "Ticking every second on your home screen."
    }
    val fixLabel: String? = when {
        !motion || !draft.showSeconds -> null
        !batteryOk -> "Fix"
        !serviceAlive -> "Restart"
        else -> null
    }
    val onFix: (() -> Unit)? = when {
        !motion || !draft.showSeconds -> null
        !batteryOk -> {
            {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                }
                ctx.startActivity(i)
            }
        }
        !serviceAlive -> { { SecondTickService.start(ctx) } }
        else -> null
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ui.Surface)
            .border(1.dp, Ui.Hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(9.dp).clip(CircleShape)
                .background(if (running) Ui.Good else if (motion && draft.showSeconds) Ui.Warn else Ui.TextLow)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (running) "Live tick running" else "Minute updates",
                color = Ui.TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Text(reason, color = Ui.TextMid, fontSize = 12.sp, lineHeight = 15.sp)
        }
        if (onFix != null && fixLabel != null) {
            Spacer(Modifier.width(10.dp))
            PillButton(fixLabel, onClick = onFix)
        }
    }
}

/** 12/24-hour: only dials that draw a digital time read the setting. */
private fun canHourFormat(dial: Dial): Boolean = when (dial.kind) {
    DialKind.DIGITAL_STYLE, DialKind.HYBRID, DialKind.SLATS,
    DialKind.DIGITAL, DialKind.BIG_DIGITAL, DialKind.WIDE -> true
    else -> false
}

/** Case shape: only the classic analog dials and Moon honour it — the
 * renderer squeezes those (withShape); every other kind keeps its own
 * shape, so the selector must not appear for them. */
private fun canShape(dial: Dial): Boolean =
    dial.kind == DialKind.ANALOG || dial.kind == DialKind.MOON

// ---------- dial lists per type chip ----------

private enum class StudioType(val label: String) {
    DIGITAL("Digital"), ANALOG("Analog"), HYBRID("Hybrid"), CLASSY("Classy"), SLATS("Slats")
}

private fun dialsForType(type: StudioType, customs: List<Dial>): List<Dial> = when (type) {
    StudioType.DIGITAL -> DigitalDials.STYLES.mapNotNull { s -> Dials.byId(s.id, customs) } +
        listOfNotNull(Dials.byId("word_en", customs), Dials.byId("word_es", customs), Dials.byId("digital", customs))
    StudioType.ANALOG -> listOf("kanji", "h24", "minimal", "neon", "moon", "bighour", "dual")
        .mapNotNull { Dials.byId(it, customs) }
    StudioType.HYBRID -> HybridDials.STYLES.mapNotNull { s -> Dials.byId(s.id, customs) }
    StudioType.CLASSY -> listOf("day", "wide", "bigdigital", "semper", "french", "frenchoval", "roman")
        .mapNotNull { Dials.byId(it, customs) }
    StudioType.SLATS -> listOfNotNull(Dials.byId("slats", customs))
}

private fun typeOf(dial: Dial): StudioType = when {
    dial.kind == DialKind.SLATS -> StudioType.SLATS
    dial.kind == DialKind.HYBRID -> StudioType.HYBRID
    dial.kind == DialKind.DIGITAL_STYLE || dial.kind == DialKind.DIGITAL ||
        dial.kind == DialKind.WORD_EN || dial.kind == DialKind.WORD_ES -> StudioType.DIGITAL
    dial.id in listOf("day", "wide", "bigdigital", "semper", "french", "frenchoval", "roman") -> StudioType.CLASSY
    else -> StudioType.ANALOG
}

// ---------- real presets: a dial plus its own designed look ----------

private data class StudioPreset(val name: String, val dialId: String)

private val STUDIO_PRESETS = listOf(
    StudioPreset("Midnight", "dig12"),
    StudioPreset("Sunset", "dig14"),
    StudioPreset("Minimal", "minimal"),
    StudioPreset("Slats", "slats")
)

// ---------- colour swatches ----------

private val SWATCHES = listOf(
    "White" to 0xFFFFFFFF.toInt(),
    "Black" to 0xFF111111.toInt(),
    "Red" to 0xFFD32F2F.toInt(),
    "Gold" to 0xFFC9A227.toInt(),
    "Orange" to 0xFFFF7A00.toInt(),
    "Pink" to 0xFFFF7BAC.toInt(),
    "Green" to 0xFF39FF6A.toInt(),
    "Cyan" to 0xFF00E5FF.toInt(),
    "Blue" to 0xFF1E88E5.toInt(),
    "Purple" to 0xFF7C4DFF.toInt()
)

private val FONT_CHOICES = listOf(
    "Dial's own" to null,
    "Sans" to "sans-serif",
    "Serif" to "serif",
    "Mono" to "monospace"
)

private enum class StudioTab(val label: String) {
    DESIGN("Design"), COLOUR("Colour"), TEXT("Text"), LAYOUT("Layout")
}

@Composable
fun StudioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val base = remember { DialPrefs.settings(ctx) }
    val customs = remember { DialPrefs.customs(ctx) }
    var draft by remember { mutableStateOf(loadDraft(ctx)) }
    var applied by remember { mutableStateOf(false) }
    var tabSel by remember { mutableStateOf(StudioTab.DESIGN) }
    val dial = Dials.byId(draft.dialId, customs) ?: Dials.BUILT_IN.first()
    val type = typeOf(dial)

    // Any edit after Apply means the widget no longer matches the preview.
    LaunchedEffect(draft) { applied = false }

    // Live preview: THE widget's own render call, with the draft's settings.
    // The provider calls renderRect when responsive is on, render() when
    // off — this calls the same one, so the preview is the widget.
    val settings = draft.toSettings(base)
    var tick by remember { mutableStateOf(0) }
    val motion = DialRenderer.hasSecondMotion(dial, settings)
    LaunchedEffect(dial.id, motion) {
        while (true) {
            delay(if (motion) 1_000L else 30_000L)
            tick++
        }
    }
    val previewBmp: Bitmap = remember(dial, tick, draft, settings) {
        val time = Calendar.getInstance()
        if (settings.responsive) {
            DialRenderer.renderRect(dial, draft.size.w, draft.size.h, time, settings)
        } else {
            DialRenderer.render(dial, minOf(draft.size.w, draft.size.h), time, settings)
        }
    }

    // Wallpaper auto-detection (v3.0): read the REAL wallpaper once when
    // the Studio opens. The reading drives the draft's backgroundLum
    // (the contrast reference the renderer uses).
    var wallpaperReading by remember { mutableStateOf<WallpaperSense.Reading?>(null) }
    LaunchedEffect(Unit) {
        try {
            WallpaperSense.sampleAsync(ctx) { reading ->
                wallpaperReading = reading
                draft = draft.copy(backgroundLum = reading.lum)
            }
        } catch (e: Exception) {
            android.util.Log.w("Studio", "Wallpaper sampling failed", e)
        }
    }

    // Preview and tabs stay pinned; only the controls below scroll.
    Column(
        Modifier.fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Studio", color = Ui.TextHi, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${dial.name} · ${draft.size.label}",
                    color = Ui.TextMid, fontSize = 13.sp, maxLines = 1
                )
            }
            PillButton(
                if (applied) "Applied ✓" else "Apply",
                onClick = {
                    applyDraft(ctx, draft)
                    applied = true
                },
                filled = !applied
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(previewBgColor(draft.backgroundLum))
                .border(1.dp, Ui.Hairline, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            // The preview background is ONLY the neutral colour at the
            // detected luminance (a real-wallpaper bitmap here crashed
            // Studio in v3.0).
            Image(
                bitmap = previewBmp.asImageBitmap(),
                contentDescription = dial.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(14.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        TickStatusRow(draft, dial, tick, settings)

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (t in StudioTab.values()) {
                ChipPill(t.label, tabSel == t) { tabSel = t }
            }
        }
        Spacer(Modifier.height(14.dp))

        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (tabSel) {
                StudioTab.DESIGN -> DesignTab(
                    dial = dial,
                    type = type,
                    customs = customs,
                    onType = { t ->
                        // Picking a type jumps to its first dial.
                        val first = dialsForType(t, customs).firstOrNull()
                        if (first != null) draft = draft.copy(dialId = first.id)
                    },
                    onDial = { d -> draft = draft.copy(dialId = d.id) },
                    onPreset = { preset ->
                        draft = draft.copy(
                            dialId = preset.dialId,
                            faceColor = null, handsColor = null, accentColor = null,
                            font = null, glass = true, glassOpacity = 0.6f,
                            solidFace = false,
                            showDate = true, showSeconds = true, hour24 = true,
                            shape = CaseShape.AUTO
                        )
                    },
                    draft = draft
                )

                StudioTab.COLOUR -> {
                    val anyColour = canColor(dial) || canAccentOnly(dial) || canGlass(dial)
                    if (!anyColour) InfoNote("This dial has no colour options.")
                    if (canColor(dial)) {
                        SectionLabel("Colours")
                        Text(
                            "“Default” keeps the colours this dial was designed with.",
                            color = Ui.TextMid, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        SurfaceCard(Modifier.fillMaxWidth()) {
                            Column {
                                ColorRow("Face", draft.faceColor) { draft = draft.copy(faceColor = it) }
                                Spacer(Modifier.height(16.dp))
                                ColorRow("Hands & text", draft.handsColor) { draft = draft.copy(handsColor = it) }
                                Spacer(Modifier.height(16.dp))
                                ColorRow("Accent", draft.accentColor) { draft = draft.copy(accentColor = it) }
                            }
                        }
                    } else if (canAccentOnly(dial)) {
                        SectionLabel("Colour")
                        Text(
                            "Recolours the rim, the slats and the time window.",
                            color = Ui.TextMid, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        SurfaceCard(Modifier.fillMaxWidth()) {
                            ColorRow("Slat colour", draft.accentColor) { draft = draft.copy(accentColor = it) }
                        }
                    }
                    if (canGlass(dial)) {
                        Spacer(Modifier.height(22.dp))
                        SectionLabel("Glass")
                        SurfaceCard(Modifier.fillMaxWidth()) {
                            Column {
                                ToggleRow("Glass effect", "Shine on the dial face", draft.glass) {
                                    draft = draft.copy(glass = it)
                                }
                                if (canGlassOpacity(dial)) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Glass opacity — ${(draft.glassOpacity * 100).toInt()}%",
                                        color = Ui.TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "How see-through the digital background is. Lower lets more wallpaper show through.",
                                        color = Ui.TextMid, fontSize = 12.sp, lineHeight = 16.sp
                                    )
                                    Slider(
                                        value = draft.glassOpacity,
                                        onValueChange = { draft = draft.copy(glassOpacity = it) },
                                        valueRange = 0.3f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Ui.Gold,
                                            activeTrackColor = Ui.Gold,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                    ToggleRow(
                                        "Solid face",
                                        "Face fully opaque — the preview colours are the widget colours on any wallpaper.",
                                        draft.solidFace
                                    ) { draft = draft.copy(solidFace = it) }
                                }
                            }
                        }
                    }
                }

                StudioTab.TEXT -> {
                    val showFont = canFont(dial)
                    val showDate = canDate(dial)
                    val showSeconds = canSeconds(dial)
                    val showHourFormat = canHourFormat(dial)
                    if (!(showFont || showDate || showSeconds || showHourFormat)) {
                        InfoNote("This dial has no text options.")
                    }
                    if (showFont) {
                        SectionLabel("Font")
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for ((label, family) in FONT_CHOICES) {
                                ChipPill(label, draft.font == family) { draft = draft.copy(font = family) }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                    if (showDate || showSeconds || showHourFormat) {
                        SectionLabel("Show")
                        SurfaceCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                            Column(Modifier.padding(horizontal = 8.dp)) {
                                var first = true
                                if (showDate) {
                                    ToggleRow("Date", "Date under the time, where this dial draws one", draft.showDate) {
                                        draft = draft.copy(showDate = it)
                                    }
                                    first = false
                                }
                                if (showSeconds) {
                                    if (!first) HairlineDivider()
                                    ToggleRow("Seconds", "Live second hand (uses more battery)", draft.showSeconds) {
                                        draft = draft.copy(showSeconds = it)
                                    }
                                    first = false
                                }
                                if (showHourFormat) {
                                    if (!first) HairlineDivider()
                                    ToggleRow("24-hour time", "On: 21:47. Off: 09:47 PM.", draft.hour24) {
                                        draft = draft.copy(hour24 = it)
                                    }
                                }
                            }
                        }
                    }
                }

                StudioTab.LAYOUT -> {
                    SectionLabel("Preview size")
                    Text(
                        "Your placed widget keeps its own size on the home screen — this only changes the preview.",
                        color = Ui.TextMid, fontSize = 12.sp, lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (sz in StudioSize.values()) {
                            ChipPill(sz.label, draft.size == sz) { draft = draft.copy(size = sz) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // Responsive sizing is the one size choice Apply can really
                    // set: Android never lets an app force a placed widget to an
                    // exact cell size.
                    SurfaceCard(Modifier.fillMaxWidth(), padding = 8.dp) {
                        Column(Modifier.padding(horizontal = 8.dp)) {
                            ToggleRow(
                                "Responsive sizing",
                                "Widget fills its real size when resized. Off keeps the square watch.",
                                draft.responsive
                            ) { draft = draft.copy(responsive = it) }
                        }
                    }
                    if (canShape(dial)) {
                        Spacer(Modifier.height(22.dp))
                        SectionLabel("Case shape")
                        Text(
                            "The watch case. Auto keeps this dial's own shape.",
                            color = Ui.TextMid, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (sh in CaseShape.values()) {
                                ChipPill(sh.label, draft.shape == sh) { draft = draft.copy(shape = sh) }
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    SectionLabel("Wallpaper")
                    Text(wallpaperStatusText(wallpaperReading), color = Ui.TextMid, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }

            // After Apply: offer to pin a widget when none is placed yet.
            if (applied && !hasPlacedWidget(ctx)) {
                Spacer(Modifier.height(22.dp))
                val pinSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported
                if (pinSupported) {
                    PillButton(
                        "Add a Dial widget to your home screen",
                        onClick = {
                            AppWidgetManager.getInstance(ctx).requestPinAppWidget(
                                ComponentName(ctx, DialWidgetProvider::class.java), null, null
                            )
                        },
                        filled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "No Dial widget on your home screen yet — long-press the home screen → Widgets → Dial to add it. It will use this look.",
                        color = Ui.TextMid, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Design tab ----------

@Composable
private fun DesignTab(
    dial: Dial,
    type: StudioType,
    customs: List<Dial>,
    draft: StudioDraft,
    onType: (StudioType) -> Unit,
    onDial: (Dial) -> Unit,
    onPreset: (StudioPreset) -> Unit
) {
    SectionLabel("Clock type")
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (t in StudioType.values()) {
            ChipPill(t.label, type == t) { onType(t) }
        }
    }

    // The dial picker: the real dials, rendered by the widget renderer.
    val list = dialsForType(type, customs)
    Spacer(Modifier.height(22.dp))
    SectionLabel("Design", "${list.size}")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list) { d ->
            val thumb = remember(d.id) {
                DialRenderer.render(d, 240, Calendar.getInstance(), DialSettings())
            }
            val on = d.id == dial.id
            val studioOnly = d.kind == DialKind.DIGITAL_STYLE && d.style !in DigitalDials.FEATURED_IDS
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(104.dp)
            ) {
                Box(
                    Modifier.size(104.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Ui.Surface)
                        .border(
                            if (on) 2.dp else 1.dp,
                            if (on) Ui.Gold else Ui.Hairline,
                            RoundedCornerShape(22.dp)
                        )
                        .clickable { onDial(d) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = d.name,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    d.name, color = if (on) Ui.Gold else Ui.TextHi,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 1
                )
                if (studioOnly) {
                    Spacer(Modifier.height(3.dp))
                    TagLabel("STUDIO")
                }
            }
        }
    }
    if (type == StudioType.DIGITAL) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Designs tagged STUDIO aren't in the main gallery — pick one here, then tap Apply.",
            color = Ui.TextMid, fontSize = 12.sp, lineHeight = 16.sp
        )
    }

    // Presets: real dials, their own designed look.
    Spacer(Modifier.height(24.dp))
    SectionLabel("Presets")
    Text(
        "A starting point — tap one, then change anything in the other tabs.",
        color = Ui.TextMid, fontSize = 12.sp
    )
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (preset in STUDIO_PRESETS) {
            val selectedPreset = draft.dialId == preset.dialId &&
                draft.faceColor == null && draft.handsColor == null &&
                draft.accentColor == null && draft.font == null
            ChipPill(preset.name, selectedPreset) { onPreset(preset) }
        }
    }
}

@Composable
private fun InfoNote(text: String) {
    SurfaceCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text(text, color = Ui.TextMid, fontSize = 14.sp)
    }
}

/** One status line for the auto wallpaper detection (v3.0). */
private fun wallpaperStatusText(reading: WallpaperSense.Reading?): String = when {
    reading == null -> "Auto — checking your wallpaper…"
    reading.readable -> "Auto — your wallpaper is ${WallpaperSense.brightnessWord(reading.lum)}; colours adjust on their own."
    else -> "Auto — couldn't read it (live wallpaper?), using the last reading."
}

/** Neutral grey with the detected wallpaper luminance — the preview
 * background when the real wallpaper bitmap isn't available. */
private fun previewBgColor(lum: Float): Color {
    val g = lum.coerceIn(0f, 1f).toDouble().pow(1.0 / 2.2).toFloat()
    return Color(g, g, g)
}

// ---------- small pieces ----------

/** One colour role: swatches first, then an optional custom R/G/B picker. */
@Composable
private fun ColorRow(label: String, current: Int?, onChange: (Int?) -> Unit) {
    var customOpen by remember { mutableStateOf(false) }
    Text(label, color = Ui.TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChipPill("Default", current == null) { onChange(null); customOpen = false }
        for ((_, argb) in SWATCHES) {
            SwatchDot(Color(argb), current == argb) { onChange(argb); customOpen = false }
        }
        ChipPill("Custom", customOpen) { customOpen = !customOpen }
    }
    if (customOpen) {
        Spacer(Modifier.height(10.dp))
        val base = current ?: 0xFFFFFFFF.toInt()
        var r by remember { mutableStateOf(android.graphics.Color.red(base)) }
        var g by remember { mutableStateOf(android.graphics.Color.green(base)) }
        var b by remember { mutableStateOf(android.graphics.Color.blue(base)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.rgb(r, g, b)))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                RgbSlider("R", r) { r = it; onChange(android.graphics.Color.rgb(r, g, b)) }
                RgbSlider("G", g) { g = it; onChange(android.graphics.Color.rgb(r, g, b)) }
                RgbSlider("B", b) { b = it; onChange(android.graphics.Color.rgb(r, g, b)) }
            }
        }
    }
}

@Composable
private fun RgbSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ui.TextMid, fontSize = 12.sp, modifier = Modifier.width(16.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Ui.Gold,
                activeTrackColor = Ui.Gold,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
        Text("$value", color = Ui.TextHi, fontSize = 12.sp, modifier = Modifier.width(28.dp))
    }
}
