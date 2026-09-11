@file:OptIn(ExperimentalMaterial3Api::class)

package com.ibis.expense.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.min

private val ChartColors = listOf(
    Color(0xFF006C4C),
    Color(0xFF2E8B8C),
    Color(0xFFD4A017),
    Color(0xFF8E5572),
    Color(0xFF5A6B7A),
    Color(0xFFB4553F),
    Color(0xFF4C6357)
)

@Composable
fun StatsScreen(vm: AppViewModel) {
    val state = vm.statsState.collectAsState().value
    val categories by vm.categoriesState.collectAsState()
    val emoji = remember(categories) { categories.associate { it.name to it.emoji } }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("统计") })
        }
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                MonthRow(s.month, onPrev = vm::previousMonth, onNext = vm::nextMonth)
                if (s.totalCents == 0L) {
                    Box(Modifier.fillMaxWidth().padding(top = 72.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "本月暂无支出",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    DonutCard(s)
                    Spacer(Modifier.height(16.dp))
                    LegendCard(s, emoji)
                }
                Spacer(Modifier.height(16.dp))
                CompareCard(s, emoji)
                Spacer(Modifier.height(16.dp))
                TrendCard(s)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MonthRow(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
        }
        Text(
            text = month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
        }
    }
}

@Composable
private fun DonutCard(s: StatsState) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
            val progress = remember { Animatable(0f) }
            LaunchedEffect(s.month) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(700))
            }
            Canvas(Modifier.size(216.dp)) {
                val stroke = 34.dp.toPx()
                val diameter = min(size.width, size.height) - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                var startAngle = -90f
                s.categoryTotals.forEachIndexed { index, ct ->
                    val sweep = ct.totalCents / s.totalCents.toFloat() * 360f * progress.value
                    drawArc(
                        color = ChartColors[index % ChartColors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(stroke, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "本月总支出",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
                Text(
                    "¥${formatAmount(s.totalCents)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun LegendCard(s: StatsState, emoji: Map<String, String>) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            s.categoryTotals.forEachIndexed { index, ct ->
                val color = ChartColors[index % ChartColors.size]
                val percent = ct.totalCents * 100f / s.totalCents
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text("${categoryEmoji(ct.category, emoji)} ${ct.category}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${"%.1f".format(percent)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "¥${formatAmount(ct.totalCents)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareCard(s: StatsState, emoji: Map<String, String>) {
    val prev = s.prevMonthTotalCents
    val curr = s.totalCents
    val delta = curr - prev
    val pct = if (prev > 0) (delta * 100f / prev) else Float.NaN
    val up = delta > 0
    val same = delta == 0L
    val red = Color(0xFFB3402A)
    val green = Color(0xFF006C4C)

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "本月 vs 上月",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("总支出", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "¥${formatAmount(curr)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (!same) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (pct.isNaN()) "新增支出" else "${if (up) "↑" else "↓"}${"%.0f".format(kotlin.math.abs(pct))}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (up) red else green
                        )
                    }
                }
            }
            Text(
                "上月同期 ¥${formatAmount(prev)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val prevMap = s.prevCategoryTotals.associate { it.category to it.totalCents }
            val deltas = (s.categoryTotals + s.prevCategoryTotals)
                .distinctBy { it.category }
                .map { ct -> Triple(ct.category, ct.totalCents, (ct.totalCents - (prevMap[ct.category] ?: 0L))) }
                .sortedByDescending { kotlin.math.abs(it.third) }
                .filter { it.third != 0L }
                .take(3)
            if (deltas.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "变化最大",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                deltas.forEach { (cat, total, d) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${categoryEmoji(cat, emoji)} $cat ¥${formatAmount(total)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${if (d > 0) "+" else ""}¥${formatAmount(d)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (d > 0) red else green
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendCard(s: StatsState) {
    var range by rememberSaveable { mutableIntStateOf(6) }
    val months = s.trend.takeLast(range)
    val maxTotal = months.maxOf { it.totalCents }.coerceAtLeast(1L)
    val primary = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "消费趋势",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                listOf(6, 12).forEach { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = { range = r },
                        label = { Text("${r}月") },
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                months.forEachIndexed { i, m ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (range == 6 || i % 2 == 0) {
                            Text(
                                compactYuan(m.totalCents),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (m.month == s.month) primary else labelColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val progress = remember { Animatable(0f) }
            LaunchedEffect(s.month, range) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(600))
            }
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val n = months.size
                if (n < 2) return@Canvas
                val stepX = size.width / n
                val topPad = 6.dp.toPx()
                val bottomY = size.height - 6.dp.toPx()
                fun pointY(v: Long): Float =
                    bottomY - (v.toFloat() / maxTotal * progress.value) * (bottomY - topPad)
                val pts = months.mapIndexed { i, m ->
                    Offset(stepX * (i + 0.5f), pointY(m.totalCents))
                }
                val fillPath = Path().apply {
                    moveTo(pts.first().x, bottomY)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, bottomY)
                    close()
                }
                drawPath(
                    fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primary.copy(alpha = 0.30f), primary.copy(alpha = 0.02f)),
                        startY = topPad,
                        endY = bottomY
                    )
                )
                val linePath = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    linePath,
                    color = primary,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                months.forEachIndexed { i, m ->
                    val selected = m.month == s.month
                    drawCircle(
                        color = primary,
                        radius = (if (selected) 7.dp else 4.5.dp).toPx(),
                        center = pts[i]
                    )
                    if (selected) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.9f),
                            radius = 2.5.dp.toPx(),
                            center = pts[i]
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                months.forEach { m ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "${m.month.monthValue}月",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (m.month == s.month) primary else labelColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun compactYuan(cents: Long): String {
    if (cents <= 0L) return "0"
    val yuan = cents / 100.0
    return if (yuan >= 10000) {
        val w = yuan / 10000
        if (w >= 100) "%.0f万".format(w) else "%.1f万".format(w)
    } else {
        "%.0f".format(yuan)
    }
}
