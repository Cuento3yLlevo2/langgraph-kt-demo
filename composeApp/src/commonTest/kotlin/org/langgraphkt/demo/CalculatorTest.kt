package org.langgraphkt.demo

import org.langgraphkt.demo.workflows.evaluate
import org.langgraphkt.demo.workflows.formatNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CalculatorTest {
    @Test
    fun respectsPrecedenceAndParentheses() {
        assertEquals("84", formatNumber(evaluate("12 * (3 + 4)")))
        assertEquals("14", formatNumber(evaluate("2 + 3 * 4")))
        assertEquals("-2.5", formatNumber(evaluate("-(10 / 4)")))
        assertEquals("0.333333", formatNumber(evaluate("1 / 3")))
    }

    @Test
    fun rejectsMalformedInput() {
        assertFailsWith<IllegalArgumentException> { evaluate("2 +") }
        assertFailsWith<IllegalArgumentException> { evaluate("(2 + 3") }
        assertFailsWith<IllegalArgumentException> { evaluate("2 3") }
        assertFailsWith<IllegalArgumentException> { evaluate("1 / 0") }
    }
}
