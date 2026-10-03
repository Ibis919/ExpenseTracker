package com.ibis.expense.ui

import com.ibis.expense.data.CategoryTotal
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsTest {
    @Test
    fun absentCurrentCategoryDropsToZeroInsteadOfKeepingLastMonthsAmount() {
        val changes = categoryChanges(listOf(CategoryTotal("餐饮", 3000)), listOf(CategoryTotal("交通", 20000)))
        val traffic = changes.single { it.category == "交通" }
        assertEquals(0L, traffic.currentCents)
        assertEquals(-20000L, traffic.deltaCents)
        assertEquals("交通", changes.first().category)
    }

    @Test
    fun newAndUnchangedCategoriesHaveTheirActualDeltas() {
        val changes = categoryChanges(
            listOf(CategoryTotal("餐饮", 5000), CategoryTotal("购物", 3000)),
            listOf(CategoryTotal("餐饮", 5000))
        )
        assertEquals(3000L, changes.single { it.category == "购物" }.deltaCents)
        assertEquals(0L, changes.single { it.category == "餐饮" }.deltaCents)
    }
}
