package com.yehleh.aislewise.data.price

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Converts between the cents stored in the database and the prices users type and read. */
object PriceParser {
    private val NOT_A_PRICE = Regex("[^0-9.]+")

    /**
     * Parses a typed price such as "$3.49" or "1,299.99" into whole cents. Returns null when
     * [text] is blank or isn't a valid non-negative amount.
     */
    fun parseToCents(text: String): Long? {
        val cleaned = text.replace(",", "").replace(NOT_A_PRICE, "")
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
        return try {
            val value = BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
            if (value.signum() < 0) null else value.movePointRight(2).longValueExact()
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun format(cents: Long, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getCurrencyInstance(locale).format(BigDecimal.valueOf(cents, 2))

    /** Plain decimal text for an editable field, e.g. 349 -> "3.49" regardless of locale. */
    fun toEditable(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
}
