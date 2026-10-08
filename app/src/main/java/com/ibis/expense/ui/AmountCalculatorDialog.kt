package com.ibis.expense.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val MAX_EXPRESSION_LENGTH = 128

@Composable
fun AmountCalculatorDialog(
    initialAmount: String,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var expression by rememberSaveable { mutableStateOf(initialAmount.ifBlank { "0" }) }
    var hasCalculated by rememberSaveable { mutableStateOf(false) }
    val currentCalculation = calculateAmountExpression(expression)
    val calculation = currentCalculation.takeIf { hasCalculated }
    val canApply = currentCalculation.cents != null

    fun appendDigit(digit: String) {
        val base = if (hasCalculated) "" else expression
        val updated = when {
            base.isEmpty() -> digit
            base == "0" -> digit
            else -> base + digit
        }
        if (updated.length <= MAX_EXPRESSION_LENGTH) expression = updated
        hasCalculated = false
    }

    fun appendDecimal() {
        var base = if (hasCalculated) "" else expression
        if (base.isEmpty()) base = "0."
        else {
            val operandStart = base.indexOfLast { it in CALCULATOR_OPERATORS } + 1
            val operand = base.substring(operandStart)
            when {
                operand.contains('.') -> return
                operand.isEmpty() -> base += "0."
                else -> base += "."
            }
        }
        if (base.length <= MAX_EXPRESSION_LENGTH) expression = base
        hasCalculated = false
    }

    fun appendOperator(operator: Char) {
        val result = if (hasCalculated) calculateAmountExpression(expression).resultText else null
        var base = result ?: expression
        if (base.isEmpty()) base = "0"
        val updated = if (base.last() in CALCULATOR_OPERATORS) {
            base.dropLast(1) + operator
        } else {
            base + operator
        }
        if (updated.length <= MAX_EXPRESSION_LENGTH) expression = updated
        hasCalculated = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = (maxHeight - 32.dp).coerceAtLeast(200.dp)),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("计算金额", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            expression.ifEmpty { " " },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("calculator-expression"),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.End
                        )
                    }
                    calculation?.resultText?.let { result ->
                        Text(
                            "结果：$result",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calculator-result"),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.End
                        )
                    }
                    calculation?.error?.let { error ->
                        Text(
                            error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("calculator-error"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    CalculatorRow(
                        listOf("清空", "退格", "÷", "×"),
                        onClick = { label ->
                            when (label) {
                                "清空" -> { expression = ""; hasCalculated = false }
                                "退格" -> { expression = expression.dropLast(1); hasCalculated = false }
                                "÷" -> appendOperator('÷')
                                "×" -> appendOperator('×')
                            }
                        }
                    )
                    CalculatorRow(listOf("7", "8", "9", "−"), onClick = { label ->
                        if (label == "−") appendOperator('−') else appendDigit(label)
                    })
                    CalculatorRow(listOf("4", "5", "6", "+"), onClick = { label ->
                        if (label == "+") appendOperator('+') else appendDigit(label)
                    })
                    CalculatorRow(listOf("1", "2", "3", "＝"), onClick = { label ->
                        if (label == "＝") hasCalculated = true else appendDigit(label)
                    })
                    CalculatorRow(
                        listOf("关闭", "0", ".", "填入"),
                        onClick = { label ->
                            when (label) {
                                "关闭" -> onDismiss()
                                "0" -> appendDigit("0")
                                "." -> appendDecimal()
                                "填入" -> {
                                    val latest = calculateAmountExpression(expression)
                                    if (latest.cents != null) onApply(latest.resultText!!)
                                }
                            }
                        },
                        disabledLabels = if (canApply) emptySet() else setOf("填入")
                    )
                }
            }
        }
    }
}

private val CALCULATOR_OPERATORS = setOf('+', '−', '×', '÷', '-', '*', '/')

@Composable
private fun CalculatorRow(
    labels: List<String>,
    onClick: (String) -> Unit,
    disabledLabels: Set<String> = emptySet()
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { label ->
            val enabled = label !in disabledLabels
            val modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            if (label == "填入") {
                Button(
                    onClick = { onClick(label) },
                    enabled = enabled,
                    modifier = modifier,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            } else {
                OutlinedButton(
                    onClick = { onClick(label) },
                    enabled = enabled,
                    modifier = modifier,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
