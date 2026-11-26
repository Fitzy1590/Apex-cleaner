package com.fitzy.apexcleaner.core

import org.junit.Assert.*
import org.junit.Test

class InstantModeTest {
    @Test fun onlyTheThreeAnimationKeysAreAllowed() {
        assertTrue(InstantModeRules.isAnimKey("window_animation_scale"))
        assertFalse(InstantModeRules.isAnimKey("alarm_volume"))
        assertFalse(InstantModeRules.isAnimKey("window_animation_scale; rm -rf /"))
    }
    @Test fun scaleMustBeAPlainNumber() {
        assertTrue(InstantModeRules.isValidScale("0"))
        assertTrue(InstantModeRules.isValidScale("1.5"))
        assertFalse(InstantModeRules.isValidScale("1; reboot"))
        assertFalse(InstantModeRules.isValidScale("99"))
    }
    @Test fun missingOldValueRestoresToNormal() {
        assertEquals("1.0", InstantModeRules.restoreValue(null))
        assertEquals("1.0", InstantModeRules.restoreValue("null"))
        assertEquals("0.5", InstantModeRules.restoreValue("0.5"))
    }
    @Test fun clockAndAlarmAppsAreAlwaysProtected() {
        for (p in listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock"))
            assertTrue(p, p in ProtectedApps.ALWAYS_PROTECTED)
    }
}
