package com.syedafridi.productivity_agent

import com.syedafridi.productivity_agent.services.AutonomousQuotaManager
import com.syedafridi.productivity_agent.services.QuotaWindow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutonomousQuotaManagerTest {

    @Before
    fun setUp() {
        AutonomousQuotaManager.resetForTesting()
    }

    @After
    fun tearDown() {
        AutonomousQuotaManager.resetForTesting()
    }

    @Test
    fun testDefaultQuotaLimits() {
        AutonomousQuotaManager.setHourForTesting(9) // Morning
        val snapshot = AutonomousQuotaManager.getSnapshot()
        assertEquals(1800L, snapshot.gamingLimitSeconds) // 30 minutes
        assertEquals(3600L, snapshot.reelsLimitSeconds)  // 60 minutes total daily
        assertEquals(1200L, snapshot.currentWindowLimit) // 20 minutes window limit
        assertEquals(0L, snapshot.gamingSecondsUsed)
        assertEquals(0L, snapshot.reelsSecondsUsed)
        assertEquals(QuotaWindow.MORNING, snapshot.currentWindow)
        assertFalse(AutonomousQuotaManager.isGamingExhausted())
        assertFalse(AutonomousQuotaManager.isCurrentWindowExhausted())
    }

    @Test
    fun testTimeWindowDetection() {
        AutonomousQuotaManager.setHourForTesting(3) // 3 AM
        assertEquals(QuotaWindow.NIGHT_LOCKED, AutonomousQuotaManager.getCurrentWindow())
        assertTrue(AutonomousQuotaManager.isCurrentWindowExhausted()) // Locked at night

        AutonomousQuotaManager.setHourForTesting(8) // 8 AM
        assertEquals(QuotaWindow.MORNING, AutonomousQuotaManager.getCurrentWindow())

        AutonomousQuotaManager.setHourForTesting(14) // 2 PM
        assertEquals(QuotaWindow.AFTERNOON, AutonomousQuotaManager.getCurrentWindow())

        AutonomousQuotaManager.setHourForTesting(20) // 8 PM
        assertEquals(QuotaWindow.EVENING, AutonomousQuotaManager.getCurrentWindow())
    }

    @Test
    fun testReelsWindowQuotaAccumulationAndExhaustion() {
        AutonomousQuotaManager.setHourForTesting(9) // Morning window
        assertFalse(AutonomousQuotaManager.isCurrentWindowExhausted())

        AutonomousQuotaManager.recordReelsTick(600) // 10 minutes
        assertEquals(600L, AutonomousQuotaManager.getSnapshot().morningReelsUsed)
        assertEquals(600L, AutonomousQuotaManager.getSnapshot().currentWindowRemaining)
        assertFalse(AutonomousQuotaManager.isCurrentWindowExhausted())

        AutonomousQuotaManager.recordReelsTick(600) // +10 minutes = 20 minutes
        assertEquals(1200L, AutonomousQuotaManager.getSnapshot().morningReelsUsed)
        assertTrue(AutonomousQuotaManager.isCurrentWindowExhausted())

        // Afternoon window opens fresh
        AutonomousQuotaManager.setHourForTesting(13) // Afternoon window
        assertEquals(0L, AutonomousQuotaManager.getSnapshot().afternoonReelsUsed)
        assertFalse(AutonomousQuotaManager.isCurrentWindowExhausted())
        assertEquals(1200L, AutonomousQuotaManager.getSnapshot().currentWindowRemaining)
    }

    @Test
    fun testGamingQuotaAccumulationAndExhaustion() {
        assertFalse(AutonomousQuotaManager.isGamingExhausted())

        AutonomousQuotaManager.recordGamingTick(600)
        assertEquals(600L, AutonomousQuotaManager.getSnapshot().gamingSecondsUsed)
        assertFalse(AutonomousQuotaManager.isGamingExhausted())

        AutonomousQuotaManager.recordGamingTick(1200)
        assertEquals(1800L, AutonomousQuotaManager.getSnapshot().gamingSecondsUsed)
        assertTrue(AutonomousQuotaManager.isGamingExhausted())
    }

    @Test
    fun testHardBlockedPackages() {
        val hardBlocked = listOf(
            "org.telegram.messenger",
            "org.thunderdog.challegram",
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "in.startv.hotstar",
            "com.disney.disneyplus",
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.jio.media.jiobeats",
            "com.bsbportal.music",
            "com.gaana",
            "com.apple.android.music",
            "com.amazon.mp3",
            "com.soundcloud.android"
        )

        for (pkg in hardBlocked) {
            assertTrue("Expected $pkg to be hard-blocked", AutonomousQuotaManager.isHardBlocked(pkg))
        }

        assertFalse(AutonomousQuotaManager.isHardBlocked("com.whatsapp"))
        assertFalse(AutonomousQuotaManager.isHardBlocked("com.google.android.youtube"))
        assertFalse(AutonomousQuotaManager.isHardBlocked("com.instagram.android"))
        assertFalse(AutonomousQuotaManager.isHardBlocked(null))
    }

    @Test
    fun testMidnightReset() {
        AutonomousQuotaManager.setHourForTesting(9)
        AutonomousQuotaManager.recordGamingTick(1800)
        AutonomousQuotaManager.recordReelsTick(1200)
        assertTrue(AutonomousQuotaManager.isGamingExhausted())
        assertTrue(AutonomousQuotaManager.isCurrentWindowExhausted())

        // Simulate next day
        AutonomousQuotaManager.checkDateResetForTesting("2026-10-03", "2026-10-02")
        val snapshot = AutonomousQuotaManager.getSnapshot()
        assertEquals(0L, snapshot.gamingSecondsUsed)
        assertEquals(0L, snapshot.morningReelsUsed)
        assertEquals(0L, snapshot.reelsSecondsUsed)
        assertFalse(AutonomousQuotaManager.isGamingExhausted())
        assertFalse(AutonomousQuotaManager.isCurrentWindowExhausted())
    }
}
