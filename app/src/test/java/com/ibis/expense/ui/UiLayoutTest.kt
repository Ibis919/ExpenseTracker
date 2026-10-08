package com.ibis.expense.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.app.Dialog
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
import androidx.compose.ui.text.style.TextAlign
import com.ibis.expense.App
import com.ibis.expense.data.ExpenseRecord
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
import org.robolectric.shadows.ShadowDialog
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
        calculatorPreviewApplyCancelAndSaveInLightTheme()
        closeActivity()
        darkDefaultSelectionUsesTheSameAccessibleControls()
        closeActivity()
        calculatorErrorUsesTextAndAccessibleControlsInDarkTheme()
        closeActivity()
        doubleFontScaleKeepsPrimaryControlsInsideTheViewportAndTheirTextUncut()
        closeActivity()
        calculatorFitsAndRemainsOperableAtDoubleFontScale()
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

    private fun calculatorPreviewApplyCancelAndSaveInLightTheme() {
        openApp()
        val recordsBefore = vm.allRecords.value.toList()
        val amount = amountField()
        assertTrue(amount.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("19.3")))
        repeat(8) { frame() }
        assertEquals("19.3", amountValue())
        assertEquals("打开前已有流水不应变化", recordsBefore, vm.allRecords.value.toList())

        val entry = calculatorEntry()
        val fieldBounds = amount.boundsInRoot
        assertTrue("计算器入口必须位于金额栏右侧", entry.positionInRoot.x > fieldBounds.left + fieldBounds.width / 2f)
        assertTrue("计算器入口必须在屏幕内", entry.positionInRoot.x + entry.size.width <= composeView()!!.width + 1)
        assertTrue(entry.config[SemanticsActions.OnClick].action!!.invoke())
        awaitDialog("计算器应在独立 Dialog 窗口中打开，且带入当前金额") {
            dialogNodeByTag("calculator-expression")?.text() == "19.3"
        }
        assertTrue("计算器必须使用独立原生 Dialog 窗口", dialogDecor() !== decor)
        assertEquals("19.3", amountValue())
        assertEquals("打开计算器不能写入流水", recordsBefore, vm.allRecords.value.toList())
        screenshot("calculator-light", dialogDecor())
        assertCalculatorLayout()
        assertSemanticTextFits("calculator-expression")

        listOf("+", "2", ".", "3", "6", "填入").forEach {
            assertTrue("无等号快速填入时按键 $it 应可操作", tapCalculator(it))
        }
        await("快速填入应关闭计算器") { !isCalculatorShowing() }
        assertEquals("无等号快速填入应基于完整的 19.3+2.36 更新表单", "21.66", amountValue())
        assertEquals("快速填入不能保存流水", recordsBefore, vm.allRecords.value.toList())
        val amountAfterDirectFill = amountField()
        assertTrue(amountAfterDirectFill.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("19.3")))
        repeat(8) { frame() }
        assertTrue(calculatorEntry().config[SemanticsActions.OnClick].action!!.invoke())
        awaitDialog("重置金额后普通等号流程仍应带入 19.3") {
            dialogNodeByTag("calculator-expression")?.text() == "19.3"
        }

        listOf("+", "2", ".", "3", "6", "＝").forEach { assertTrue("按键 $it 应可操作", tapCalculator(it)) }
        awaitDialog("等号应显示 21.66") { dialogNodeByTag("calculator-result")?.text()?.contains("21.66") == true }
        assertEquals("19.3", amountValue())
        assertEquals("等号不能保存流水", recordsBefore, vm.allRecords.value.toList())
        assertSemanticTextFits("calculator-result")
        screenshot("calculator-result", dialogDecor())
        assertTrue(tapCalculator("填入"))
        await("填入应关闭 Dialog 并更新表单金额") {
            !isCalculatorShowing() && amountValue() == "21.66"
        }
        assertEquals("填入不能保存流水", recordsBefore, vm.allRecords.value.toList())

        assertTrue(calculatorEntry().config[SemanticsActions.OnClick].action!!.invoke())
        awaitDialog("重新打开时应带入表单当前金额") {
            dialogNodeByTag("calculator-expression")?.text() == "21.66"
        }
        assertTrue(tapCalculator("清空"))
        assertTrue(tapCalculator("9"))
        assertTrue(tapCalculator("关闭"))
        await("关闭应取消计算器并保留已有金额") {
            !isCalculatorShowing() && amountValue() == "21.66"
        }
        assertEquals("关闭取消不能保存流水", recordsBefore, vm.allRecords.value.toList())
        assertTrue(calculatorEntry().config[SemanticsActions.OnClick].action!!.invoke())
        awaitDialog("取消后重开仍应从旧表单金额开始") {
            dialogNodeByTag("calculator-expression")?.text() == "21.66"
        }
        assertTrue(tapCalculator("关闭"))
        await("第二次关闭仍应保留旧金额") { !isCalculatorShowing() && amountValue() == "21.66" }

        val note = nodes().filter { it.config.getOrNull(SemanticsActions.SetText)?.action != null }.last()
        assertTrue(note.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("计算器金额原生UI验收")))
        assertTrue(clickNode("保存并返回账本")!!.config[SemanticsActions.OnClick].action!!.invoke())
        await("用户显式保存后数据库和账本应显示 21.66") {
            vm.allRecords.value.size == recordsBefore.size + 1 &&
                vm.allRecords.value.any { it.amountCents == 2166L } &&
                nodes().any { "计算器金额原生UI验收" in it.text() } &&
                nodes().any { "¥21.66" in it.text() }
        }
        screenshot("calculator-light-saved")
    }

    private fun calculatorErrorUsesTextAndAccessibleControlsInDarkTheme() {
        openApp(darkTheme = true)
        val recordsBefore = vm.allRecords.value.toList()
        val amount = amountField()
        assertTrue(amount.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("42.50")))
        repeat(8) { frame() }
        assertTrue(calculatorEntry().config[SemanticsActions.OnClick].action!!.invoke())
        awaitDialog("深色主题计算器应打开") { dialogNodeByTag("calculator-expression") != null }
        assertCalculatorLayout()
        assertTrue(tapCalculator("清空"))
        listOf("1", "0", "÷", "0", "＝").forEach { assertTrue("按键 $it 应可操作", tapCalculator(it)) }
        awaitDialog("除零错误应显示可读文字并保留算式") {
            dialogNodeByTag("calculator-error")?.text() == "除数不能为0" &&
                dialogNodeByTag("calculator-expression")?.text() == "10÷0"
        }
        assertTrue("除零时填入必须禁用", dialogNodeForText("填入")?.config?.getOrNull(SemanticsProperties.Disabled) != null)
        assertEquals("42.50", amountValue())
        assertEquals("除零预览不能保存流水", recordsBefore, vm.allRecords.value.toList())
        assertSemanticTextFits("calculator-expression")
        assertSemanticTextFits("calculator-error")
        screenshot("calculator-dark", dialogDecor())

        listOf("清空", "1", "2", "+", "＝").forEach { assertTrue("按键 $it 应可操作", tapCalculator(it)) }
        awaitDialog("未完成表达式应保留输入并显示文字错误") {
            dialogNodeByTag("calculator-error")?.text() == "算式不完整或格式错误" &&
                dialogNodeByTag("calculator-expression")?.text() == "12+"
        }
        assertTrue("未完成算式时填入必须禁用",
            dialogNodeForText("填入")?.config?.getOrNull(SemanticsProperties.Disabled) != null)
        assertEquals("42.50", amountValue())

        listOf("清空", "1", "÷", "3", "＝").forEach { assertTrue("按键 $it 应可操作", tapCalculator(it)) }
        awaitDialog("1÷3 应显示两位小数") {
            dialogNodeByTag("calculator-result")?.text()?.contains("0.33") == true
        }
        assertTrue(tapCalculator("×"))
        assertTrue(tapCalculator("3"))
        assertTrue(tapCalculator("＝"))
        awaitDialog("等号后续算必须从显示的 0.33 开始，结果为 0.99") {
            dialogNodeByTag("calculator-expression")?.text() == "0.33×3" &&
                dialogNodeByTag("calculator-result")?.text()?.contains("0.99") == true
        }
        assertEquals("42.50", amountValue())
        assertEquals("续算预览不能保存流水", recordsBefore, vm.allRecords.value.toList())
        assertCalculatorStateSurvivesActivityRecreation(recordsBefore)
    }

    private fun assertCalculatorStateSurvivesActivityRecreation(recordsBefore: List<ExpenseRecord>) {
        val previousActivity = controller.get()
        controller = controller.recreate()
        val recreatedActivity = controller.get()
        assertTrue("Robolectric recreate 应创建新的 ComponentActivity", recreatedActivity !== previousActivity)
        renderApp(recreatedActivity, darkTheme = true)
        decor = recreatedActivity.window.decorView
        await("重建后 Compose 表单应恢复") {
            clickNode("✓ 微信") != null && vm.categoriesState.value.isNotEmpty()
        }
        awaitDialog("重建后计算器 Dialog、算式和结果应恢复") {
            dialogNodeByTag("calculator-expression")?.text() == "0.33×3" &&
                dialogNodeByTag("calculator-result")?.text()?.contains("0.99") == true
        }
        assertEquals("表单金额在旋转重建后仍应保留", "42.50", amountValue())
        assertEquals("旋转重建不能保存流水", recordsBefore, vm.allRecords.value.toList())
        assertTrue(tapCalculator("+"))
        assertTrue(tapCalculator("1"))
        assertTrue(tapCalculator("＝"))
        awaitDialog("恢复后的计算器仍应允许继续运算") {
            dialogNodeByTag("calculator-expression")?.text() == "0.99+1" &&
                dialogNodeByTag("calculator-result")?.text()?.contains("1.99") == true
        }
        assertTrue(tapCalculator("关闭"))
        await("恢复后的 Dialog 应可关闭且不改写表单") {
            !isCalculatorShowing() && amountValue() == "42.50"
        }
    }

    private fun calculatorFitsAndRemainsOperableAtDoubleFontScale() {
        openApp(scale = 2f)
        val recordsBefore = vm.allRecords.value.toList()
        val amount = amountField()
        assertTrue(amount.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("12.34")))
        repeat(8) { frame() }
        assertTrue(calculatorEntry().config[SemanticsActions.OnClick].action!!.invoke())
        awaitDialog("200% 字号计算器应打开") { dialogNodeByTag("calculator-expression") != null }
        assertSemanticTextFits("calculator-expression")
        screenshot("calculator-font-200", dialogDecor())
        assertCalculatorLayout()
        assertControlLayout("1", dialogDecor())
        assertTrue("200% 字号下按键仍应可操作", tapCalculator("1"))
        awaitDialog("大字号下按键后表达式应更新") {
            dialogNodeByTag("calculator-expression")?.text() == "12.341"
        }
        assertEquals("大字号下操作不能保存流水", recordsBefore, vm.allRecords.value.toList())
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
        renderApp(activity, darkTheme)
        decor = activity.window.decorView
        await("实际Compose记账页面应完成初次布局") {
            clickNode("✓ 微信") != null && vm.categoriesState.value.isNotEmpty()
        }
        repeat(24) { frame() }
    }

    private fun renderApp(activity: ComponentActivity, darkTheme: Boolean = dark) {
        activity.setContent { ExpenseTheme(darkTheme = darkTheme) { App(vm) } }
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
        ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let { dialogDecor ->
            dialogDecor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST))
            dialogDecor.layout(0, 0, dialogDecor.measuredWidth, dialogDecor.measuredHeight)
        }
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

    private fun dialog(): Dialog? = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }

    private fun isCalculatorShowing(): Boolean = dialog()?.window?.decorView?.let(::composeView) != null

    private fun dialogDecor(): View = dialog()?.window?.decorView
        ?: throw AssertionError("计算器 Dialog 窗口未显示")

    private fun composeView(root: View = decor): View? = descendants(root).firstOrNull {
        it.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView"
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private fun nodes(merged: Boolean = true, root: View = decor): List<SemanticsNode> {
        val view = composeView(root) ?: return emptyList()
        // AndroidComposeView is internal; only this test reflects its public owner getter.
        val getter = view.javaClass.methods.first { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
        val owner = getter.invoke(view) as SemanticsOwner
        return owner.getAllSemanticsNodes(mergingEnabled = merged)
    }

    private fun SemanticsNode.text(): String = config.getOrNull(SemanticsProperties.Text)
        .orEmpty().joinToString(" | ") { it.text }

    private fun clickNode(label: String, root: View = decor): SemanticsNode? = nodes(root = root).firstOrNull {
        it.config.getOrNull(SemanticsActions.OnClick)?.action != null &&
            it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label }
    }

    private fun textNode(label: String, root: View = decor): SemanticsNode? = nodes(root = root).firstOrNull {
        it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label }
    }

    private fun amountField(): SemanticsNode = nodes().first {
        it.config.getOrNull(SemanticsActions.SetText)?.action != null
    }

    private fun amountValue(): String = amountField().config[SemanticsProperties.EditableText].text

    private fun calculatorEntry(): SemanticsNode = nodes().first {
        it.config.getOrNull(SemanticsActions.OnClick)?.action != null &&
            it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains("打开计算器")
    }

    private fun dialogNodeByTag(tag: String): SemanticsNode? = nodes(root = dialogDecor()).firstOrNull {
        it.config.getOrNull(SemanticsProperties.TestTag) == tag
    }

    private fun dialogNodeForText(label: String): SemanticsNode? = textNode(label, dialogDecor())

    private fun tapCalculator(label: String): Boolean = clickNode(label, dialogDecor())
        ?.config?.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true

    private fun awaitDialog(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (System.nanoTime() < deadline) {
            frame()
            if (isCalculatorShowing() && condition()) return
            Thread.sleep(10)
        }
        val current = if (isCalculatorShowing()) nodes(root = dialogDecor()) else emptyList()
        val dialogStates = ShadowDialog.getShownDialogs().joinToString { "showing=${it.isShowing} type=${it.javaClass.name}" }
        throw AssertionError("$message\nDialogs=[$dialogStates]\nActivity=" +
            nodes().joinToString("\n") { "${it.id}: ${it.text()} ${it.boundsInRoot}" } + "\nDialog=" +
            current.joinToString("\n") { "${it.id}: ${it.text()} ${it.boundsInRoot}" })
    }

    private fun assertCalculatorLayout() {
        val root = dialogDecor()
        val view = composeView(root) ?: throw AssertionError("Dialog 中缺少原生 Compose 视图")
        assertTrue("计算器标题缺失", nodes(root = root).any { it.text() == "计算金额" })
        val equals = clickNode("＝", root) ?: throw AssertionError("缺少等号按键")
        val apply = clickNode("填入", root) ?: throw AssertionError("缺少填入按键")
        val equalsCenter = equals.positionInRoot.x + equals.size.width / 2f
        val applyCenter = apply.positionInRoot.x + apply.size.width / 2f
        assertTrue("等号必须与填入按键同列", kotlin.math.abs(equalsCenter - applyCenter) <= 1f)
        assertTrue("等号必须位于填入按键上方", equals.positionInRoot.y + equals.size.height <= apply.positionInRoot.y + 1f)
        val labels = listOf("清空", "退格", "÷", "×", "7", "8", "9", "−", "4", "5", "6", "+",
            "1", "2", "3", "＝", "关闭", "0", ".", "填入")
        labels.forEach { label ->
            val control = clickNode(label, root) ?: throw AssertionError("Dialog 缺少按键：$label")
            assertTrue("$label 触点小于 48dp: ${control.size}", control.size.width >= 48 && control.size.height >= 48)
            assertControlLayout(label, root)
        }
        assertTrue("Dialog Compose 视图横向越界", view.width <= 360)
    }

    private fun assertSemanticTextFits(tag: String) {
        val root = dialogDecor()
        var node = dialogNodeByTag(tag) ?: throw AssertionError("Dialog 缺少语义标签：$tag")
        val view = composeView(root)!!
        if (node.boundsInRoot.height + 1 < node.size.height || node.positionInRoot.y < -1 ||
            node.positionInRoot.y + node.size.height > view.height + 1) {
            val raw = nodes(false, root).first { it.id == node.id }
            val scroller = generateSequence(raw.parent) { it.parent }.firstOrNull {
                it.config.getOrNull(SemanticsActions.ScrollBy)?.action != null &&
                    it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
            } ?: throw AssertionError("$tag 被裁切且无法滚动到")
            val viewport = scroller.boundsInRoot
            val delta = node.positionInRoot.y - viewport.top - (viewport.height - node.size.height) / 2
            assertTrue("无法滚动以显示 $tag", scroller.config[SemanticsActions.ScrollBy].action!!.invoke(0f, delta))
            repeat(40) { frame() }
            node = dialogNodeByTag(tag)!!
        }
        val textNodes = semanticDescendants(node).filter {
            it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action != null
        }.toList()
        assertTrue("$tag 缺少真实 TextLayoutResult", textNodes.isNotEmpty())
        textNodes.forEach { textNode ->
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(textNode.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
            assertTrue("$tag 文字布局结果为空", layouts.isNotEmpty())
            layouts.forEach { result ->
                val alignOffset = result.paragraphOffsetX()
                val lineEdges = (0 until result.lineCount).joinToString { index ->
                    "$index:${result.getLineLeft(index) + alignOffset}..${result.getLineRight(index) + alignOffset}"
                }
                val evidence = "$tag text=${result.layoutInput.text}; node=${textNode.size}; size=${result.size}; " +
                    "paragraph=${result.multiParagraph.width}; alignOffset=$alignOffset; " +
                    "overflow=${result.hasVisualOverflow}; lines=${result.lineCount}; edges=[$lineEdges]"
                assertTrue("$evidence", result.multiParagraph.height <= result.size.height + 1)
                assertEquals("$evidence", result.layoutInput.text.length,
                    result.getLineEnd(result.lineCount - 1))
                for (index in 0 until result.lineCount) {
                    assertFalse("$evidence", result.isLineEllipsized(index))
                    val left = result.getLineLeft(index) + alignOffset
                    val right = result.getLineRight(index) + alignOffset
                    assertTrue("$evidence", left >= -1 && right <= result.size.width + 1)
                    assertTrue("$evidence", textNode.positionInRoot.x + left >= textNode.boundsInRoot.left - 1 &&
                        textNode.positionInRoot.x + right <= textNode.boundsInRoot.right + 1)
                    assertTrue("$evidence", right - left <= textNode.size.width + 1)
                }
                assertTrue("$evidence", textNode.boundsInRoot.height + 1 >= result.multiParagraph.height)
            }
        }
        assertTrue("$tag 文字节点被 Dialog viewport 裁切", node.positionInRoot.y >= -1 &&
            node.positionInRoot.y + node.size.height <= view.height + 1)
    }

    private fun assertControlLayout(label: String, root: View = decor) {
        var control = clickNode(label, root) ?: throw AssertionError("缺少控件：$label")
        if (control.boundsInRoot.height + 1 < control.size.height) {
            val raw = nodes(false, root).first { it.id == control.id }
            val scroller = generateSequence(raw.parent) { it.parent }.firstOrNull {
                it.config.getOrNull(SemanticsActions.ScrollBy)?.action != null &&
                    it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
            } ?: throw AssertionError("控件被裁切且无法滚动到：$label")
            val viewport = scroller.boundsInRoot
            val delta = control.positionInRoot.y - viewport.top - (viewport.height - control.size.height) / 2
            assertTrue(scroller.config[SemanticsActions.ScrollBy].action!!.invoke(0f, delta))
            repeat(40) { frame() }
            control = clickNode(label, root)!!
        }
        val view = composeView(root)!!
        assertTrue("$label 横向越界", control.positionInRoot.x >= -1 &&
            control.positionInRoot.x + control.size.width <= view.width + 1)
        assertTrue("$label 纵向越界", control.positionInRoot.y >= -1 &&
            control.positionInRoot.y + control.size.height <= view.height + 1)
        assertTrue("$label 仍被裁切", control.boundsInRoot.height + 1 >= control.size.height &&
            control.boundsInRoot.width + 1 >= control.size.width)
        val raw = nodes(false, root).first { it.id == control.id }
        val textNodes = semanticDescendants(raw).filter {
            it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action != null
        }.toList()
        assertTrue("$label 必须取得真实文字布局", textNodes.isNotEmpty())
        textNodes.forEach { textNode ->
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(textNode.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
            assertTrue("$label 缺少文字布局结果", layouts.isNotEmpty())
            layouts.forEach { result ->
                val alignOffset = result.paragraphOffsetX()
                val lineEdges = (0 until result.lineCount).joinToString { index ->
                    "$index:${result.getLineLeft(index) + alignOffset}..${result.getLineRight(index) + alignOffset}"
                }
                val evidence = "dark=$dark fontScale=$fontScale $label text=${result.layoutInput.text}; " +
                    "node=${textNode.size}; size=${result.size}; paragraph=${result.multiParagraph.width}x${result.multiParagraph.height}; " +
                    "alignOffset=$alignOffset; " +
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
                    val left = result.getLineLeft(index) + alignOffset
                    val right = result.getLineRight(index) + alignOffset
                    assertTrue("$label 行宽越界：$evidence", left >= -1 && right <= result.size.width + 1)
                    assertTrue("$label 文字被父布局横向裁切：$evidence",
                        textNode.positionInRoot.x + left >= textNode.boundsInRoot.left - 1 &&
                            textNode.positionInRoot.x + right <= textNode.boundsInRoot.right + 1)
                    assertTrue("$label 绘制文字超出触点：$evidence",
                        textNode.positionInRoot.x + left >= control.boundsInRoot.left - 1 &&
                            textNode.positionInRoot.x + right <= control.boundsInRoot.right + 1)
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

    private fun TextLayoutResult.paragraphOffsetX(): Float =
        if (layoutInput.style.textAlign == TextAlign.Center) {
            (size.width - multiParagraph.width) / 2f
        } else {
            0f
        }

    private fun screenshot(name: String, root: View = decor) {
        repeat(24) { frame() }
        val width = root.width.takeIf { it > 0 } ?: 360
        val height = root.height.takeIf { it > 0 } ?: 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val samples = mutableSetOf<Int>()
        for (y in 0 until height step 4) for (x in 0 until width step 4) samples += bitmap.getPixel(x, y)
        assertTrue("原生画布不能输出空白截图", samples.size > 20)
        val directory = File("build/reports/native-ui").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        File(directory, "$name.txt").writeText(
            "Robolectric 4.13; Android SDK 34; native graphics; ${width}x${height} mdpi; " +
                "darkTheme=$dark; fontScale=$fontScale\n" +
                nodes(root = root).joinToString("\n") { "${it.id}: ${it.text()} ${it.boundsInRoot}" })
        bitmap.recycle()
    }
}
