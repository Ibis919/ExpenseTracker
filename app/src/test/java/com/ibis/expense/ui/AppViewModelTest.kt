package com.ibis.expense.ui

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.lifecycle.ViewModelStore
import com.ibis.expense.data.ExpenseDatabase
import com.ibis.expense.data.ExpenseRecord
import com.ibis.expense.data.MIGRATION_4_5
import com.ibis.expense.data.MIGRATION_5_6
import com.ibis.expense.data.MIGRATION_6_7
import com.ibis.expense.data.TransactionType
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
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
import org.robolectric.annotation.SQLiteMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
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
    fun savingFromAnOldMonthAndSearchReturnsToTheRecordedMonth() = runBlocking {
        vm.previousMonth()
        val oldMonth = YearMonth.now().minusMonths(1)
        withTimeout(5_000) { vm.homeState.first { it?.month == oldMonth } }
        vm.setSearchQuery("不存在的备注")

        vm.addRecord(2800, day, "餐饮", "刚保存")
        withTimeout(5_000) {
            while (db.dao().getAllOnce().none { it.note == "刚保存" }) delay(10)
        }
        val state = withTimeout(5_000) {
            vm.homeState.first { it?.month == YearMonth.now() && it.days.flatMap { d -> d.records }.any { r -> r.note == "刚保存" } }!!
        }

        assertEquals(YearMonth.now(), state.month)
        assertEquals("", vm.searchQuery.value)
        assertTrue(state.days.flatMap { it.records }.any { it.note == "刚保存" })
    }

    @Test
    fun failedSaveKeepsTheFormContextAndDoesNotSignalSuccess() = runBlocking {
        vm.setSearchQuery("原来的搜索")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_save BEFORE INSERT ON expenses BEGIN SELECT RAISE(ABORT, 'rejected'); END")

        val result = vm.addRecord(2800, day, "餐饮", "拒绝保存")

        assertTrue(result.isFailure)
        assertTrue(db.dao().getAllOnce().isEmpty())
        assertEquals("原来的搜索", vm.searchQuery.value)
        assertEquals(null, vm.savedRecordId.value)
    }

    @Test
    fun editingVisibleSearchResultKeepsTheConditionsButHiddenEditsRevealTheRecord() = runBlocking {
        val id = vm.addRecord(2800, day, "餐饮", "午饭", paymentMethod = PaymentMethod.ALIPAY).getOrThrow()
        val original = withTimeout(5000) { vm.homeState.first { it?.days?.flatMap { d -> d.records }?.any { r -> r.id == id } == true }!! }
            .days.flatMap { it.records }.single { it.id == id }
        vm.acknowledgeSavedRecord()
        vm.setSearchQuery("午饭")
        vm.setRecordFilter(RecordFilter(paymentMethod = PaymentMethod.ALIPAY))
        vm.updateRecord(original, 2900, day, "餐饮", "午饭加菜").getOrThrow()
        assertEquals("午饭", vm.searchQuery.value)
        assertEquals(PaymentMethod.ALIPAY, vm.recordFilter.value.paymentMethod)
        assertEquals(null, vm.savedRecordId.value)

        val olderDay = YearMonth.now().minusMonths(1).atDay(1).toEpochDay()
        vm.updateRecord(original, 2900, olderDay, "餐饮", "晚饭").getOrThrow()
        assertEquals("", vm.searchQuery.value)
        assertTrue(!vm.recordFilter.value.isActive)
        assertEquals(id, vm.savedRecordId.value)
        val revealed = withTimeout(5000) { vm.homeState.first { it?.month == YearMonth.from(LocalDate.ofEpochDay(olderDay)) } }
        assertEquals(YearMonth.from(LocalDate.ofEpochDay(olderDay)), revealed?.month)
    }

    @Test
    fun typedCsvRemapsRefundIdsWhenMergedAndRefusesBadRelationsBeforeReplacement() = runBlocking {
        val id = vm.addRecord(10000, day, "购物", "原支出").getOrThrow()
        vm.addRecord(4000, day, "购物", "退款", type = TransactionType.REFUND, relatedRecordId = id).getOrThrow()
        val originals = db.dao().getAllOnce()
        val uri = csvFile(encodeRecordCsv(originals))
        vm.importCsv(uri, replace = false).getOrThrow()
        val merged = db.dao().getAllOnce()
        val addedExpense = merged.single { it.type == TransactionType.EXPENSE && it.id !in originals.map { r -> r.id } }
        assertEquals(addedExpense.id, merged.single { it.type == TransactionType.REFUND && it.id !in originals.map { r -> r.id } }.relatedRecordId)
        val invalid = csvFile("日期,金额,分类,备注,代付,支付方式,类型,转入账户,原记录序号\n${LocalDate.ofEpochDay(day)},40,购物,非法退款,否,微信,退款,,99")
        assertTrue(vm.importCsv(invalid, replace = true).isFailure)
        assertEquals(merged, db.dao().getAllOnce())
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
    fun statsCategoryRecordsMatchTheSelectedMonthsTotals() = runBlocking {
        store.clear()
        db.dao().insert(record())
        db.dao().insert(record().copy(amountCents = 766, createdAt = 2_000, note = "晚餐"))
        db.dao().insert(record().copy(amountCents = 500, category = "交通"))
        db.dao().insert(record().copy(amountCents = 9_000, excluded = true))
        db.dao().insert(record().copy(amountCents = 8_000, deletedAt = 1))
        db.dao().insert(record().copy(
            amountCents = 7_777,
            epochDay = YearMonth.now().minusMonths(1).atDay(1).toEpochDay()
        ))
        vm = AppViewModel(app)
        store.put("test", vm)

        val stats = withTimeout(5_000) {
            vm.statsState.first { it?.totalCents == 2_500L }!!
        }
        val food = stats.monthRecords.filter { it.category == "餐饮" }

        assertEquals(YearMonth.now(), stats.month)
        assertEquals(2_000L, stats.categoryTotals.single { it.category == "餐饮" }.totalCents)
        assertEquals(3, stats.monthRecords.size)
        assertEquals(listOf("晚餐", "原始记录"), food.map { it.note })
        assertEquals(listOf(766L, 1_234L), food.map { it.amountCents })
        assertTrue(food.all { it.epochDay == day })
    }

    @Test
    fun csvRoundTripPreservesSpecialCharactersAndExcludedFlag() = runBlocking {
        val original = record().copy(
            category = "餐饮,\"聚餐\"",
            note = "第一行,\"AA\"\r\n第二行\n第三行",
            excluded = true,
            paymentMethod = PaymentMethod.ALIPAY
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
        assertEquals(PaymentMethod.ALIPAY, imported.paymentMethod)
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
        assertEquals("", imported.paymentMethod)
    }

    @Test
    fun accountBalanceTracksPaymentEditsExcludedExpensesAndTrash() = runBlocking {
        db.dao().insert(record()) // Upgraded records are not assigned to an account.
        vm.addRecord(1_200, day, "餐饮", "first")
        withTimeout(5_000) {
            while (db.dao().getAllOnce().size != 2) delay(10)
        }

        vm.setAccountBalance(PaymentMethod.WECHAT, 10_000)
        vm.setAccountBalance(PaymentMethod.ALIPAY, 5_000)
        withTimeout(5_000) {
            vm.accountBalances.first { it[PaymentMethod.WECHAT] == 10_000L && it[PaymentMethod.ALIPAY] == 5_000L }
        }

        vm.addRecord(200, day, "餐饮", "second", excluded = true)
        withTimeout(5_000) {
            vm.accountBalances.first { it[PaymentMethod.WECHAT] == 9_800L }
        }
        val second = withTimeout(5_000) {
            vm.homeState.first { state ->
                state?.days?.flatMap { it.records }?.any { it.note == "second" } == true
            }!!.days.flatMap { it.records }.single { it.note == "second" }
        }
        vm.updateRecord(second, 300, day, "餐饮", "second", paymentMethod = PaymentMethod.ALIPAY)
        withTimeout(5_000) {
            vm.accountBalances.first {
                it[PaymentMethod.WECHAT] == 10_000L && it[PaymentMethod.ALIPAY] == 4_700L
            }
        }
        vm.deleteRecord(second.id)
        withTimeout(5_000) {
            vm.accountBalances.first { it[PaymentMethod.ALIPAY] == 5_000L }
        }
        vm.restoreRecord(second.id)
        withTimeout(5_000) {
            vm.accountBalances.first { it[PaymentMethod.ALIPAY] == 4_700L }
        }
        Unit
    }

    @Test
    fun migrationKeepsOldRecordsUnassignedToAccounts() = runBlocking {
        val name = "migration-4-to-5.db"
        app.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath(name), null).use { old ->
            old.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "amountCents INTEGER NOT NULL, epochDay INTEGER NOT NULL, createdAt INTEGER NOT NULL, " +
                "category TEXT NOT NULL, note TEXT NOT NULL, excluded INTEGER NOT NULL, " +
                "deletedAt INTEGER NOT NULL, recurringId INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE recurring_expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "amountCents INTEGER NOT NULL, dayOfMonth INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL)")
            old.execSQL("CREATE TABLE categories (name TEXT NOT NULL PRIMARY KEY, emoji TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE templates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "amountCents INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL)")
            old.execSQL("INSERT INTO expenses VALUES (1, 1234, $day, 1000, '餐饮', '旧账', 0, 0, 0)")
            old.execSQL("INSERT INTO recurring_expenses VALUES (1, 1000, 1, '娱乐', '旧规则')")
            old.execSQL("INSERT INTO templates VALUES (1, 1200, '餐饮', '旧模板')")
            old.execSQL("INSERT INTO expenses VALUES (2, 1000, ${YearMonth.now().minusMonths(1).atDay(1).toEpochDay()}, ${System.currentTimeMillis()}, '娱乐', '改过日期的旧周期流水', 0, 0, 1)")
            old.version = 4
        }
        val migrated = Room.databaseBuilder(app, ExpenseDatabase::class.java, name)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
        try {
            val record = migrated.dao().getAllOnce().single { it.id == 1L }
            assertEquals("旧账", record.note)
            assertEquals("", record.paymentMethod)
            assertEquals(TransactionType.EXPENSE, record.type)
            assertEquals("", record.transferTo)
            assertEquals(0L, record.relatedRecordId)
            assertTrue(migrated.dao().observeAccountBalances().first().isEmpty())
            assertEquals("", migrated.dao().getAllRecurringOnce().single().paymentMethod)
            assertEquals(YearMonth.now().toString(), migrated.dao().getAllRecurringOnce().single().lastGeneratedMonth)
            assertEquals("", migrated.dao().getAllTemplatesOnce().single().paymentMethod)
        } finally {
            migrated.close()
            app.deleteDatabase(name)
        }
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

    @Test
    fun recurringAndTemplatesUseTheirSelectedAccountsWithoutDuplicatingTheMonth() = runBlocking {
        vm.setAccountBalance(PaymentMethod.ALIPAY, 10000)
        val id = vm.saveRecurring(null, 1200, 1, "娱乐", "会员", PaymentMethod.ALIPAY).getOrThrow()
        vm.syncRecurringForCurrentMonth()
        vm.syncRecurringForCurrentMonth()
        val generated = db.dao().getAllOnce().single { it.recurringId == id }
        assertEquals(PaymentMethod.ALIPAY, generated.paymentMethod)
        assertEquals(1, db.dao().countRecurringInstance(id, day - 31, day))
        val balances = withTimeout(5000) { vm.accountBalances.first { it[PaymentMethod.ALIPAY] == 8800L } }
        assertEquals(8800L, balances[PaymentMethod.ALIPAY])

        val rule = db.dao().getAllRecurringOnce().single()
        vm.saveRecurring(rule, 1500, 1, "娱乐", "会员", PaymentMethod.WECHAT).getOrThrow()
        vm.syncRecurringForCurrentMonth()
        assertEquals(PaymentMethod.ALIPAY, db.dao().getAllOnce().single().paymentMethod)

        vm.saveTemplate(2800, "餐饮", "午饭", PaymentMethod.ALIPAY)
        val template = withTimeout(5000) {
            while (db.dao().getAllTemplatesOnce().isEmpty()) delay(10)
            db.dao().getAllTemplatesOnce().single()
        }
        assertEquals(PaymentMethod.ALIPAY, template.paymentMethod)
    }

    @Test
    fun rejectedRecurringInstanceDoesNotLeaveAnOrphanRule() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_cycle BEFORE INSERT ON expenses BEGIN SELECT RAISE(ABORT, 'rejected'); END")
        assertTrue(vm.saveRecurring(null, 1200, 1, "娱乐", "会员", PaymentMethod.ALIPAY).isFailure)
        assertTrue(db.dao().getAllRecurringOnce().isEmpty())
        assertTrue(db.dao().getAllOnce().isEmpty())
    }

    @Test
    fun recurringGenerationSurvivesCsvReplacementAndMovingTheRecordedDate() = runBlocking {
        val id = vm.saveRecurring(null, 1200, 1, "娱乐", "会员", PaymentMethod.ALIPAY).getOrThrow()
        val generated = withTimeout(5000) { vm.homeState.first { it?.days?.flatMap { d -> d.records }?.any { r -> r.note.contains("会员") } == true }!! }
            .days.flatMap { it.records }.single()
        val olderDay = YearMonth.now().minusMonths(1).atDay(1).toEpochDay()
        vm.updateRecord(generated, 1200, olderDay, "娱乐", "会员改日期", paymentMethod = PaymentMethod.ALIPAY).getOrThrow()
        vm.syncRecurringForCurrentMonth()
        assertEquals(1, db.dao().getAllOnce().size)
        assertEquals(YearMonth.now().toString(), db.dao().getAllRecurringOnce().single().lastGeneratedMonth)
        vm.importCsv(csvFile(encodeRecordCsv(db.dao().getAllOnce())), replace = true).getOrThrow()
        vm.syncRecurringForCurrentMonth()
        assertEquals(1, db.dao().getAllOnce().size)
        assertEquals(id, db.dao().getAllRecurringOnce().single().id)
        assertEquals(1200L, db.dao().getAllOnce().single().amountCents)
    }

    @Test
    fun keywordAndFiltersShareConsistentDailyAndExcludedTotals() = runBlocking {
        db.dao().insert(record().copy(amountCents = 5000, note = "午饭", paymentMethod = PaymentMethod.ALIPAY))
        db.dao().insert(record().copy(amountCents = 6000, note = "午饭代付", paymentMethod = PaymentMethod.ALIPAY, excluded = true))
        db.dao().insert(record().copy(amountCents = 7000, note = "午饭", paymentMethod = PaymentMethod.WECHAT))
        vm.setSearchQuery("午饭")
        vm.setRecordFilter(RecordFilter(PaymentMethod.ALIPAY, fromDay = day, toDay = day, minCents = 5000, maxCents = 6000))
        val result = withTimeout(5000) { vm.searchState.first { it?.count == 2 }!! }
        assertEquals(5000L, result.totalCents)
        assertEquals(6000L, result.excludedTotalCents)
        assertEquals(5000L, result.days.single().dayTotalCents)
        assertEquals(6000L, result.days.single().excludedTotalCents)
        vm.clearSearchAndFilters()
        withTimeout(5000) { vm.searchState.first { it == null } }
        assertEquals("", vm.searchQuery.value)
        assertTrue(!vm.recordFilter.value.isActive)
    }

    @Test
    fun incomeTransferRefundAndTransferEditsKeepBudgetsAndBothAccountsConsistent() = runBlocking {
        vm.setAccountBalance(PaymentMethod.WECHAT, 100000)
        vm.setAccountBalance(PaymentMethod.ALIPAY, 50000)
        val expenseId = vm.addRecord(10000, day, "购物", "原支出").getOrThrow()
        vm.addRecord(20000, day, "收入", "工资", type = TransactionType.INCOME).getOrThrow()
        val transferId = vm.addRecord(30000, day, "转账", "转余额", type = TransactionType.TRANSFER, transferTo = PaymentMethod.ALIPAY).getOrThrow()
        val before = withTimeout(5000) { vm.accountBalances.first { it[PaymentMethod.WECHAT] == 80000L && it[PaymentMethod.ALIPAY] == 80000L } }
        assertEquals(80000L, before[PaymentMethod.ALIPAY])
        assertEquals(10000L, withTimeout(5000) { vm.homeState.first { it?.spentCents == 10000L }!! }.spentCents)
        vm.addRecord(4000, day, "其他", "部分退款", type = TransactionType.REFUND, relatedRecordId = expenseId).getOrThrow()
        assertTrue(vm.addRecord(7000, day, "其他", "超额退款", type = TransactionType.REFUND, relatedRecordId = expenseId).isFailure)
        assertEquals(4, db.dao().getAllOnce().size)
        val state = withTimeout(5000) { vm.homeState.first { it?.spentCents == 6000L }!! }
        val transfer = state.days.flatMap { it.records }.single { it.id == transferId }
        vm.updateRecord(transfer, 20000, day, "转账", "改为转入微信", paymentMethod = PaymentMethod.ALIPAY, transferTo = PaymentMethod.WECHAT).getOrThrow()
        withTimeout(5000) { vm.accountBalances.first { it[PaymentMethod.WECHAT] == 134000L && it[PaymentMethod.ALIPAY] == 30000L } }
        val expense = state.days.flatMap { it.records }.single { it.id == expenseId }
        assertTrue(vm.updateRecord(expense, 3000, day, "购物", "降低原额").isFailure)
        assertTrue(vm.updateRecord(expense, 10000, day, "购物", "切换账户", paymentMethod = PaymentMethod.ALIPAY).isFailure)
        vm.deleteRecord(transferId)
        withTimeout(5000) { vm.accountBalances.first { it[PaymentMethod.WECHAT] == 114000L && it[PaymentMethod.ALIPAY] == 50000L } }
        vm.restoreRecord(transferId).getOrThrow()
        withTimeout(5000) { vm.accountBalances.first { it[PaymentMethod.WECHAT] == 134000L && it[PaymentMethod.ALIPAY] == 30000L } }
        assertEquals(6000L, vm.homeState.value!!.spentCents)
    }

    @Test
    fun refundOffsetsTheMonthItOccursAndInheritsTheOriginalAccountAndExcludedFlag() = runBlocking {
        val oldDay = YearMonth.now().minusMonths(1).atDay(1).toEpochDay()
        val expense = vm.addRecord(20000, oldDay, "交通", "上月交通", paymentMethod = PaymentMethod.ALIPAY).getOrThrow()
        vm.addRecord(5000, day, "其他", "本月退款", type = TransactionType.REFUND, relatedRecordId = expense).getOrThrow()
        val stats = withTimeout(5000) { vm.statsState.first { it?.totalCents == -5000L }!! }
        assertEquals(20000L, stats.prevMonthTotalCents)
        assertEquals("交通", stats.categoryTotals.single().category)
        assertEquals(-5000L, stats.categoryTotals.single().totalCents)
        assertEquals(TransactionType.REFUND, stats.monthRecords.single().type)
        assertEquals(PaymentMethod.ALIPAY, stats.monthRecords.single().paymentMethod)
        assertTrue(vm.addRecord(100, oldDay - 1, "其他", "过早退款", type = TransactionType.REFUND, relatedRecordId = expense).isFailure)
        val excludedExpense = vm.addRecord(4000, day, "餐饮", "代付", excluded = true).getOrThrow()
        vm.addRecord(1000, day, "其他", "代付退款", type = TransactionType.REFUND, relatedRecordId = excludedExpense).getOrThrow()
        assertTrue(db.dao().getAllOnce().single { it.note == "代付退款" }.excluded)
        assertEquals(-5000L, withTimeout(5000) { vm.homeState.first { it?.days?.flatMap { d -> d.records }?.size == 3 }!! }.spentCents)
    }

    @Test
    fun deletingOriginalExpenseRestoresOnlyItsAssociatedActiveRefunds() = runBlocking {
        vm.setAccountBalance(PaymentMethod.WECHAT, 100000)
        val expense = vm.addRecord(10000, day, "购物", "原支出").getOrThrow()
        val earlierRefund = vm.addRecord(2000, day, "购物", "较早退款", type = TransactionType.REFUND, relatedRecordId = expense).getOrThrow()
        val currentRefund = vm.addRecord(3000, day, "购物", "有效退款", type = TransactionType.REFUND, relatedRecordId = expense).getOrThrow()
        vm.deleteRecord(earlierRefund)
        withTimeout(5000) { while (db.dao().getAllOnce().any { it.id == earlierRefund }) delay(10) }
        vm.deleteRecord(expense)
        withTimeout(5000) { while (db.dao().getAllOnce().isNotEmpty()) delay(10) }
        assertTrue(vm.restoreRecord(currentRefund).isFailure)
        vm.restoreRecord(expense).getOrThrow()
        assertEquals(setOf(expense, currentRefund), db.dao().getAllOnce().map { it.id }.toSet())
        withTimeout(5000) { vm.accountBalances.first { it[PaymentMethod.WECHAT] == 93000L } }
        vm.deleteRecord(expense)
        withTimeout(5000) { while (db.dao().getAllOnce().isNotEmpty()) delay(10) }
        vm.deleteRecordForever(expense)
        withTimeout(5000) { while (db.dao().getAllStoredOnce().isNotEmpty()) delay(10) }
    }

    @Test
    fun restoringARefundThatWouldExceedTheEditedExpenseRollsBack() = runBlocking {
        val expenseId = vm.addRecord(10000, day, "购物", "原支出").getOrThrow()
        val refundId = vm.addRecord(8000, day, "购物", "退款", type = TransactionType.REFUND, relatedRecordId = expenseId).getOrThrow()
        vm.deleteRecord(refundId)
        withTimeout(5000) { while (db.dao().getAllOnce().size != 1) delay(10) }
        val expense = withTimeout(5000) { vm.homeState.first { it?.days?.flatMap { d -> d.records }?.size == 1 }!! }.days.single().records.single()
        vm.updateRecord(expense, 5000, day, "购物", "改低金额").getOrThrow()
        assertTrue(vm.restoreRecord(refundId).isFailure)
        assertTrue(db.dao().getAllStoredOnce().single { it.id == refundId }.deletedAt > 0)
        assertEquals(5000L, db.dao().getAllOnce().single().amountCents)
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
