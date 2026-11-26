package com.fitzy.apexcleaner.net

import org.junit.Assert.*
import org.junit.Test

class QuietRouterTest {
    private val night = QuietRule("Night", true, listOf("games.example.com"), 22 * 60, 6 * 60, true)

    @Test fun windowWrapsPastMidnight() {
        assertTrue(night.activeAt(23 * 60)); assertTrue(night.activeAt(2 * 60)); assertTrue(night.activeAt(0))
        assertFalse(night.activeAt(6 * 60)); assertFalse(night.activeAt(12 * 60)); assertTrue(night.activeAt(22 * 60))
    }

    @Test fun disabledOrEmptyWindowNeverActive() {
        assertFalse(night.copy(enabled = false).activeAt(23 * 60))
        assertFalse(night.copy(startMin = 60, endMin = 60).activeAt(60))
    }

    @Test fun blocksSocialAndCustomOnlyInsideWindow() {
        val r = listOf(night)
        assertTrue(QuietHours.blocksNow(r, "www.instagram.com", 23 * 60))
        assertTrue(QuietHours.blocksNow(r, "games.example.com", 1 * 60))
        assertFalse(QuietHours.blocksNow(r, "www.instagram.com", 12 * 60))
        assertFalse(QuietHours.blocksNow(r, "bank.example.org", 23 * 60))
    }

    @Test fun ruleSurvivesSaveAndLoad() {
        assertEquals(night, QuietRule.decode(night.encode()))
        assertNull(QuietRule.decode("garbage"))
        assertNull(QuietRule.decode("a|1||2000|5|1"))
    }

    @Test fun pinMustBeFourDigitsAndIsNotStoredPlain() {
        val p = PinLock()
        assertFalse(p.set("12a4")); assertFalse(p.set("123")); assertTrue(p.set("4821"))
        assertFalse(p.state().contains("4821"))
        assertTrue(p.check("4821", 0)); assertFalse(p.check("1111", 0))
        assertTrue(PinLock.parse(p.state()).check("4821", 0))
    }

    @Test fun fiveWrongTriesLockForFiveMinutes() {
        val p = PinLock().also { it.set("4821") }
        repeat(5) { assertFalse(p.check("0000", 1000)) }
        assertTrue(p.lockedFor(1000) > 0)
        assertFalse(p.check("4821", 2000))
        assertTrue(p.check("4821", 1000 + 5 * 60_000L + 1))
    }

    @Test fun routerScriptsUseTheAddressAndRefusePort853() {
        for (k in RouterScripts.Kind.values()) {
            val s = RouterScripts.generate(k, "192.168.1.1")!!
            assertTrue(k.name, s.contains("192.168.1.1")); assertTrue(k.name, s.contains("853"))
        }
        assertTrue(RouterScripts.generate(RouterScripts.Kind.ASUS, "10.0.0.2")!!.contains("-j DNAT --to-destination 10.0.0.2:53"))
        assertNull(RouterScripts.generate(RouterScripts.Kind.OPENWRT, "300.1.1.1"))
        assertNull(RouterScripts.generate(RouterScripts.Kind.OPENWRT, "1.1.1.1; reboot"))
    }
}
