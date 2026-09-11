package com.ibis.expense.ui

val Categories = listOf("餐饮", "交通", "购物", "日用", "娱乐", "医疗", "其他")

val EmojiChoices = listOf(
    "🍜", "🚗", "🛍️", "🧴", "🎮", "💊", "📦", "☕", "🍎", "🏠",
    "💡", "📱", "👕", "🐾", "✈️", "🎬", "📚", "🎁", "⚽", "🎓",
    "🏥", "💰", "🧾", "🔧"
)

fun categoryEmoji(category: String, custom: Map<String, String> = emptyMap()): String =
    custom[category] ?: when (category) {
        "餐饮" -> "🍜"
        "交通" -> "🚗"
        "购物" -> "🛍️"
        "日用" -> "🧴"
        "娱乐" -> "🎮"
        "医疗" -> "💊"
        "其他" -> "📦"
        else -> "📦"
    }
