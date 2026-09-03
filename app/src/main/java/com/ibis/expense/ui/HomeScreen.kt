@file:OptIn(ExperimentalMaterial3Api::class)

package com.ibis.expense.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onRecordClick: (UiRecord) -> Unit,
    onSettings: () -> Unit
) {
    val state = vm.homeState.collectAsState().value
    var openRowId by remember { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记账") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                item(key = "balance") { BalanceCard(s, onPrev = vm::previousMonth, onNext = vm::nextMonth) }
                if (s.days.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🧾", fontSize = 52.sp)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "本月暂无记录",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "点下方「记一笔」开始",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                for (day in s.days) {
                    item(key = "day-${day.date}") {
                        DayHeader(day, Modifier.animateItem())
                    }
                    items(day.records, key = { it.id }) { record ->
                        Box(Modifier.padding(bottom = 4.dp).animateItem()) {
                            SwipeRecordRow(
                                record = record,
                                isOpen = openRowId == record.id,
                                onOpenChange = { open ->
                                    openRowId = if (open) record.id else null
                                },
                                onDelete = {
                                    openRowId = null
                                    vm.deleteRecord(record.id)
                                },
                                onClick = { onRecordClick(record) }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun BalanceCard(state: HomeState, onPrev: () -> Unit, onNext: () -> Unit) {
    val over = state.isOverBudget
    val isCurrentMonth = state.month == YearMonth.now()
    val container = if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (over) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val accent = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "上个月",
                        tint = onContainer.copy(alpha = 0.7f)
                    )
                }
                AnimatedContent(
                    targetState = state.month,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { it / 3 } + fadeOut())
                        }
                    },
                    label = "month",
                    modifier = Modifier.weight(1f)
                ) { month ->
                    Text(
                        text = month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "下个月",
                        tint = onContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = if (over) "已超支，少花点" else "剩余可用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.75f)
                )
                AnimatedContent(
                    targetState = state.remainingCents,
                    transitionSpec = {
                        if (targetState < initialState) {
                            (slideInVertically { it / 3 } + fadeIn()) togetherWith
                                (slideOutVertically { -it / 3 } + fadeOut())
                        } else {
                            (slideInVertically { -it / 3 } + fadeIn()) togetherWith
                                (slideOutVertically { it / 3 } + fadeOut())
                        }
                    },
                    label = "remaining"
                ) { cents ->
                    Text(
                        text = (if (cents < 0) "-" else "") + "¥" + formatAmount(abs(cents)),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(12.dp))
                val rawFraction = if (state.budgetCents == 0L) 0f else state.spentCents.toFloat() / state.budgetCents
                val fraction by animateFloatAsState(
                    targetValue = rawFraction.coerceIn(0f, 1f),
                    animationSpec = tween(400),
                    label = "progress"
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(onContainer.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(accent)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "已花 ¥${formatAmount(state.spentCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "预算 ¥${formatAmount(state.budgetCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f)
                    )
                }
                if (isCurrentMonth) {
                    val today = LocalDate.now()
                    val daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1
                    val dailyCents = state.remainingCents / daysLeft
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (over) {
                            "日均超支 ¥${formatAmount(abs(dailyCents))}"
                        } else {
                            "日均可用 ¥${formatAmount(dailyCents)} · 还剩 $daysLeft 天"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.date.format(DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "¥${formatAmount(day.dayTotalCents)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwipeRecordRow(
    record: UiRecord,
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val buttonWidthPx = with(LocalDensity.current) { 72.dp.toPx() }
    val offset = remember(record.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isOpen) {
        offset.animateTo(if (isOpen) -buttonWidthPx else 0f, spring(stiffness = 500f))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(record.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offset.snapTo((offset.value + dragAmount).coerceIn(-buttonWidthPx, 0f))
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offset.value < -buttonWidthPx / 2) {
                                offset.animateTo(-buttonWidthPx)
                                onOpenChange(true)
                            } else {
                                offset.animateTo(0f)
                                onOpenChange(false)
                            }
                        }
                    }
                )
            }
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(72.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Text("删除", color = MaterialTheme.colorScheme.onError)
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    if (offset.value < 0f) {
                        onOpenChange(false)
                    } else {
                        onClick()
                    }
                }
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                RecordRowContent(record)
            }
        }
    }
}

@Composable
private fun RecordRowContent(record: UiRecord) {
    val color = if (record.overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(categoryEmoji(record.category), fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = record.category,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = color
            )
            if (record.note.isNotBlank()) {
                Text(
                    text = record.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.overBudget) color else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "-¥${formatAmount(record.amountCents)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
