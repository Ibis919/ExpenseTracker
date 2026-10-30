@file:OptIn(ExperimentalMaterial3Api::class)

package com.ibis.expense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ibis.expense.data.Category
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun CategoryReorderScreen(
    categories: List<Category>,
    onSave: suspend (List<String>) -> Result<Unit>,
    onDone: () -> Unit
) {
    val ordered = remember { categories.toMutableStateList() }
    val originalNames = remember { categories.map { it.name } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var draggedName by remember { mutableStateOf<String?>(null) }
    var dragStartTop by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var dragHeight by remember { mutableFloatStateOf(0f) }
    var edgeScroll by remember { mutableFloatStateOf(0f) }
    var beforeDrag by remember { mutableStateOf<List<Category>>(emptyList()) }

    fun move(name: String, target: Int): Boolean {
        val from = ordered.indexOfFirst { it.name == name }
        if (saving || from < 0 || target !in ordered.indices || from == target) return false
        ordered.add(target, ordered.removeAt(from))
        return true
    }

    fun moveBy(name: String, amount: Int): Boolean {
        val from = ordered.indexOfFirst { it.name == name }
        return from >= 0 && move(name, from + amount)
    }

    fun moveUnderPointer() {
        val name = draggedName ?: return
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return
        val center = dragStartTop + dragDistance + dragHeight / 2f
        val target = visible.firstOrNull { center >= it.offset && center < it.offset + it.size }
            ?: if (center < visible.first().offset) visible.first() else visible.last()
        val targetName = target.key as? String ?: return
        move(name, ordered.indexOfFirst { it.name == targetName })
    }

    fun updateEdgeScroll() {
        val layout = listState.layoutInfo
        val edge = with(density) { 48.dp.toPx() }
        val maxStep = with(density) { 12.dp.toPx() }
        val top = dragStartTop + dragDistance
        val bottom = top + dragHeight
        edgeScroll = when {
            top < layout.viewportStartOffset + edge ->
                -((layout.viewportStartOffset + edge - top) / edge).coerceIn(0f, 1f) * maxStep
            bottom > layout.viewportEndOffset - edge ->
                ((bottom - (layout.viewportEndOffset - edge)) / edge).coerceIn(0f, 1f) * maxStep
            else -> 0f
        }
    }

    fun startDrag(name: String) {
        if (saving || draggedName != null) return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == name } ?: return
        beforeDrag = ordered.toList()
        draggedName = name
        dragStartTop = item.offset.toFloat()
        dragDistance = 0f
        dragHeight = item.size.toFloat()
        updateEdgeScroll()
        menuFor = null
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun finishDrag(cancelled: Boolean) {
        if (cancelled && draggedName != null) {
            ordered.clear()
            ordered.addAll(beforeDrag)
        }
        edgeScroll = 0f
        draggedName = null
        dragDistance = 0f
    }

    BackHandler { if (!saving) onDone() }

    LaunchedEffect(draggedName, edgeScroll) {
        val active = draggedName ?: return@LaunchedEffect
        val amount = edgeScroll
        if (amount == 0f) return@LaunchedEffect
        while (isActive && draggedName == active && edgeScroll == amount) {
            if (listState.scrollBy(amount) == 0f) break
            moveUnderPointer()
            delay(16)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类排序") },
                navigationIcon = {
                    IconButton(onClick = onDone, enabled = !saving) {
                        Icon(Icons.Default.Close, contentDescription = "取消排序")
                    }
                },
                actions = {
                    TextButton(
                        enabled = !saving && draggedName == null,
                        onClick = {
                            if (!saving && draggedName == null) {
                                val names = ordered.map { it.name }
                                if (names == originalNames) {
                                    onDone()
                                } else {
                                    saving = true
                                    scope.launch {
                                        try {
                                            onSave(names).onSuccess { onDone() }
                                                .onFailure { error = it.message ?: "请重试。" }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            error = e.message ?: "请重试。"
                                        } finally {
                                            saving = false
                                        }
                                    }
                                }
                            }
                        }
                    ) { Text(if (saving) "保存中…" else "完成") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            Text(
                "长按右侧图标拖动排序，也可点按图标上下移动。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("category-reorder-list"),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(ordered, key = { it.name }) { category ->
                    val index = ordered.indexOfFirst { it.name == category.name }
                    val isDragging = draggedName == category.name
                    var handleFocused by remember { mutableStateOf(false) }
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == category.name }
                    val translation = if (isDragging && item != null) dragStartTop + dragDistance - item.offset else 0f
                    Surface(
                        modifier = Modifier.fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = translation }
                            .semantics { if (isDragging) stateDescription = "正在拖动" }
                            .testTag("category-row-${category.name}"),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(if (isDragging) 12.dp else 0.dp),
                        border = if (isDragging) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                        shadowElevation = if (isDragging) 4.dp else 0.dp
                    ) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${category.emoji} ${category.name}",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Box {
                                    Box(
                                        modifier = Modifier.size(48.dp)
                                            .then(if (handleFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)) else Modifier)
                                            .testTag("category-handle-${category.name}")
                                            .semantics(mergeDescendants = true) {
                                                role = Role.Button
                                                contentDescription = "调整${category.name}顺序"
                                                if (saving) disabled()
                                                onClick(label = "调整顺序") {
                                                    if (saving || draggedName != null) false
                                                    else { menuFor = category.name; true }
                                                }
                                                customActions = buildList {
                                                    if (index > 0) add(CustomAccessibilityAction("上移一位") { moveBy(category.name, -1) })
                                                    if (index < ordered.lastIndex) add(CustomAccessibilityAction("下移一位") { moveBy(category.name, 1) })
                                                }
                                            }
                                            .onKeyEvent { event ->
                                                if (!saving && draggedName == null && event.type == KeyEventType.KeyUp &&
                                                    (event.key == Key.Enter || event.key == Key.NumPadEnter ||
                                                        event.key == Key.DirectionCenter || event.key == Key.Spacebar)) {
                                                    menuFor = category.name
                                                    true
                                                } else false
                                            }
                                            .onFocusChanged { handleFocused = it.isFocused }
                                            .focusable(enabled = !saving)
                                            .pointerInput(category.name) {
                                                awaitEachGesture {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    var finishedBeforeLongPress = false
                                                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                                        waitForUpOrCancellation().also { finishedBeforeLongPress = true }
                                                    }
                                                    if (finishedBeforeLongPress) {
                                                        if (up != null && !saving && draggedName == null) {
                                                            up.consume()
                                                            menuFor = category.name
                                                        }
                                                    } else {
                                                        startDrag(category.name)
                                                        if (draggedName == category.name) {
                                                            try {
                                                                val completed = drag(down.id) { change ->
                                                                    dragDistance += change.positionChange().y
                                                                    change.consume()
                                                                    moveUnderPointer()
                                                                    updateEdgeScroll()
                                                                }
                                                                finishDrag(cancelled = !completed)
                                                            } catch (e: CancellationException) {
                                                                finishDrag(cancelled = true)
                                                                throw e
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Menu, contentDescription = null)
                                    }
                                    DropdownMenu(expanded = menuFor == category.name, onDismissRequest = { menuFor = null }) {
                                        DropdownMenuItem(
                                            text = { Text("上移一位") },
                                            enabled = index > 0 && !saving,
                                            onClick = { moveBy(category.name, -1); menuFor = null }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("下移一位") },
                                            enabled = index < ordered.lastIndex && !saving,
                                            onClick = { moveBy(category.name, 1); menuFor = null }
                                        )
                                    }
                                }
                            }
                            if (index < ordered.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("保存顺序失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("确定") } }
        )
    }
}
