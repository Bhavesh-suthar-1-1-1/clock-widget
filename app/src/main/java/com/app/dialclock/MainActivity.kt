package com.app.dialclock

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableStateOf(0)

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — Torch just opens the app again if it is missing */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The Quick tools widget's Torch needs the Camera permission. Ask
        // once, on first launch; never nag again after that.
        if (!DialPrefs.cameraAsked(this) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            DialPrefs.setCameraAsked(this)
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        setContent { DialApp(resumeTick) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // Keep the extra widgets fresh whenever the app is opened.
        DeviceWidgetProvider.refreshAll(this)
        BatteryWidgetProvider.refreshAll(this)
        CalendarWidgetProvider.refreshAll(this)
        // Self-heal the clock: re-render the Dial widget and re-arm the
        // minute alarm, so merely opening the app repairs a dead chain.
        DialWidgetProvider.refreshAll(this)
        TickScheduler.scheduleNextMinute(this)
    }
}

private val Gold = Color(0xFFC9A86A)
private val Glass = Color.White.copy(alpha = 0.07f)
private val GlassBorder = Color.White.copy(alpha = 0.12f)
private val NEW_DIALS = setOf("moon", "frenchoval", "bighour", "semper", "dual") +
    DigitalDials.STYLES.map { it.id } + HybridDials.STYLES.map { it.id }

/** Gallery section: analog watches, digital clocks, hybrids, word clocks. */
private fun sectionOf(dial: Dial): String = when (dial.kind) {
    DialKind.DIGITAL_STYLE -> "Digital"
    DialKind.HYBRID -> "Hybrid"
    DialKind.WORD_EN, DialKind.WORD_ES -> "Word"
    else -> "Analog"
}

