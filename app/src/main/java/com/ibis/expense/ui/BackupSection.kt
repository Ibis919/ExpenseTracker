package com.ibis.expense.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibis.expense.data.BackupSnapshot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun BackupSection(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<BackupSnapshot?>(null) }
    var localNames by remember { mutableStateOf<List<String>?>(null) }

    fun runOperation(label: String, operation: suspend () -> Unit) {
        if (busy != null) return
        busy = label
        error = null
        status = null
        scope.launch {
            try {
                operation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "操作未完成，请重试。"
            } finally {
                busy = null
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) runOperation("正在保存完整备份…") {
            val count = vm.exportBackupTo(uri).getOrThrow()
            status = "完整备份已保存，包含 $count 条记录（含回收站）。"
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runOperation("正在读取备份…") {
            snapshot = vm.readBackup(uri).getOrThrow()
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("完整备份与恢复", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("保存流水、回收站、账户余额、预算、分类、周期规则和模板，供换机或恢复使用。",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = {
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                exportLauncher.launch("记账完整备份-$stamp.json")
            }, enabled = busy == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("保存完整备份")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = busy == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("从完整备份文件恢复")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                runOperation("正在读取本机备份列表…") { localNames = vm.localBackupNames() }
            }, enabled = busy == null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("选择本机备份恢复")
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text("操作失败：$it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    val candidate = snapshot
    val names = localNames
    val running = busy
    when {
        candidate != null -> AlertDialog(
            onDismissRequest = { if (busy == null) { snapshot = null; error = null } },
            title = { Text("确认恢复完整备份") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    val date = Instant.ofEpochMilli(candidate.createdAt).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
                    val active = candidate.records.count { it.deletedAt == 0L }
                    Text("备份时间：$date")
                    Text("流水：$active 条 · 回收站：${candidate.records.size - active} 条")
                    Text("账户：${candidate.balances.size} 个 · 月预算：¥${formatAmount(candidate.budgetCents)}")
                    Text("分类：${candidate.categories.size} 个 · 周期规则：${candidate.recurring.size} 条")
                    Text("快捷模板：${candidate.templates.size} 个")
                    Spacer(Modifier.height(12.dp))
                    Text("将覆盖当前全部流水、回收站、账户余额、预算、分类、周期规则和模板。",
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("恢复前会自动保存当前数据的保护备份，可在本机备份列表中再次恢复。",
                        style = MaterialTheme.typography.bodySmall)
                    busy?.let {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text("恢复失败：$it")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    runOperation("正在覆盖并恢复，请稍候…") {
                        vm.restoreBackup(candidate).getOrThrow()
                        snapshot = null
                        status = "恢复完成，当前数据已替换为所选备份。恢复前数据已保存到本机保护备份。"
                    }
                }, enabled = busy == null) { Text("覆盖并恢复") }
            },
            dismissButton = {
                TextButton(onClick = { snapshot = null; error = null }, enabled = busy == null) { Text("取消") }
            }
        )
        names != null -> AlertDialog(
            onDismissRequest = { if (busy == null) localNames = null },
            title = { Text("选择本机备份") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("包含最近自动备份和恢复前保护备份，选择后先查看内容摘要。",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    if (names.isEmpty()) Text("本机暂无可恢复的备份。")
                    names.forEach { name ->
                        TextButton(onClick = {
                            localNames = null
                            runOperation("正在读取本机备份…") {
                                snapshot = vm.readLocalBackup(name).getOrThrow()
                            }
                        }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                            val day = name.removePrefix("expenses-").removeSuffix(".json").toLongOrNull()
                            Text(if (name == "before-restore.json") "恢复前的保护备份" else day?.let { "自动备份 · ${java.time.LocalDate.ofEpochDay(it)}" } ?: name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { localNames = null }, enabled = busy == null) { Text("取消") } }
        )
        running != null -> AlertDialog(
            onDismissRequest = {},
            title = { Text(running) },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = {}
        )
    }
}
