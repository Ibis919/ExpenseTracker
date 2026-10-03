package com.ibis.expense.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ibis.expense.BudgetNotifier
import com.ibis.expense.data.AccountBalance
import com.ibis.expense.data.BackupManager
import com.ibis.expense.data.Category
import com.ibis.expense.data.CategoryTotal
import com.ibis.expense.data.ExpenseDatabase
import com.ibis.expense.data.ExpenseRecord
import com.ibis.expense.data.RecordTemplate
import com.ibis.expense.data.RecurringExpense
import com.ibis.expense.data.BackupSnapshot
import com.ibis.expense.data.TransactionType
import com.ibis.expense.data.accountImpact
import com.ibis.expense.data.budgetImpact
import com.ibis.expense.data.expenseImpact
import com.ibis.expense.data.validateTransactions
import androidx.room.withTransaction
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiRecord(
    val id: Long,
    val amountCents: Long,
    val epochDay: Long,
    val createdAt: Long,
    val category: String,
    val note: String,
    val overBudget: Boolean,
    val excluded: Boolean,
    val paymentMethod: String,
    val type: String = TransactionType.EXPENSE,
    val transferTo: String = "",
    val relatedRecordId: Long = 0
)

data class DayGroup(
    val date: LocalDate,
    val dayTotalCents: Long,
    val records: List<UiRecord>,
    val excludedTotalCents: Long = 0
)

data class HomeState(
    val month: YearMonth,
    val budgetCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val isOverBudget: Boolean,
    val days: List<DayGroup>
)

data class MonthSpent(
    val month: YearMonth,
    val totalCents: Long
)

data class StatsState(
    val month: YearMonth,
    val totalCents: Long,
    val categoryTotals: List<CategoryTotal>,
    val monthRecords: List<ExpenseRecord>,
    val trend: List<MonthSpent>,
    val prevMonthTotalCents: Long,
    val prevCategoryTotals: List<CategoryTotal>
)

