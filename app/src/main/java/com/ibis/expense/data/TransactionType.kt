package com.ibis.expense.data

import java.time.LocalDate

object TransactionType {
    const val EXPENSE = "expense"
    const val INCOME = "income"
    const val TRANSFER = "transfer"
    const val REFUND = "refund"
    val ALL = listOf(EXPENSE, INCOME, TRANSFER, REFUND)
    fun label(type: String): String = when (type) {
        INCOME -> "收入"
        TRANSFER -> "转账"
        REFUND -> "退款"
        else -> "支出"
    }
}

fun ExpenseRecord.expenseImpact(): Long = when (type) {
    TransactionType.EXPENSE -> amountCents
    TransactionType.REFUND -> -amountCents
    else -> 0L
}

fun ExpenseRecord.budgetImpact(): Long = if (excluded) 0L else expenseImpact()

fun ExpenseRecord.accountImpact(method: String): Long = when (type) {
    TransactionType.EXPENSE -> if (paymentMethod == method) -amountCents else 0L
    TransactionType.INCOME, TransactionType.REFUND -> if (paymentMethod == method) amountCents else 0L
    TransactionType.TRANSFER -> when (method) { paymentMethod -> -amountCents; transferTo -> amountCents; else -> 0L }
    else -> 0L
}

fun validateTransactions(records: List<ExpenseRecord>) {
    val accounts = setOf("微信", "支付宝")
    val byId = records.associateBy { it.id }
    for (record in records) {
        require(record.type in TransactionType.ALL) { "交易类型无效" }
        require(record.amountCents in 1..999999999L) { "请输入正确金额" }
        require(runCatching { LocalDate.ofEpochDay(record.epochDay) }.isSuccess) { "记账日期无效" }
        require(record.paymentMethod.isEmpty() || record.paymentMethod in accounts) { "支付账户无效" }
        require(record.category.isNotBlank()) { "分类不能为空" }
        if (record.type == TransactionType.TRANSFER) {
            require(record.paymentMethod in accounts && record.transferTo in accounts && record.paymentMethod != record.transferTo) { "转出和转入账户必须不同" }
        } else require(record.transferTo.isEmpty()) { "只有转账可以填写转入账户" }
        if (record.type in listOf(TransactionType.INCOME, TransactionType.TRANSFER)) {
            require(!record.excluded) { "收入和转账不能标记为代付" }
        }
        if (record.type == TransactionType.REFUND) {
            val parent = byId[record.relatedRecordId]
            require(record.relatedRecordId > 0 && parent?.type == TransactionType.EXPENSE) { "退款必须关联原支出" }
            require(parent.deletedAt == 0L || record.deletedAt > 0) { "请先恢复原支出，再恢复退款" }
            require(record.epochDay >= parent.epochDay) { "退款日期不能早于原支出" }
            require(record.category == parent.category && record.excluded == parent.excluded && record.paymentMethod == parent.paymentMethod) { "已有退款，原支出的分类、代付和账户需保持一致" }
        } else require(record.relatedRecordId == 0L) { "只有退款可以关联原支出" }
    }
    records.filter { it.type == TransactionType.REFUND && it.deletedAt == 0L }
        .groupBy { it.relatedRecordId }.forEach { (id, refunds) ->
            require(refunds.sumOf { it.amountCents } <= byId.getValue(id).amountCents) { "累计退款不能超过原支出金额" }
        }
}
