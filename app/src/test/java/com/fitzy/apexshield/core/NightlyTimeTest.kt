package com.fitzy.apexshield.core

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class NightlyTimeTest {
    private fun at(h: Int, m: Int) = Calendar.getInstance().apply { set(2026, 10, 21, h, m, 0); set(Calendar.MILLISECOND, 0) }

    @Test fun beforeThreeWaitsUntilToday() =
        assertEquals(60 * 60_000L, PowerPolicy.millisUntil3Am(at(2, 0)))

    @Test fun afterThreeWaitsUntilTomorrow() =
        assertEquals(23 * 60 * 60_000L, PowerPolicy.millisUntil3Am(at(4, 0)))

    @Test fun exactlyThreeWaitsAFullDay() =
        assertEquals(24 * 60 * 60_000L, PowerPolicy.millisUntil3Am(at(3, 0)))
}
