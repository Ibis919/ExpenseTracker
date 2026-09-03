package com.ibis.expense.ui

val Categories = listOf("餐饮", "交通", "购物", "日用", "娱乐", "医疗", "其他")

fun categoryEmoji(category: String): String = when (category) {
    "餐饮" -> "🍜"
    "交通" -> "🚗"
    "购物" -> "🛍️"
    "日用" -> "🧴"
    "娱乐" -> "🎮"
    "医疗" -> "💊"
    else -> "📦"
}
