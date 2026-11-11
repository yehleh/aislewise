package com.yehleh.aislewise.data

import com.yehleh.aislewise.data.price.RelativeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_700_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test fun `under a minute is just now`() {
        assertEquals("just now", RelativeTime.format(now, now))
        assertEquals("just now", RelativeTime.format(now - 30_000, now))
    }

    @Test fun `singular units do not get an s`() {
        assertEquals("1 minute ago", RelativeTime.format(now - minute, now))
        assertEquals("1 hour ago", RelativeTime.format(now - hour, now))
        assertEquals("1 day ago", RelativeTime.format(now - day, now))
        assertEquals("1 week ago", RelativeTime.format(now - 7 * day, now))
        assertEquals("1 year ago", RelativeTime.format(now - 365 * day, now))
    }

    @Test fun `plural units scale up`() {
        assertEquals("3 minutes ago", RelativeTime.format(now - 3 * minute, now))
        assertEquals("5 hours ago", RelativeTime.format(now - 5 * hour, now))
        assertEquals("3 weeks ago", RelativeTime.format(now - 21 * day, now))
        assertEquals("2 months ago", RelativeTime.format(now - 60 * day, now))
        assertEquals("2 years ago", RelativeTime.format(now - 730 * day, now))
    }

    @Test fun `future timestamps read as just now`() {
        assertEquals("just now", RelativeTime.format(now + day, now))
    }
}
