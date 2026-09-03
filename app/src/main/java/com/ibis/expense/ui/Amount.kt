package com.ibis.expense.ui

import java.util.Locale

fun formatAmount(cents: Long): String =
    String.format(Locale.US, "%.2f", cents / 100.0)

fun centsToInput(cents: Long): String {
    val yuan = cents / 100
    val remainder = (cents % 100).toInt()
    return when {
        remainder == 0 -> yuan.toString()
        remainder % 10 == 0 -> "$yuan.${remainder / 10}"
        else -> "$yuan.${remainder.toString().padStart(2, '0')}"
    }
}

fun parseAmountToCents(input: String): Long? {
    val text = input.trim()
    if (!Regex("""^\d{1,7}(\.\d{1,2})?$""").matches(text)) return null
    val dot = text.indexOf('.')
    val yuan = if (dot == -1) text.toLong() else text.substring(0, dot).toLong()
    val fraction = if (dot == -1) "" else text.substring(dot + 1)
    val fractionCents = when (fraction.length) {
        0 -> 0L
        1 -> fraction.toLong() * 10
        else -> fraction.substring(0, 2).toLong()
    }
    val result = yuan * 100 + fractionCents
    return if (result > 0) result else null
}
