package com.ibis.expense.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import com.ibis.expense.App
import com.ibis.expense.ui.theme.ExpenseTheme
import java.io.File
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class UiLayoutTest {
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var vm: AppViewModel
    private lateinit var decor: View
    private var activityOpen = false
    private var fontScale = 1f
    private var dark = false

    @After
    fun closeActivity() {
        try {
            if (activityOpen) {
                activityOpen = false
                controller.get().viewModelStore.clear()
                controller.pause().stop().destroy()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun nativeUiCoversSaveStatisticsDarkAndDoubleFontScale() {
        File("build/reports/native-ui").mkdirs()
        File("build/reports/native-ui/text-layout.txt").writeText("")
        // Compose caches AndroidUiDispatcher.Main. Keep all scenarios on one Robolectric looper.
        lightDefaultSelectionAndSuccessfulSaveReturnToTheVisibleLedger()
        closeActivity()
        darkDefaultSelectionUsesTheSameAccessibleControls()
        closeActivity()
        doubleFontScaleKeepsPrimaryControlsInsideTheViewportAndTheirTextUncut()
    }

    private fun lightDefaultSelectionAndSuccessfulSaveReturnToTheVisibleLedger() {
        openApp()
        assertDefaultSelection()
        screenshot("light-record")
        val savedCategory = vm.categoriesState.value.first().name
        vm.previousMonth()
        vm.setSearchQuery("旧搜索不会包含新记录")
        val fields = nodes().filter { it.config.getOrNull(SemanticsActions.SetText)?.action != null }
        assertEquals("记账表单应有金额和备注两个输入框", 2, fields.size)
        assertTrue(fields.first().config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("28.00")))
        assertTrue(fields.last().config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("原生UI保存验收")))
        await("金额填写后保存按钮应可用") {
            clickNode("保存并返回账本")?.config?.getOrNull(SemanticsProperties.Disabled) == null &&
                clickNode("保存并返回账本") != null
        }
        assertTrue(clickNode("保存并返回账本")!!.config[SemanticsActions.OnClick].action!!.invoke())
        await("保存完成后应切换到账本并显示新流水") {
            nodes().any { "原生UI保存验收" in it.text() && it.boundsInRoot.height > 0 } &&
                clickNode("✓ 账本")?.config?.getOrNull(SemanticsProperties.Selected) == true
        }
        assertEquals("", vm.searchQuery.value)
        screenshot("light-ledger-saved")
        assertTrue(clickNode("统计")!!.config[SemanticsActions.OnClick].action!!.invoke())
        await("统计应展示保存的分类、金额及6/12个月控件") {
            val content = nodes().map { it.text() }
            content.any { savedCategory in it } && content.any { "¥28.00" in it } &&
                clickNode("✓ 6个月") != null && clickNode("12个月") != null
        }
        assertEquals(true, clickNode("✓ 统计")!!.config.getOrNull(SemanticsProperties.Selected))
        screenshot("light-stats")
    }

    private fun darkDefaultSelectionUsesTheSameAccessibleControls() {
        openApp(darkTheme = true)
        assertDefaultSelection()
        screenshot("dark-record")
        listOf("保存并返回账本", "✓ 微信", "支付宝").forEach(::assertControlLayout)
    }

    private fun doubleFontScaleKeepsPrimaryControlsInsideTheViewportAndTheirTextUncut() {
        openApp(scale = 2f)
        assertDefaultSelection()
        screenshot("font-200-record")
        listOf("保存并返回账本", "✓ 微信", "支付宝", "✓ 支出", "收入", "转账", "退款",
            "账本", "统计", "✓ 记一笔").forEach(::assertControlLayout)
    }

    private fun openApp(darkTheme: Boolean = false, scale: Float = 1f) {
        dark = darkTheme
        fontScale = scale
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher())
        RuntimeEnvironment.setFontScale(scale)
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("expenses.db")
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear()
            .putLong("last_backup_day", LocalDate.now().toEpochDay()).commit()
        controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.setup().visible().get()
        activityOpen = true
        vm = AppViewModel(app)
        activity.viewModelStore.put("native-ui", vm)
        activity.setContent { ExpenseTheme(darkTheme = darkTheme) { App(vm) } }
        decor = activity.window.decorView
        await("实际Compose记账页面应完成初次布局") {
            clickNode("✓ 微信") != null && vm.categoriesState.value.isNotEmpty()
        }
        repeat(24) { frame() }
    }

    private fun assertDefaultSelection() {
        assertEquals(true, clickNode("✓ 记一笔")!!.config.getOrNull(SemanticsProperties.Selected))
        assertEquals(true, clickNode("✓ 微信")!!.config.getOrNull(SemanticsProperties.Selected))
        assertEquals(false, clickNode("支付宝")!!.config.getOrNull(SemanticsProperties.Selected))
    }

    private fun frame() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 360, 800)
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (System.nanoTime() < deadline) {
            frame()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("$message\n" + nodes().joinToString("\n") { "${it.id}: ${it.text()} ${it.boundsInRoot}" })
    }

    private fun composeView(): View? = descendants(decor).firstOrNull {
        it.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView"
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private fun nodes(merged: Boolean = true): List<SemanticsNode> {
        val view = composeView() ?: return emptyList()
        // AndroidComposeView is internal; only this test reflects its public owner getter.
        val getter = view.javaClass.methods.first { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
        val owner = getter.invoke(view) as SemanticsOwner
        return owner.getAllSemanticsNodes(mergingEnabled = merged)
    }

    private fun SemanticsNode.text(): String = config.getOrNull(SemanticsProperties.Text)
        .orEmpty().joinToString(" | ") { it.text }

    private fun clickNode(label: String): SemanticsNode? = nodes().firstOrNull {
        it.config.getOrNull(SemanticsActions.OnClick)?.action != null &&
            it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label }
    }

    private fun assertControlLayout(label: String) {
        var control = clickNode(label) ?: throw AssertionError("缺少控件：$label")
        if (control.boundsInRoot.height + 1 < control.size.height) {
            val raw = nodes(false).first { it.id == control.id }
            val scroller = generateSequence(raw.parent) { it.parent }.firstOrNull {
                it.config.getOrNull(SemanticsActions.ScrollBy)?.action != null &&
                    it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
            } ?: throw AssertionError("控件被裁切且无法滚动到：$label")
            val viewport = scroller.boundsInRoot
            val delta = control.positionInRoot.y - viewport.top - (viewport.height - control.size.height) / 2
            assertTrue(scroller.config[SemanticsActions.ScrollBy].action!!.invoke(0f, delta))
            repeat(40) { frame() }
            control = clickNode(label)!!
        }
        val view = composeView()!!
        assertTrue("$label 横向越界", control.positionInRoot.x >= -1 &&
            control.positionInRoot.x + control.size.width <= view.width + 1)
        assertTrue("$label 纵向越界", control.positionInRoot.y >= -1 &&
            control.positionInRoot.y + control.size.height <= view.height + 1)
        assertTrue("$label 仍被裁切", control.boundsInRoot.height + 1 >= control.size.height &&
            control.boundsInRoot.width + 1 >= control.size.width)
        val raw = nodes(false).first { it.id == control.id }
        val textNodes = semanticDescendants(raw).filter {
            it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action != null
        }.toList()
        assertTrue("$label 必须取得真实文字布局", textNodes.isNotEmpty())
        textNodes.forEach { textNode ->
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(textNode.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
            assertTrue("$label 缺少文字布局结果", layouts.isNotEmpty())
            layouts.forEach { result ->
                val lineEdges = (0 until result.lineCount).joinToString { index ->
                    "$index:${result.getLineLeft(index)}..${result.getLineRight(index)}"
                }
                val evidence = "dark=$dark fontScale=$fontScale $label text=${result.layoutInput.text}; " +
                    "node=${textNode.size}; size=${result.size}; paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}; " +
                    "overflow=${result.hasVisualOverflow}; lines=${result.lineCount}/${result.layoutInput.maxLines}; " +
                    "constraints=${result.layoutInput.constraints}; edges=[$lineEdges]"
                println(evidence)
                File("build/reports/native-ui/text-layout.txt").appendText("$evidence\n")
                // MultiParagraph.width is its max-width constraint; painted line edges prove actual width.
                assertTrue("$label 文字排版高度溢出：$evidence",
                    result.multiParagraph.height <= result.size.height + 1)
                assertEquals("$label 文字不可被截断：$evidence", result.layoutInput.text.length,
                    result.getLineEnd(result.lineCount - 1))
                for (index in 0 until result.lineCount) {
                    assertFalse("$label 文字不可被省略：$evidence", result.isLineEllipsized(index))
                    val left = result.getLineLeft(index)
                    val right = result.getLineRight(index)
                    assertTrue("$label 行宽越界：$evidence", left >= -1 && right <= result.size.width + 1)
                    assertTrue("$label 文字被父布局横向裁切：$evidence",
                        textNode.positionInRoot.x + left >= textNode.boundsInRoot.left - 1 &&
                            textNode.positionInRoot.x + right <= textNode.boundsInRoot.right + 1)
                }
                assertTrue("$label 文字被父布局纵向裁切：$evidence",
                    textNode.boundsInRoot.height + 1 >= result.multiParagraph.height)
            }
        }
    }

    private fun semanticDescendants(node: SemanticsNode): Sequence<SemanticsNode> = sequence {
        yield(node)
        node.children.forEach { yieldAll(semanticDescendants(it)) }
    }

    private fun screenshot(name: String) {
        repeat(24) { frame() }
        val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        val samples = mutableSetOf<Int>()
        for (y in 0 until 800 step 4) for (x in 0 until 360 step 4) samples += bitmap.getPixel(x, y)
        assertTrue("原生画布不能输出空白截图", samples.size > 20)
        val directory = File("build/reports/native-ui").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        File(directory, "$name.txt").writeText(
            "Robolectric 4.13; Android SDK 34; native graphics; 360x800 mdpi; " +
                "darkTheme=$dark; fontScale=$fontScale\n" +
                nodes().joinToString("\n") { "${it.id}: ${it.text()} ${it.boundsInRoot}" })
        bitmap.recycle()
    }
}
