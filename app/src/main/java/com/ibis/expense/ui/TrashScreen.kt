@file:OptIn(ExperimentalMaterial3Api::class)

package com.ibis.expense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibis.expense.data.ExpenseRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrashScreen(vm: AppViewModel, onDone: () -> Unit) {
    val trash by vm.trashState.collectAsState()
    val categories by vm.categoriesState.collectAsState()
    val emoji = remember(categories) { categories.associate { it.name to it.emoji } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回收站") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (trash.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "回收站是空的",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trash, key = { it.id }) { record ->
                    TrashItem(
                        record = record,
                        emoji = emoji,
                        onRestore = { vm.restoreRecord(record.id) },
                        onDeleteForever = { vm.deleteRecordForever(record.id) }
                    )
                }
                item {
                    Text(
                        "左滑删除的记录会在这里保留 30 天，之后自动清除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashItem(record: ExpenseRecord, emoji: Map<String, String>, onRestore: () -> Unit, onDeleteForever: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${categoryEmoji(record.category, emoji)} ${record.category}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "¥${formatAmount(record.amountCents)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            if (record.note.isNotBlank()) {
                Text(
                    record.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "删除于 ${formatDeletedDate(record.deletedAt)} · 记账日 ${LocalDate.ofEpochDay(record.epochDay)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = onRestore) { Text("恢复") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDeleteForever) { Text("彻底删除") }
            }
        }
    }
}

private fun formatDeletedDate(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
