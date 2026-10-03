@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ibis.expense.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun RecordFilterDialog(initial: RecordFilter, onApply: (RecordFilter) -> Unit, onDismiss: () -> Unit) {
    var method by remember { mutableStateOf(initial.paymentMethod) }
    var excluded by remember { mutableStateOf(initial.excluded) }
    var from by remember { mutableStateOf(initial.fromDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "") }
    var to by remember { mutableStateOf(initial.toDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "") }
    var min by remember { mutableStateOf(initial.minCents?.let(::centsToInput) ?: "") }
    var max by remember { mutableStateOf(initial.maxCents?.let(::centsToInput) ?: "") }
    val fromDay = runCatching { LocalDate.parse(from).toEpochDay() }.getOrNull()
    val toDay = runCatching { LocalDate.parse(to).toEpochDay() }.getOrNull()
    val minCents = parseAmountToCents(min, allowZero = true)
    val maxCents = parseAmountToCents(max, allowZero = true)
    val valid = (from.isBlank() || fromDay != null) && (to.isBlank() || toDay != null) &&
        (min.isBlank() || minCents != null) && (max.isBlank() || maxCents != null) &&
        (fromDay == null || toDay == null || fromDay <= toDay) &&
        (minCents == null || maxCents == null || minCents <= maxCents)

    AlertDialog(onDismissRequest = onDismiss, title = { Text("筛选流水") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("账户", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<String?>(null) + PaymentMethod.ALL_WITH_LEGACY).forEach { m ->
                    FilterChip(selected = method == m, onClick = { method = m },
                        label = { Text((if (method == m) "✓ " else "") + (m?.ifBlank { "未关联" } ?: "全部")) },
                        border = if (method == m) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null)
                }
            }
            Text("代付状态", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "全部", false to "非代付", true to "仅代付").forEach { (value, label) ->
                    FilterChip(selected = excluded == value, onClick = { excluded = value },
                        label = { Text((if (excluded == value) "✓ " else "") + label) },
                        border = if (excluded == value) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null)
                }
            }
            Text("日期范围 · 留空不限", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(from, { from = it }, label = { Text("起始日期，如 2026-10-01") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(to, { to = it }, label = { Text("截止日期，如 2026-10-31") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            Text("单笔金额 · 留空不限", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(min, { min = it }, label = { Text("最低金额（元）") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(max, { max = it }, label = { Text("最高金额（元）") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (!valid) Text("请检查日期或金额，起始值不能大于截止值。")
        }
    }, confirmButton = {
        TextButton(onClick = { onApply(RecordFilter(method, excluded, fromDay, toDay, minCents, maxCents)) }, enabled = valid) { Text("应用筛选") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
