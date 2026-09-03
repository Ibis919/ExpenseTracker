package com.ibis.expense.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.ibis.expense.BudgetNotifier
import com.ibis.expense.data.BackupManager
import com.ibis.expense.data.CategoryTotal
import com.ibis.expense.data.ExpenseDatabase
import com.ibis.expense.data.ExpenseRecord
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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
    val overBudget: Boolean
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
    val trend: List<MonthSpent>
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = Room.databaseBuilder(app, ExpenseDatabase::class.java, "expenses.db").build()
    private val dao = db.dao()
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _budgetCents = MutableStateFlow(prefs.getLong(KEY_BUDGET_CENTS, DEFAULT_BUDGET_CENTS))
    val budgetCents: StateFlow<Long> = _budgetCents.asStateFlow()

    private val _month = MutableStateFlow(YearMonth.now())

    private val _lastBackup = MutableStateFlow<LocalDate?>(null)
    val lastBackup: StateFlow<LocalDate?> = _lastBackup.asStateFlow()

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
        combine(
            dao.observeCategoryTotals(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()),
            dao.observeRange(month.minusMonths(5).atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
        ) { catTotals, records ->
            val byMonth = records.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }
            val trend = (5L downTo 0L).map { offset ->
                val ym = month.minusMonths(offset)
                MonthSpent(ym, byMonth[ym]?.sumOf { it.amountCents } ?: 0L)
            }
            StatsState(
                month = month,
                totalCents = catTotals.sumOf { it.totalCents },
                categoryTotals = catTotals,
                trend = trend
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            homeState.collect { s -> s?.let(::checkBudgetAlert) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            BackupManager.backupIfNeeded(app, db)
            _lastBackup.value = BackupManager.latestBackupDate(app)
        }
    }

    fun addRecord(amountCents: Long, epochDay: Long, category: String, note: String) {
        viewModelScope.launch {
            dao.insert(
                ExpenseRecord(
                    amountCents = amountCents,
                    epochDay = epochDay,
                    createdAt = System.currentTimeMillis(),
                    category = category,
                    note = note.trim()
                )
            )
        }
    }

    fun updateRecord(record: UiRecord, amountCents: Long, epochDay: Long, category: String, note: String) {
        viewModelScope.launch {
            dao.update(
                ExpenseRecord(
                    id = record.id,
                    amountCents = amountCents,
                    epochDay = epochDay,
                    createdAt = record.createdAt,
                    category = category,
                    note = note.trim()
                )
            )
        }
    }

    fun deleteRecord(id: Long) {
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

    suspend fun exportCsv(): File? = withContext(Dispatchers.IO) {
        val records = dao.getAllOnce()
        if (records.isEmpty()) return@withContext null
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = File(dir, "expenses-$stamp.csv")
        file.writeText(buildCsv(records), Charsets.UTF_8)
        file
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
                    note = fields.getOrElse(3) { "" }.trim()
                )
            }
            if (records.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("文件中没有有效记录"))
            }
            if (replace) dao.deleteAll()
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
        appendLine("日期,金额,分类,备注")
        val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        for (r in records) {
            append(LocalDate.ofEpochDay(r.epochDay).format(dateFormat)).append(',')
            append(formatAmount(r.amountCents)).append(',')
            append(r.category).append(',')
            append('"').append(r.note.replace("\"", "\"\"")).append('"')
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

    override fun onCleared() {
        db.close()
    }

    private fun buildHomeState(month: YearMonth, budget: Long, records: List<ExpenseRecord>): HomeState {
        val ascending = records.sortedWith(compareBy({ it.epochDay }, { it.createdAt }))
        val over = HashMap<Long, Boolean>(records.size)
        var cumulative = 0L
        for (record in ascending) {
            cumulative += record.amountCents
            over[record.id] = cumulative > budget
        }
        val spent = records.sumOf { it.amountCents }
        val days = records.groupBy { it.epochDay }.map { (day, list) ->
            DayGroup(
                date = LocalDate.ofEpochDay(day),
                dayTotalCents = list.sumOf { it.amountCents },
                records = list.map {
                    UiRecord(
                        id = it.id,
                        amountCents = it.amountCents,
                        epochDay = it.epochDay,
                        createdAt = it.createdAt,
                        category = it.category,
                        note = it.note,
                        overBudget = over[it.id] == true
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
    }
}
