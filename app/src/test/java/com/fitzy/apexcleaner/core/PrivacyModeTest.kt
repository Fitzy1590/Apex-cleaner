package com.fitzy.apexcleaner.core

import org.junit.Assert.*
import org.junit.Test

class PrivacyModeTest {
    @Test fun onlyCameraAndMicAreAllowed() {
        assertTrue(PrivacyModeRules.isSensor("camera")); assertTrue(PrivacyModeRules.isSensor("microphone"))
        assertFalse(PrivacyModeRules.isSensor("accelerometer")); assertFalse(PrivacyModeRules.isSensor("camera; reboot"))
    }
    @Test fun readsThePhoneReply() {
        assertEquals(1, PrivacyModeRules.parseBlocked("true\n")); assertEquals(0, PrivacyModeRules.parseBlocked("false"))
        assertEquals(-1, PrivacyModeRules.parseBlocked("Exception: denied"))
    }
    @Test fun leavesAlreadyBlockedSensorsAlone() {
        assertEquals(listOf("camera"), PrivacyModeRules.toBlock(mapOf("camera" to 0, "microphone" to 1)))
        assertTrue(PrivacyModeRules.toBlock(mapOf("camera" to -1, "microphone" to -1)).isEmpty())
    }
}
