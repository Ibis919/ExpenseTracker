package com.ibis.expense.ui

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class AmountCalculation(
    val resultText: String? = null,
    val cents: Long? = null,
    val error: String? = null
)

private const val MAX_EXPRESSION_LENGTH = 128
private const val MAX_AMOUNT_CENTS = 999_999_999L
private val DIVISION_CONTEXT = MathContext.DECIMAL128

fun calculateAmountExpression(input: String): AmountCalculation {
    if (input.length > MAX_EXPRESSION_LENGTH) {
        return AmountCalculation(error = "算式不能超过${MAX_EXPRESSION_LENGTH}个字符")
    }

    val value = try {
        ExpressionParser(input).parse()
    } catch (_: DivisionByZeroException) {
        return AmountCalculation(error = "除数不能为0")
    } catch (_: InvalidExpressionException) {
        return AmountCalculation(error = "算式不完整或格式错误")
    }

    val roundedValue = value.setScale(2, RoundingMode.HALF_UP)
    val resultText = roundedValue.toPlainString()
    if (roundedValue.signum() <= 0) {
        return AmountCalculation(resultText = resultText, error = "金额必须大于0")
    }
    if (roundedValue > BigDecimal.valueOf(MAX_AMOUNT_CENTS, 2)) {
        return AmountCalculation(resultText = resultText, error = "金额超过9999999.99元")
    }

    val cents = roundedValue.movePointRight(2).longValueExact()
    return AmountCalculation(resultText = resultText, cents = cents)
}

private class ExpressionParser(private val input: String) {
    private var position = 0

    fun parse(): BigDecimal {
        skipWhitespace()
        val negative = take('-') || take('−')
        skipWhitespace()
        var value = parseTerm()
        if (negative) value = value.negate()

        while (true) {
            skipWhitespace()
            when (peek()) {
                '+' -> {
                    position++
                    value = value.add(parseTerm())
                }
                '-', '−' -> {
                    position++
                    value = value.subtract(parseTerm())
                }
                else -> break
            }
        }

        skipWhitespace()
        if (position != input.length) throw InvalidExpressionException()
        return value
    }

    private fun parseTerm(): BigDecimal {
        var value = parseNumber()
        while (true) {
            skipWhitespace()
            when (peek()) {
                '*', '×' -> {
                    position++
                    value = value.multiply(parseNumber())
                }
                '/', '÷' -> {
                    position++
                    val divisor = parseNumber()
                    if (divisor.signum() == 0) throw DivisionByZeroException()
                    value = value.divide(divisor, DIVISION_CONTEXT)
                }
                else -> return value
            }
        }
    }

    private fun parseNumber(): BigDecimal {
        skipWhitespace()
        val start = position
        var digits = 0
        while (peek()?.let { it in '0'..'9' } == true) {
            position++
            digits++
        }
        if (peek() == '.') {
            position++
            while (peek()?.let { it in '0'..'9' } == true) {
                position++
                digits++
            }
        }
        if (digits == 0) throw InvalidExpressionException()

        val token = input.substring(start, position)
        return try {
            BigDecimal(if (token.startsWith('.')) "0$token" else token)
        } catch (_: NumberFormatException) {
            throw InvalidExpressionException()
        }
    }

    private fun skipWhitespace() {
        while (peek()?.isWhitespace() == true) position++
    }

    private fun peek(): Char? = input.getOrNull(position)

    private fun take(char: Char): Boolean {
        if (peek() != char) return false
        position++
        return true
    }
}

private class InvalidExpressionException : RuntimeException()
private class DivisionByZeroException : RuntimeException()