data class SearchState(
    val query: String,
    val count: Int,
    val totalCents: Long,
    val excludedTotalCents: Long,
    val days: List<DayGroup>
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = ExpenseDatabase.build(app)
    private val dao = db.dao()
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _budgetCents = MutableStateFlow(prefs.getLong(KEY_BUDGET_CENTS, DEFAULT_BUDGET_CENTS))
    val budgetCents: StateFlow<Long> = _budgetCents.asStateFlow()

    private val _month = MutableStateFlow(YearMonth.now())

    private val _lastBackup = MutableStateFlow<LocalDate?>(null)
    val lastBackup: StateFlow<LocalDate?> = _lastBackup.asStateFlow()

    private val _backupError = MutableStateFlow<String?>(null)
    val backupError = _backupError.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _recordFilter = MutableStateFlow(RecordFilter())
    val recordFilter = _recordFilter.asStateFlow()
    fun setRecordFilter(filter: RecordFilter) { _recordFilter.value = filter }
    fun clearSearchAndFilters() { _searchQuery.value = ""; _recordFilter.value = RecordFilter() }

    private val _savedRecordId = MutableStateFlow<Long?>(null)
    val savedRecordId: StateFlow<Long?> = _savedRecordId.asStateFlow()

    fun acknowledgeSavedRecord() { _savedRecordId.value = null }

    private fun revealSavedRecord(id: Long, epochDay: Long) {
        _searchQuery.value = ""
        _recordFilter.value = RecordFilter()
        _month.value = YearMonth.from(LocalDate.ofEpochDay(epochDay))
        _savedRecordId.value = id
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val searchState: StateFlow<SearchState?> = combine(_searchQuery, _recordFilter, dao.observeAll()) { query, filter, records ->
        val q = query.trim()
        if (q.isEmpty() && !filter.isActive) null else buildSearchState(q, records.filter(filter::matches))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val homeState: StateFlow<HomeState?> = _month.flatMapLatest { month ->
        combine(
            dao.observeRange(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()),
            _budgetCents
        ) { records, budget ->
            buildHomeState(month, budget, records)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val statsState: StateFlow<StatsState?> = _month.flatMapLatest { month ->
        val prev = month.minusMonths(1)
        combine(
            dao.observeCategoryTotals(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()),
            dao.observeRange(month.minusMonths(11).atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()),
            dao.observeCategoryTotals(prev.atDay(1).toEpochDay(), prev.atEndOfMonth().toEpochDay())
        ) { catTotals, records, prevCatTotals ->
            val byMonth = records.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }
            val trend = (11L downTo 0L).map { offset ->
                val ym = month.minusMonths(offset)
                MonthSpent(ym, byMonth[ym]?.sumOf { it.budgetImpact() } ?: 0L)
            }
            StatsState(
                month = month,
                totalCents = catTotals.sumOf { it.totalCents },
                categoryTotals = catTotals,
                monthRecords = byMonth[month].orEmpty().filter { !it.excluded && it.type in listOf(TransactionType.EXPENSE, TransactionType.REFUND) },
                trend = trend,
                prevMonthTotalCents = prevCatTotals.sumOf { it.totalCents },
                prevCategoryTotals = prevCatTotals
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trashState: StateFlow<List<ExpenseRecord>> = dao.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recurringState: StateFlow<List<RecurringExpense>> = dao.observeRecurring()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoriesState: StateFlow<List<Category>> = dao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templatesState: StateFlow<List<RecordTemplate>> = dao.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accountBalances: StateFlow<Map<String, Long>> = combine(
        dao.observeAccountBalances(), dao.observeAll()
    ) { bases, records ->
        bases.associate { balance ->
            balance.method to (balance.baseCents + records.sumOf { it.accountImpact(balance.method) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            homeState.collect { s -> s?.let(::checkBudgetAlert) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (dao.countAllCategories() == 0) {
                listOf(
                    "餐饮" to "🍜", "交通" to "🚗", "购物" to "🛍️", "日用" to "🧴",
                    "娱乐" to "🎮", "医疗" to "💊", "其他" to "📦"
                ).forEachIndexed { i, (name, emoji) ->
                    dao.insertCategory(Category(name, emoji, i + 1))
                }
            }
            dao.purgeTrashOlderThan(System.currentTimeMillis() - TRASH_RETENTION_MS)
            syncRecurringForCurrentMonth()
            try {
                BackupManager.backupIfNeeded(app, db)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _backupError.value = e.message ?: "自动备份失败"
            }
            _lastBackup.value = BackupManager.latestBackupDate(app)
        }
    }

    internal suspend fun syncRecurringForCurrentMonth() = db.withTransaction {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        for (r in dao.getAllRecurringOnce()) {
            if (r.dayOfMonth > today.dayOfMonth) continue
            if (r.lastGeneratedMonth == month.toString()) continue
            if (recurringGeneratedInMonth(r.id, month)) {
                dao.updateRecurring(r.copy(lastGeneratedMonth = month.toString()))
                continue
            }
            val day = r.dayOfMonth.coerceIn(1, month.lengthOfMonth())
            dao.insert(
                ExpenseRecord(
                    amountCents = r.amountCents,
                    epochDay = LocalDate.of(today.year, today.monthValue, day).toEpochDay(),
                    createdAt = System.currentTimeMillis(),
                    category = r.category,
                    note = "🔁 ${r.note}".trim(),
                    recurringId = r.id,
                    paymentMethod = r.paymentMethod
                )
            )
            dao.updateRecurring(r.copy(lastGeneratedMonth = month.toString()))
        }
    }

    private suspend fun recurringGeneratedInMonth(id: Long, month: YearMonth): Boolean {
        val zone = ZoneId.systemDefault()
        return dao.countRecurringInstance(id, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()) > 0 ||
            dao.countRecurringCreated(id, month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()) > 0
    }

    suspend fun saveRecurring(initial: RecurringExpense?, amountCents: Long, dayOfMonth: Int, category: String, note: String, paymentMethod: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            require(amountCents > 0 && dayOfMonth in 1..28 && paymentMethod in PaymentMethod.ALL_WITH_LEGACY) { "请检查金额、日期和支付方式" }
            val id = db.withTransaction {
                val current = initial?.let { item -> dao.getAllRecurringOnce().singleOrNull { it.id == item.id } ?: error("周期规则已删除") }
                val item = RecurringExpense(current?.id ?: 0, amountCents, dayOfMonth, category, note.trim(), paymentMethod, current?.lastGeneratedMonth ?: "")
                val savedId = if (initial == null) dao.insertRecurring(item) else {
                    dao.updateRecurring(item)
                    item.id
                }
                if (initial == null) syncRecurringForCurrentMonth()
                savedId
            }
            Result.success(id)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    fun deleteRecurring(id: Long) {
        viewModelScope.launch { dao.deleteRecurringById(id) }
    }

    fun addCategory(name: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@launch
            dao.insertCategory(Category(trimmed, emoji, dao.maxCategorySort() + 1))
        }
    }

    fun renameCategory(oldName: String, newName: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) return@launch
            db.withTransaction {
                if (trimmed != oldName) {
                    if (dao.countCategory(trimmed) > 0) return@withTransaction
                    dao.renameCategoryRow(oldName, trimmed, emoji)
                    dao.reassignExpensesCategory(oldName, trimmed)
                    dao.reassignRecurringCategory(oldName, trimmed)
                    dao.reassignTemplatesCategory(oldName, trimmed)
                } else {
                    dao.renameCategoryRow(oldName, trimmed, emoji)
                }
            }
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (name == "其他") return@launch
            db.withTransaction {
                dao.reassignExpensesCategory(name, "其他")
                dao.reassignRecurringCategory(name, "其他")
                dao.reassignTemplatesCategory(name, "其他")
                dao.deleteCategoryRow(name)
            }
        }
    }

    fun saveTemplate(amountCents: Long, category: String, note: String, paymentMethod: String = PaymentMethod.WECHAT) {
        viewModelScope.launch(Dispatchers.IO) {
            val exists = dao.getAllTemplatesOnce().any {
                it.amountCents == amountCents && it.category == category && it.note == note && it.paymentMethod == paymentMethod
            }
            if (!exists) {
                dao.insertTemplate(RecordTemplate(amountCents = amountCents, category = category, note = note, paymentMethod = paymentMethod))
            }
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { dao.deleteTemplateById(id) }
    }

    suspend fun addRecord(amountCents: Long, epochDay: Long, category: String, note: String, excluded: Boolean = false, paymentMethod: String = PaymentMethod.WECHAT,
        type: String = TransactionType.EXPENSE, transferTo: String = "", relatedRecordId: Long = 0): Result<Long> {
        val result = withContext(Dispatchers.IO) {
            try {
                Result.success(db.withTransaction {
                    val records = dao.getAllStoredOnce()
                    val candidate = normalizeRefund(ExpenseRecord(amountCents = amountCents, epochDay = epochDay,
                        createdAt = System.currentTimeMillis(), category = category, note = note.trim(),
                        excluded = excluded, paymentMethod = paymentMethod, type = type,
                        transferTo = transferTo, relatedRecordId = relatedRecordId), records)
                    validateTransactions(records + candidate)
                    dao.insert(candidate)
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        result.onSuccess { revealSavedRecord(it, epochDay) }
        return result
    }

    suspend fun updateRecord(record: UiRecord, amountCents: Long, epochDay: Long, category: String, note: String, excluded: Boolean = record.excluded, paymentMethod: String = record.paymentMethod,
        transferTo: String = record.transferTo): Result<Unit> {
        val result = withContext(Dispatchers.IO) {
            try {
                val updated = db.withTransaction {
                    val records = dao.getAllStoredOnce()
                    val original = records.singleOrNull { it.id == record.id && it.deletedAt == 0L } ?: error("记录不存在或已删除")
                    val candidate = normalizeRefund(original.copy(amountCents = amountCents, epochDay = epochDay,
                        category = category, note = note.trim(), excluded = excluded, paymentMethod = paymentMethod, transferTo = transferTo), records)
                    validateTransactions(records.filterNot { it.id == record.id } + candidate)
                    check(dao.updateRecordEntity(candidate) == 1) { "记录不存在或已删除" }
                    candidate
                }
                Result.success(updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        result.onSuccess { updated ->
            val searching = _searchQuery.value.isNotBlank() || _recordFilter.value.isActive
            val visible = if (searching) _recordFilter.value.matches(updated) && buildSearchState(_searchQuery.value, listOf(updated)).count == 1
                else _month.value == YearMonth.from(LocalDate.ofEpochDay(epochDay))
            if (visible) acknowledgeSavedRecord() else revealSavedRecord(record.id, epochDay)
        }
        return result.map { Unit }
    }

    suspend fun setAccountBalance(method: String, cents: Long) {
        withContext(Dispatchers.IO) {
            db.withTransaction {
                require(method in listOf(PaymentMethod.WECHAT, PaymentMethod.ALIPAY) && cents >= 0) { "账户或金额无效" }
                dao.upsertAccountBalance(AccountBalance(method, cents - dao.getAllOnce().sumOf { it.accountImpact(method) }))
            }
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            db.withTransaction {
                dao.softDelete(id, maxOf(System.currentTimeMillis(), dao.latestDeletionTimestamp() + 1))
            }
        }
    }

    suspend fun restoreRecord(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.withTransaction {
                val record = dao.getAllStoredOnce().singleOrNull { it.id == id } ?: error("记录不存在")
                dao.restore(id, record.deletedAt)
                validateTransactions(dao.getAllStoredOnce())
            }
            Result.success(Unit)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    private fun normalizeRefund(record: ExpenseRecord, records: List<ExpenseRecord>): ExpenseRecord {
        if (record.type != TransactionType.REFUND) return record
        val parent = records.singleOrNull { it.id == record.relatedRecordId && it.deletedAt == 0L && it.type == TransactionType.EXPENSE }
            ?: error("请选择有效的原支出")
        return record.copy(category = parent.category, excluded = parent.excluded, paymentMethod = parent.paymentMethod)
    }

    val allRecords = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteRecordForever(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
        }
    }

    fun setBudgetCents(cents: Long) {
        prefs.edit().putLong(KEY_BUDGET_CENTS, cents).apply()
        _budgetCents.value = cents
    }

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    suspend fun exportBackupTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val app = getApplication<Application>()
            val snapshot = BackupManager.capture(app, db)
            app.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(BackupManager.encode(snapshot).toByteArray(Charsets.UTF_8))
            } ?: error("无法打开目标文件")
            Result.success(snapshot.records.size)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun readBackup(uri: Uri): Result<BackupSnapshot> = withContext(Dispatchers.IO) {
        try {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("无法读取文件")
            Result.success(BackupManager.decode(text))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun localBackupNames(): List<String> = withContext(Dispatchers.IO) {
        File(getApplication<Application>().filesDir, "backups").listFiles()
            ?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
            ?.map { it.name }.orEmpty()
    }

    suspend fun readLocalBackup(name: String): Result<BackupSnapshot> = withContext(Dispatchers.IO) {
        try {
            require(name in localBackupNames()) { "备份文件不存在" }
            Result.success(BackupManager.decode(File(getApplication<Application>().filesDir, "backups/$name").readText(Charsets.UTF_8)))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun restoreBackup(snapshot: BackupSnapshot): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            BackupManager.restore(getApplication(), db, snapshot)
            _budgetCents.value = snapshot.budgetCents
            _searchQuery.value = ""
            _recordFilter.value = RecordFilter()
            _savedRecordId.value = null
            _backupError.value = null
            Result.success(Unit)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun exportCsvForShare(): File? = withContext(Dispatchers.IO) {
        val records = dao.getAllOnce()
        if (records.isEmpty()) return@withContext null
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = File(dir, "记账-$stamp.csv")
        file.writeText(encodeRecordCsv(records), Charsets.UTF_8)
        file
    }

    suspend fun exportCsvTo(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val records = dao.getAllOnce()
            if (records.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("暂无记录可导出"))
            }
            val app = getApplication<Application>()
            app.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(encodeRecordCsv(records).toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@withContext Result.failure(IllegalStateException("无法打开目标文件"))
            Result.success(records.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class ImportOutcome(val imported: Int, val skipped: Int)

    suspend fun importCsv(uri: Uri, replace: Boolean): Result<ImportOutcome> = withContext(Dispatchers.IO) {
        try {
            val app = getApplication<Application>()
            val content = app.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("无法读取文件")
            val decoded = decodeRecordCsv(content)
            val records = decoded.records
            db.withTransaction {
                if (replace) {
                    BackupManager.saveProtection(app, db)
                    val month = YearMonth.now()
                    for (rule in dao.getAllRecurringOnce()) {
                        if (recurringGeneratedInMonth(rule.id, month)) {
                            dao.updateRecurring(rule.copy(lastGeneratedMonth = month.toString()))
                        }
                    }
                    dao.deleteAll()
                }
                val known = HashSet<String>()
                for (record in records) {
                    if (record.type !in listOf(TransactionType.EXPENSE, TransactionType.REFUND) || !known.add(record.category)) continue
                    if (dao.countCategory(record.category) == 0) {
                        dao.insertCategory(Category(record.category, "📦", dao.maxCategorySort() + 1))
                    }
                }
                val ids = dao.insertAll(records.map { it.copy(id = 0, relatedRecordId = 0) })
                val idMap = records.mapIndexed { index, record -> record.id to ids[index] }.toMap()
                records.forEachIndexed { index, record ->
                    if (record.type == TransactionType.REFUND) {
                        check(dao.updateRecordEntity(record.copy(id = ids[index], relatedRecordId = idMap.getValue(record.relatedRecordId))) == 1)
                    }
                }
                validateTransactions(dao.getAllStoredOnce())
            }
            Result.success(ImportOutcome(records.size, decoded.skipped))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    private fun checkBudgetAlert(s: HomeState) {
        if (s.month != YearMonth.now() || s.budgetCents <= 0) return
        val app = getApplication<Application>()
        val key = s.month.toString()
        if (s.spentCents > s.budgetCents) {
            if (!prefs.getBoolean("alert100_$key", false)) {
                prefs.edit().putBoolean("alert100_$key", true).apply()
                BudgetNotifier.notifyOverBudget(app, s.spentCents, s.budgetCents)
            }
        } else if (s.spentCents * 10 >= s.budgetCents * 8) {
            if (!prefs.getBoolean("alert80_$key", false)) {
                prefs.edit().putBoolean("alert80_$key", true).apply()
                BudgetNotifier.notifyNearLimit(app, s.spentCents, s.budgetCents)
            }
        }
    }

    private fun buildSearchState(query: String, records: List<ExpenseRecord>): SearchState {
        val terms = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val dateCache = HashMap<Long, List<String>>(32)
        val matched = records.filter { r ->
            val dateStrings = dateCache.getOrPut(r.epochDay) { buildDateStrings(r.epochDay) }
            terms.all { term ->
                val tl = term.lowercase()
                val termCents = parseAmountToCents(term)
                r.note.lowercase().contains(tl) ||
                    r.category.lowercase().contains(tl) ||
                    r.paymentMethod.contains(term) || r.transferTo.contains(term) || TransactionType.label(r.type).contains(term) ||
                    (termCents != null && r.amountCents == termCents) ||
                    dateStrings.any { it.contains(term) }
            }
        }
        val days = matched.groupBy { it.epochDay }.map { (day, list) ->
            DayGroup(
                date = LocalDate.ofEpochDay(day),
                dayTotalCents = list.sumOf { it.budgetImpact() },
                excludedTotalCents = list.filter { it.excluded }.sumOf { it.expenseImpact() },
                records = list.sortedByDescending { it.createdAt }.map {
                    UiRecord(
                        id = it.id,
                        amountCents = it.amountCents,
                        epochDay = it.epochDay,
                        createdAt = it.createdAt,
                        category = it.category,
                        note = it.note,
                        overBudget = false,
                        excluded = it.excluded,
                        paymentMethod = it.paymentMethod,
                        type = it.type,
                        transferTo = it.transferTo,
                        relatedRecordId = it.relatedRecordId
                    )
                }
            )
        }.sortedByDescending { it.date }
        return SearchState(
            query = query,
            count = matched.size,
            totalCents = matched.sumOf { it.budgetImpact() },
            excludedTotalCents = matched.filter { it.excluded }.sumOf { it.expenseImpact() },
            days = days
        )
    }

    private fun buildDateStrings(epochDay: Long): List<String> {
        val d = LocalDate.ofEpochDay(epochDay)
        return listOf(
            d.format(DateTimeFormatter.ISO_LOCAL_DATE),
            d.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
            d.format(DateTimeFormatter.ofPattern("M月d日")),
            d.format(DateTimeFormatter.ofPattern("yyyy年M月")),
            d.format(DateTimeFormatter.ofPattern("M月")),
            d.format(DateTimeFormatter.ofPattern("d日")),
            d.format(DateTimeFormatter.ofPattern("M-d")),
            d.format(DateTimeFormatter.ofPattern("M.d")),
            d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
        )
    }

    override fun onCleared() {
        db.close()
    }

    private fun buildHomeState(month: YearMonth, budget: Long, records: List<ExpenseRecord>): HomeState {
        val ascending = records.sortedWith(compareBy({ it.epochDay }, { it.createdAt }))
        val over = HashMap<Long, Boolean>(records.size)
        var cumulative = 0L
        for (record in ascending) {
            if (record.excluded || record.type in listOf(TransactionType.INCOME, TransactionType.TRANSFER)) {
                over[record.id] = false
                continue
            }
            cumulative += record.budgetImpact()
            over[record.id] = record.type == TransactionType.EXPENSE && cumulative > budget
        }
        val spent = records.sumOf { it.budgetImpact() }
        val days = records.groupBy { it.epochDay }.map { (day, list) ->
            DayGroup(
                date = LocalDate.ofEpochDay(day),
                dayTotalCents = list.sumOf { it.budgetImpact() },
                excludedTotalCents = list.filter { it.excluded }.sumOf { it.expenseImpact() },
                records = list.map {
                    UiRecord(
                        id = it.id,
                        amountCents = it.amountCents,
                        epochDay = it.epochDay,
                        createdAt = it.createdAt,
                        category = it.category,
                        note = it.note,
                        overBudget = over[it.id] == true,
                        excluded = it.excluded,
                        paymentMethod = it.paymentMethod,
                        type = it.type,
                        transferTo = it.transferTo,
                        relatedRecordId = it.relatedRecordId
                    )
                }
            )
        }.sortedByDescending { it.date }
        return HomeState(
            month = month,
            budgetCents = budget,
            spentCents = spent,
            remainingCents = budget - spent,
            isOverBudget = spent > budget,
            days = days
        )
    }

    companion object {
        private const val KEY_BUDGET_CENTS = "budget_cents"
        const val DEFAULT_BUDGET_CENTS = 600_00L
        private const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
