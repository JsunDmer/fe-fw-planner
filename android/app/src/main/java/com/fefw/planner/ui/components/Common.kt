package com.fefw.planner.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fefw.planner.data.GrowthCell
import com.fefw.planner.ui.theme.AccentSoft
import com.fefw.planner.ui.theme.Bg
import com.fefw.planner.ui.theme.Border
import com.fefw.planner.ui.theme.BorderSoft
import com.fefw.planner.ui.theme.Muted
import com.fefw.planner.ui.theme.Muted2
import com.fefw.planner.ui.theme.OkSoft
import com.fefw.planner.ui.theme.OkText
import com.fefw.planner.ui.theme.OnPrimary
import com.fefw.planner.ui.theme.Panel3
import com.fefw.planner.ui.theme.Primary
import com.fefw.planner.ui.theme.ScoutText
import com.fefw.planner.ui.theme.Surface1
import com.fefw.planner.ui.theme.Tertiary
import com.fefw.planner.ui.theme.TextPrimary
import com.fefw.planner.ui.theme.TextSecondary
import com.fefw.planner.ui.theme.WarnSoft
import com.fefw.planner.ui.theme.WarnText

private val imageCache = object : LruCache<String, ImageBitmap>(100) {}

fun parseHexColor(hex: String?, fallback: Color = Primary): Color {
    val h = hex?.trim()?.removePrefix("#") ?: return fallback
    return runCatching {
        when (h.length) {
            6 -> Color(("FF$h").toLong(16))
            8 -> Color(h.toLong(16))
            else -> fallback
        }
    }.getOrDefault(fallback)
}

@Composable
fun AssetImage(path: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val key = path ?: ""
    var bitmap by remember(key) { mutableStateOf(if (key.isBlank()) null else imageCache.get(key)) }
    LaunchedEffect(key) {
        if (key.isBlank() || imageCache.get(key) != null) return@LaunchedEffect
        val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.assets.open(key).use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (decoded != null) {
            imageCache.put(key, decoded)
            bitmap = decoded
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier.clip(RoundedCornerShape(6.dp)).background(Panel3).border(1.dp, Border, RoundedCornerShape(6.dp)))
    }
}

enum class BadgeKind { Default, Story, Scout, Hero, No, Mount }

@Composable
fun Badge(text: String, kind: BadgeKind = BadgeKind.Default, modifier: Modifier = Modifier) {
    val fg = when (kind) {
        BadgeKind.Story -> OkText
        BadgeKind.Scout -> ScoutText
        BadgeKind.Hero -> WarnText
        BadgeKind.No -> Muted2
        BadgeKind.Mount -> Color(0xFFFFD9A8)
        BadgeKind.Default -> TextSecondary
    }
    val bg = when (kind) {
        BadgeKind.Story -> OkSoft
        BadgeKind.Scout -> AccentSoft
        BadgeKind.Hero -> WarnSoft
        BadgeKind.Mount -> WarnSoft
        else -> Panel3
    }
    val bd = when (kind) {
        BadgeKind.Story -> Color(0x6646A86A)
        BadgeKind.Scout -> Color(0x663D9AC0)
        BadgeKind.Hero -> Color(0x66D9A441)
        BadgeKind.Mount -> Color(0x59D9A441)
        else -> Border
    }
    Text(
        text = text,
        color = fg,
        fontSize = 11.sp,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, bd, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = ScoutText,
        fontSize = 11.sp,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AccentSoft)
            .border(1.dp, Color(0x4D3D9AC0), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun CheckChip(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    plan: Boolean = false,
    mark: String = "✓"
) {
    val borderColor = if (checked && plan) Tertiary else if (checked) Primary else Border
    val bg = if (checked && plan) WarnSoft else if (checked) AccentSoft else Panel3
    val boxBg = if (checked && plan) Tertiary else if (checked) Primary else Bg
    val boxFg = if (checked && plan) Color(0xFF2A1C00) else if (checked) OnPrimary else Color.Transparent
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(boxBg)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Text(mark, fontSize = 10.sp, color = boxFg, lineHeight = 10.sp)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) TextPrimary else TextSecondary
        )
    }
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) TextPrimary else TextSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) AccentSoft else Panel3)
            .border(1.dp, if (selected) Primary else Border, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
fun RouteChip(
    label: String,
    dotColor: Color,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) AccentSoft else Panel3)
            .border(1.dp, if (active) Primary else Border, RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(999.dp)).background(dotColor))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) TextPrimary else TextSecondary
        )
    }
}

@Composable
fun CondChip(text: String, isOther: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = if (isOther) Muted else TextSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Panel3)
            .border(1.dp, BorderSoft, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun SectionHeader(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            if (!hint.isNullOrBlank()) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp), color = Border)
    }
}

@Composable
fun PanelCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column { content() }
    }
}

@Composable
fun KV(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
fun FefwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = Muted) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Panel3,
            unfocusedContainerColor = Panel3,
            focusedIndicatorColor = Primary,
            unfocusedIndicatorColor = Border,
            cursorColor = Primary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        modifier = modifier
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(vertical = 24.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

/**
 * 3×3 成长率网格。每格使用固定高度与 weight 布局，
 * 保证不同职业节点之间列对齐、行高等宽，避免纯文本成长串造成的错行与不可读。
 */
@Composable
fun GrowthGrid(cells: List<GrowthCell>, modifier: Modifier = Modifier, compact: Boolean = false) {
    val cellHeight = if (compact) 38.dp else 46.dp
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(3).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowCells.forEach { cell ->
                    GrowthCellView(cell = cell, height = cellHeight, compact = compact, modifier = Modifier.weight(1f))
                }
                repeat(3 - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun GrowthCellView(cell: GrowthCell, height: Dp, compact: Boolean, modifier: Modifier = Modifier) {
    val valueColor = when {
        cell.value >= 60 -> OkText
        cell.value >= 45 -> Primary
        cell.value >= 30 -> TextSecondary
        else -> Muted
    }
    Column(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(Panel3)
            .border(
                1.dp,
                if (cell.bonus > 0) Primary.copy(alpha = 0.55f) else BorderSoft,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                cell.label,
                fontSize = 9.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (cell.bonus > 0) {
                Text("+${cell.bonus}", fontSize = 8.sp, color = Primary, maxLines = 1)
            }
        }
        Text(
            "${cell.value}",
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
        Box(
            Modifier
                .height(3.dp)
                .fillMaxWidth((cell.value / 80f).coerceIn(0.08f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(valueColor.copy(alpha = 0.8f))
        )
    }
}
