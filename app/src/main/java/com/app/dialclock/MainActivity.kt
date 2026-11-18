package com.app.dialclock

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings as SettingsGlyph
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DialApp(resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // Self-heal the clock: re-render the Dial widget and re-arm the
        // minute alarm, so merely opening the app repairs a dead chain.
        DialWidgetProvider.refreshAll(this)
        TickScheduler.scheduleNextMinute(this)
        // Re-read the real wallpaper too (v3.0): the manifest receiver
        // for wallpaper changes is deprecated and unreliable on newer
        // Android, so opening the app is the guaranteed re-check — the
        // widget is only re-rendered if the luminance actually moved.
        WallpaperSense.sampleAsync(this)
    }
}

// ───────────────────────── gallery model ─────────────────────────

private enum class Tab(val label: String, val icon: ImageVector) {
    GALLERY("Gallery", Icons.Rounded.GridView),
    STUDIO("Studio", Icons.Rounded.Palette),
    SETTINGS("Settings", Icons.Rounded.SettingsGlyph)
}

private enum class GalleryFilter(val label: String) {
    ALL("All"), ANALOG("Analog"), DIGITAL("Digital"), HYBRID("Hybrid"), WORD("Word"), SAVED("Saved")
}

/** Gallery section of a dial: featured clock, analog watches, digital clocks, hybrids, word clocks. */
private fun sectionOf(dial: Dial): String = when (dial.kind) {
    DialKind.SLATS, DialKind.KLOKERS -> "Featured"
    DialKind.DIGITAL_STYLE -> "Digital"
    DialKind.HYBRID -> "Hybrid"
    DialKind.WORD_EN, DialKind.WORD_ES -> "Word"
    else -> "Analog"
}

/**
 * v3.2: half of the Digital styles are Studio-only. They are still real
 * dials — pick one in the Studio and Save & Apply, and it becomes the
 * widget. The gallery shows them only while one is applied (so the
 * "Active" card is never missing) or when the user saved it as a favourite.
 */
private fun visibleInGallery(dial: Dial, selected: String, favs: Set<String>): Boolean {
    if (dial.kind != DialKind.DIGITAL_STYLE) return true
    if (dial.style in DigitalDials.FEATURED_IDS) return true
    return dial.id == selected || dial.id in favs
}

private fun appVersion(ctx: Context): String = try {
    @Suppress("DEPRECATION")
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (_: Exception) {
    ""
}

// ───────────────────────── previews ─────────────────────────

/**
 * One dial preview, drawn by the same renderer as the widget. v3.2: Slats
 * no longer previews through Vulkan — the Vulkan shader is a separate
 * drawing of the clock, so the in-app picture and the home-screen widget
 * could differ. Everything is now Canvas, so what you see is the widget.
 */
@Composable
private fun DialPreview(dial: Dial, sizeDp: Int, settings: DialSettings = DialSettings()) {
    var tick by remember { mutableStateOf(0) }
    // Slats re-renders every second so its rings visibly turn in real
    // time; every other preview keeps the 30-second refresh.
    val refreshMs = if (dial.kind == DialKind.SLATS) 1_000L else 30_000L
    LaunchedEffect(dial.id) {
        while (true) {
            delay(refreshMs)
            tick++
        }
    }
    val bmp = remember(dial, tick, settings) {
        DialRenderer.render(dial, (sizeDp * 2.5f).toInt(), Calendar.getInstance(), settings)
    }
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = dial.name,
        modifier = Modifier.size(sizeDp.dp)
    )
}