@Composable
private fun DialSection(
    title: String,
    dials: List<Dial>,
    selected: String,
    settings: DialSettings,
    onPick: (Dial) -> Unit,
    onRefresh: () -> Unit
) {
    val ctx = LocalContext.current
    if (dials.isEmpty()) return
    Text("$title · ${dials.size}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    for (row in dials.chunked(2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (dial in row) {
                Frosted(
                    Modifier.weight(1f).clickable { onPick(dial) },
                    border = if (selected == dial.id) Gold else GlassBorder
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DialPreview(dial, 120, settings)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dial.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (dial.id in NEW_DIALS) {
                                Spacer(Modifier.width(6.dp))
                                Text("NEW", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            if (selected == dial.id) "On your home screen" else "Tap to apply",
                            color = if (selected == dial.id) Gold else Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Button(
                                onClick = {
                                    DialPrefs.toggleFav(ctx, dial.id)
                                    onRefresh()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (DialPrefs.isFav(ctx, dial.id)) Gold else Color.White.copy(alpha = 0.10f),
                                    contentColor = if (DialPrefs.isFav(ctx, dial.id)) Color.Black else Color.White
                                )
                            ) { Text(if (DialPrefs.isFav(ctx, dial.id)) "♥ Saved" else "♡ Save") }
                            if (dial.custom) {
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        DialPrefs.deleteCustom(ctx, dial.id)
                                        DialWidgetProvider.refreshAll(ctx)
                                        onRefresh()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White)
                                ) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Frosted(mod: Modifier, border: Color = GlassBorder, content: @Composable () -> Unit) {
    Box(
        mod.clip(RoundedCornerShape(24.dp))
            .background(Glass)
            .border(1.dp, border, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun DialPreview(dial: Dial, sizeDp: Int, settings: DialSettings = DialSettings()) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }
    val bmp = remember(dial, tick, settings) {
        DialRenderer.render(dial, (sizeDp * 2.5f).toInt(), Calendar.getInstance(), settings)
    }
    androidx.compose.foundation.Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = dial.name,
        modifier = Modifier.size(sizeDp.dp)
    )
}

@Composable
private fun DialApp(resumeTick: Int) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf("home") }
    val selected = remember(refresh, resumeTick) { DialPrefs.selectedId(ctx) }
    val ordered = remember(refresh, resumeTick) { DialPrefs.ordered(ctx) }
    val settings = remember(refresh, resumeTick) { DialPrefs.settings(ctx) }

    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    val batteryOk = remember(refresh, resumeTick) { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val exactOk = remember(refresh, resumeTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0C))) {
        // Soft blurred blobs behind everything (frosted-glass backdrop)
        Box(
            Modifier.size(300.dp)
                .graphicsLayer {
                    translationX = -120f
                    translationY = -80f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = RenderEffect.createBlurEffect(90f, 90f, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
                }
                .background(Color(0xFF3A2A18), RoundedCornerShape(200.dp))
        )
        Box(
            Modifier.size(260.dp)
                .graphicsLayer {
                    translationX = 420f
                    translationY = 700f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = RenderEffect.createBlurEffect(90f, 90f, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
                }
                .background(Color(0xFF1B2A3A), RoundedCornerShape(200.dp))
        )

        if (screen == "settings") {
            SettingsScreen(
                settings = settings,
                batteryOk = batteryOk,
                exactOk = exactOk,
                onBack = { screen = "home" },
                onChanged = {
                    DialWidgetProvider.refreshAll(ctx)
                    refresh++
                }
            )
        } else {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Top bar
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Dial", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Real watch dials, live on your home screen.",
                            color = Color.Gray, fontSize = 14.sp
                        )
                    }
                    Button(
                        onClick = { screen = "settings" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = Color.White
                        )
                    ) { Text("⚙ Settings") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add the widget: long-press your home screen → Widgets → Dial.",
                    color = Color.Gray, fontSize = 13.sp
                )

                // Compact setup warning (full setup lives in Settings)
                if (!batteryOk || !exactOk) {
                    Spacer(Modifier.height(16.dp))
                    Frosted(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Widget updates need permission", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Allow unrestricted battery and exact alarms so the widget ticks every minute.",
                                    color = Color.Gray, fontSize = 13.sp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { screen = "settings" },
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                            ) { Text("Fix") }
                        }
                    }
                }

                // Gallery, grouped into sections (most used first inside each).
                Spacer(Modifier.height(20.dp))
                val sections = listOf("Analog", "Digital", "Hybrid", "Word")
                for (section in sections) {
                    val dials = ordered.filter { sectionOf(it) == section }
                    DialSection(
                        title = section,
                        dials = dials,
                        selected = selected,
                        settings = settings,
                        onPick = { dial ->
                            DialPrefs.select(ctx, dial.id)
                            DialWidgetProvider.refreshAll(ctx)
                            refresh++
                        },
                        onRefresh = { refresh++ }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // More widgets: Device, Battery, Calendar, Quick tools.
                // Tap a card to switch its design; add the widget from the
                // home screen (long-press -> Widgets -> Dial).
                Spacer(Modifier.height(12.dp))
                Text("More widgets", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add from your home screen: long-press -> Widgets -> Dial.",
                    color = Color.Gray, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                MoreWidgetCard(
                    name = "Device",
                    design = DeviceWidgets.NAMES[DialPrefs.widgetDesign(ctx, DialPrefs.KEY_DEVICE_DESIGN, DeviceWidgets.COUNT)],
                    count = DeviceWidgets.COUNT,
                    onTap = {
                        DialPrefs.cycleWidgetDesign(ctx, DialPrefs.KEY_DEVICE_DESIGN, DeviceWidgets.COUNT)
                        DeviceWidgetProvider.refreshAll(ctx)
                        refresh++
                    }
                )
                MoreWidgetCard(
                    name = "Battery",
                    design = BatteryWidgets.NAMES[DialPrefs.widgetDesign(ctx, DialPrefs.KEY_BATTERY_DESIGN, BatteryWidgets.COUNT)],
                    count = BatteryWidgets.COUNT,
                    onTap = {
                        DialPrefs.cycleWidgetDesign(ctx, DialPrefs.KEY_BATTERY_DESIGN, BatteryWidgets.COUNT)
                        BatteryWidgetProvider.refreshAll(ctx)
                        refresh++
                    }
                )
                MoreWidgetCard(
                    name = "Calendar",
                    design = CalendarWidgets.NAMES[DialPrefs.widgetDesign(ctx, DialPrefs.KEY_CALENDAR_DESIGN, CalendarWidgets.COUNT)],
                    count = CalendarWidgets.COUNT,
                    onTap = {
                        DialPrefs.cycleWidgetDesign(ctx, DialPrefs.KEY_CALENDAR_DESIGN, CalendarWidgets.COUNT)
                        CalendarWidgetProvider.refreshAll(ctx)
                        refresh++
                    }
                )
                MoreWidgetCard(
                    name = "Quick tools",
                    design = "Wi-Fi · Bluetooth · Torch · Calc",
                    count = 1,
                    onTap = { /* one wide widget, no designs to cycle */ }
                )

                Spacer(Modifier.height(24.dp))
                Text("Dial 2.1", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MoreWidgetCard(name: String, design: String, count: Int, onTap: () -> Unit) {
    Frosted(Modifier.fillMaxWidth().clickable { onTap() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(design, color = Color.Gray, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (count > 1) "Tap to change design" else "1 design",
                color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingToggle(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Color.Gray, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Gold,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
private fun SettingsScreen(
    settings: DialSettings,
    batteryOk: Boolean,
    exactOk: Boolean,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.10f),
                    contentColor = Color.White
                )
            ) { Text("← Back") }
            Spacer(Modifier.width(12.dp))
            Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))
        Text("Look", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Frosted(Modifier.fillMaxWidth()) {
            Column {
                SettingToggle("Glass effect", "Crystal shine on every dial", settings.glass) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_GLASS, it); onChanged()
                }
                SettingToggle("Drop shadow", "Soft shadow of the watch on your wallpaper", settings.shadow) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_SHADOW, it); onChanged()
                }
                SettingToggle("Live second hand", "Ticks every second on analog dials. Uses more battery.", settings.secondHand) {
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
                SettingToggle("Date window", "Show the date box on analog dials", settings.dateWindow) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_DATEWIN, it); onChanged()
                }
                SettingToggle("Brand text", "Show the small DIAL name on analog dials", settings.brand) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_BRAND, it); onChanged()
                }
                SettingToggle("Minute track", "Minute ticks around analog dials", settings.minuteTrack) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_TICKS, it); onChanged()
                }
                SettingToggle("Watch strap", "Leather strap above and below the case", settings.strap) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_STRAP, it); onChanged()
                }
                SettingToggle("Responsive sizing", "Widgets fill their real size when resized. Off keeps the square watch.", settings.responsive) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_RESPONSIVE, it); onChanged()
                }
                Text("Case shape", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaseShape.values().forEach { s ->
                        val on = settings.shape == s
                        Button(
                            onClick = { DialPrefs.setShape(ctx, s); onChanged() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (on) MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (on) MaterialTheme.colorScheme.onPrimary
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text(s.label) }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Time", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Frosted(Modifier.fillMaxWidth()) {
            Column {
                SettingToggle("24-hour time", "Digital dials show 21:47. Off shows 09:47 PM.", settings.hour24) {
                    DialPrefs.setSetting(ctx, DialPrefs.KEY_HOUR24, it); onChanged()
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Keep it ticking", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Frosted(Modifier.fillMaxWidth()) {
            Column {
                if (batteryOk && exactOk) {
                    Text("Battery and alarms are set. The widget ticks every minute.", color = Color.Gray, fontSize = 13.sp)
                } else {
                    Text(
                        "Allow unrestricted battery so the widget updates every minute, even in the background.",
                        color = Color.Gray, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    if (!batteryOk) {
                        Button(
                            onClick = {
                                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${ctx.packageName}")
                                }
                                ctx.startActivity(i)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                        ) { Text("Allow unrestricted battery") }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!exactOk) {
                        Button(
                            onClick = {
                                val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${ctx.packageName}")
                                }
                                ctx.startActivity(i)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
                        ) { Text("Allow exact alarms") }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Tap the widget to open Dial. It never changes the dial on its own — pick a dial here and it stays.",
            color = Color.Gray, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        Text("Dial 2.1", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/iopXtiop_bot"))
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black)
        ) { Text("Connect with") }
        Spacer(Modifier.height(24.dp))
    }
}
