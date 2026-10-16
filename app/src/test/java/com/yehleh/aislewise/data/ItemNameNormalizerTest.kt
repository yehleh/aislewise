package com.yehleh.aislewise.data

import com.yehleh.aislewise.data.normalize.ItemNameNormalizer.normalize
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemNameNormalizerTest {

    @Test fun `case and whitespace are ignored`() {
        assertEquals("whole milk", normalize("  Whole   MILK "))
    }

    @Test fun `plurals match singulars`() {
        listOf(
            "Eggs" to "Egg",
            "Tomatoes" to "Tomato",
            "Berries" to "Berry",
            "Cookies" to "Cookie",
            "Peaches" to "Peach",
            "Boxes" to "Box",
            "Cheeses" to "Cheese",
            "Baby carrots" to "baby carrot",
            "Shoes" to "Shoe",
        ).forEach { (plural, singular) -> assertEquals(plural, normalize(singular), normalize(plural)) }
    }

    @Test fun `words that only look plural are kept`() {
        assertEquals("hummus", normalize("Hummus"))
        assertEquals("asparagus", normalize("asparagus"))
        assertEquals("swiss", normalize("Swiss"))
        assertEquals("molasses", normalize("molasses"))
        assertEquals("oj", normalize("OJ"))
    }

    @Test fun `stray punctuation is dropped`() {
        assertEquals("paper towel", normalize("paper towels!"))
        assertEquals("ben & jerry's", normalize("Ben & Jerry's"))
    }
}
