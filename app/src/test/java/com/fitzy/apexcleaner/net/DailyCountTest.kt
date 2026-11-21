package com.fitzy.apexcleaner.net

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyCountTest {
    @Test fun countsWithinOneDay() {
        val c = DailyCount()
        c.add("2026-11-21"); c.add("2026-11-21"); c.add("2026-11-21")
        assertEquals(3, c.get("2026-11-21"))
    }

    @Test fun startsAgainOnANewDay() {
        val c = DailyCount("2026-11-21", 40)
        assertEquals(1, c.add("2026-11-22"))
        assertEquals(1, c.get("2026-11-22"))
    }

    @Test fun readingANewDayShowsZero() {
        assertEquals(0, DailyCount("2026-11-21", 40).get("2026-11-22"))
    }
}
