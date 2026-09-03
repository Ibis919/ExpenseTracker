package com.ibis.expense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ibis.expense.ui.AppViewModel
import com.ibis.expense.ui.EditScreen
import com.ibis.expense.ui.HomeScreen
import com.ibis.expense.ui.SettingsScreen
import com.ibis.expense.ui.StatsScreen
import com.ibis.expense.ui.UiRecord
import com.ibis.expense.ui.theme.ExpenseTheme

sealed interface Screen {
    data class Edit(val record: UiRecord) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseTheme {
                RequestNotificationPermission()
                App()
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
fun App(vm: AppViewModel = viewModel()) {
    var tab by remember { mutableStateOf(0) }
    var overlay by remember { mutableStateOf<Screen?>(null) }
    when (val current = overlay) {
        is Screen.Edit -> EditScreen(
            vm = vm,
            initial = current.record,
            onDone = { overlay = null }
        )
        Screen.Settings -> SettingsScreen(
            vm = vm,
            onDone = { overlay = null }
        )
        null -> Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("明细") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.PieChart, contentDescription = null) },
                        label = { Text("统计") }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        label = { Text("记一笔") }
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
                            onDone = { tab = 0 }
                        )
                    }
                }
            }
        }
    }
}
