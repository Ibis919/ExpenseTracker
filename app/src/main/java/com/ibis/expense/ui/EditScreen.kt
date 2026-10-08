@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.ibis.expense.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ibis.expense.data.RecordTemplate
import com.ibis.expense.data.ExpenseRecord
import com.ibis.expense.data.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun EditScreen(vm: AppViewModel, initial: UiRecord?, onDone: () -> Unit, onBusyChange: (Boolean) -> Unit = {}) {
    val categories by vm.categoriesState.collectAsState()
    val templates by vm.templatesState.collectAsState()
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
        if (!canSave) return
        saving = true
        onBusyChange(true)
        saveError = null
        scope.launch {
            try {
                val result = if (initial == null) {
                    vm.addRecord(amountCents!!, epochDay, effectiveCategory!!, note,
                        excluded = type == TransactionType.EXPENSE && excluded, paymentMethod = paymentMethod,
                        type = type, transferTo = if (type == TransactionType.TRANSFER) transferTo else "",
                        relatedRecordId = if (type == TransactionType.REFUND) relatedId else 0)
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
                if (paymentMethod.isEmpty()) Text("未关联账户，选择后才影响余额。", style = MaterialTheme.typography.bodySmall)
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
                if (initial == null) {
                    Spacer(Modifier.height(20.dp))
                    TemplateBar(templates, emoji, amountCents != null && effectiveCategory != null && !saving,
                        onUse = { t -> if (!saving) {
                            amount = centsToInput(t.amountCents); selectedCategory = t.category; note = t.note; paymentMethod = t.paymentMethod
                        } }, onDelete = { if (!saving) deleteTemplateTarget = it },
                        onSave = { vm.saveTemplate(amountCents!!, effectiveCategory!!, note, paymentMethod) })
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
    onSave: () -> Unit
) {
    Column {
        Text("快捷模板", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(templates, key = { it.id }) { t ->
                val label = buildString {
                    append(categoryEmoji(t.category, emoji))
                    append(' ')
                    append("¥${formatAmount(t.amountCents)} · ${t.paymentMethod.ifBlank { "未关联" }}")
                    append(" · ${t.note.lineSequence().firstOrNull()?.take(16)?.ifBlank { t.category } ?: t.category}")
                }
                TemplateChip(
                    label = label,
                    onClick = { onUse(t) },
                    onLongClick = { onDelete(t) }
                )
            }
            item(key = "save") {
                TemplateChip(
                    label = "＋ 存为模板",
                    enabled = canSave,
                    onClick = onSave
                )
            }
        }
        if (templates.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "点按填入 · 长按删除",
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
private fun TemplateChip(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        )
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
