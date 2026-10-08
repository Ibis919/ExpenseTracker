package com.ibis.expense.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountExpressionTest {
    @Test
    fun calculatesDecimalExpressionsWithPrecedenceAndLeftAssociativity(): Unit {
        assertSuccess("19.3+2.36", "21.66", 2166L)
        assertSuccess("2+3×4", "14.00", 1400L)
        assertSuccess("8÷4×2", "4.00", 400L)
        assertSuccess("0.1+0.2", "0.30", 30L)
        assertSuccess("1÷3×3", "1.00", 100L)
        assertSuccess("10÷4", "2.50", 250L)
        assertSuccess("1÷8", "0.13", 13L)
    }

    @Test
    fun acceptsAsciiOperatorsWhitespaceAndPartialDecimalForms(): Unit {
        assertSuccess("2 * 3 / 2", "3.00", 300L)
        assertSuccess(".5 + 19.", "19.50", 1950L)
        assertSuccess(" 1 + 2 ", "3.00", 300L)
        assertInvalid("1 2")
    }

    @Test
    fun reportsDivisionByZeroAndMalformedExpressions(): Unit {
        val divisionByZero = calculateAmountExpression("10÷0")
        assertEquals("除数不能为0", divisionByZero.error)
        assertNull(divisionByZero.resultText)
        assertNull(divisionByZero.cents)

        assertInvalid("12+")
        val calculatedDivisionByZero = calculateAmountExpression("2-5/0")
        assertEquals("除数不能为0", calculatedDivisionByZero.error)
        assertNull(calculatedDivisionByZero.resultText)
        assertNull(calculatedDivisionByZero.cents)
        assertInvalid("2++3")
        assertInvalid("1.2.3")
        assertInvalid(".")
        assertInvalid("(1+2)")
        assertInvalid("1%2")
    }

    @Test
    fun keepsCalculatedTextForNonFillableAmounts(): Unit {
        assertAmountError("0", "0.00", "金额必须大于0")
        assertAmountError("2-5", "-3.00", "金额必须大于0")
        assertSuccess("-2+5", "3.00", 300L)
        assertSuccess("2-5+10", "7.00", 700L)
        assertAmountError("10000000", "10000000.00", "金额超过9999999.99元")
        assertAmountError("9999999.99+0.01", "10000000.00", "金额超过9999999.99元")
        assertAmountError("0.004", "0.00", "金额必须大于0")
        assertSuccess("0.005", "0.01", 1L)
        assertSuccess("9999999.99", "9999999.99", 999_999_999L)
    }

    @Test
    fun rejectsOversizedInput(): Unit {
        assertEquals("算式不能超过128个字符", calculateAmountExpression("1".repeat(129)).error)
    }

    private fun assertSuccess(input: String, resultText: String, cents: Long) {
        val result = calculateAmountExpression(input)
        assertEquals(input, resultText, result.resultText)
        assertEquals(input, cents, result.cents)
        assertNull(input, result.error)
    }

    private fun assertInvalid(input: String) {
        val result = calculateAmountExpression(input)
        assertNull(input, result.resultText)
        assertNull(input, result.cents)
        assertEquals(input, "算式不完整或格式错误", result.error)
    }

    private fun assertAmountError(input: String, resultText: String, error: String?) {
        val result = calculateAmountExpression(input)
        assertEquals(input, resultText, result.resultText)
        assertEquals(input, error, result.error)
        if (error != null) assertNull(input, result.cents)
    }
}
