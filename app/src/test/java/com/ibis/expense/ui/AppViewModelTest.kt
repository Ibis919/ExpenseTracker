package com.ibis.expense.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import com.ibis.expense.data.ExpenseDatabase
import com.ibis.expense.data.ExpenseRecord
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppViewModelTest {
    private lateinit var app: Application
    private lateinit var db: ExpenseDatabase
    private lateinit var vm: AppViewModel
    private val store = ViewModelStore()
    private val day = LocalDate.now().toEpochDay()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putLong("last_backup_day", LocalDate.now().toEpochDay()).commit()
        db = ExpenseDatabase.build(app)
        vm = AppViewModel(app)
        store.put("test", vm)
    }

    @After
    fun tearDown() {
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun editingRecurringRecordKeepsItsLinkAndCreationTime() = runBlocking {
        db.dao().insert(record().copy(recurringId = 42))
        val original = db.dao().getAllOnce().single()
        store.clear()
        vm = AppViewModel(app)
        store.put("test", vm)
        val displayed = withTimeout(5_000) {
            vm.homeState.first { state ->
                state?.days?.any { group -> group.records.any { it.id == original.id } } == true
            }!!.days.flatMap { it.records }.single()
        }

        vm.updateRecord(displayed, 2500, day, "交通", "修改后的备注", excluded = true)
        val updated = withTimeout(5_000) {
            var current: ExpenseRecord
            do {
                current = db.dao().getAllOnce().single()
                if (current.amountCents != 2500L) delay(10)
            } while (current.amountCents != 2500L)
            current
        }

        assertEquals(42L, updated.recurringId)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals("交通", updated.category)
        assertEquals("修改后的备注", updated.note)
        assertTrue(updated.excluded)
        assertEquals(1, db.dao().countRecurringInstance(42, day, day))
    }

    @Test
    fun csvRoundTripPreservesSpecialCharactersAndExcludedFlag() = runBlocking {
        val original = record().copy(
            category = "餐饮,\"聚餐\"",
            note = "第一行,\"AA\"\r\n第二行\n第三行",
            excluded = true
        )
        db.dao().insert(original)
        val csv = vm.exportCsvForShare()!!

        val outcome = vm.importCsv(Uri.fromFile(csv), replace = true).getOrThrow()
        val imported = db.dao().getAllOnce().single()

        assertEquals(AppViewModel.ImportOutcome(1, 0), outcome)
        assertEquals(original.amountCents, imported.amountCents)
        assertEquals(original.epochDay, imported.epochDay)
        assertEquals(original.category, imported.category)
        assertEquals(original.note, imported.note)
        assertTrue(imported.excluded)
    }

    @Test
    fun importsLegacyFourColumnCsvAndKeepsExistingRecordsWhenMerging() = runBlocking {
        db.dao().insert(record())
        val csv = csvFile("\uFEFF日期,金额,分类,备注\r\n2026-09-21,0.10,餐饮,\"早餐,咖啡\"\r\n")

        val outcome = vm.importCsv(csv, replace = false).getOrThrow()
        val records = db.dao().getAllOnce()

        assertEquals(AppViewModel.ImportOutcome(1, 0), outcome)
        assertEquals(2, records.size)
        val imported = records.single { it.note == "早餐,咖啡" }
        assertEquals(10L, imported.amountCents)
        assertEquals(false, imported.excluded)
    }

    @Test
    fun skipsImpossibleDatesInsteadOfChangingTheRecordedDay() = runBlocking {
        val csv = csvFile("日期,金额,分类,备注\n2026-02-30,12.34,餐饮,无效日期\n2026-02-28,5,餐饮,有效日期")

        val outcome = vm.importCsv(csv, replace = false).getOrThrow()

        assertEquals(AppViewModel.ImportOutcome(1, 1), outcome)
        assertEquals("有效日期", db.dao().getAllOnce().single().note)
    }

    @Test
    fun malformedQuotedCsvDoesNotReplaceExistingRecords() = runBlocking {
        db.dao().insert(record())
        val original = db.dao().getAllOnce()
        val csv = csvFile("日期,金额,分类,备注\n2026-09-22,12.34,餐饮,\"未闭合备注")

        val result = vm.importCsv(csv, replace = true)

        assertTrue(result.isFailure)
        assertEquals(original, db.dao().getAllOnce())
    }

    @Test
    fun failedReplacementRollsBackRecordsAndNewCategories() = runBlocking {
        db.dao().insert(record())
        val original = db.dao().getAllOnce()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_import BEFORE INSERT ON expenses " +
                "WHEN NEW.note = 'reject' BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        val csv = csvFile("日期,金额,分类,备注\n2026-09-22,12.34,导入专用,reject")

        val result = vm.importCsv(csv, replace = true)

        assertTrue(result.isFailure)
        assertEquals(original, db.dao().getAllOnce())
        assertEquals(0, db.dao().countCategory("导入专用"))
    }

    private fun record() = ExpenseRecord(
        amountCents = 1234,
        epochDay = day,
        createdAt = 1_000,
        category = "餐饮",
        note = "原始记录"
    )

    private fun csvFile(text: String): Uri {
        val file = File(app.cacheDir, "import-test.csv")
        file.writeText(text, Charsets.UTF_8)
        return Uri.fromFile(file)
    }
}
