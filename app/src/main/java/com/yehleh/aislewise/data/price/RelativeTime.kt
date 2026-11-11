package com.yehleh.aislewise.data.price

import kotlin.math.max

/** Turns a past timestamp into a short, human phrase like "3 weeks ago". */
object RelativeTime {
    fun format(updatedAt: Long, now: Long = System.currentTimeMillis()): String {
        val elapsed = max(0L, now - updatedAt)
        val minutes = elapsed / MINUTE
        val hours = elapsed / HOUR
        val days = elapsed / DAY
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> count(minutes, "minute")
            hours < 24 -> count(hours, "hour")
            days < 7 -> count(days, "day")
            days < 30 -> count(days / 7, "week")
            days < 365 -> count(days / 30, "month")
            else -> count(days / 365, "year")
        }
    }

    private fun count(value: Long, unit: String): String =
        "$value $unit${if (value == 1L) "" else "s"} ago"

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
}
