package com.yehleh.aislewise.data

import com.yehleh.aislewise.data.price.PriceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class PriceParserTest {
    @Test fun `parses common price formats to cents`() {
        assertEquals(349L, PriceParser.parseToCents("3.49"))
        assertEquals(349L, PriceParser.parseToCents("$3.49"))
        assertEquals(349L, PriceParser.parseToCents("  $3.49  "))
        assertEquals(300L, PriceParser.parseToCents("3"))
        assertEquals(129_999L, PriceParser.parseToCents("1,299.99"))
        assertEquals(49L, PriceParser.parseToCents(".49"))
    }

    @Test fun `rounds to the nearest cent`() {
        assertEquals(400L, PriceParser.parseToCents("3.999"))
        assertEquals(350L, PriceParser.parseToCents("3.495"))
    }

    @Test fun `rejects blank and non-price text`() {
        assertNull(PriceParser.parseToCents(""))
        assertNull(PriceParser.parseToCents("   "))
        assertNull(PriceParser.parseToCents("abc"))
        assertNull(PriceParser.parseToCents("."))
        assertNull(PriceParser.parseToCents("3.4.5"))
    }

    @Test fun `formats cents as currency`() {
        assertEquals("$3.49", PriceParser.format(349L, Locale.US))
        assertEquals("$0.05", PriceParser.format(5L, Locale.US))
        assertEquals("$1,299.99", PriceParser.format(129_999L, Locale.US))
    }

    @Test fun `editable text round-trips through the parser`() {
        assertEquals("3.49", PriceParser.toEditable(349L))
        assertEquals(349L, PriceParser.parseToCents(PriceParser.toEditable(349L)))
        assertEquals(129_999L, PriceParser.parseToCents(PriceParser.toEditable(129_999L)))
    }
}
