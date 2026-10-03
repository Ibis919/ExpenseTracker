package com.ibis.expense.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackupManagerTest {
    private lateinit var app: Application
    private lateinit var db: ExpenseDatabase
    private lateinit var backupDir: File
    private val day = LocalDate.of(2026, 10, 1).toEpochDay()

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        backupDir = File(app.filesDir, "backups")
        backupDir.deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(app, ExpenseDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
        backupDir.deleteRecursively()
    }

    @Test
    fun fullJsonRoundTripPreservesTrashBalancesBudgetRulesAndTemplates() {
        val expected = snapshot()

        val actual = BackupManager.decode(BackupManager.encode(expected))

        assertEquals(expected, actual)
        assertEquals(1_700_000_100_000L, actual.records[1].deletedAt)
        assertEquals("晚餐,\"朋友\"\n第二行", actual.records[0].note)
    }

    @Test
    fun typedSnapshotRoundTripAndRestorePreserveIncomeTransferAndRefundParents() = runBlocking {
        val previous = oldSnapshot()
        seed(previous)
        val expected = typedSnapshot()

        val decoded = BackupManager.decode(BackupManager.encode(expected))
        assertEquals(expected, decoded)
        BackupManager.restore(app, db, decoded)

        assertStored(expected)
        assertSnapshotsEqual(expected, BackupManager.capture(app, db))
        assertSnapshotsEqual(previous, BackupManager.decode(File(backupDir, "before-restore.json").readText()))
    }

    @Test
    fun savingProtectionCapturesAllStoredTypesWithoutChangingDatabaseOrBudget() = runBlocking {
        val expected = typedSnapshot()
        seed(expected)

        BackupManager.saveProtection(app, db)

        assertStored(expected)
        assertSnapshotsEqual(expected, BackupManager.decode(File(backupDir, "before-restore.json").readText()))
    }

    @Test
    fun invalidRefundsDoNotOverwriteExistingDataBudgetOrProtection() = runBlocking {
        val previous = oldSnapshot()
        seed(previous)
        BackupManager.saveProtection(app, db)
        val protection = File(backupDir, "before-restore.json")
        val protectedText = protection.readText()
        val valid = typedSnapshot()
        val invalidRecords = listOf(
            valid.records.map { if (it.id == 16L) it.copy(relatedRecordId = 999) else it },
            valid.records.map { if (it.id == 16L) it.copy(amountCents = 1235) else it },
            valid.records.map { if (it.id == 11L) it.copy(deletedAt = 1000) else it },
            valid.records.map { if (it.id == 16L) it.copy(epochDay = day - 1) else it },
            valid.records.map { if (it.id == 16L) it.copy(paymentMethod = "支付宝") else it },
            valid.records.map { if (it.id == 16L) it.copy(relatedRecordId = 14) else it }
        )

        for (records in invalidRecords) {
            assertTrue(runCatching { BackupManager.restore(app, db, valid.copy(records = records)) }.isFailure)
            assertStored(previous)
            assertEquals(protectedText, protection.readText())
        }
        val invalidJson = JSONObject(BackupManager.encode(valid)).apply {
            val records = getJSONArray("records")
            for (index in 0 until records.length()) {
                val record = records.getJSONObject(index)
                if (record.getLong("id") == 16L) record.put("relatedRecordId", 999)
            }
        }
        assertTrue(runCatching { BackupManager.decode(invalidJson.toString()) }.isFailure)
        assertStored(previous)
        assertEquals(protectedText, protection.readText())
    }

    @Test
    fun schemaFiveAndSixDefaultTransactionFieldsButSchemaSevenRequiresEachField() {
        val expected = snapshot()
        val source = BackupManager.encode(expected)
        for (key in listOf("type", "transferTo", "relatedRecordId")) {
            val broken = JSONObject(source)
            broken.getJSONArray("records").getJSONObject(0).remove(key)
            assertTrue(runCatching { BackupManager.decode(broken.toString()) }.isFailure)
        }
        for (schema in 5..6) {
            val legacy = JSONObject(source).put("schemaVersion", schema)
            val records = legacy.getJSONArray("records")
            for (index in 0 until records.length()) {
                records.getJSONObject(index).apply {
                    remove("type")
                    remove("transferTo")
                    remove("relatedRecordId")
                }
            }
            val decoded = BackupManager.decode(legacy.toString())
            assertEquals(expected.records, decoded.records)
            if (schema == 6) assertEquals(expected.copy(
                recurring = expected.recurring.map { it.copy(lastGeneratedMonth = "") }
            ), decoded)
        }
    }

    @Test
    fun restoreReplacesEveryTableAndBudgetAndProtectsPreviousState() = runBlocking {
        val previous = oldSnapshot()
        seed(previous)
        val incoming = snapshot()

        BackupManager.restore(app, db, incoming)

        assertStored(incoming)
        val captured = BackupManager.capture(app, db)
        assertSnapshotsEqual(incoming, captured)
        assertTrue(captured.createdAt > 0)
        val protection = File(backupDir, "before-restore.json")
        assertTrue(protection.isFile)
        assertSnapshotsEqual(previous, BackupManager.decode(protection.readText()))
    }

    @Test
    fun corruptOrFutureFilesDoNotChangeDatabaseBudgetOrProtection() = runBlocking {
        val previous = oldSnapshot()
        seed(previous)
        val json = BackupManager.encode(snapshot())
        val inputs = listOf(
            "{坏掉的文件",
            json + " trailing",
            JSONObject(json).put("version", 99).toString(),
            JSONObject(json).put("schemaVersion", 99).toString(),
            JSONObject(json).apply { remove("balances") }.toString()
        )

        for (input in inputs) {
            val failure = runCatching {
                BackupManager.restore(app, db, BackupManager.decode(input))
            }
            assertTrue(failure.isFailure)
            assertStored(previous)
            assertFalse(File(backupDir, "before-restore.json").exists())
        }
    }

    @Test
    fun decodingRejectsFractionalAmountsIdsAndOverflowInsteadOfTruncating() {
        val source = BackupManager.encode(snapshot())
        val inputs = listOf(
            JSONObject(source).apply { getJSONArray("records").getJSONObject(0).put("amountCents", 12.5) }.toString(),
            JSONObject(source).apply { getJSONArray("records").getJSONObject(0).put("id", 1.5) }.toString(),
            JSONObject(source).put("budgetCents", "60000").toString(),
            JSONObject(source).put("budgetCents", 9.223372036854776E18).toString()
        )

        for (input in inputs) assertTrue(runCatching { BackupManager.decode(input) }.isFailure)
    }

    @Test
    fun decodingRejectsDuplicateIdsIllegalMoneyAndUnknownAccounts() {
        val source = BackupManager.encode(snapshot())
        val inputs = listOf(
            JSONObject(source).apply {
                getJSONArray("records").put(getJSONArray("records").getJSONObject(0))
            },
            JSONObject(source).apply {
                getJSONArray("templates").put(getJSONArray("templates").getJSONObject(0))
            },
            JSONObject(source).apply {
                getJSONArray("recurring").put(getJSONArray("recurring").getJSONObject(0))
            },
            JSONObject(source).apply { getJSONArray("records").getJSONObject(0).put("amountCents", 0) },
            JSONObject(source).apply { getJSONArray("records").getJSONObject(0).put("amountCents", 1_000_000_000L) },
            JSONObject(source).apply { getJSONArray("records").getJSONObject(0).put("paymentMethod", "银行卡") },
            JSONObject(source).apply { getJSONArray("balances").getJSONObject(0).put("baseCents", 12.5) },
            JSONObject(source).apply { getJSONArray("balances").getJSONObject(0).put("method", "现金") },
            JSONObject(source).apply { getJSONArray("recurring").getJSONObject(0).put("dayOfMonth", 32) },
            JSONObject(source).apply { getJSONArray("recurring").getJSONObject(0).put("paymentMethod", "现金") },
            JSONObject(source).apply { getJSONArray("templates").getJSONObject(0).put("paymentMethod", "银行卡") },
            JSONObject(source).apply { getJSONArray("categories").put(getJSONArray("categories").getJSONObject(0)) },
            JSONObject(source).put("budgetCents", -1)
        )

        for (input in inputs) assertTrue(runCatching { BackupManager.decode(input.toString()) }.isFailure)
    }

    @Test
    fun schemaFiveDefaultsMissingRuleAndTemplateAccountsButSchemaSixRequiresThem() {
        val current = JSONObject(BackupManager.encode(snapshot())).put("schemaVersion", 6)
        current.getJSONArray("recurring").getJSONObject(0).remove("paymentMethod")
        current.getJSONArray("templates").getJSONObject(0).remove("paymentMethod")
        assertTrue(runCatching { BackupManager.decode(current.toString()) }.isFailure)

        current.put("schemaVersion", 5)
        val expected = snapshot().let { it.copy(
            recurring = it.recurring.map { rule -> rule.copy(paymentMethod = "", lastGeneratedMonth = "") },
            templates = it.templates.map { template -> template.copy(paymentMethod = "") }
        ) }
        assertEquals(expected, BackupManager.decode(current.toString()))
    }

    @Test
    fun schemaSevenRequiresAValidGeneratedMonthAndLegacyRulesDefaultToEmpty() {
        val expected = snapshot()
        val source = BackupManager.encode(expected)
        assertEquals("2026-10", BackupManager.decode(source).recurring.single().lastGeneratedMonth)
        val blank = expected.copy(recurring = expected.recurring.map { it.copy(lastGeneratedMonth = "") })
        assertEquals(blank, BackupManager.decode(BackupManager.encode(blank)))
        val missing = JSONObject(source).apply {
            getJSONArray("recurring").getJSONObject(0).remove("lastGeneratedMonth")
        }
        assertTrue(runCatching { BackupManager.decode(missing.toString()) }.isFailure)
        for (schema in 5..6) {
            val legacy = JSONObject(missing.toString()).put("schemaVersion", schema)
            assertEquals("", BackupManager.decode(legacy.toString()).recurring.single().lastGeneratedMonth)
        }
        for (month in listOf("2026-13", "2026-10-01", "不是月份")) {
            val invalid = JSONObject(source).apply {
                getJSONArray("recurring").getJSONObject(0).put("lastGeneratedMonth", month)
            }
            assertTrue(runCatching { BackupManager.decode(invalid.toString()) }.isFailure)
        }
    }

    @Test
    fun zeroBudgetSignedLargeBalanceAndHistoricalLinksRemainValid() {
        val historical = snapshot().let { it.copy(
            records = it.records.map { record -> record.copy(recurringId = 999, category = "已清理分类", createdAt = 1000, deletedAt = 1) },
            balances = listOf(AccountBalance("微信", -2_000_000_000L), AccountBalance("支付宝", Long.MAX_VALUE)),
            budgetCents = 0
        ) }

        assertEquals(historical, BackupManager.decode(BackupManager.encode(historical)))
    }

    @Test
    fun invalidInMemorySnapshotIsRejectedBeforeAnyRestoreSideEffect() = runBlocking {
        val previous = oldSnapshot()
        seed(previous)
        val invalid = snapshot().let { it.copy(records = it.records.map { record -> record.copy(id = 0) }) }

        assertTrue(runCatching { BackupManager.restore(app, db, invalid) }.isFailure)

        assertStored(previous)
        assertFalse(File(backupDir, "before-restore.json").exists())
    }

    @Test
    fun failedSqliteRestoreRollsBackAllTablesAndBudgetAndKeepsProtection() = runBlocking {
        val previous = oldSnapshot()
        seed(previous)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_restore BEFORE INSERT ON expenses " +
                "WHEN NEW.note = '恢复触发拒绝' BEGIN SELECT RAISE(ABORT, 'rejected'); END"
        )
        val incoming = snapshot().let { it.copy(records = it.records.map { record ->
            record.copy(note = "恢复触发拒绝")
        }) }

        assertTrue(runCatching { BackupManager.restore(app, db, incoming) }.isFailure)

        assertStored(previous)
        val protection = File(backupDir, "before-restore.json")
        assertTrue(protection.isFile)
        assertSnapshotsEqual(previous, BackupManager.decode(protection.readText()))
    }

    @Test
    fun protectionWriteFailureLeavesDatabaseAndBudgetUntouched() = runBlocking {
        val previous = oldSnapshot()
        seed(previous)
        File(backupDir, "before-restore.json").mkdirs()

        assertTrue(runCatching { BackupManager.restore(app, db, snapshot()) }.isFailure)

        assertStored(previous)
    }

    @Test
    fun failedAutomaticBackupIsReportedWithoutUpdatingTheDailyMarker() = runBlocking {
        seed(snapshot())
        backupDir.writeText("阻止创建备份目录")

        assertTrue(runCatching { BackupManager.backupIfNeeded(app, db) }.isFailure)

        assertFalse(app.getSharedPreferences("settings", Context.MODE_PRIVATE).contains("last_backup_day"))
    }

    @Test
    fun dailyBackupCreatesCompleteJsonEvenWithALegacySameDayMarkerAndKeepsSeven() = runBlocking {
        val expected = snapshot()
        seed(expected)
        backupDir.mkdirs()
        val today = LocalDate.now().toEpochDay()
        val oldJson = BackupManager.encode(expected)
        for (offset in 1L..9L) File(backupDir, "expenses-${today - offset}.json").writeText(oldJson)
        val legacy = File(backupDir, "expenses-$today.db").apply { writeText("旧格式保留") }
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putLong("last_backup_day", today).commit()

        BackupManager.backupIfNeeded(app, db)

        val daily = File(backupDir, "expenses-$today.json")
        assertSnapshotsEqual(expected, BackupManager.decode(daily.readText()))
        assertEquals(7, backupDir.listFiles()!!.count { it.name.startsWith("expenses-") && it.extension == "json" })
        assertFalse(File(backupDir, "expenses-${today - 7}.json").exists())
        assertTrue(legacy.exists())
        assertEquals(LocalDate.now(), BackupManager.latestBackupDate(app))
        val saved = daily.readText()
        db.dao().insert(ExpenseRecord(id = 100, amountCents = 100, epochDay = day, createdAt = 1, category = "餐饮", note = "今天第二次启动"))
        BackupManager.backupIfNeeded(app, db)
        assertEquals(saved, daily.readText())
    }

    @Test
    fun latestBackupDateAcceptsOldDatabaseNamesAndIgnoresUnrelatedFiles() {
        backupDir.mkdirs()
        File(backupDir, "expenses-${day - 1}.json").writeText("{}")
        File(backupDir, "expenses-$day.db").writeText("旧格式保留")
        File(backupDir, "expenses-999999999999999999.json").writeText("{}")
        File(backupDir, "expenses-invalid.json").writeText("{}")
        File(backupDir, "before-restore.json").writeText("{}")

        assertEquals(LocalDate.of(2026, 10, 1), BackupManager.latestBackupDate(app))
    }

    private fun snapshot() = BackupSnapshot(
        records = listOf(
            ExpenseRecord(11, 1234, day, 1_700_000_000_000, "餐饮", "晚餐,\"朋友\"\n第二行", paymentMethod = "微信"),
            ExpenseRecord(12, 4500, day - 1, 1_700_000_000_001, "交通", "代付", excluded = true, deletedAt = 1_700_000_100_000, recurringId = 31, paymentMethod = "支付宝"),
            ExpenseRecord(13, 900, day - 2, 1_700_000_000_002, "交通", "旧记录未关联账户")
        ),
        balances = listOf(AccountBalance("微信", 50000), AccountBalance("支付宝", 25000)),
        categories = listOf(Category("餐饮", "🍜", 1), Category("交通", "🚗", 2)),
        recurring = listOf(RecurringExpense(31, 4500, 31, "交通", "月度开销", paymentMethod = "支付宝", lastGeneratedMonth = "2026-10")),
        templates = listOf(RecordTemplate(41, 1234, "餐饮", "快捷晚餐", paymentMethod = "微信")),
        budgetCents = 80000,
        createdAt = 1_700_000_200_000
    )

    private fun typedSnapshot() = snapshot().let { it.copy(records = it.records + listOf(
        ExpenseRecord(14, 10000, day, 1000, "收入", "工资", paymentMethod = "微信", type = TransactionType.INCOME),
        ExpenseRecord(15, 1000, day, 1001, "转账", "内部调拨", paymentMethod = "微信", type = TransactionType.TRANSFER, transferTo = "支付宝"),
        ExpenseRecord(16, 500, day + 1, 1002, "餐饮", "餐费退款", paymentMethod = "微信", type = TransactionType.REFUND, relatedRecordId = 11),
        ExpenseRecord(17, 600, day, 1003, "交通", "代付退款回收站", excluded = true, deletedAt = 1_700_000_100_001, paymentMethod = "支付宝", type = TransactionType.REFUND, relatedRecordId = 12)
    )) }

    private fun oldSnapshot() = BackupSnapshot(
        records = listOf(ExpenseRecord(1, 500, day, 1, "旧分类", "恢复前记录")),
        balances = listOf(AccountBalance("微信", 9000)),
        categories = listOf(Category("旧分类", "📦", 0)),
        recurring = listOf(RecurringExpense(2, 1000, 2, "旧分类", "旧周期")),
        templates = listOf(RecordTemplate(3, 200, "旧分类", "旧模板")),
        budgetCents = 70000,
        createdAt = 1
    )

    private suspend fun seed(snapshot: BackupSnapshot) {
        val dao = db.dao()
        snapshot.categories.forEach { dao.insertCategory(it) }
        snapshot.recurring.forEach { dao.insertRecurring(it) }
        snapshot.templates.forEach { dao.insertTemplate(it) }
        snapshot.balances.forEach { dao.upsertAccountBalance(it) }
        dao.insertAll(snapshot.records)
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putLong("budget_cents", snapshot.budgetCents).commit()
    }

    private suspend fun assertStored(expected: BackupSnapshot) {
        val dao = db.dao()
        assertEquals(expected.records.sortedBy { it.id }, dao.getAllStoredOnce())
        assertEquals(expected.balances.sortedBy { it.method }, dao.getAccountBalancesOnce())
        assertEquals(expected.categories.sortedWith(compareBy({ it.sortOrder }, { it.name })), dao.getAllCategoriesOnce())
        assertEquals(expected.recurring.sortedBy { it.id }, dao.getAllRecurringOnce().sortedBy { it.id })
        assertEquals(expected.templates.sortedBy { it.id }, dao.getAllTemplatesOnce().sortedBy { it.id })
        assertEquals(expected.budgetCents, app.getSharedPreferences("settings", Context.MODE_PRIVATE).getLong("budget_cents", -1))
    }

    private fun assertSnapshotsEqual(expected: BackupSnapshot, actual: BackupSnapshot) {
        fun normalized(snapshot: BackupSnapshot) = snapshot.copy(
            records = snapshot.records.sortedBy { it.id },
            balances = snapshot.balances.sortedBy { it.method },
            categories = snapshot.categories.sortedBy { it.name },
            recurring = snapshot.recurring.sortedBy { it.id },
            templates = snapshot.templates.sortedBy { it.id },
            createdAt = 0
        )
        assertEquals(normalized(expected), normalized(actual))
    }
}
