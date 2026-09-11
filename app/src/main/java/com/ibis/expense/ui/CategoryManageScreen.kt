@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ibis.expense.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.ibis.expense.data.Category

@Composable
fun CategoryManageScreen(vm: AppViewModel, onDone: () -> Unit) {
    val categories by vm.categoriesState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Category?>(null) }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }
    val names = remember(categories) { categories.map { it.name } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类管理") },
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
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.name }) { c ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${c.emoji} ${c.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { editTarget = c }) { Text("编辑") }
                        if (c.name != "其他") {
                            TextButton(onClick = { deleteTarget = c }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "分类在记账、统计、周期支出中通用。删除分类时，其下记录会归入「其他」，不会丢失。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (showAdd) {
        CategoryEditDialog(
            existing = null,
            names = names,
            onConfirm = { name, emoji ->
                vm.addCategory(name, emoji)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    editTarget?.let { target ->
        CategoryEditDialog(
            existing = target,
            names = names,
            onConfirm = { name, emoji ->
                vm.renameCategory(target.name, name, emoji)
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除分类") },
            text = { Text("「${target.name}」下的记录、周期支出和模板将归入「其他」，记录本身不会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCategory(target.name)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CategoryEditDialog(
    existing: Category?,
    names: List<String>,
    onConfirm: (name: String, emoji: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "📦") }
    val trimmed = name.trim()
    val duplicate = trimmed.isNotEmpty() && trimmed != existing?.name && trimmed in names

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加分类" else "编辑分类") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 6) name = it },
                    label = { Text("名称") },
                    isError = duplicate,
                    supportingText = { if (duplicate) Text("已有同名分类") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("图标", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EmojiChoices.forEach { e ->
                        FilterChip(
                            selected = emoji == e,
                            onClick = { emoji = e },
                            label = { Text(e) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(trimmed, emoji) },
                enabled = trimmed.isNotEmpty() && !duplicate
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
