package com.ibis.expense.ui

import com.ibis.expense.data.CategoryTotal
import kotlin.math.abs

data class CategoryChange(val category: String, val currentCents: Long, val deltaCents: Long)

fun categoryChanges(current: List<CategoryTotal>, previous: List<CategoryTotal>): List<CategoryChange> {
    val now = current.associate { it.category to it.totalCents }
    val before = previous.associate { it.category to it.totalCents }
    return (now.keys + before.keys).map { category ->
        val cents = now[category] ?: 0L
        CategoryChange(category, cents, cents - (before[category] ?: 0L))
    }.sortedByDescending { abs(it.deltaCents) }
}
