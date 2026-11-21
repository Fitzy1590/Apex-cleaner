package com.fitzy.apexcleaner.core

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightlyRulesTest {
    @Test fun needsAllThreeConditions() {
        assertTrue(NightlyRules.canRun(true, 81, false, 30))
        assertFalse(NightlyRules.canRun(false, 90, false, 60))
        assertFalse(NightlyRules.canRun(true, 80, false, 60))
        assertFalse(NightlyRules.canRun(true, 90, true, 60))
        assertFalse(NightlyRules.canRun(true, 90, false, 29))
    }

    @Test fun delayTargetsNextThreeAm() {
        val c = Calendar.getInstance().apply { set(2026, 0, 5, 23, 0, 0); set(Calendar.MILLISECOND, 0) }
        assertEquals(4L * 3_600_000, NightlyRules.delayToNextThreeAm(c))
        val d = Calendar.getInstance().apply { set(2026, 0, 5, 1, 0, 0); set(Calendar.MILLISECOND, 0) }
        assertEquals(2L * 3_600_000, NightlyRules.delayToNextThreeAm(d))
    }
}
