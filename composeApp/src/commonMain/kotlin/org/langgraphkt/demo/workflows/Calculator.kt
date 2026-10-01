package org.langgraphkt.demo.workflows

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * Evaluates an arithmetic expression with `+ - * /` and parentheses.
 *
 * @throws IllegalArgumentException if the expression is malformed or divides by zero.
 */
fun evaluate(expression: String): Double {
    val parser = ExpressionParser(expression)
    val value = parser.expression()
    require(parser.atEnd()) { "Unexpected '${parser.rest()}' in '$expression'" }
    return value
}

/** Formats a result the same way on every platform: `84` rather than `84.0`, at most six decimals. */
fun formatNumber(value: Double): String =
    if (value == floor(value) && abs(value) < 1e15) {
        value.toLong().toString()
    } else {
        (round(value * 1e6) / 1e6).toString()
    }

private class ExpressionParser(private val text: String) {
    private var position = 0

    fun atEnd(): Boolean {
        skipSpaces()
        return position == text.length
    }

    fun rest(): String = text.substring(position)

    fun expression(): Double {
        var value = term()
        while (true) {
            value = when {
                take('+') -> value + term()
                take('-') -> value - term()
                else -> return value
            }
        }
    }

    private fun term(): Double {
        var value = factor()
        while (true) {
            value = when {
                take('*') -> value * factor()
                take('/') -> {
                    val divisor = factor()
                    require(divisor != 0.0) { "Division by zero" }
                    value / divisor
                }
                else -> return value
            }
        }
    }

    private fun factor(): Double = when {
        take('-') -> -factor()
        take('(') -> expression().also { require(take(')')) { "Missing ')'" } }
        else -> number()
    }

    private fun number(): Double {
        skipSpaces()
        val start = position
        while (position < text.length && (text[position].isDigit() || text[position] == '.')) position++
        return text.substring(start, position).toDoubleOrNull()
            ?: throw IllegalArgumentException("Expected a number at '${text.substring(start)}'")
    }

    private fun take(char: Char): Boolean {
        skipSpaces()
        return (position < text.length && text[position] == char).also { if (it) position++ }
    }

    private fun skipSpaces() {
        while (position < text.length && text[position].isWhitespace()) position++
    }
}
