package com.ibis.expense.ui

import com.ibis.expense.data.ExpenseRecord
import com.ibis.expense.data.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordCsvTest {
    private val day = LocalDate.of(2026, 9, 1).toEpochDay()
    private val header = "日期,金额,分类,备注,代付,支付方式,类型,转入账户,原记录序号"

    @Test
    fun typedRoundTripPreservesEveryTypeSpecialCharactersAndFileRelativeRefunds() {
        val expense = record(41).copy(
            category = "购物,\"共同购买\"",
            note = "\n第一行,\"AA\"\r\n第二行\n第三行\n",
            excluded = true,
            recurringId = 12
        )
        val refund = expense.copy(id = 500, amountCents = 2500, epochDay = day + 1,
            type = TransactionType.REFUND, relatedRecordId = 41, recurringId = 0)
        val transfer = record(900).copy(type = TransactionType.TRANSFER,
            paymentMethod = PaymentMethod.WECHAT, transferTo = PaymentMethod.ALIPAY)
        val income = record(300).copy(type = TransactionType.INCOME, paymentMethod = PaymentMethod.WECHAT)
        val unassigned = record(7).copy(paymentMethod = "")
        val original = listOf(refund, expense, transfer, income, unassigned)

        val csv = encodeRecordCsv(original)
        val decoded = decodeRecordCsv(csv)

        assertTrue(csv.startsWith("\uFEFF$header\n"))
        assertEquals(0, decoded.skipped)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), decoded.records.map { it.id })
        assertEquals(2L, decoded.records.first().relatedRecordId)
        assertEquals("", decoded.records.last().paymentMethod)
        original.zip(decoded.records).forEachIndexed { index, (before, after) ->
            assertEquals(before.copy(id = index + 1L, createdAt = after.createdAt, recurringId = 0,
                relatedRecordId = if (before.type == TransactionType.REFUND) 2L else 0L), after)
        }
        assertTrue(decoded.records.zipWithNext().all { (first, second) -> second.createdAt == first.createdAt + 1 })
    }

    @Test
    fun legacyThreeToSixColumnsKeepBlankAccountsAndQuotedNotes() {
        val csv = "\uFEFF日期,金额,分类,备注,代付,支付方式\r\n" +
            "2026-09-01,1.23,餐饮\r\n" +
            "2026-09-02,4.56,餐饮,\"早餐,咖啡 \"\"AA\"\"\r\n第二行\"\r\n" +
            "2026-09-03,7.89,购物,代买,是\r\n" +
            "2026-09-04,1.00,交通,地铁,否,\r\n" +
            "2026-09-05,2.00,交通,公交,否,支付宝\r\n"

        val decoded = decodeRecordCsv(csv)

        assertEquals(0, decoded.skipped)
        assertEquals(5, decoded.records.size)
        assertEquals("早餐,咖啡 \"AA\"\r\n第二行", decoded.records[1].note)
        assertEquals(listOf("", "", "", "", PaymentMethod.ALIPAY), decoded.records.map { it.paymentMethod })
        assertTrue(decoded.records.all { it.type == TransactionType.EXPENSE && it.relatedRecordId == 0L })
        assertTrue(decoded.records[2].excluded)
    }

    @Test
    fun invalidLegacyRowsAreSkippedWithoutRenumberingTheRemainingDataRows() {
        val decoded = decodeRecordCsv("日期,金额,分类,备注\n" +
            "2026-02-30,12.34,餐饮,不存在的日期\n" +
            "2026-02-28,5.00,餐饮,有效日期\n" +
            "2026-03-01,-1,餐饮,非法金额\n" +
            "2026-03-02,1,餐饮,多余字段,否,,尾列\n")

        assertEquals(3, decoded.skipped)
        assertEquals(2L, decoded.records.single().id)
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), decoded.records.single().epochDay)
    }

    @Test
    fun unknownTypesMissingParentsAndCumulativeExcessRefundsRejectTheTypedFile() {
        val expense = "2026-09-01,100.00,购物,消费,否,支付宝,支出,,"
        val invalidBatches = listOf(
            listOf("2026-09-01,100.00,购物,未知类型,否,支付宝,奖励,,"),
            listOf(expense, "2026-09-02,25.00,购物,退款,否,支付宝,退款,,99"),
            listOf(expense, "2026-09-02,25.00,购物,退款一,否,支付宝,退款,,1",
                "2026-09-03,75.01,购物,退款二,否,支付宝,退款,,1")
        )
        invalidBatches.forEach { rows ->
            assertThrows(IllegalArgumentException::class.java) { decodeRecordCsv(typedCsv(*rows.toTypedArray())) }
        }
    }

    @Test
    fun refundFieldsAndDatesAreValidatedWithoutSilentlyInheritingFromTheExpense() {
        val expense = "2026-09-01,100.00,购物,消费,否,支付宝,支出,,"
        listOf(
            "2026-09-02,25.00,餐饮,退款,否,支付宝,退款,,1",
            "2026-09-02,25.00,购物,退款,否,微信,退款,,1",
            "2026-09-02,25.00,购物,退款,是,支付宝,退款,,1",
            "2026-08-31,25.00,购物,退款,否,支付宝,退款,,1"
        ).forEach { refund ->
            assertThrows(IllegalArgumentException::class.java) { decodeRecordCsv(typedCsv(expense, refund)) }
        }
    }

    @Test
    fun typedFilesRejectLegacyRowsTruncatedRowsInvalidDatesAndFutureColumns() {
        val expense = "2026-09-01,100.00,购物,消费,否,支付宝,支出,,"
        listOf(
            typedCsv(expense, "2026-09-02,1.00,餐饮"),
            typedCsv(expense, "2026-09-02,1.00,餐饮,缺一列,否,支付宝,支出,"),
            typedCsv("2026-02-30,100.00,购物,无效日期,否,支付宝,支出,,", expense),
            typedCsv(expense, ",,,,,,,,"),
            "$header,未来列\n$expense,未来内容"
        ).forEach { csv ->
            assertThrows(IllegalArgumentException::class.java) { decodeRecordCsv(csv) }
        }
    }

    @Test
    fun transferAccountsAndTypeSpecificFieldsMustBeValid() {
        listOf(
            "2026-09-01,10.00,转账,同账户,否,微信,转账,微信,",
            "2026-09-01,10.00,购物,错误转入,否,支付宝,支出,微信,",
            "2026-09-01,10.00,收入,错误代付,是,微信,收入,,",
            "2026-09-01,10.00,购物,非法标记,可能,支付宝,支出,,",
            "2026-09-01,10.00,购物,非法引用,否,支付宝,支出,,非整数"
        ).forEach { row ->
            assertThrows(IllegalArgumentException::class.java) { decodeRecordCsv(typedCsv(row)) }
        }
    }

    @Test
    fun malformedQuotesRejectTheFileAndNoValidRowsHaveAClearError() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeRecordCsv("日期,金额,分类,备注\n2026-09-01,1,餐饮,\"没有结束")
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeRecordCsv("日期,金额,分类,备注\n2026-09-01,1,餐饮,\"结束了\"还有文字")
        }
        val error = assertThrows(IllegalStateException::class.java) {
            decodeRecordCsv("日期,金额,分类\n2026-02-30,1,餐饮")
        }
        assertEquals("文件中没有有效记录", error.message)
    }

    @Test
    fun exportingRefundsWithoutTheirOriginalExpenseIsRejected() {
        val refund = record(72).copy(type = TransactionType.REFUND, relatedRecordId = 900)
        assertThrows(IllegalArgumentException::class.java) { encodeRecordCsv(listOf(refund)) }
    }

    private fun typedCsv(vararg rows: String) = "$header\n" + rows.joinToString("\n")

    private fun record(id: Long) = ExpenseRecord(
        id = id,
        amountCents = 10000,
        epochDay = day,
        createdAt = 1000 + id,
        category = "购物",
        note = "消费",
        paymentMethod = PaymentMethod.ALIPAY
    )
}
