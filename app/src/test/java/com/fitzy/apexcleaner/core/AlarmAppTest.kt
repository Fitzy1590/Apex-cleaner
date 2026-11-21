package com.fitzy.apexcleaner.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmAppTest {
    @Test fun clockAppsCountButFantasyAndNewsAppsDoNot() {
        assertTrue(ProtectedApps.looksLikeAlarmApp("com.google.android.deskclock"))
        assertTrue(ProtectedApps.looksLikeAlarmApp("Alarm Clock Xtreme"))
        assertFalse(ProtectedApps.looksLikeAlarmApp("com.yahoo.mobile.client.android.fantasyfootball"))
        assertFalse(ProtectedApps.looksLikeAlarmApp("Yahoo Fantasy Sports"))
    }
}
