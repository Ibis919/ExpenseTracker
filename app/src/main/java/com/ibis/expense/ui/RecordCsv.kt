package com.ibis.expense.ui

import com.ibis.expense.data.ExpenseRecord
import com.ibis.expense.data.TransactionType
import com.ibis.expense.data.validateTransactions
import java.time.LocalDate

data class CsvRecords(val records: List<ExpenseRecord>, val skipped: Int)

private const val RECORD_CSV_HEADER = "日期,金额,分类,备注,代付,支付方式,类型,转入账户,原记录序号"

fun encodeRecordCsv(records: List<ExpenseRecord>): String {
    validateTransactions(records)
    val sequenceById = records.mapIndexed { index, record -> record.id to index + 1L }.toMap()
    return buildString {
        append('\uFEFF')
        appendLine(RECORD_CSV_HEADER)
        for (record in records) {
            val related = if (record.type == TransactionType.REFUND) {
                sequenceById[record.relatedRecordId]?.toString()
                    ?: throw IllegalArgumentException("退款必须包含原支出记录")
            } else ""
            val fields = listOf(
                LocalDate.ofEpochDay(record.epochDay).toString(),
                formatAmount(record.amountCents),
                record.category,
                record.note,
                if (record.excluded) "是" else "否",
                record.paymentMethod,
                TransactionType.label(record.type),
                record.transferTo,
                related
            )
            appendLine(fields.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
        }
    }
}

fun decodeRecordCsv(content: String): CsvRecords {
    val rows = parseRecordCsv(content)
    require(rows.none { it.size > 9 }) { "CSV 列数超过支持范围" }
    val header = rows.firstOrNull()?.takeIf { it.firstOrNull()?.trim() == "日期" }
    val dataRows = if (header == null) rows else rows.drop(1)
    val typed = rows.any { it.size == 9 }
    if (typed) {
        require((header == null || header.size == 9) && dataRows.all { it.size == 9 }) {
            "交易 CSV 必须使用完整九列，不能混用旧版记录"
        }
    }
    val typesByLabel = TransactionType.ALL.associateBy { TransactionType.label(it) }
    val records = mutableListOf<ExpenseRecord>()
    var skipped = 0
    val createdAt = System.currentTimeMillis()
    dataRows.forEachIndexed { index, fields ->
        val date = runCatching { LocalDate.parse(fields.getOrNull(0)?.trim()) }.getOrNull()
        val cents = fields.getOrNull(1)?.let { parseAmountToCents(it) }
        val category = fields.getOrNull(2)?.let { if (typed) it else it.trim() }
        val method = fields.getOrElse(5) { "" }.let { if (typed) it else it.trim() }
        if (date == null || cents == null || category.isNullOrBlank() ||
            (!typed && (fields.size !in 3..6 || method !in PaymentMethod.ALL_WITH_LEGACY))) {
            if (typed) throw IllegalArgumentException("第 ${index + 1} 条交易的日期、金额或分类无效")
            skipped++
            return@forEachIndexed
        }
        val type = if (typed) {
            typesByLabel[fields[6]] ?: throw IllegalArgumentException("第 ${index + 1} 条交易类型无效")
        } else TransactionType.EXPENSE
        if (typed) require(fields[4] == "是" || fields[4] == "否") { "代付标记必须为是或否" }
        val related = if (typed && fields[8].isNotEmpty()) {
            fields[8].toLongOrNull() ?: throw IllegalArgumentException("原记录序号必须为整数")
        } else 0L
        records += ExpenseRecord(
            id = index + 1L,
            amountCents = cents,
            epochDay = date.toEpochDay(),
            createdAt = createdAt + index,
            category = category,
            note = fields.getOrElse(3) { "" }.let { if (typed) it else it.trim() },
            excluded = fields.getOrElse(4) { "" }.let { if (typed) it else it.trim() } == "是",
            paymentMethod = method,
            type = type,
            transferTo = if (typed) fields[7] else "",
            relatedRecordId = related
        )
    }
    if (records.isEmpty()) throw IllegalStateException("文件中没有有效记录")
    validateTransactions(records)
    return CsvRecords(records, skipped)
}

private fun parseRecordCsv(content: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val fields = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var afterQuotes = false

    fun finishField() {
        fields.add(field.toString())
        field.clear()
        afterQuotes = false
    }

    fun finishRow() {
        finishField()
        if (fields.size > 1 || fields.any { it.isNotBlank() }) rows.add(fields.toList())
        fields.clear()
    }

    var index = 0
    val text = content.removePrefix("\uFEFF")
    while (index < text.length) {
        val c = text[index]
        when {
            inQuotes && c == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                field.append('"')
                index++
            }
            inQuotes && c == '"' -> {
                inQuotes = false
                afterQuotes = true
            }
            inQuotes -> field.append(c)
            c == '"' && field.isEmpty() && !afterQuotes -> inQuotes = true
            c == '"' -> throw IllegalArgumentException("CSV 引号格式错误")
            c == ',' -> finishField()
            c == '\r' || c == '\n' -> {
                finishRow()
                if (c == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
            }
            afterQuotes && !c.isWhitespace() -> throw IllegalArgumentException("CSV 引号格式错误")
            !afterQuotes -> field.append(c)
        }
        index++
    }
    if (inQuotes) throw IllegalArgumentException("CSV 引号未闭合")
    if (field.isNotEmpty() || fields.isNotEmpty() || afterQuotes) finishRow()
    return rows
}
