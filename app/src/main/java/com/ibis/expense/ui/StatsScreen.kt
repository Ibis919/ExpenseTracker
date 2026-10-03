@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ibis.expense.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibis.expense.data.ExpenseRecord
import com.ibis.expense.data.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.min

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
            var selectedCategory by remember(s.month) { mutableStateOf<String?>(null) }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                MonthRow(s.month, onPrev = vm::previousMonth, onNext = vm::nextMonth)
                DonutCard(s)
                Spacer(Modifier.height(16.dp))
                LegendCard(s, emoji, onCategoryClick = { selectedCategory = it })
                Spacer(Modifier.height(16.dp))
                CompareCard(s, emoji)
                Spacer(Modifier.height(16.dp))
                TrendCard(s)
                Spacer(Modifier.height(24.dp))
            }
            selectedCategory?.let { category ->
                val total = s.categoryTotals.firstOrNull { it.category == category }
                if (total != null) {
                    CategoryDetailsDialog(
                        month = s.month,
                        category = category,
                        categoryEmoji = categoryEmoji(category, emoji),
                        totalCents = total.totalCents,
                        records = s.monthRecords.filter { it.category == category },
                        onDismiss = { selectedCategory = null }
                    )
                }
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
    val colors = MaterialTheme.colorScheme
    val hasPercent = s.totalCents > 0L && s.categoryTotals.none { it.totalCents < 0L }
    val fontScale = LocalDensity.current.fontScale
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.primaryContainer,
            contentColor = colors.onSurface
        )
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
            if (fontScale >= 1.3f || maxWidth < 280.dp) {
                Column {
                    NetTotal(s)
                    Spacer(Modifier.height(16.dp))
                    CategoryRing(s, hasPercent)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { NetTotal(s) }
                    Spacer(Modifier.width(16.dp))
                    CategoryRing(s, hasPercent)
                }
            }
        }
    }
}

