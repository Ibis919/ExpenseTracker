@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ibis.expense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun RecurringScreen(vm: AppViewModel, onDone: () -> Unit) {
    val items by vm.recurringState.collectAsState()
    val categories by vm.categoriesState.collectAsState()
    val emoji = remember(categories) { categories.associate { it.name to it.emoji } }
    var showAdd by remember { mutableStateOf(false) }

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
                    RecurringItem(item = item, emoji = emoji, onDelete = { vm.deleteRecurring(item.id) })
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

    if (showAdd) {
        AddRecurringDialog(
            categories = categories,
            emoji = emoji,
            onConfirm = { cents, day, category, note ->
                vm.addRecurring(cents, day, category, note)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }
}

@Composable
private fun RecurringItem(
    item: com.ibis.expense.data.RecurringExpense,
    emoji: Map<String, String>,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
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
            }
            Text(
                "¥${formatAmount(item.amountCents)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

@Composable
private fun AddRecurringDialog(
    categories: List<com.ibis.expense.data.Category>,
    emoji: Map<String, String>,
    onConfirm: (cents: Long, day: Int, category: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.firstOrNull()?.name ?: "其他") }
    var note by remember { mutableStateOf("") }
    val cents = parseAmountToCents(amount)
    val dayNum = day.toIntOrNull()?.coerceIn(1, 28)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加周期性支出") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("金额") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("每月几号（1-28）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { c ->
                        FilterChip(
                            selected = category == c.name,
                            onClick = { category = c.name },
                            label = { Text("${categoryEmoji(c.name, emoji)} ${c.name}") }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（如：房租 / 视频会员）") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (cents != null && dayNum != null && note.isNotBlank()) {
                        onConfirm(cents, dayNum, category, note)
                    }
                },
                enabled = cents != null && dayNum != null && note.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
