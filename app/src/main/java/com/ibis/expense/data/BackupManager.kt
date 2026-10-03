package com.ibis.expense.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.room.withTransaction
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

data class BackupSnapshot(
    val records: List<ExpenseRecord>,
    val balances: List<AccountBalance>,
    val categories: List<Category>,
    val recurring: List<RecurringExpense>,
    val templates: List<RecordTemplate>,
    val budgetCents: Long,
    val createdAt: Long
)

object BackupManager {
    private const val PREFS = "settings"
    private const val KEY_LAST_BACKUP_DAY = "last_backup_day"
    private const val KEY_BUDGET_CENTS = "budget_cents"
    private const val DEFAULT_BUDGET_CENTS = 60000L
    private const val FORMAT_VERSION = 1
    private const val SCHEMA_VERSION = 7
    private const val MAX_AMOUNT_CENTS = 999999999L
    private const val KEEP = 7
    private val methods = setOf("微信", "支付宝")
    private val backupName = Regex("expenses-(-?\\d+)\\.(json|db)")

    suspend fun capture(context: Context, db: ExpenseDatabase): BackupSnapshot = db.withTransaction {
        val dao = db.dao()
        BackupSnapshot(
            records = dao.getAllStoredOnce(),
            balances = dao.getAccountBalancesOnce(),
            categories = dao.getAllCategoriesOnce(),
            recurring = dao.getAllRecurringOnce(),
            templates = dao.getAllTemplatesOnce(),
            budgetCents = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_BUDGET_CENTS, DEFAULT_BUDGET_CENTS),
            createdAt = System.currentTimeMillis()
        )
    }

    fun encode(snapshot: BackupSnapshot): String {
        validate(snapshot)
        return JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("schemaVersion", SCHEMA_VERSION)
            put("createdAt", snapshot.createdAt)
            put("budgetCents", snapshot.budgetCents)
            put("records", JSONArray().apply {
                snapshot.records.forEach { record -> put(JSONObject().apply {
                    put("id", record.id)
                    put("amountCents", record.amountCents)
                    put("epochDay", record.epochDay)
                    put("createdAt", record.createdAt)
                    put("category", record.category)
                    put("note", record.note)
                    put("excluded", record.excluded)
                    put("deletedAt", record.deletedAt)
                    put("recurringId", record.recurringId)
                    put("paymentMethod", record.paymentMethod)
                    put("type", record.type)
                    put("transferTo", record.transferTo)
                    put("relatedRecordId", record.relatedRecordId)
                }) }
            })
            put("balances", JSONArray().apply {
                snapshot.balances.forEach { balance -> put(JSONObject().apply {
                    put("method", balance.method)
                    put("baseCents", balance.baseCents)
                }) }
            })
            put("categories", JSONArray().apply {
                snapshot.categories.forEach { category -> put(JSONObject().apply {
                    put("name", category.name)
                    put("emoji", category.emoji)
                    put("sortOrder", category.sortOrder)
                }) }
            })
            put("recurring", JSONArray().apply {
                snapshot.recurring.forEach { item -> put(JSONObject().apply {
                    put("id", item.id)
                    put("amountCents", item.amountCents)
                    put("dayOfMonth", item.dayOfMonth)
                    put("category", item.category)
                    put("note", item.note)
                    put("paymentMethod", item.paymentMethod)
                    put("lastGeneratedMonth", item.lastGeneratedMonth)
                }) }
            })
            put("templates", JSONArray().apply {
                snapshot.templates.forEach { template -> put(JSONObject().apply {
                    put("id", template.id)
                    put("amountCents", template.amountCents)
                    put("category", template.category)
                    put("note", template.note)
                    put("paymentMethod", template.paymentMethod)
                }) }
            })
        }.toString(2)
    }

    fun decode(text: String): BackupSnapshot {
        try {
            val tokener = JSONTokener(text)
            val root = tokener.nextValue() as? JSONObject
                ?: throw IllegalArgumentException("备份文件必须是 JSON 对象")
            require(tokener.nextClean() == '\u0000') { "备份文件尾部包含无效内容" }
            val version = root.long("version")
            require(version <= FORMAT_VERSION) { "备份来自新版应用，请升级后恢复" }
            require(version == FORMAT_VERSION.toLong()) { "不支持此备份格式版本" }
            val schema = root.long("schemaVersion")
            require(schema <= SCHEMA_VERSION) { "备份数据版本较新，请升级后恢复" }
            require(schema >= 5) { "不支持此备份数据版本" }
            return BackupSnapshot(
                records = root.objects("records") { record ->
                    ExpenseRecord(
                        id = record.long("id"),
                        amountCents = record.long("amountCents"),
                        epochDay = record.long("epochDay"),
                        createdAt = record.long("createdAt"),
                        category = record.string("category"),
                        note = record.string("note"),
                        excluded = record.boolean("excluded"),
                        deletedAt = record.long("deletedAt"),
                        recurringId = record.long("recurringId"),
                        paymentMethod = record.string("paymentMethod"),
                        type = if (schema >= 7) record.string("type") else TransactionType.EXPENSE,
                        transferTo = if (schema >= 7) record.string("transferTo") else "",
                        relatedRecordId = if (schema >= 7) record.long("relatedRecordId") else 0
                    )
                },
                balances = root.objects("balances") { balance ->
                    AccountBalance(balance.string("method"), balance.long("baseCents"))
                },
                categories = root.objects("categories") { category ->
                    Category(category.string("name"), category.string("emoji"), category.int("sortOrder"))
                },
                recurring = root.objects("recurring") { item ->
                    RecurringExpense(item.long("id"), item.long("amountCents"), item.int("dayOfMonth"),
                        item.string("category"), item.string("note"),
                        if (schema >= 6) item.string("paymentMethod") else "",
                        if (schema >= 7) item.string("lastGeneratedMonth") else "")
                },
                templates = root.objects("templates") { template ->
                    RecordTemplate(template.long("id"), template.long("amountCents"),
                        template.string("category"), template.string("note"),
                        if (schema >= 6) template.string("paymentMethod") else "")
                },
                budgetCents = root.long("budgetCents"),
                createdAt = root.long("createdAt")
            ).also(::validate)
        } catch (e: JSONException) {
            throw IllegalArgumentException("备份文件损坏或缺少必要字段", e)
        }
    }

    suspend fun saveProtection(context: Context, db: ExpenseDatabase): Unit = withContext(Dispatchers.IO) {
        writeAtomic(File(directory(context), "before-restore.json"), encode(capture(context, db)))
    }

    suspend fun restore(context: Context, db: ExpenseDatabase, snapshot: BackupSnapshot): Unit = withContext(Dispatchers.IO) {
        validate(snapshot)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hadBudget = prefs.contains(KEY_BUDGET_CENTS)
        val previousBudget = prefs.getLong(KEY_BUDGET_CENTS, DEFAULT_BUDGET_CENTS)
        saveProtection(context, db)
        try {
            db.withTransaction {
                val dao = db.dao()
                dao.deleteAll()
                dao.deleteAllAccountBalances()
                dao.deleteAllRecurring()
                dao.deleteAllTemplates()
                dao.deleteAllCategories()
                snapshot.categories.forEach { dao.insertCategory(it) }
                snapshot.recurring.forEach { dao.insertRecurring(it) }
                snapshot.templates.forEach { dao.insertTemplate(it) }
                snapshot.balances.forEach { dao.upsertAccountBalance(it) }
                dao.insertAll(snapshot.records)
                check(prefs.edit().putLong(KEY_BUDGET_CENTS, snapshot.budgetCents).commit()) {
                    "预算保存失败，已取消恢复"
                }
            }
        } catch (e: Exception) {
            val editor = prefs.edit()
            if (hadBudget) editor.putLong(KEY_BUDGET_CENTS, previousBudget) else editor.remove(KEY_BUDGET_CENTS)
            if (!editor.commit()) e.addSuppressed(IOException("恢复原预算失败"))
            if (e is CancellationException) throw e
            throw IOException("恢复失败，原有数据已保留", e)
        }
    }

    suspend fun backupIfNeeded(context: Context, db: ExpenseDatabase): Unit = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay()
        val daily = File(context.filesDir, "backups/expenses-$today.json")
        if (prefs.getLong(KEY_LAST_BACKUP_DAY, -1L) == today && daily.isFile) return@withContext
        try {
            writeAtomic(File(directory(context), daily.name), encode(capture(context, db)))
            directory(context).listFiles()?.mapNotNull { file ->
                if (file.extension == "json") backupDay(file)?.let { it to file } else null
            }?.sortedByDescending { it.first }?.drop(KEEP)?.forEach { it.second.delete() }
            prefs.edit().putLong(KEY_LAST_BACKUP_DAY, today).apply()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("BackupManager", "自动备份失败，下次启动将重试", e)
            throw e
        }
    }

    fun latestBackupDate(context: Context): LocalDate? = File(context.filesDir, "backups")
        .listFiles()?.mapNotNull(::backupDay)?.maxOrNull()?.let(LocalDate::ofEpochDay)

    private fun validate(snapshot: BackupSnapshot) {
        require(snapshot.budgetCents in 0..MAX_AMOUNT_CENTS) { "预算金额无效" }
        require(snapshot.createdAt >= 0) { "备份时间无效" }
        require(snapshot.records.map { it.id }.distinct().size == snapshot.records.size) { "记录 ID 重复" }
        require(snapshot.recurring.map { it.id }.distinct().size == snapshot.recurring.size) { "周期账单 ID 重复" }
        require(snapshot.templates.map { it.id }.distinct().size == snapshot.templates.size) { "模板 ID 重复" }
        require(snapshot.categories.map { it.name }.distinct().size == snapshot.categories.size) { "分类名称重复" }
        require(snapshot.balances.map { it.method }.distinct().size == snapshot.balances.size) { "支付账户重复" }
        snapshot.records.forEach { record ->
            require(record.id > 0) { "记录 ID 无效" }
            require(record.createdAt >= 0 && record.deletedAt >= 0 && record.recurringId >= 0) { "记录时间或周期关联无效" }
        }
        validateTransactions(snapshot.records)
        snapshot.balances.forEach { require(it.method in methods) { "支付账户无效" } }
        snapshot.categories.forEach {
            require(it.name.isNotBlank() && it.emoji.isNotBlank()) { "分类名称或图标不能为空" }
        }
        snapshot.recurring.forEach {
            require(it.id > 0 && it.amountCents in 1..MAX_AMOUNT_CENTS && it.dayOfMonth in 1..31) { "周期账单 ID、金额或日期无效" }
            require(it.category.isNotBlank()) { "周期账单分类不能为空" }
            require(it.paymentMethod.isEmpty() || it.paymentMethod in methods) { "周期账单支付账户无效" }
            require(it.lastGeneratedMonth.isEmpty() || runCatching { YearMonth.parse(it.lastGeneratedMonth) }.isSuccess) {
                "周期账单已生成月份无效"
            }
        }
        snapshot.templates.forEach {
            require(it.id > 0 && it.amountCents in 1..MAX_AMOUNT_CENTS) { "模板 ID 或金额无效" }
            require(it.category.isNotBlank()) { "模板分类不能为空" }
            require(it.paymentMethod.isEmpty() || it.paymentMethod in methods) { "模板支付账户无效" }
        }
    }

    private fun JSONObject.long(key: String): Long = when (val value = get(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("备份字段 $key 必须是有效整数")
    }

    private fun JSONObject.int(key: String): Int {
        val value = long(key)
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "备份字段 $key 超出整数范围" }
        return value.toInt()
    }

    private fun JSONObject.string(key: String): String = get(key) as? String
        ?: throw IllegalArgumentException("备份字段 $key 必须是文字")

    private fun JSONObject.boolean(key: String): Boolean = get(key) as? Boolean
        ?: throw IllegalArgumentException("备份字段 $key 必须是布尔值")

    private fun <T> JSONObject.objects(key: String, read: (JSONObject) -> T): List<T> {
        val array = get(key) as? JSONArray ?: throw IllegalArgumentException("备份字段 $key 必须是列表")
        return (0 until array.length()).map { index ->
            val item = array.get(index) as? JSONObject ?: throw IllegalArgumentException("备份列表 $key 包含无效项目")
            read(item)
        }
    }

    private fun directory(context: Context): File = File(context.filesDir, "backups").apply {
        if (!isDirectory && !mkdirs()) throw IOException("无法创建备份目录")
    }

    private fun writeAtomic(file: File, text: String) {
        if (file.exists() && !file.isFile) throw IOException("备份目标不是文件，无法安全写入")
        val atomic = AtomicFile(file)
        val stream = try { atomic.startWrite() } catch (e: IOException) {
            throw IOException("无法创建备份文件", e)
        }
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw IOException("备份文件写入失败", e)
        }
        if (!file.isFile) throw IOException("备份文件写入后校验失败")
        val persisted = try { file.readText(Charsets.UTF_8) } catch (e: IOException) {
            throw IOException("无法核对已写入的备份文件", e)
        }
        if (persisted != text) throw IOException("备份文件写入后校验失败")
    }

    private fun backupDay(file: File): Long? {
        if (!file.isFile) return null
        val day = backupName.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return day.takeIf { runCatching { LocalDate.ofEpochDay(it) }.isSuccess }
    }
}
