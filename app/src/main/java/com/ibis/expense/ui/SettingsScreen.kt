@file:OptIn(ExperimentalMaterial3Api::class)

package com.ibis.expense.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun CsvImportDialog(vm: AppViewModel, uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun runImport(replace: Boolean) {
        scope.launch {
            vm.importCsv(uri, replace)
                .onSuccess { outcome ->
                    val msg = if (outcome.skipped > 0) {
                        "成功导入 ${outcome.imported} 条，跳过 ${outcome.skipped} 条无效记录"
                    } else {
                        "成功导入 ${outcome.imported} 条记录"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
                .onFailure { e ->
                    Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                }
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入数据") },
        text = {
            Column {
                Text("选择导入方式：")
                Spacer(Modifier.height(8.dp))
                Text(
                    "合并导入：保留现有记录，追加 CSV 中的记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { runImport(replace = true) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("清空后导入")
                }
                Text(
                    "清空后导入：删除现有全部记录，再导入 CSV（换机迁移用）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { runImport(replace = false) }) {
                Text("合并导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ImportSourceDialog(
    onPickLocal: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入 CSV") },
        text = {
            Column {
                Text("从哪里导入？")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onPickLocal, modifier = Modifier.fillMaxWidth()) {
                    Text("📁 从本机文件选择")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "通过系统文件选择器从下载、文档等位置选取 CSV 文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("💬 从微信等应用导入")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "在微信中点击收到的 CSV 文件 → 右上角「···」→「用其他应用打开」→ 选择「记账」，即可直接导入，无需先保存到本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ExportSourceDialog(
    onShare: () -> Unit,
    onSaveLocal: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出 CSV") },
        text = {
            Column {
                Text("导出到哪里？")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                    Text("💬 分享到微信等应用")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "通过系统分享面板发送，可直接发到微信聊天或文件传输助手。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onSaveLocal, modifier = Modifier.fillMaxWidth()) {
                    Text("💾 保存到本机")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "通过系统保存对话框存到下载、文档等任意位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun SettingsScreen(vm: AppViewModel, onDone: () -> Unit) {
    var text by remember { mutableStateOf(centsToInput(vm.budgetCents.value)) }
    val cents = parseAmountToCents(text)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastBackup by vm.lastBackup.collectAsState()
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportSource by remember { mutableStateOf(false) }
    var showExportSource by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImportUri = uri }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                vm.exportCsvTo(uri)
                    .onSuccess { Toast.makeText(context, "已导出 $it 条记录", Toast.LENGTH_LONG).show() }
                    .onFailure { e -> Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun shareCsv() {
        scope.launch {
            val file = vm.exportCsvForShare()
            if (file == null) {
                Toast.makeText(context, "暂无记录可导出", Toast.LENGTH_SHORT).show()
            } else {
                val uri = FileProvider.getUriForFile(context, "com.ibis.expense.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "分享记账数据"))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            Text("每月预算", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("金额（元）") },
                modifier = Modifier.fillMaxWidth(),
                isError = text.isNotBlank() && cents == null,
                supportingText = {
                    if (text.isNotBlank() && cents == null) Text("请输入正确金额，最多两位小数")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "每月 1 号自动重置为该预算，无需手动操作。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    cents?.let {
                        vm.setBudgetCents(it)
                        onDone()
                    }
                },
                enabled = cents != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("保存", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(32.dp))
            Text("数据", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "自动备份",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = lastBackup?.let {
                            "每天首次打开应用时自动备份，保留最近 7 份。\n最近备份：${it.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))}"
                        } ?: "每天首次打开应用时自动备份，保留最近 7 份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showExportSource = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("📤 导出 CSV")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showImportSource = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("📥 导入 CSV")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showExportSource) {
        ExportSourceDialog(
            onShare = {
                showExportSource = false
                shareCsv()
            },
            onSaveLocal = {
                showExportSource = false
                val stamp = java.time.LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                exportLauncher.launch("记账-$stamp.csv")
            },
            onDismiss = { showExportSource = false }
        )
    }

    if (showImportSource) {
        ImportSourceDialog(
            onPickLocal = {
                showImportSource = false
                importLauncher.launch(
                    arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain")
                )
            },
            onDismiss = { showImportSource = false }
        )
    }

    pendingImportUri?.let { uri ->
        CsvImportDialog(vm = vm, uri = uri, onDismiss = { pendingImportUri = null })
    }
}
