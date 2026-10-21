package com.yehleh.aislewise.data

import com.yehleh.aislewise.data.StorePalette.DARK_TEXT
import com.yehleh.aislewise.data.StorePalette.LIGHT_TEXT
import com.yehleh.aislewise.data.db.entities.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorePaletteTest {

    @Test fun `dark colors get white text and light colors get dark text`() {
        assertEquals(LIGHT_TEXT, StorePalette.onColor(0xFF3949AB.toInt())) // indigo
        assertEquals(LIGHT_TEXT, StorePalette.onColor(0xFF6D4C41.toInt())) // brown
        assertEquals(DARK_TEXT, StorePalette.onColor(0xFFFDD835.toInt())) // yellow
        assertEquals(DARK_TEXT, StorePalette.onColor(0xFF4DD0E1.toInt())) // light cyan
        assertEquals(DARK_TEXT, StorePalette.onColor(0xFFFFFFFF.toInt()))
        assertEquals(LIGHT_TEXT, StorePalette.onColor(0xFF000000.toInt()))
    }

    @Test fun `every palette color has readable text`() {
        StorePalette.COLORS.forEach { bg ->
            val text = StorePalette.onColor(bg)
            val ratio = StorePalette.contrast(StorePalette.luminance(bg), StorePalette.luminance(text))
            // WCAG AA for large/bold text; badges are short bold labels.
            assertTrue("0x%08X has contrast %.2f".format(bg, ratio), ratio >= 3.0)
        }
    }

    @Test fun `palette colors are distinct`() {
        assertEquals(StorePalette.COLORS.size, StorePalette.COLORS.toSet().size)
    }

    @Test fun `new stores get the first unused color and cycle once all are taken`() {
        val c = StorePalette.COLORS
        assertEquals(c[0], StorePalette.nextUnused(emptyList()))
        assertEquals(c[1], StorePalette.nextUnused(listOf(c[0], c[2])))
        assertEquals(c[0], StorePalette.nextUnused(c))
        assertEquals(c[1], StorePalette.nextUnused(c + c[0]))
    }

    @Test fun `stores without a chosen color get a stable default`() {
        val store = Store(id = 3, name = "Target")
        assertEquals(StorePalette.COLORS[2], StorePalette.colorOf(store))
        assertEquals(0xFF123456.toInt(), StorePalette.colorOf(store.copy(color = 0xFF123456.toInt())))
    }
}
