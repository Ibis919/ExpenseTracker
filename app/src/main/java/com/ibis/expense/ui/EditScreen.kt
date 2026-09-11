@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.ibis.expense.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ibis.expense.data.RecordTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun EditScreen(vm: AppViewModel, initial: UiRecord?, onDone: () -> Unit) {
    val categories by vm.categoriesState.collectAsState()
    val templates by vm.templatesState.collectAsState()
    var amount by remember {
        mutableStateOf(initial?.let { centsToInput(it.amountCents) } ?: "")
    }
    var epochDay by remember {
        mutableStateOf(initial?.epochDay ?: LocalDate.now().toEpochDay())
    }
    var selectedCategory by remember { mutableStateOf(initial?.category) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var excluded by remember { mutableStateOf(initial?.excluded ?: false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTemplateTarget by remember { mutableStateOf<RecordTemplate?>(null) }
    val amountCents = parseAmountToCents(amount)
    val effectiveCategory = selectedCategory ?: categories.firstOrNull()?.name
    val emoji = remember(categories) { categories.associate { it.name to it.emoji } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "记一笔" else "编辑记录") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (initial == null) {
                TemplateBar(
                    templates = templates,
                    emoji = emoji,
                    canSave = amountCents != null && effectiveCategory != null,
                    onUse = { t ->
                        amount = centsToInput(t.amountCents)
                        selectedCategory = t.category
                        note = t.note
                    },
                    onDelete = { deleteTemplateTarget = it },
                    onSave = {
                        vm.saveTemplate(amountCents!!, effectiveCategory!!, note)
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.displayMedium.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                ),
                placeholder = {
                    Text(
                        "0.00",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                },
                isError = amount.isNotBlank() && amountCents == null,
                supportingText = {
                    if (amount.isNotBlank() && amountCents == null) Text("请输入正确金额，最多两位小数")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📅  ${LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}")
            }
            Spacer(Modifier.height(16.dp))
            Text("分类", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { c ->
                    FilterChip(
                        selected = effectiveCategory == c.name,
                        onClick = { selectedCategory = c.name },
                        label = { Text("${c.emoji} ${c.name}") }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            FilterChip(
                selected = excluded,
                onClick = { excluded = !excluded },
                label = { Text(if (excluded) "🤝 代付：不计入我的开销" else "🤝 帮别人代付（不计入我的开销）") },
                modifier = Modifier.fillMaxWidth()
            )
            if (excluded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "这笔钱不占预算、不计入统计，但会出现在明细列表中。备注里写上对方名字，日后一搜即知数额。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val cents = amountCents
                    val category = effectiveCategory
                    if (cents != null && category != null) {
                        if (initial == null) {
                            vm.addRecord(cents, epochDay, category, note, excluded)
                        } else {
                            vm.updateRecord(initial, cents, epochDay, category, note, excluded)
                        }
                        onDone()
                    }
                },
                enabled = amountCents != null && effectiveCategory != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("保存", style = MaterialTheme.typography.titleMedium)
            }
            if (initial != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = epochDay * MILLIS_PER_DAY
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { epochDay = it / MILLIS_PER_DAY }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDeleteConfirm && initial != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除记录") },
            text = { Text("删除后进入回收站，保留 30 天内可恢复。确定删除这条记录？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRecord(initial.id)
                    onDone()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    deleteTemplateTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTemplateTarget = null },
            title = { Text("删除模板") },
            text = { Text("删除后可随时重新保存，不影响已有记录。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTemplate(target.id)
                    deleteTemplateTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTemplateTarget = null }) { Text("取消") }
            }
        )
    }
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
                    append(t.note.ifBlank { t.category })
                    append(" ¥${formatAmount(t.amountCents)}")
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
