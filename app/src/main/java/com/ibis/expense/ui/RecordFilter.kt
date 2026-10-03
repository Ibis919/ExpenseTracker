package com.ibis.expense.ui

import com.ibis.expense.data.ExpenseRecord

data class RecordFilter(
    val paymentMethod: String? = null,
    val excluded: Boolean? = null,
    val fromDay: Long? = null,
    val toDay: Long? = null,
    val minCents: Long? = null,
    val maxCents: Long? = null
) {
    val isActive: Boolean get() = this != RecordFilter()

    fun matches(record: ExpenseRecord): Boolean =
        (paymentMethod == null || record.paymentMethod == paymentMethod || (paymentMethod.isNotEmpty() && record.transferTo == paymentMethod)) &&
            (excluded == null || record.excluded == excluded) &&
            (fromDay == null || record.epochDay >= fromDay) &&
            (toDay == null || record.epochDay <= toDay) &&
            (minCents == null || record.amountCents >= minCents) &&
            (maxCents == null || record.amountCents <= maxCents)
}
