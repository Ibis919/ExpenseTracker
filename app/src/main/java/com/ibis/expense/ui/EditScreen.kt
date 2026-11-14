@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.ibis.expense.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ibis.expense.data.RecordTemplate
import com.ibis.expense.data.RecurringExpense
import com.ibis.expense.data.ExpenseRecord
import com.ibis.expense.data.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun EditScreen(vm: AppViewModel, initial: UiRecord?, onDone: () -> Unit, onBusyChange: (Boolean) -> Unit = {}) {
    val categories by vm.categoriesState.collectAsState()
    val templates by vm.templatesState.collectAsState()
    val recurringRules by vm.recurringState.collectAsState()
    val recurringRecorded by vm.recurringRecordedThisMonth.collectAsState()
    val records by vm.allRecords.collectAsState()
    var amount by rememberSaveable(initial?.id) { mutableStateOf(initial?.let { centsToInput(it.amountCents) } ?: "") }
    var epochDay by rememberSaveable(initial?.id) { mutableStateOf(initial?.epochDay ?: LocalDate.now().toEpochDay()) }
    var selectedCategory by rememberSaveable(initial?.id) { mutableStateOf(initial?.category) }
    var note by rememberSaveable(initial?.id) { mutableStateOf(initial?.note ?: "") }
    var excluded by rememberSaveable(initial?.id) { mutableStateOf(initial?.excluded ?: false) }
    var paymentMethod by rememberSaveable(initial?.id) { mutableStateOf(initial?.paymentMethod ?: PaymentMethod.WECHAT) }
    var type by rememberSaveable(initial?.id) { mutableStateOf(initial?.type ?: TransactionType.EXPENSE) }
    var transferTo by rememberSaveable(initial?.id) { mutableStateOf(initial?.transferTo?.ifBlank { PaymentMethod.ALIPAY } ?: PaymentMethod.ALIPAY) }
    var relatedId by rememberSaveable(initial?.id) { mutableStateOf(initial?.relatedRecordId ?: 0L) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showRefundPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAmountCalculator by rememberSaveable { mutableStateOf(false) }
    var deleteTemplateTarget by remember { mutableStateOf<RecordTemplate?>(null) }
    var selectedRuleId by rememberSaveable(initial?.id) { mutableStateOf(0L) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    BackHandler(enabled = saving) { }
    val amountCents = parseAmountToCents(amount)
    val parent = records.firstOrNull { it.id == relatedId && it.type == TransactionType.EXPENSE }
    val remainingRefund = parent?.let { p -> p.amountCents - records.filter {
        it.type == TransactionType.REFUND && it.relatedRecordId == p.id && it.id != initial?.id
    }.sumOf { it.amountCents } }
    val effectiveCategory = when (type) {
        TransactionType.REFUND -> parent?.category
        TransactionType.INCOME -> "收入"
        TransactionType.TRANSFER -> "转账"
        else -> selectedCategory ?: categories.firstOrNull()?.name
    }
    val canSave = amountCents != null && effectiveCategory != null && !saving &&
        (type != TransactionType.REFUND || (remainingRefund != null && amountCents <= remainingRefund && epochDay >= parent!!.epochDay)) &&
        (type != TransactionType.TRANSFER || (paymentMethod in listOf(PaymentMethod.WECHAT, PaymentMethod.ALIPAY) &&
            transferTo in listOf(PaymentMethod.WECHAT, PaymentMethod.ALIPAY) && paymentMethod != transferTo))
    val emoji = remember(categories) { categories.associate { it.name to it.emoji } }

    fun save() {
        if (saving || !canSave) return
        saving = true
        onBusyChange(true)
        saveError = null
        scope.launch {
            try {
                val result = if (initial == null) {
                    vm.addRecord(amountCents!!, epochDay, effectiveCategory!!, note,
                        excluded = type == TransactionType.EXPENSE && excluded, paymentMethod = paymentMethod,
                        type = type, transferTo = if (type == TransactionType.TRANSFER) transferTo else "",
                        relatedRecordId = if (type == TransactionType.REFUND) relatedId else 0,
                        recurringId = if (type == TransactionType.EXPENSE || type == TransactionType.INCOME) selectedRuleId else 0)
                } else {
                    vm.updateRecord(initial, amountCents!!, epochDay, effectiveCategory!!, note,
                        excluded = type == TransactionType.EXPENSE && excluded, paymentMethod = paymentMethod,
                        transferTo = if (type == TransactionType.TRANSFER) transferTo else "")
                }
                result.onSuccess {
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    onDone()
                }.onFailure { saveError = it.message ?: "请重试" }
            } finally { saving = false; onBusyChange(false) }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(title = { Text(if (initial == null) "记一笔" else "编辑记录") },
                navigationIcon = { IconButton(onClick = onDone, enabled = !saving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                } })
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    saveError?.let { Text("保存失败：$it"); Spacer(Modifier.height(8.dp)) }
                    Button(onClick = ::save, enabled = canSave,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                        Text(if (saving) "保存中…" else "保存并返回账本", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (initial == null && type == TransactionType.EXPENSE) {
                TemplateBar(templates, emoji, amountCents != null && effectiveCategory != null && !saving,
                    onUse = { t -> if (!saving) {
                        amount = centsToInput(t.amountCents); selectedCategory = t.category; note = t.note; paymentMethod = t.paymentMethod
                    } }, onDelete = { if (!saving) deleteTemplateTarget = it },
                    onSave = { vm.saveTemplate(amountCents!!, effectiveCategory!!, note, paymentMethod) },
                    onReorder = { ids -> vm.reorderTemplates(ids) })
                Spacer(Modifier.height(16.dp))
            }
            OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                enabled = !saving, modifier = Modifier.fillMaxWidth(), label = { Text("金额（元）") },
                textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                placeholder = { Text("0.00", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineMedium) },
                isError = amount.isNotBlank() && amountCents == null,
                supportingText = { if (amount.isNotBlank() && amountCents == null) Text("请输入正确金额，最多两位小数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            showAmountCalculator = true
                        },
                        enabled = !saving
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = "打开计算器")
                    }
                })
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.ALL.forEach { value ->
                    FilterChip(selected = type == value, onClick = {
                        type = value
                        selectedRuleId = 0
                        saveError = null
                        if (value == TransactionType.TRANSFER && transferTo == paymentMethod) {
                            transferTo = if (paymentMethod == PaymentMethod.WECHAT) PaymentMethod.ALIPAY else PaymentMethod.WECHAT
                        }
                    }, enabled = !saving && initial == null,
                        label = { Text((if (type == value) "✓ " else "") + TransactionType.label(value)) },
                        border = if (type == value) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null)
                }
            }
            if (initial != null) Text("交易类型在创建后保持不变。", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { showDatePicker = true }, enabled = !saving,
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("📅  ${LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}")
            }
            Spacer(Modifier.height(16.dp))
            if (type == TransactionType.REFUND) {
                OutlinedButton(onClick = { showRefundPicker = true }, enabled = !saving && initial == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(parent?.let { "原支出：${LocalDate.ofEpochDay(it.epochDay)} · ${it.category} ¥${formatAmount(it.amountCents)}" } ?: "选择原支出")
                }
                parent?.let {
                    Text("退款到${it.paymentMethod.ifBlank { "未关联账户" }} · 最多可退 ¥${formatAmount(remainingRefund!!)}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text(it.note.ifBlank { "原支出无备注" }, style = MaterialTheme.typography.bodySmall)
                    Text("按退款日期冲减净支出，分类和代付状态沿用原支出。", style = MaterialTheme.typography.bodySmall)
                    if (amountCents != null && amountCents > remainingRefund!!) Text("退款不能超过可退金额。")
                    if (epochDay < it.epochDay) Text("退款日期不能早于原支出。")
                }
            } else {
                Text(when (type) { TransactionType.INCOME -> "收款账户"; TransactionType.TRANSFER -> "转出账户"; else -> "支付方式" },
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                PaymentChoices(paymentMethod, !saving) { method ->
                    paymentMethod = method
                    if (type == TransactionType.TRANSFER && transferTo == method) {
                        transferTo = if (method == PaymentMethod.WECHAT) PaymentMethod.ALIPAY else PaymentMethod.WECHAT
                    }
                }
                if (paymentMethod.isEmpty()) Text("未关联账户：补选支付方式不会改变现有余额。", style = MaterialTheme.typography.bodySmall)
                if (type == TransactionType.TRANSFER) {
                    Spacer(Modifier.height(12.dp))
                    Text("转入账户", style = MaterialTheme.typography.titleSmall)
                    PaymentChoices(transferTo, !saving, blocked = paymentMethod) { transferTo = it }
                    Text("两个账户同步增减，不计消费预算。", style = MaterialTheme.typography.bodySmall)
                }
                if (type == TransactionType.INCOME) Text("增加所选账户余额，不增加消费预算。", style = MaterialTheme.typography.bodySmall)
            }
            if (type == TransactionType.EXPENSE) {
                Spacer(Modifier.height(16.dp))
                Text("分类", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { c ->
                        FilterChip(selected = effectiveCategory == c.name, onClick = { selectedCategory = c.name }, enabled = !saving,
                            label = { Text("${if (effectiveCategory == c.name) "✓ " else ""}${c.emoji} ${c.name}") },
                            border = if (effectiveCategory == c.name) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = note, onValueChange = { note = it }, enabled = !saving, label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6)
            if (type == TransactionType.EXPENSE) {
                Spacer(Modifier.height(12.dp))
                FilterChip(selected = excluded, onClick = { excluded = !excluded }, enabled = !saving,
                    label = { Text(if (excluded) "✓ 代付 · 不占预算" else "帮别人代付") },
                    border = if (excluded) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null)
                if (excluded) Text("不计消费预算和统计，仍扣支付账户余额。", style = MaterialTheme.typography.bodySmall)
            }
            if (initial == null && (type == TransactionType.EXPENSE || type == TransactionType.INCOME)) {
                if (recurringRules.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    RecurringBar(recurringRules, emoji, recurringRecorded, selectedRuleId, !saving) { r ->
                        amount = centsToInput(r.amountCents)
                        selectedCategory = r.category
                        note = r.note
                        paymentMethod = r.paymentMethod
                        type = r.type
                        selectedRuleId = r.id
                    }
                }
            }
            if (initial != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { showDeleteConfirm = true }, enabled = !saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("删除记录") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showAmountCalculator) {
        AmountCalculatorDialog(
            initialAmount = amount,
            onApply = { calculatedAmount ->
                amount = calculatedAmount
                showAmountCalculator = false
            },
            onDismiss = { showAmountCalculator = false }
        )
    }
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = epochDay * MILLIS_PER_DAY)
        DatePickerDialog(onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { epochDay = it / MILLIS_PER_DAY }; showDatePicker = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }) { DatePicker(picker) }
    }
    if (showRefundPicker) RefundPickerDialog(records, onPick = { relatedId = it.id; showRefundPicker = false }, onDismiss = { showRefundPicker = false })
    if (showDeleteConfirm && initial != null) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false }, title = { Text("删除记录") },
            text = { Text("记录会进入回收站，30 天内可恢复。原支出关联的退款也会一同删除和恢复。") },
            confirmButton = { TextButton(onClick = { vm.deleteRecord(initial.id); onDone() }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } })
    }
    deleteTemplateTarget?.let { target ->
        AlertDialog(onDismissRequest = { deleteTemplateTarget = null }, title = { Text("删除模板") },
            text = { Text("不影响已有记录。") },
            confirmButton = { TextButton(onClick = { vm.deleteTemplate(target.id); deleteTemplateTarget = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTemplateTarget = null }) { Text("取消") } })
    }
}

@Composable
private fun PaymentChoices(selected: String, enabled: Boolean, blocked: String? = null, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PaymentMethod.WECHAT, PaymentMethod.ALIPAY).filter { it != blocked }.forEach { method ->
            FilterChip(selected = selected == method, onClick = { onSelect(method) }, enabled = enabled,
                label = { Text(if (selected == method) "✓ $method" else method) }, leadingIcon = { PaymentIcon(method) },
                border = if (selected == method) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null)
        }
    }
}