@Composable
private fun NetTotal(s: StatsState) {
    Text("净支出", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    StatsAmount(s.totalCents, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "普通消费 − 退款\n不计收入、转账、代付",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))
    Text(
        when {
            s.month == YearMonth.now() ->
                "本月未结束 · 截至 ${LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日"))}"
            s.month > YearMonth.now() -> "该月尚未开始"
            else -> "整月数据"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CategoryRing(s: StatsState, hasPercent: Boolean) {
    val colors = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    LaunchedEffect(s.month) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(500))
    }
    val description = when {
        s.categoryTotals.isEmpty() -> "暂无分类记录"
        hasPercent -> "分类净支出构成，金额和占比见下方分类列表"
        else -> "净额非正或含负分类，环形占比不适用，分类金额见下方列表"
    }
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(112.dp).semantics { contentDescription = description }) {
            val stroke = 12.dp.toPx()
            val diameter = min(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            if (hasPercent) {
                val positiveCategories = s.categoryTotals.filter { it.totalCents > 0L }
                var startAngle = -90f
                positiveCategories.forEach { ct ->
                    val sweep = (ct.totalCents.toDouble() / s.totalCents * 360).toFloat()
                    val gap = if (positiveCategories.size > 1) min(3f, sweep / 3) else 0f
                    drawArc(
                        color = colors.onSurface,
                        startAngle = startAngle + gap / 2,
                        sweepAngle = (sweep - gap) * progress.value,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(stroke, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
            } else {
                drawCircle(
                    color = colors.outline,
                    radius = diameter / 2,
                    style = Stroke(2.dp.toPx())
                )
            }
        }
        Text(
            when {
                s.categoryTotals.isEmpty() -> "暂无"
                hasPercent -> "${s.categoryTotals.count { it.totalCents > 0L }} 类"
                else -> "净额"
            },
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface
        )
    }
}

@Composable
private fun LegendCard(s: StatsState, emoji: Map<String, String>, onCategoryClick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val hasPercent = s.totalCents > 0L && s.categoryTotals.none { it.totalCents < 0L }
    val maxMagnitude = s.categoryTotals.maxOfOrNull { abs(it.totalCents) }?.coerceAtLeast(1L) ?: 1L
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "分类净支出",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                when {
                    s.categoryTotals.isEmpty() -> "本月暂无分类记录"
                    hasPercent -> "点按分类查看明细"
                    else -> "净额非正或含负分类，占比不适用。条形长度表示金额绝对值。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            s.categoryTotals.forEach { ct ->
                val percentLabel = if (hasPercent) {
                    "占比 ${"%.1f".format(ct.totalCents.toDouble() * 100 / s.totalCents)}%"
                } else {
                    "占比不适用"
                }
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(onClickLabel = "查看${ct.category}明细") { onCategoryClick(ct.category) }
                        .semantics(mergeDescendants = true) {}
                        .padding(vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${categoryEmoji(ct.category, emoji)} ${ct.category}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            if (ct.totalCents < 0L) "$percentLabel · 负净额" else percentLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                        StatsAmount(ct.totalCents)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(3.dp)).background(colors.surfaceVariant)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(
                                if (hasPercent) (ct.totalCents.toDouble() / s.totalCents).toFloat()
                                else (abs(ct.totalCents).toDouble() / maxMagnitude).toFloat()
                            )
                                .fillMaxHeight().background(colors.onSurface)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDetailsDialog(
    month: YearMonth,
    category: String,
    categoryEmoji: String,
    totalCents: Long,
    records: List<ExpenseRecord>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$categoryEmoji $category · ${month.format(DateTimeFormatter.ofPattern("yyyy年M月"))}") },
        text = {
            Column {
                Text(
                    "共 ${records.size} 笔 · 净支出 ${amountLabel(totalCents)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(records, key = { it.id }) { record ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "${LocalDate.ofEpochDay(record.epochDay).format(DateTimeFormatter.ISO_LOCAL_DATE)} · ${TransactionType.label(record.type)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(transactionAmountLabel(record.type, record.amountCents), style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                record.note.ifBlank { "无备注" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "支付方式：${record.paymentMethod.ifBlank { "未关联账户" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun CompareCard(s: StatsState, emoji: Map<String, String>) {
    val prev = s.prevMonthTotalCents
    val curr = s.totalCents
    val delta = curr - prev
    val pct = if (prev > 0L) delta.toDouble() * 100 / prev else null

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "本月累计 · 对比上月整月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("本月净支出", style = MaterialTheme.typography.bodyMedium)
                StatsAmount(curr)
            }
            Text(
                "上月整月 ${amountLabel(prev)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    delta > 0L -> "↑ 增加 ${amountLabel(delta)}"
                    delta < 0L -> "↓ 减少 ${amountLabel(abs(delta))}"
                    else -> "→ 与上月整月持平"
                } + if (pct != null && delta != 0L) "（${"%.1f".format(abs(pct))}%）" else "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (pct == null) {
                Text(
                    "上月净额非正，变化百分比不适用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val deltas = categoryChanges(s.categoryTotals, s.prevCategoryTotals)
                .filter { it.deltaCents != 0L }
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
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(
                            "${categoryEmoji(cat, emoji)} $cat",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "本月 ${amountLabel(total)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${if (d > 0) "↑ 增加" else "↓ 减少"} ${amountLabel(abs(d))}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
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
    val highest = months.maxOfOrNull { it.totalCents } ?: 0L
    val lowest = months.minOfOrNull { it.totalCents } ?: 0L
    val colors = MaterialTheme.colorScheme
    val monthFormat = DateTimeFormatter.ofPattern("yyyy年M月")
    val timeRange = if (months.isEmpty()) "暂无趋势记录" else
        "${months.first().month.format(monthFormat)} — ${months.last().month.format(monthFormat)}"

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "净支出趋势",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(6, 12).forEach { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = { range = r },
                        label = { Text(if (range == r) "✓ ${r}个月" else "${r}个月") },
                        border = if (range == r) BorderStroke(2.dp, colors.onSurface) else null,
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            Text(
                timeRange,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            if (s.month == YearMonth.now()) {
                Text(
                    "本月未结束，末点为截至今天的累计净额",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "最高 ${amountLabel(highest)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Text(
                    "最低 ${amountLabel(lowest)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            val progress = remember { Animatable(0f) }
            LaunchedEffect(s.month, range) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(500))
            }
            Canvas(
                Modifier.fillMaxWidth().height(152.dp).semantics {
                    contentDescription = "$timeRange 净支出趋势；零线下方为负净额；空心点为当前查看月份；逐月金额见下方"
                }
            ) {
                val n = months.size
                if (n == 0) return@Canvas
                val stepX = size.width / n
                val topPad = 10.dp.toPx()
                val bottomY = size.height - 10.dp.toPx()
                val lower = lowest.coerceAtMost(0L).toDouble()
                val upper = highest.coerceAtLeast(0L).toDouble()
                val span = (upper - lower).coerceAtLeast(1.0)
                fun scaledY(v: Double): Float =
                    bottomY - ((v - lower) / span).toFloat() * (bottomY - topPad)
                val zeroY = scaledY(0.0)
                drawLine(
                    color = colors.outline,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.dp.toPx()
                )
                val pts = months.mapIndexed { i, m ->
                    val y = zeroY + (scaledY(m.totalCents.toDouble()) - zeroY) * progress.value
                    Offset(stepX * (i + 0.5f), y)
                }
                val linePath = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    linePath,
                    color = colors.onSurface,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                months.forEachIndexed { i, m ->
                    val selected = m.month == s.month
                    if (selected) {
                        drawCircle(
                            color = colors.surface,
                            radius = 7.dp.toPx(),
                            center = pts[i]
                        )
                        drawCircle(
                            color = colors.onSurface,
                            radius = 7.dp.toPx(),
                            style = Stroke(2.5.dp.toPx()),
                            center = pts[i]
                        )
                    } else {
                        drawCircle(
                            color = colors.onSurface,
                            radius = 4.dp.toPx(),
                            center = pts[i]
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "横线为零净支出 · 空心点为查看月份",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text("逐月净支出", style = MaterialTheme.typography.titleSmall)
            months.forEach { m ->
                FlowRow(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        m.month.format(monthFormat) + if (m.month == s.month) " · 查看月份" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (m.month == s.month) FontWeight.SemiBold else FontWeight.Normal
                    )
                    StatsAmount(m.totalCents, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun StatsAmount(
    cents: Long,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    Text(
        amountLabel(cents),
        style = style.copy(
            fontFamily = FontFamily.Monospace,
            fontFeatureSettings = "tnum",
            letterSpacing = 0.sp
        ),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

private fun amountLabel(cents: Long): String =
    (if (cents < 0L) "-¥" else "¥") + formatAmount(abs(cents))
