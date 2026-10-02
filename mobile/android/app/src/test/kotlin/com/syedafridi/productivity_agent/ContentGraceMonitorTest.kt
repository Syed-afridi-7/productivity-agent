package com.syedafridi.productivity_agent

import com.syedafridi.productivity_agent.services.ContentGraceMonitor
import com.syedafridi.productivity_agent.services.GraceState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContentGraceMonitorTest {

    @Before
    fun setUp() {
        ContentGraceMonitor.resetForTesting()
    }

    @After
    fun tearDown() {
        ContentGraceMonitor.resetForTesting()
    }

    @Test
    fun testInitialStateIsInactive() {
        assertEquals(GraceState.INACTIVE, ContentGraceMonitor.currentState)
        assertFalse(ContentGraceMonitor.isGracePeriodActive())
    }

    @Test
    fun testStartGracePeriodTransitionsToGrace() {
        ContentGraceMonitor.startGracePeriod("com.google.android.youtube")
        assertEquals(GraceState.GRACE_PERIOD, ContentGraceMonitor.currentState)
        assertTrue(ContentGraceMonitor.isGracePeriodActive())
    }

    @Test
    fun testMarkProductiveClearsGrace() {
        ContentGraceMonitor.startGracePeriod("com.google.android.youtube")
        ContentGraceMonitor.markProductive()
        assertEquals(GraceState.PRODUCTIVE_CONFIRMED, ContentGraceMonitor.currentState)
        assertFalse(ContentGraceMonitor.isGracePeriodActive())
    }

    @Test
    fun testResetClearsEverything() {
        ContentGraceMonitor.startGracePeriod("com.google.android.youtube")
        ContentGraceMonitor.resetForTesting()
        assertEquals(GraceState.INACTIVE, ContentGraceMonitor.currentState)
    }

    @Test
    fun testGracePeriodExpiredAfterTimeout() {
        ContentGraceMonitor.startGracePeriodWithTimestamp(
            "com.google.android.youtube",
            System.currentTimeMillis() - 4_000 // > 3s buffer
        )
        assertTrue(ContentGraceMonitor.isGracePeriodExpired())
    }

    @Test
    fun testGracePeriodNotExpiredWithinTimeout() {
        ContentGraceMonitor.startGracePeriodWithTimestamp(
            "com.google.android.youtube",
            System.currentTimeMillis() - 1_000 // < 3s buffer
        )
        assertFalse(ContentGraceMonitor.isGracePeriodExpired())
    }

    @Test
    fun testWarningPhaseExpiredAfterWarningTimeout() {
        ContentGraceMonitor.startWarningPhaseWithTimestamp(
            System.currentTimeMillis() - 13_000 // > 12s warning
        )
        assertTrue(ContentGraceMonitor.isWarningExpired())
    }

    @Test
    fun testTrackedPackageIsCorrect() {
        ContentGraceMonitor.startGracePeriod("com.android.chrome")
        assertEquals("com.android.chrome", ContentGraceMonitor.trackedPackage)
    }

    @Test
    fun testDifferentPackageRestartsGrace() {
        ContentGraceMonitor.startGracePeriod("com.google.android.youtube")
        ContentGraceMonitor.startGracePeriod("com.android.chrome")
        assertEquals("com.android.chrome", ContentGraceMonitor.trackedPackage)
        assertEquals(GraceState.GRACE_PERIOD, ContentGraceMonitor.currentState)
    }
}