@Composable
private fun DialCard(
    dial: Dial,
    active: Boolean,
    faved: Boolean,
    settings: DialSettings,
    modifier: Modifier,
    onPick: () -> Unit,
    onFav: () -> Unit,
    onDelete: (() -> Unit)?
) {
    SurfaceCard(
        modifier = modifier,
        selected = active,
        corner = 26.dp,
        padding = 0.dp,
        onClick = onPick
    ) {
        Column {
            // Tile: the dial on a soft dark stage.
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1F2229), Color(0xFF14161B))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                DialPreview(dial, 128, settings)
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { onFav() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (faved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (faved) "Remove from saved" else "Save",
                        tint = if (faved) Ui.Gold else Ui.TextMid,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (active) {
                    Box(Modifier.align(Alignment.TopStart).padding(10.dp)) { TagLabel("ACTIVE") }
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    dial.name, color = Ui.TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (active) "On your home screen" else sectionOf(dial),
                        color = if (active) Ui.Gold else Ui.TextLow, fontSize = 12.sp,
                        modifier = Modifier.weight(1f), maxLines = 1
                    )
                    if (onDelete != null) {
                        Text(
                            "Delete", color = Ui.Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialGrid(
    dials: List<Dial>,
    selected: String,
    favs: Set<String>,
    settings: DialSettings,
    onPick: (Dial) -> Unit,
    onFav: (Dial) -> Unit,
    onDelete: (Dial) -> Unit
) {
    for (row in dials.chunked(2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (dial in row) {
                DialCard(
                    dial = dial,
                    active = selected == dial.id,
                    faved = dial.id in favs,
                    settings = settings,
                    modifier = Modifier.weight(1f),
                    onPick = { onPick(dial) },
                    onFav = { onFav(dial) },
                    onDelete = if (dial.custom) ({ onDelete(dial) }) else null
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
    }
}

// ───────────────────────── gallery screen ─────────────────────────

@Composable
private fun GalleryScreen(
    ordered: List<Dial>,
    selected: String,
    favs: Set<String>,
    settings: DialSettings,
    needsPermission: Boolean,
    onPick: (Dial) -> Unit,
    onFav: (Dial) -> Unit,
    onDelete: (Dial) -> Unit,
    onOpenStudio: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var filter by remember { mutableStateOf(GalleryFilter.ALL) }
    val activeDial = ordered.firstOrNull { it.id == selected }
    val shown = ordered.filter { visibleInGallery(it, selected, favs) }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 110.dp)
    ) {
        ScreenTitle("Dial", "Real watch dials, live on your home screen.")

        // Hero: what the widget shows right now, with a way to tweak it.
        if (activeDial != null) {
            Spacer(Modifier.height(20.dp))
            SurfaceCard(Modifier.fillMaxWidth(), corner = 28.dp, padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DialPreview(activeDial, 112, settings)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        TagLabel("ON YOUR HOME SCREEN")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            activeDial.name, color = Ui.TextHi, fontSize = 22.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(sectionOf(activeDial), color = Ui.TextMid, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        PillButton("Customise", onClick = onOpenStudio, icon = Icons.Rounded.Palette)
                    }
                }
            }
        }

        if (needsPermission) {
            Spacer(Modifier.height(14.dp))
            SurfaceCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Ui.Warn))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Widget needs permission", color = Ui.TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Allow unrestricted battery and exact alarms so it never freezes.",
                            color = Ui.TextMid, fontSize = 12.sp, lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    PillButton("Fix", onClick = onOpenSettings)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (f in GalleryFilter.values()) {
                ChipPill(f.label, filter == f) { filter = f }
            }
        }
        Spacer(Modifier.height(18.dp))

        val sections = listOf("Featured", "Analog", "Digital", "Hybrid", "Word")
        when (filter) {
            GalleryFilter.ALL -> {
                for (section in sections) {
                    val dials = shown.filter { sectionOf(it) == section }
                    if (dials.isEmpty()) continue
                    SectionLabel(section, "${dials.size}")
                    DialGrid(dials, selected, favs, settings, onPick, onFav, onDelete)
                    Spacer(Modifier.height(6.dp))
                }
                // The other half of the digital designs lives in the Studio.
                val hiddenCount = DigitalDials.STYLES.count { !it.featured }
                SurfaceCard(
                    Modifier.fillMaxWidth(), padding = 16.dp, onClick = onOpenStudio
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "$hiddenCount more digital designs",
                                color = Ui.TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Open the Studio → Design → Digital to switch to any of them.",
                                color = Ui.TextMid, fontSize = 12.sp, lineHeight = 16.sp
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = Ui.Gold)
                    }
                }
            }
            GalleryFilter.SAVED -> {
                val dials = ordered.filter { it.id in favs }
                if (dials.isEmpty()) {
                    Text(
                        "Nothing saved yet. Tap the heart on any dial to keep it here.",
                        color = Ui.TextMid, fontSize = 14.sp
                    )
                } else {
                    DialGrid(dials, selected, favs, settings, onPick, onFav, onDelete)
                }
            }
            else -> {
                val wanted = when (filter) {
                    GalleryFilter.ANALOG -> listOf("Featured", "Analog")
                    GalleryFilter.DIGITAL -> listOf("Digital")
                    GalleryFilter.HYBRID -> listOf("Hybrid")
                    else -> listOf("Word")
                }
                val dials = shown.filter { sectionOf(it) in wanted }
                DialGrid(dials, selected, favs, settings, onPick, onFav, onDelete)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Add the widget: long-press your home screen → Widgets → Dial.",
            color = Ui.TextLow, fontSize = 12.sp
        )
    }
}

// ───────────────────────── bottom navigation ─────────────────────────

@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .padding(bottom = 18.dp)
            .clip(shape)
            .background(Color(0xFF1A1D23).copy(alpha = 0.96f))
            .border(1.dp, Ui.Hairline, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (t in Tab.values()) {
            val on = t == current
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) Ui.Gold else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(horizontal = if (on) 18.dp else 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    t.icon, contentDescription = t.label,
                    tint = if (on) Color(0xFF1A1405) else Ui.TextMid,
                    modifier = Modifier.size(20.dp)
                )
                if (on) {
                    Spacer(Modifier.width(8.dp))
                    Text(t.label, color = Color(0xFF1A1405), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ───────────────────────── app root ─────────────────────────

@Composable
private fun DialApp(resumeTick: Int) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(Tab.GALLERY) }
    val selected = remember(refresh, resumeTick) { DialPrefs.selectedId(ctx) }
    val ordered = remember(refresh, resumeTick) { DialPrefs.ordered(ctx) }
    val favs = remember(refresh, resumeTick) { DialPrefs.favorites(ctx) }
    val settings = remember(refresh, resumeTick) { DialPrefs.settings(ctx) }

    // System back from Studio / Settings returns to the gallery instead of
    // closing the app.
    BackHandler(enabled = tab != Tab.GALLERY) { tab = Tab.GALLERY }

    // In-app update check (Atmo-style): once per launch, silent when up to
    // date or offline. Only a newer GitHub release shows the dialog.
    var update by remember { mutableStateOf<UpdateCheck.Release?>(null) }
    var updateProgress by remember { mutableStateOf(-1) } // -1 = not downloading
    var updateError by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val rel = UpdateCheck.check(ctx)
        if (rel != null && DialPrefs.hiddenUpdateVersion(ctx) != rel.version) update = rel
    }

    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    val batteryOk = remember(refresh, resumeTick) { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val exactOk = remember(refresh, resumeTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
    }

    AppBackdrop {
        when (tab) {
            Tab.GALLERY -> GalleryScreen(
                ordered = ordered,
                selected = selected,
                favs = favs,
                settings = settings,
                needsPermission = !batteryOk || !exactOk,
                onPick = { dial ->
                    DialPrefs.select(ctx, dial.id)
                    DialWidgetProvider.refreshAll(ctx)
                    refresh++
                },
                onFav = { dial ->
                    DialPrefs.toggleFav(ctx, dial.id)
                    refresh++
                },
                onDelete = { dial ->
                    DialPrefs.deleteCustom(ctx, dial.id)
                    DialWidgetProvider.refreshAll(ctx)
                    refresh++
                },
                onOpenStudio = { tab = Tab.STUDIO },
                onOpenSettings = { tab = Tab.SETTINGS }
            )
            Tab.STUDIO -> Box(Modifier.fillMaxSize().padding(bottom = 92.dp)) {
                StudioScreen(onBack = { tab = Tab.GALLERY })
            }
            Tab.SETTINGS -> SettingsScreen(
                settings = settings,
                batteryOk = batteryOk,
                exactOk = exactOk,
                onChanged = {
                    DialWidgetProvider.refreshAll(ctx)
                    refresh++
                }
            )
        }

        BottomNav(
            current = tab,
            onSelect = { tab = it; refresh++ },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (update != null) {
            val release = update!!
            UpdateDialog(
                release = release,
                progress = updateProgress,
                error = updateError,
                onHide = {
                    DialPrefs.hideUpdate(ctx, release.version)
                    update = null
                },
                onUpdate = {
                    updateError = false
                    updateProgress = 0
                    updateScope.launch {
                        val file = UpdateCheck.download(ctx, release.apkUrl) { p -> updateProgress = p }
                        if (file != null) {
                            update = null
                            UpdateCheck.install(ctx, file)
                        } else {
                            updateProgress = -1
                            updateError = true
                        }
                    }
                },
                onDismiss = { update = null }
            )
        }
    }
}

@Composable
private fun UpdateDialog(
    release: UpdateCheck.Release,
    progress: Int,
    error: Boolean,
    onHide: () -> Unit,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available", color = Ui.TextHi, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Version ${release.version}", color = Ui.Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                for (note in release.notes) {
                    Row {
                        Text("• ", color = Ui.TextMid, fontSize = 13.sp)
                        Text(note, color = Ui.TextHi, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (progress >= 0) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Ui.Gold,
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Downloading… $progress%", color = Ui.TextMid, fontSize = 12.sp)
                }
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Download failed — check your connection and try again.",
                        color = Ui.Danger, fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            PillButton(
                if (progress >= 0) "Downloading…" else "Update",
                onClick = onUpdate,
                enabled = progress < 0
            )
        },
        dismissButton = { PillButton("Hide", onClick = onHide, filled = false) },
        containerColor = Ui.SurfaceHi,
        shape = RoundedCornerShape(28.dp)
    )
}

// ───────────────────────── settings ─────────────────────────

@Composable
private fun SettingsScreen(
    settings: DialSettings,
    batteryOk: Boolean,
    exactOk: Boolean,
    onChanged: () -> Unit
) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 110.dp)
    ) {
        ScreenTitle("Settings", "Global switches for every dial.")

        Spacer(Modifier.height(22.dp))
        SectionLabel("Look")
        SurfaceCard(Modifier.fillMaxWidth(), padding = 8.dp) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                ToggleRow("Glass effect", "Crystal shine on every dial", settings.glass) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_GLASS, it); onChanged()
                }
                HairlineDivider()
                ToggleRow("Drop shadow", "Soft shadow of the watch on your wallpaper", settings.shadow) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_SHADOW, it); onChanged()
                }
                HairlineDivider()
                ToggleRow("Date window", "Show the date box on analog dials", settings.dateWindow) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_DATEWIN, it); onChanged()
                }
                HairlineDivider()
                ToggleRow("Brand text", "Show the small DIAL name on analog dials", settings.brand) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_BRAND, it); onChanged()
                }
                HairlineDivider()
                ToggleRow("Minute track", "Minute ticks around analog dials", settings.minuteTrack) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_TICKS, it); onChanged()
                }
                HairlineDivider()
                ToggleRow("Watch strap", "Leather strap above and below the case", settings.strap) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_STRAP, it); onChanged()
                }
                HairlineDivider()
                ToggleRow("Responsive sizing", "Widgets fill their real size when resized. Off keeps the square watch.", settings.responsive) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_RESPONSIVE, it); onChanged()
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("Case shape")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CaseShape.values().forEach { s ->
                ChipPill(s.label, settings.shape == s) { DialPrefs.setShape(ctx, s); onChanged() }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("Time")
        SurfaceCard(Modifier.fillMaxWidth(), padding = 8.dp) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                ToggleRow("24-hour time", "Digital dials show 21:47. Off shows 09:47 PM.", settings.hour24) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_HOUR24, it); onChanged()
                }
                HairlineDivider()
                ToggleRow(
                    "Live second hand",
                    "Ticks every second on analog dials and turns the Slats rings. Uses more battery.",
                    settings.secondHand
                ) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_SECOND, it)
                    if (it && !batteryOk) {
                        // The live tick needs unrestricted battery — open the
                        // same permission flow; the service only starts once granted.
                        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                        }
                        ctx.startActivity(i)
                    }
                    onChanged()
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("Keep it ticking")
        SurfaceCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (batteryOk && exactOk) Ui.Good else Ui.Warn)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (batteryOk && exactOk) "Battery and alarms are set. The widget ticks on time."
                        else "Allow these so the widget updates on time, even in the background.",
                        color = Ui.TextMid, fontSize = 13.sp, lineHeight = 18.sp
                    )
                }
                if (!batteryOk) {
                    Spacer(Modifier.height(12.dp))
                    PillButton(
                        "Allow unrestricted battery",
                        onClick = {
                            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${ctx.packageName}")
                            }
                            ctx.startActivity(i)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!exactOk) {
                    Spacer(Modifier.height(8.dp))
                    PillButton(
                        "Allow exact alarms",
                        onClick = {
                            val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${ctx.packageName}")
                            }
                            ctx.startActivity(i)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("About")
        SurfaceCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Column {
                Text("Dial ${appVersion(ctx)}", color = Ui.TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap the widget to open Dial. It never changes the dial on its own — pick a dial and it stays.",
                    color = Ui.TextMid, fontSize = 12.sp, lineHeight = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                PillButton(
                    "Contact on Telegram",
                    onClick = {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/iopXtiop_bot"))
                        )
                    },
                    filled = false
                )
            }
        }
    }
}
