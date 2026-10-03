@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import com.ibis.expense.data.TransactionType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onRecordClick: (UiRecord) -> Unit,
    onSettings: () -> Unit
) {
    val state = vm.homeState.collectAsState().value
    val search = vm.searchState.collectAsState().value
    val query = vm.searchQuery.collectAsState().value
    val filter by vm.recordFilter.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    val accountBalances by vm.accountBalances.collectAsState()
    val categories by vm.categoriesState.collectAsState()
    val emoji = remember(categories) { categories.associate { it.name to it.emoji } }
    var openRowId by remember { mutableStateOf<Long?>(null) }
    var editingAccount by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val savedRecordId by vm.savedRecordId.collectAsState()
    var highlightedRecordId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(savedRecordId, state) {
        val id = savedRecordId ?: return@LaunchedEffect
        val current = state ?: return@LaunchedEffect
        var index = 2
        var target: Int? = null
        for (day in current.days) {
            index++
            for (record in day.records) {
                if (record.id == id) target = index
                index++
            }
        }
        target?.let {
            listState.scrollToItem(it)
            highlightedRecordId = id
            delay(2_000)
            highlightedRecordId = null
            vm.acknowledgeSavedRecord()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账本") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(value = query, onValueChange = vm::setSearchQuery)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showFilters = true }) { Text(if (filter.isActive) "✓ 筛选已应用" else "筛选") }
                if (query.isNotBlank() || filter.isActive) {
                    TextButton(onClick = vm::clearSearchAndFilters) { Text("清空全部条件") }
                }
            }
            val s = search
            if (s != null) {
                SearchResults(
                    s,
                    emoji = emoji,
                    openRowId = openRowId,
                    onOpenChange = { open -> openRowId = open },
                    onDelete = { id ->
                        openRowId = null
                        vm.deleteRecord(id)
                    },
                    onRecordClick = onRecordClick
                )
            } else if (state != null) {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    state = listState
                ) {
                    item(key = "balance") { BalanceCard(state, onPrev = vm::previousMonth, onNext = vm::nextMonth) }
                    item(key = "accounts") {
                        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Text("当前账户余额 · 点按校准", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            BoxWithConstraints {
                                if (LocalDensity.current.fontScale >= 1.3f || maxWidth < 320.dp) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AccountCard(PaymentMethod.WECHAT, accountBalances[PaymentMethod.WECHAT], Modifier.fillMaxWidth()) { editingAccount = PaymentMethod.WECHAT }
                                        AccountCard(PaymentMethod.ALIPAY, accountBalances[PaymentMethod.ALIPAY], Modifier.fillMaxWidth()) { editingAccount = PaymentMethod.ALIPAY }
                                    }
                                } else Row {
                                    AccountCard(PaymentMethod.WECHAT, accountBalances[PaymentMethod.WECHAT], Modifier.weight(1f)) { editingAccount = PaymentMethod.WECHAT }
                                    Spacer(Modifier.width(8.dp))
                                    AccountCard(PaymentMethod.ALIPAY, accountBalances[PaymentMethod.ALIPAY], Modifier.weight(1f)) { editingAccount = PaymentMethod.ALIPAY }
                                }
                            }
                        }
                    }
                    if (state.days.isEmpty()) {
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    for (day in state.days) {
                        item(key = "day-${day.date}") {
                            DayHeader(day, Modifier.animateItem())
                        }
                        itemsIndexed(day.records, key = { _, record -> record.id }) { position, record ->
                            val shape = ledgerRowShape(position, day.records.size)
                            Box(Modifier.animateItem().then(
                                if (highlightedRecordId == record.id) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(16.dp))
                                else Modifier
                            )) {
                                SwipeRecordRow(
                                    record = record,
                                    shape = shape,
                                    emoji = emoji,
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
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showFilters) RecordFilterDialog(filter, onApply = { vm.setRecordFilter(it); showFilters = false }, onDismiss = { showFilters = false })

    editingAccount?.let { method ->
        val current = accountBalances[method]
        var input by remember(method) { mutableStateOf(current?.takeIf { it >= 0 }?.let(::centsToInput) ?: "") }
        var savingBalance by remember(method) { mutableStateOf(false) }
        var balanceError by remember(method) { mutableStateOf<String?>(null) }
        val cents = parseAmountToCents(input, allowZero = true)
        AlertDialog(
            onDismissRequest = { if (!savingBalance) editingAccount = null },
            title = { Text("设置${method}余额") },
            text = {
                Column {
                    Text("填写当前实际余额。此后用${method}记账会自动扣减。")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("当前余额（元）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = input.isNotEmpty() && cents == null,
                        enabled = !savingBalance,
                        singleLine = true
                    )
                    balanceError?.let { Text("保存失败：$it") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    savingBalance = true
                    balanceError = null
                    scope.launch {
                        try {
                            vm.setAccountBalance(method, cents!!)
                            editingAccount = null
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { balanceError = e.message ?: "请重试" }
                        finally { savingBalance = false }
                    }
                }, enabled = cents != null && !savingBalance) { Text(if (savingBalance) "保存中…" else "保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingAccount = null }, enabled = !savingBalance) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AccountCard(method: String, cents: Long?, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PaymentIcon(method)
                Spacer(Modifier.width(6.dp))
                Text("${method}余额", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                cents?.let { (if (it < 0) "-¥" else "¥") + formatAmount(abs(it)) } ?: "点击设置",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        singleLine = true,
        placeholder = { Text("搜索：日期 / 备注 / 金额，空格组合") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清空")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun SearchResults(
    state: SearchState,
    emoji: Map<String, String>,
    openRowId: Long?,
    onOpenChange: (Long?) -> Unit,
    onDelete: (Long) -> Unit,
    onRecordClick: (UiRecord) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item(key = "search-summary") {
            Text(
                text = "找到 ${state.count} 条 · 净支出 ¥${formatAmount(state.totalCents)}" +
                    if (state.excludedTotalCents != 0L) " · 代付 ¥${formatAmount(state.excludedTotalCents)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        if (state.days.isEmpty()) {
            item(key = "search-empty") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔍", fontSize = 52.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "没有匹配的记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "试试换个关键词，或清空搜索",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        for (day in state.days) {
            item(key = "s-day-${day.date}") {
                DayHeader(day, Modifier.animateItem(), showYear = true)
            }
            itemsIndexed(day.records, key = { _, record -> "s-${record.id}" }) { position, record ->
                Box(Modifier.animateItem()) {
                    SwipeRecordRow(
                        record = record,
                        shape = ledgerRowShape(position, day.records.size),
                        emoji = emoji,
                        isOpen = openRowId == record.id,
                        onOpenChange = { open -> onOpenChange(if (open) record.id else null) },
                        onDelete = {
                            onOpenChange(null)
                            onDelete(record.id)
                        },
                        onClick = { onRecordClick(record) }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
                        tint = onContainer
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
                        tint = onContainer
                    )
                }
            }
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = if (over) "！预算已超出" else "预算剩余",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer
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
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(rawFraction.coerceIn(0f, 1f), 0f..1f) }
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
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "净支出 ¥${formatAmount(state.spentCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer
                    )
                    Text(
                        "预算 ¥${formatAmount(state.budgetCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer
                    )
                }
                Text(if (over) "！超过 100% 上限" else if (rawFraction >= 0.8f) "接近预算 · 已达 80% 提醒线" else "预算内 · 80% 提醒 / 100% 上限",
                    style = MaterialTheme.typography.labelMedium, color = onContainer)
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
                        color = onContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup, modifier: Modifier = Modifier, showYear: Boolean = false) {
    FlowRow(
        modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = day.date.format(
                DateTimeFormatter.ofPattern(
                    if (showYear) "yyyy年M月d日 EEE" else "M月d日 EEE",
                    Locale.CHINA
                )
            ),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "净支出 ¥${formatAmount(day.dayTotalCents)}" +
                if (day.excludedTotalCents != 0L) " · 代付 ¥${formatAmount(day.excludedTotalCents)}" else "",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwipeRecordRow(
    record: UiRecord,
    shape: Shape,
    emoji: Map<String, String>,
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
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .semantics { customActions = listOf(CustomAccessibilityAction("删除记录") { onDelete(); true }) }
                .clickable {
                    if (offset.value < 0f) {
                        onOpenChange(false)
                    } else {
                        onClick()
                    }
                }
        ) {
            Column {
                RecordRowContent(record, emoji)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

private fun ledgerRowShape(position: Int, count: Int) = RoundedCornerShape(
    topStart = if (position == 0) 16.dp else 0.dp, topEnd = if (position == 0) 16.dp else 0.dp,
    bottomStart = if (position == count - 1) 16.dp else 0.dp, bottomEnd = if (position == count - 1) 16.dp else 0.dp
)

@Composable
private fun RecordRowContent(record: UiRecord, emoji: Map<String, String>) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = when (record.type) {
                    TransactionType.INCOME -> "＋ 收入"
                    TransactionType.TRANSFER -> "⇄ 转账"
                    TransactionType.REFUND -> "↩ 退款 · ${record.category}"
                    else -> "${categoryEmoji(record.category, emoji)} ${record.category}"
                },
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium
            )
            Text(transactionAmountLabel(record.type, record.amountCents),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        }
        if (record.note.isNotBlank()) Text(record.note, Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        val account = if (record.type == TransactionType.TRANSFER) "${record.paymentMethod} → ${record.transferTo}"
            else record.paymentMethod.ifBlank { "未关联账户" }
        val meta = listOf(account, if (record.excluded) "代付" else "", if (record.overBudget) "！超预算" else "").filter { it.isNotBlank() }.joinToString(" · ")
        Text(meta, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}
