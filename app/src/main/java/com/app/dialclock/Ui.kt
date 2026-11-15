package com.app.dialclock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ui.kt — the v3.2 design system. One palette and one set of small
 * components shared by the gallery, the Studio and Settings, so the whole
 * app looks like one product: deep ink background, soft surfaces with a
 * hairline edge, a single warm-gold accent, pill controls.
 */
object Ui {
    val Bg = Color(0xFF07080B)
    val Surface = Color(0xFF13151A)
    val SurfaceHi = Color(0xFF1B1E25)
    val Hairline = Color.White.copy(alpha = 0.08f)
    val Gold = Color(0xFFE6BC6E)
    val GoldSoft = Color(0xFFE6BC6E).copy(alpha = 0.16f)
    val TextHi = Color(0xFFF5F2EB)
    val TextMid = Color(0xFFABA79E)
    val TextLow = Color(0xFF72706A)
    val Good = Color(0xFF5ED394)
    val Warn = Color(0xFFFFB347)
    val Danger = Color(0xFFFF6B5E)
}

/** Full-screen background: ink with two soft coloured glows. */
@Composable
fun AppBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Ui.Bg)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF2A2013).copy(alpha = 0.75f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(120f, 80f),
                    radius = 900f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF111C2B).copy(alpha = 0.85f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(1000f, 1900f),
                    radius = 1100f
                )
            )
    ) { content() }
}

/** Rounded surface card. [selected] draws the gold ring. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    corner: Dp = 24.dp,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    var m = modifier
        .clip(shape)
        .background(Ui.Surface)
        .border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Ui.Gold else Ui.Hairline), shape)
    if (onClick != null) m = m.clickable { onClick() }
    Box(m.padding(padding)) { content() }
}

/** Pill button. [filled] = gold; otherwise a quiet outlined pill. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(50)
    val bg = if (!enabled) Color.White.copy(alpha = 0.06f) else if (filled) Ui.Gold else Color.White.copy(alpha = 0.08f)
    val fg = if (!enabled) Ui.TextLow else if (filled) Color(0xFF1A1405) else Ui.TextHi
    Row(
        modifier.clip(shape)
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(1.dp, Ui.Hairline, shape))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Selectable chip (filters, type pickers, option groups). */
@Composable
fun ChipPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(if (selected) Ui.GoldSoft else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (selected) Ui.Gold else Ui.Hairline, shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Ui.Gold else Ui.TextHi,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Tiny uppercase tag ("ACTIVE", "STUDIO"). */
@Composable
fun TagLabel(text: String, color: Color = Ui.Gold) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Text(title, color = Ui.TextHi, fontSize = 32.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) {
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = Ui.TextMid, fontSize = 14.sp)
    }
}

@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            color = Ui.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) Text(trailing, color = Ui.TextLow, fontSize = 12.sp)
    }
    Spacer(Modifier.height(10.dp))
}

/** Title + helper line + switch. Used by Settings and the Studio. */
@Composable
fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ui.TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(sub, color = Ui.TextMid, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF1A1405),
                checkedTrackColor = Ui.Gold,
                uncheckedThumbColor = Ui.TextMid,
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun HairlineDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ui.Hairline))
}

/** Round "selected" tick used on colour swatches. */
@Composable
fun SwatchDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) Ui.Gold else Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            val lum = (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue)
            Icon(
                Icons.Rounded.Check, contentDescription = null,
                tint = if (lum > 0.55f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
