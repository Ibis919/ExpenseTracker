package com.ibis.expense.ui

import com.ibis.expense.data.ExpenseRecord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordFilterTest {
    private val record = ExpenseRecord(amountCents = 5000, epochDay = 100, createdAt = 1,
        category = "餐饮", note = "", excluded = true, paymentMethod = PaymentMethod.ALIPAY)

    @Test
    fun filtersCombineAndIncludeDateAndAmountBoundaries() {
        val filter = RecordFilter(PaymentMethod.ALIPAY, true, 100, 110, 5000, 10000)
        assertTrue(filter.isActive)
        assertTrue(filter.matches(record))
        assertTrue(filter.matches(record.copy(epochDay = 110, amountCents = 10000)))
        assertFalse(filter.matches(record.copy(epochDay = 99)))
        assertFalse(filter.matches(record.copy(amountCents = 10001)))
        assertFalse(filter.matches(record.copy(excluded = false)))
        assertFalse(filter.matches(record.copy(paymentMethod = PaymentMethod.WECHAT)))
    }

    @Test
    fun clearFilterIncludesLegacyAccountsAndBothStatuses() {
        assertFalse(RecordFilter().isActive)
        assertTrue(RecordFilter().matches(record))
        assertTrue(RecordFilter().matches(record.copy(paymentMethod = "", excluded = false)))
        assertTrue(RecordFilter(paymentMethod = "").matches(record.copy(paymentMethod = "")))
    }
}
