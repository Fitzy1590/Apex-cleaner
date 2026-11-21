package com.fitzy.apexcleaner.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeMathTest {
    @Test fun microampsPassThrough() = assertEquals(1_500_000L, ChargeMath.normalizeMicroAmps(-1_500_000L))
    @Test fun milliampsAreScaledUp() = assertEquals(1_500_000L, ChargeMath.normalizeMicroAmps(1500L))
    @Test fun unsupportedCurrentIsZero() = assertEquals(0L, ChargeMath.normalizeMicroAmps(Long.MIN_VALUE))

    @Test fun wattsFromVoltageAndCurrent() {
        assertEquals(16.0f, ChargeMath.watts(4000, 4_000_000L)!!, 0.01f)
    }

    @Test fun unreadableOrAbsurdWattsAreNull() {
        assertNull(ChargeMath.watts(0, 1_000_000L))
        assertNull(ChargeMath.watts(4000, 0L))
        assertNull(ChargeMath.watts(4000, 90_000_000L))
    }

    @Test fun profilesFollowWatts() {
        assertEquals(ChargeProfile.NONE, ChargeMath.profileFor(10f, false))
        assertEquals(ChargeProfile.UNKNOWN, ChargeMath.profileFor(null, true))
        assertEquals(ChargeProfile.USB_5W, ChargeMath.profileFor(5f, true))
        assertEquals(ChargeProfile.FAST_15W, ChargeMath.profileFor(16f, true))
        assertEquals(ChargeProfile.SUPER_45W, ChargeMath.profileFor(40f, true))
    }

    @Test fun smoothingMovesGently() {
        assertEquals(100.0, ChargeMath.smooth(null, 100.0), 0.0)
        assertEquals(120.0, ChargeMath.smooth(100.0, 200.0), 0.001)
    }

    @Test fun warmAt40OrWhenPhoneSaysSo() {
        assertFalse(ChargeMath.isTooWarm(39.9f, 0))
        assertTrue(ChargeMath.isTooWarm(40f, null))
        assertTrue(ChargeMath.isTooWarm(30f, 2))
    }

    @Test fun etaIsZeroWhenFullAndNullWhenUnknown() {
        assertEquals(0 to 0, ChargeMath.etaRange(100, 0, 0))
        assertNull(ChargeMath.etaRange(50, 0, 1_000_000L))
        assertNull(ChargeMath.etaRange(50, 2_000_000L, 0L))
    }

    @Test fun etaSlowsDownAbove80() {
        // 4000 mAh phone at 3 A: from 40% vs from 85%.
        val from40 = ChargeMath.etaRange(40, 1_600_000L, 3_000_000L)!!
        val from85 = ChargeMath.etaRange(85, 3_400_000L, 3_000_000L)!!
        assertTrue(from40.first < from40.second)
        assertTrue(from85.second < from40.second)
        assertNotNull(from85)
        // The last 15% at double cost: about 24 min base, shown as a range around it.
        assertTrue(from85.first in 15..25)
    }

    @Test fun etaTextFormats() {
        assertEquals("40–50 min", ChargeMath.formatEta(40, 50))
        assertEquals("55 min to 1 h 10 min", ChargeMath.formatEta(55, 70))
    }

    @Test fun limitIsClampedToSafeRange() {
        assertEquals(50, ChargeMath.clampLimit(10))
        assertEquals(100, ChargeMath.clampLimit(130))
        assertEquals(80, ChargeMath.clampLimit(80))
    }

    @Test fun alertsFireOncePerCharge() {
        assertEquals(ChargeAlert.NONE, ChargeMath.decideAlert(70, 80, false, false))
        assertEquals(ChargeAlert.REACHED_LIMIT, ChargeMath.decideAlert(80, 80, false, false))
        assertEquals(ChargeAlert.NONE, ChargeMath.decideAlert(81, 80, true, false))
        assertEquals(ChargeAlert.FULL, ChargeMath.decideAlert(100, 80, true, false))
        assertEquals(ChargeAlert.NONE, ChargeMath.decideAlert(100, 80, true, true))
    }

    @Test fun limitOf100OnlyAlertsWhenFull() {
        assertEquals(ChargeAlert.NONE, ChargeMath.decideAlert(99, 100, false, false))
        assertEquals(ChargeAlert.FULL, ChargeMath.decideAlert(100, 100, false, false))
    }
}
