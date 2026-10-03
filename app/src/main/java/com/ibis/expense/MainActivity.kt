package com.ibis.expense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ibis.expense.ui.AppViewModel
import com.ibis.expense.ui.CategoryManageScreen
import com.ibis.expense.ui.CsvImportDialog
import com.ibis.expense.ui.EditScreen
import com.ibis.expense.ui.HomeScreen
import com.ibis.expense.ui.RecurringScreen
import com.ibis.expense.ui.SettingsScreen
import com.ibis.expense.ui.StatsScreen
import com.ibis.expense.ui.TrashScreen
import com.ibis.expense.ui.UiRecord
import com.ibis.expense.ui.theme.ExpenseTheme

sealed interface Screen {
    data class Edit(val record: UiRecord) : Screen
    data object Settings : Screen
    data object Trash : Screen
    data object Recurring : Screen
    data object CategoryManage : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val importUri = if (savedInstanceState == null) {
            intent?.takeIf { it.action == android.content.Intent.ACTION_VIEW }?.data
        } else null
        setContent {
            ExpenseTheme {
                RequestNotificationPermission()
                App(importUri = importUri)
            }
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun App(vm: AppViewModel = viewModel(), importUri: android.net.Uri? = null) {
    var tab by rememberSaveable { mutableStateOf(2) }
    var overlay by remember { mutableStateOf<Screen?>(null) }
    var recordSaving by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf(importUri) }
    BackHandler(enabled = overlay != null && !recordSaving) {
        overlay = when (overlay) { Screen.Trash, Screen.Recurring, Screen.CategoryManage -> Screen.Settings; else -> null }
    }
    when (val current = overlay) {
        is Screen.Edit -> EditScreen(
            vm = vm,
            initial = current.record,
            onBusyChange = { recordSaving = it },
            onDone = { overlay = null }
        )
        Screen.Settings -> SettingsScreen(
            vm = vm,
            onDone = { overlay = null },
            onOpenTrash = { overlay = Screen.Trash },
            onOpenRecurring = { overlay = Screen.Recurring },
            onOpenCategories = { overlay = Screen.CategoryManage }
        )
        Screen.Trash -> TrashScreen(vm = vm, onDone = { overlay = Screen.Settings })
        Screen.Recurring -> RecurringScreen(vm = vm, onDone = { overlay = Screen.Settings })
        Screen.CategoryManage -> CategoryManageScreen(vm = vm, onDone = { overlay = Screen.Settings })
        null -> Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        enabled = !recordSaving,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null,
                            modifier = if (tab == 0) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp)) else Modifier) },
                        label = { Text(if (tab == 0) "✓ 账本" else "账本") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        enabled = !recordSaving,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.PieChart, contentDescription = null,
                            modifier = if (tab == 1) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp)) else Modifier) },
                        label = { Text(if (tab == 1) "✓ 统计" else "统计") }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        enabled = !recordSaving,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null,
                            modifier = if (tab == 2) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp)) else Modifier) },
                        label = { Text(if (tab == 2) "✓ 记一笔" else "记一笔") }
                    )
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it / 4 } + fadeOut())
                    }
                },
                label = "tab",
                modifier = Modifier.padding(padding)
            ) { currentTab ->
                Box(Modifier.fillMaxSize()) {
                    when (currentTab) {
                        0 -> HomeScreen(
                            vm = vm,
                            onRecordClick = { overlay = Screen.Edit(it) },
                            onSettings = { overlay = Screen.Settings }
                        )
                        1 -> StatsScreen(vm)
                        else -> EditScreen(
                            vm = vm,
                            initial = null,
                            onBusyChange = { recordSaving = it },
                            onDone = { tab = 0 }
                        )
                    }
                }
            }
        }
    }

    pendingImportUri?.let { uri ->
        CsvImportDialog(vm = vm, uri = uri, onDismiss = { pendingImportUri = null })
    }
}