@Composable
private fun RefundPickerDialog(records: List<ExpenseRecord>, onPick: (ExpenseRecord) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val refunds = records.filter { it.type == TransactionType.REFUND }.groupBy { it.relatedRecordId }
    val choices = records.filter { r -> r.type == TransactionType.EXPENSE &&
        r.amountCents > refunds[r.id].orEmpty().sumOf { it.amountCents } &&
        (query.isBlank() || "${r.category} ${r.note} ${r.paymentMethod} ${LocalDate.ofEpochDay(r.epochDay)}".contains(query)) }
        .sortedWith(compareByDescending<ExpenseRecord> { it.epochDay }.thenByDescending { it.createdAt })
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择退款的原支出") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("搜索日期、分类或备注") }, singleLine = true)
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (choices.isEmpty()) item { Text("没有可退款的支出", Modifier.padding(vertical = 16.dp)) }
                items(choices, key = { it.id }) { record ->
                    TextButton(onClick = { onPick(record) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("${LocalDate.ofEpochDay(record.epochDay)} · ${record.category} ¥${formatAmount(record.amountCents)}")
                            Text(record.note.ifBlank { "无备注" }, style = MaterialTheme.typography.bodySmall)
                            Text("${record.paymentMethod.ifBlank { "未关联账户" }} · 可退 ¥${formatAmount(record.amountCents - refunds[record.id].orEmpty().sumOf { it.amountCents })}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun TemplateBar(
    templates: List<RecordTemplate>,
    emoji: Map<String, String>,
    canSave: Boolean,
    onUse: (RecordTemplate) -> Unit,
    onDelete: (RecordTemplate) -> Unit,
    onSave: () -> Unit,
    onReorder: suspend (List<Long>) -> Result<Unit>
) {
    val ordered = remember(templates) { templates.toMutableStateList() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragGrabX by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var beforeDrag by remember { mutableStateOf<List<RecordTemplate>>(emptyList()) }
    var reordering by remember { mutableStateOf(false) }
    var barRect by remember { mutableStateOf(Rect.Zero) }
    var dropRect by remember { mutableStateOf(Rect.Zero) }
    val overDrop = remember { derivedStateOf {
        if (draggedId == null) false
        else dropRect.contains(Offset(barRect.left + dragStartX + dragDistance, barRect.center.y))
    } }

    fun move(id: Long, target: Int): Boolean {
        val from = ordered.indexOfFirst { it.id == id }
        if (reordering || from < 0 || target !in ordered.indices || from == target) return false
        ordered.add(target, ordered.removeAt(from))
        return true
    }

    fun moveBy(id: Long, amount: Int): Boolean {
        val from = ordered.indexOfFirst { it.id == id }
        return from >= 0 && move(id, from + amount)
    }

    fun moveUnderPointer() {
        val id = draggedId ?: return
        val visible = listState.layoutInfo.visibleItemsInfo.filter { it.key is Long }
        if (visible.isEmpty()) return
        val center = dragStartX + dragDistance
        // 落在两个 chip 之间的 8dp 间隙时取最近的 chip，避免直接跳到最右端。
        val target = visible.firstOrNull { center >= it.offset && center < it.offset + it.size }
            ?: visible.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - center) }
            ?: return
        val targetId = target.key as? Long ?: return
        move(id, ordered.indexOfFirst { it.id == targetId })
    }

    fun restoreBeforeDrag() {
        ordered.clear()
        ordered.addAll(beforeDrag)
    }

    fun startDrag(id: Long, startPos: Offset) {
        if (draggedId != null || reordering) return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        beforeDrag = ordered.toList()
        draggedId = id
        dragStartX = item.offset + startPos.x
        dragGrabX = startPos.x
        dragDistance = 0f
        try {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (e: Exception) {
            // Haptics are unavailable in some environments (e.g. Robolectric); dragging must still work.
        }
    }

    fun finishDragCancelled() {
        if (draggedId != null) restoreBeforeDrag()
        draggedId = null
        dragDistance = 0f
        dragGrabX = 0f
    }

    fun commitOrder() {
        val ids = ordered.map { it.id }
        if (reordering || ids == beforeDrag.map { it.id }) return
        reordering = true
        scope.launch {
            try {
                onReorder(ids).onFailure { e ->
                    restoreBeforeDrag()
                    Toast.makeText(context, e.message ?: "模板排序保存失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                restoreBeforeDrag()
                Toast.makeText(context, e.message ?: "模板排序保存失败", Toast.LENGTH_SHORT).show()
            } finally { reordering = false }
        }
    }

    fun moveAndCommit(id: Long, amount: Int): Boolean {
        if (draggedId != null || reordering) return false
        val from = ordered.indexOfFirst { it.id == id }
        if (from < 0 || from + amount !in ordered.indices) return false
        beforeDrag = ordered.toList()
        moveBy(id, amount)
        commitOrder()
        return true
    }

    fun dropTemplate(id: Long) {
        if (draggedId != id) return
        val center = Offset(barRect.left + dragStartX + dragDistance, barRect.center.y)
        val hitDrop = overDrop.value
        draggedId = null
        dragDistance = 0f
        dragGrabX = 0f
        if (hitDrop) {
            restoreBeforeDrag()
            ordered.firstOrNull { it.id == id }?.let { onDelete(it) }
            return
        }
        if (center.x < barRect.left || center.x > barRect.right) { restoreBeforeDrag(); return }
        commitOrder()
    }

    Column {
        Text("快捷模板", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth()
                .onGloballyPositioned {
                    val pos = it.positionInRoot()
                    barRect = Rect(
                        Offset(pos.x.toFloat(), pos.y.toFloat()),
                        Offset((pos.x + it.size.width).toFloat(), (pos.y + it.size.height).toFloat()),
                    )
                }
        ) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ordered, key = { it.id }) { t ->
                    val isDragging = draggedId == t.id
                    val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == t.id }
                    val dragTranslationX = if (isDragging && itemInfo != null)
                        dragStartX + dragDistance - dragGrabX - itemInfo.offset else 0f
                    val displayNote = t.note.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { t.category }.take(12)
                    val label = categoryEmoji(t.category, emoji) + " " + displayNote
                    val index = ordered.indexOfFirst { it.id == t.id }
                    TemplateChip(
                        label = label,
                        dragging = isDragging,
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationX = dragTranslationX }
                            .animateItem()
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = "快捷模板 $label，点按填入，长按可拖动排序"
                                if (isDragging) stateDescription = "正在拖动"
                                onClick(label = "填入模板") { onUse(t); true }
                                customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction("上移一位") { moveAndCommit(t.id, -1) })
                                    if (index < ordered.lastIndex) add(CustomAccessibilityAction("下移一位") { moveAndCommit(t.id, 1) })
                                    add(CustomAccessibilityAction("删除") { onDelete(t); true })
                                }
                            }
                            .testTag("template-chip-${t.id}")
                            .pointerInput(t.id) {
                                // 点按填入 与 长按拖动排序 由这一个手势检测器裁决（与 CategoryReorderScreen 同一写法）。
                                // 不能再让 chip 自己挂 clickable：那个点击节点在本 pointerInput 内侧，会先消费 DOWN，
                                // 令长按永不成立。拖动中会消费位移事件，因此拖动不会带动外层表单滚动。
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var finishedBeforeLongPress = false
                                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        waitForUpOrCancellation().also { finishedBeforeLongPress = true }
                                    }
                                    if (finishedBeforeLongPress) {
                                        if (up != null) {
                                            up.consume()
                                            onUse(t)
                                        }
                                    } else {
                                        startDrag(t.id, down.position)
                                        if (draggedId == t.id) {
                                            try {
                                                val completed = drag(down.id) { change ->
                                                    dragDistance += change.positionChange().x
                                                    change.consume()
                                                    moveUnderPointer()
                                                }
                                                if (completed) dropTemplate(t.id) else finishDragCancelled()
                                            } catch (e: CancellationException) {
                                                finishDragCancelled()
                                                throw e
                                            }
                                        }
                                    }
                                }
                            }
                    )
                }
                item(key = "save") {
                    TemplateChip(
                        label = "＋ 存为模板",
                        enabled = canSave,
                        modifier = Modifier.animateItem(),
                        onClick = onSave
                    )
                }
            }
            if (draggedId != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(76.dp, 52.dp)
                        .onGloballyPositioned {
                            val pos = it.positionInRoot()
                            dropRect = Rect(
                                Offset(pos.x.toFloat(), pos.y.toFloat()),
                                Offset((pos.x + it.size.width).toFloat(), (pos.y + it.size.height).toFloat()),
                            )
                        }
                        .background(
                            if (overDrop.value) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            2.dp,
                            if (overDrop.value) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .semantics(mergeDescendants = true) { contentDescription = "拖到此处删除模板" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🗑 删除",
                        color = if (overDrop.value) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (templates.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "点按填入 · 长按拖动排序 · 拖到右侧删除",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(4.dp))
            Text(
                "填好金额和分类后点「存为模板」，下次一键带入",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecurringBar(
    rules: List<RecurringExpense>,
    emoji: Map<String, String>,
    recorded: Set<Long>,
    selectedRuleId: Long,
    enabled: Boolean,
    onUse: (RecurringExpense) -> Unit
) {
    Column {
        Text("🔁 周期记账", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(rules, key = { it.id }) { r ->
                val income = r.type == TransactionType.INCOME
                val label = buildString {
                    append(if (income) "💰" else categoryEmoji(r.category, emoji))
                    append(' ')
                    append(r.note.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { r.category }.take(12))
                    if (r.id in recorded) append(" ✓")
                }
                TemplateChip(
                    label = label,
                    enabled = enabled,
                    selected = selectedRuleId == r.id,
                    onClick = { if (enabled) onUse(r) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val hint = if (selectedRuleId > 0) {
            "已带入周期规则：改好金额后保存，这笔会记到本月的该规则上"
        } else {
            "点按带入金额、分类和账户，可改金额后保存"
        }
        Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TemplateChip(
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    dragging: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        border = if (selected || dragging) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
        shadowElevation = if (dragging) 8.dp else 0.dp,
        // 快捷模板 chip 传 onClick = null：点按与长按拖动都由 TemplateBar 里那一个 pointerInput 处理。
        // 以前这里挂 Modifier.clickable，产生的点击节点位于 pointerInput 内侧、会先消费 DOWN，
        // 长按因此永远不成立、模板拖不动。
        modifier = if (onClick == null) modifier else modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
            }
        )
    }
}

