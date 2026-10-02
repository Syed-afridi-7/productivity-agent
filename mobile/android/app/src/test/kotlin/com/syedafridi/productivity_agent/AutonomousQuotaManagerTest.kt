package com.syedafridi.productivity_agent

import com.syedafridi.productivity_agent.services.AutonomousQuotaManager
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
        val snapshot = AutonomousQuotaManager.getSnapshot()
        assertEquals(1800L, snapshot.gamingLimitSeconds) // 30 minutes
        assertEquals(1200L, snapshot.reelsLimitSeconds)  // 20 minutes
        assertEquals(0L, snapshot.gamingSecondsUsed)
        assertEquals(0L, snapshot.reelsSecondsUsed)
        assertFalse(AutonomousQuotaManager.isGamingExhausted())
        assertFalse(AutonomousQuotaManager.isReelsExhausted())
    }

    @Test
    fun testGamingQuotaAccumulationAndExhaustion() {
        assertFalse(AutonomousQuotaManager.isGamingExhausted())

        AutonomousQuotaManager.recordGamingTick(600) // 10 minutes
        assertEquals(600L, AutonomousQuotaManager.getSnapshot().gamingSecondsUsed)
        assertFalse(AutonomousQuotaManager.isGamingExhausted())

        AutonomousQuotaManager.recordGamingTick(1200) // +20 minutes = 30 minutes
        assertEquals(1800L, AutonomousQuotaManager.getSnapshot().gamingSecondsUsed)
        assertTrue(AutonomousQuotaManager.isGamingExhausted())

        AutonomousQuotaManager.recordGamingTick(10) // Over limit
        assertTrue(AutonomousQuotaManager.isGamingExhausted())
    }

    @Test
    fun testReelsQuotaAccumulationAndExhaustion() {
        assertFalse(AutonomousQuotaManager.isReelsExhausted())

        AutonomousQuotaManager.recordReelsTick(600) // 10 minutes
        assertEquals(600L, AutonomousQuotaManager.getSnapshot().reelsSecondsUsed)
        assertFalse(AutonomousQuotaManager.isReelsExhausted())

        AutonomousQuotaManager.recordReelsTick(600) // +10 minutes = 20 minutes
        assertEquals(1200L, AutonomousQuotaManager.getSnapshot().reelsSecondsUsed)
        assertTrue(AutonomousQuotaManager.isReelsExhausted())
    }

    @Test
    fun testHardBlockedPackages() {
        val hardBlocked = listOf(
            // Messaging
            "org.telegram.messenger",
            "org.thunderdog.challegram",
            // Streaming
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "in.startv.hotstar",
            "com.disney.disneyplus",
            // Music
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
        AutonomousQuotaManager.recordGamingTick(1800)
        AutonomousQuotaManager.recordReelsTick(1200)
        assertTrue(AutonomousQuotaManager.isGamingExhausted())
        assertTrue(AutonomousQuotaManager.isReelsExhausted())

        // Simulate next day
        AutonomousQuotaManager.checkDateResetForTesting("2026-10-03", "2026-10-02")
        val snapshot = AutonomousQuotaManager.getSnapshot()
        assertEquals(0L, snapshot.gamingSecondsUsed)
        assertEquals(0L, snapshot.reelsSecondsUsed)
        assertFalse(AutonomousQuotaManager.isGamingExhausted())
        assertFalse(AutonomousQuotaManager.isReelsExhausted())
    }
}
