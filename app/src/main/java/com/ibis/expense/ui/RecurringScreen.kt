@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ibis.expense.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ibis.expense.data.RecurringExpense
import kotlinx.coroutines.launch

@Composable
fun RecurringScreen(vm: AppViewModel, onDone: () -> Unit) {
    val items by vm.recurringState.collectAsState()
    val categories by vm.categoriesState.collectAsState()
    val emoji = remember(categories) { categories.associate { it.name to it.emoji } }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringExpense?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("周期性支出") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showAdd = true }) { Text("添加") }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "还没有周期性支出\n房租、会员等固定开销登记一次即可",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    RecurringItem(item = item, emoji = emoji, onEdit = { editing = item }, onDelete = { vm.deleteRecurring(item.id) })
                }
                item {
                    Text(
                        "每月到达指定日期后，打开应用即自动记一笔（备注带 🔁 标记）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showAdd || editing != null) {
        AddRecurringDialog(
            initial = editing,
            categories = categories,
            emoji = emoji,
            onConfirm = { cents, day, category, note, method ->
                vm.saveRecurring(editing, cents, day, category, note, method)
            },
            onDismiss = { showAdd = false; editing = null }
        )
    }
}

@Composable
private fun RecurringItem(
    item: com.ibis.expense.data.RecurringExpense,
    emoji: Map<String, String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "🔁 ${item.note}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${categoryEmoji(item.category, emoji)} ${item.category} · 每月 ${item.dayOfMonth} 号",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(item.paymentMethod.ifBlank { "未关联账户 · 点按设置" }, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "¥${formatAmount(item.amountCents)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun AddRecurringDialog(
    initial: RecurringExpense?,
    categories: List<com.ibis.expense.data.Category>,
    emoji: Map<String, String>,
    onConfirm: suspend (cents: Long, day: Int, category: String, note: String, method: String) -> Result<Long>,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf(initial?.amountCents?.let(::centsToInput) ?: "") }
    var day by remember { mutableStateOf(initial?.dayOfMonth?.toString() ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: categories.firstOrNull()?.name ?: "其他") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var method by remember { mutableStateOf(initial?.paymentMethod ?: PaymentMethod.WECHAT) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cents = parseAmountToCents(amount)
    val dayNum = day.toIntOrNull()?.takeIf { it in 1..28 }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "添加周期性支出" else "编辑周期性支出") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (initial != null) Text("修改仅影响以后生成的记录，已有流水不变。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("金额") },
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("每月几号（1-28）") },
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("支付账户", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.ALL_WITH_LEGACY.forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m }, enabled = !busy,
                            label = { Text((if (method == m) "✓ " else "") + m.ifBlank { "未关联" }) },
                            border = if (method == m) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null)
                    }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { c ->
                        FilterChip(
                            selected = category == c.name,
                            onClick = { category = c.name },
                            enabled = !busy,
                            label = { Text("${if (category == c.name) "✓ " else ""}${categoryEmoji(c.name, emoji)} ${c.name}") },
                            border = if (category == c.name) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（如：房租 / 视频会员）") },
                    enabled = !busy,
                    singleLine = true
                )
                error?.let { Text("保存失败：$it") }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (cents != null && dayNum != null && note.isNotBlank() && !busy) {
                        busy = true
                        scope.launch {
                            try {
                                onConfirm(cents, dayNum, category, note, method).onSuccess { onDismiss() }
                                    .onFailure { error = it.message ?: "请重试" }
                            } finally { busy = false }
                        }
                    }
                },
                enabled = cents != null && dayNum != null && note.isNotBlank() && !busy
            ) { Text(if (busy) "保存中…" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        }
    )
}
