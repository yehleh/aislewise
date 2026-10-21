package com.yehleh.aislewise.data

import com.yehleh.aislewise.data.db.entities.Store
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Badge colors for stores, and the readable text color to put on each. Colors are ARGB ints. */
object StorePalette {

    val COLORS: List<Int> = listOf(
        0xFF1E88E5, // blue
        0xFFE53935, // red
        0xFF43A047, // green
        0xFFFB8C00, // orange
        0xFF8E24AA, // purple
        0xFF00897B, // teal
        0xFFFDD835, // yellow
        0xFFF06292, // pink
        0xFF3949AB, // indigo
        0xFF4DD0E1, // light cyan
        0xFF6D4C41, // brown
        0xFFC0CA33, // lime
    ).map { it.toInt() }

    const val DARK_TEXT: Int = 0xFF1C1B1F.toInt()
    const val LIGHT_TEXT: Int = 0xFFFFFFFF.toInt()

    /** The store's chosen color, or a stable default for stores that never picked one. */
    fun colorOf(store: Store): Int = store.color ?: COLORS[((store.id - 1).mod(COLORS.size.toLong())).toInt()]

    /** First palette color not in [used]; cycles through the palette once every color is taken. */
    fun nextUnused(used: List<Int>): Int =
        COLORS.firstOrNull { it !in used } ?: COLORS[used.size % COLORS.size]

    /** White or dark text, whichever has the higher WCAG contrast ratio against [background]. */
    fun onColor(background: Int): Int {
        val bg = luminance(background)
        return if (contrast(bg, luminance(LIGHT_TEXT)) >= contrast(bg, luminance(DARK_TEXT))) LIGHT_TEXT else DARK_TEXT
    }

    internal fun contrast(l1: Double, l2: Double): Double = (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)

    /** WCAG relative luminance. */
    internal fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
