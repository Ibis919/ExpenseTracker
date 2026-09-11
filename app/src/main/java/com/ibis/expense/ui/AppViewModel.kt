package com.ibis.expense.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ibis.expense.BudgetNotifier
import com.ibis.expense.data.BackupManager
import com.ibis.expense.data.Category
import com.ibis.expense.data.CategoryTotal
import com.ibis.expense.data.ExpenseDatabase
import com.ibis.expense.data.ExpenseRecord
import com.ibis.expense.data.RecordTemplate
import com.ibis.expense.data.RecurringExpense
import androidx.room.withTransaction
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
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
    val excluded: Boolean
)

data class DayGroup(
    val date: LocalDate,
    val dayTotalCents: Long,
    val records: List<UiRecord>
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val searchState: StateFlow<SearchState?> = combine(_searchQuery, dao.observeAll()) { query, records ->
        val q = query.trim()
        if (q.isEmpty()) null else buildSearchState(q, records)
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
                MonthSpent(ym, byMonth[ym]?.filter { !it.excluded }?.sumOf { it.amountCents } ?: 0L)
            }
            StatsState(
                month = month,
                totalCents = catTotals.sumOf { it.totalCents },
                categoryTotals = catTotals,
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

    init {
        viewModelScope.launch {
            homeState.collect { s -> s?.let(::checkBudgetAlert) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            BackupManager.backupIfNeeded(app, db)
            _lastBackup.value = BackupManager.latestBackupDate(app)
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
        }
    }

    private suspend fun syncRecurringForCurrentMonth() {
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        val monthStart = month.atDay(1).toEpochDay()
        val monthEnd = month.atEndOfMonth().toEpochDay()
        for (r in dao.getAllRecurringOnce()) {
            if (r.dayOfMonth > today.dayOfMonth) continue
            if (dao.countRecurringInstance(r.id, monthStart, monthEnd) > 0) continue
            val day = r.dayOfMonth.coerceIn(1, month.lengthOfMonth())
            dao.insert(
                ExpenseRecord(
                    amountCents = r.amountCents,
                    epochDay = LocalDate.of(today.year, today.monthValue, day).toEpochDay(),
                    createdAt = System.currentTimeMillis(),
                    category = r.category,
                    note = "🔁 ${r.note}".trim(),
                    recurringId = r.id
                )
            )
        }
    }

    fun addRecurring(amountCents: Long, dayOfMonth: Int, category: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insertRecurring(
                RecurringExpense(
                    amountCents = amountCents,
                    dayOfMonth = dayOfMonth.coerceIn(1, 28),
                    category = category,
                    note = note.trim()
                )
            )
            val today = LocalDate.now()
            val month = YearMonth.from(today)
            if (dayOfMonth <= today.dayOfMonth &&
                dao.countRecurringInstance(id, month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()) == 0
            ) {
                dao.insert(
                    ExpenseRecord(
                        amountCents = amountCents,
                        epochDay = LocalDate.of(today.year, today.monthValue, dayOfMonth.coerceIn(1, month.lengthOfMonth())).toEpochDay(),
                        createdAt = System.currentTimeMillis(),
                        category = category,
                        note = "🔁 ${note.trim()}".trim(),
                        recurringId = id
                    )
                )
            }
        }
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

    fun saveTemplate(amountCents: Long, category: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val exists = dao.getAllTemplatesOnce().any {
                it.amountCents == amountCents && it.category == category && it.note == note
            }
            if (!exists) {
                dao.insertTemplate(RecordTemplate(amountCents = amountCents, category = category, note = note))
            }
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { dao.deleteTemplateById(id) }
    }

    fun addRecord(amountCents: Long, epochDay: Long, category: String, note: String, excluded: Boolean = false) {
        viewModelScope.launch {
            dao.insert(
                ExpenseRecord(
                    amountCents = amountCents,
                    epochDay = epochDay,
                    createdAt = System.currentTimeMillis(),
                    category = category,
                    note = note.trim(),
                    excluded = excluded
                )
            )
        }
    }

    fun updateRecord(record: UiRecord, amountCents: Long, epochDay: Long, category: String, note: String, excluded: Boolean = record.excluded) {
        viewModelScope.launch {
            dao.update(
                ExpenseRecord(
                    id = record.id,
                    amountCents = amountCents,
                    epochDay = epochDay,
                    createdAt = record.createdAt,
                    category = category,
                    note = note.trim(),
                    excluded = excluded
                )
            )
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            dao.softDelete(id, System.currentTimeMillis())
        }
    }

    fun restoreRecord(id: Long) {
        viewModelScope.launch {
            dao.restore(id)
        }
    }

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

    suspend fun exportCsvForShare(): File? = withContext(Dispatchers.IO) {
        val records = dao.getAllOnce()
        if (records.isEmpty()) return@withContext null
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = File(dir, "记账-$stamp.csv")
        file.writeText(buildCsv(records), Charsets.UTF_8)
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
                out.write(buildCsv(records).toByteArray(Charsets.UTF_8))
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
            val lines = app.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readLines()
                ?: return@withContext Result.failure(IllegalStateException("无法读取文件"))
            val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val records = mutableListOf<ExpenseRecord>()
            var skipped = 0
            for (raw in lines) {
                val line = raw.trim().removePrefix("\uFEFF")
                if (line.isEmpty() || line.startsWith("日期")) continue
                val fields = parseCsvLine(line)
                val date = runCatching { LocalDate.parse(fields.getOrNull(0), dateFormat) }.getOrNull()
                val cents = fields.getOrNull(1)?.let { parseAmountToCents(it) }
                if (date == null || cents == null || fields.getOrNull(2).isNullOrBlank()) {
                    skipped++
                    continue
                }
                records += ExpenseRecord(
                    amountCents = cents,
                    epochDay = date.toEpochDay(),
                    createdAt = System.currentTimeMillis(),
                    category = fields[2].trim(),
                    note = fields.getOrElse(3) { "" }.trim(),
                    excluded = fields.getOrElse(4) { "" }.trim() == "是"
                )
            }
            if (records.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("文件中没有有效记录"))
            }
            if (replace) dao.deleteAll()
            val known = HashSet<String>()
            for (r in records) {
                if (r.category in known) continue
                known += r.category
                if (dao.countCategory(r.category) == 0) {
                    dao.insertCategory(Category(r.category, "📦", dao.maxCategorySort() + 1))
                }
            }
            dao.insertAll(records)
            Result.success(ImportOutcome(records.size, skipped))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }

    private fun buildCsv(records: List<ExpenseRecord>): String = buildString {
        append('\uFEFF')
        appendLine("日期,金额,分类,备注,代付")
        val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        for (r in records) {
            append(LocalDate.ofEpochDay(r.epochDay).format(dateFormat)).append(',')
            append(formatAmount(r.amountCents)).append(',')
            append(r.category).append(',')
            append('"').append(r.note.replace("\"", "\"\"")).append('"').append(',')
            append(if (r.excluded) "是" else "否")
            appendLine()
        }
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
                    (termCents != null && r.amountCents == termCents) ||
                    dateStrings.any { it.contains(term) }
            }
        }
        val days = matched.groupBy { it.epochDay }.map { (day, list) ->
            DayGroup(
                date = LocalDate.ofEpochDay(day),
                dayTotalCents = list.sumOf { it.amountCents },
                records = list.sortedByDescending { it.createdAt }.map {
                    UiRecord(
                        id = it.id,
                        amountCents = it.amountCents,
                        epochDay = it.epochDay,
                        createdAt = it.createdAt,
                        category = it.category,
                        note = it.note,
                        overBudget = false,
                        excluded = it.excluded
                    )
                }
            )
        }.sortedByDescending { it.date }
        return SearchState(
            query = query,
            count = matched.size,
            totalCents = matched.filter { !it.excluded }.sumOf { it.amountCents },
            excludedTotalCents = matched.filter { it.excluded }.sumOf { it.amountCents },
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
            if (record.excluded) {
                over[record.id] = false
                continue
            }
            cumulative += record.amountCents
            over[record.id] = cumulative > budget
        }
        val spent = records.filter { !it.excluded }.sumOf { it.amountCents }
        val days = records.groupBy { it.epochDay }.map { (day, list) ->
            DayGroup(
                date = LocalDate.ofEpochDay(day),
                dayTotalCents = list.filter { !it.excluded }.sumOf { it.amountCents },
                records = list.map {
                    UiRecord(
                        id = it.id,
                        amountCents = it.amountCents,
                        epochDay = it.epochDay,
                        createdAt = it.createdAt,
                        category = it.category,
                        note = it.note,
                        overBudget = over[it.id] == true,
                        excluded = it.excluded
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
