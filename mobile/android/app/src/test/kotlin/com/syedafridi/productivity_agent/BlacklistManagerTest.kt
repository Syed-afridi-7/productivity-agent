package com.syedafridi.productivity_agent

import com.syedafridi.productivity_agent.services.BlacklistManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlacklistManagerTest {

    @Before
    fun setUp() {
        BlacklistManager.resetToDefaults()
        BlacklistManager.clearEmergencyPasses()
    }

    @After
    fun tearDown() {
        BlacklistManager.resetToDefaults()
        BlacklistManager.clearEmergencyPasses()
    }

    @Test
    fun testDefaultBlacklistContainsSocialApps() {
        assertTrue(BlacklistManager.isBlacklisted("com.instagram.android"))
        assertTrue(BlacklistManager.isBlacklisted("com.zhiliaoapp.musically"))
        assertTrue(BlacklistManager.isBlacklisted("com.google.android.youtube"))
        assertTrue(BlacklistManager.isBlacklisted("com.twitter.android"))
        assertTrue(BlacklistManager.isBlacklisted("com.facebook.katana"))
        assertTrue(BlacklistManager.isBlacklisted("com.reddit.frontpage"))
    }

    @Test
    fun testCustomBlacklistReplacement() {
        val customList = listOf("com.custom.distractingapp", "com.games.addictive")
        BlacklistManager.setBlacklist(customList)

        assertEquals(customList.toSet(), BlacklistManager.getBlacklist())
        assertTrue(BlacklistManager.isBlacklisted("com.custom.distractingapp"))
        assertTrue(BlacklistManager.isBlacklisted("com.games.addictive"))
        assertFalse(BlacklistManager.isBlacklisted("com.instagram.android"))

        BlacklistManager.resetToDefaults()
        assertTrue(BlacklistManager.isBlacklisted("com.instagram.android"))
        assertFalse(BlacklistManager.isBlacklisted("com.custom.distractingapp"))
    }

    @Test
    fun testEmergencyPassSuppressesBlacklist() {
        val targetApp = "com.instagram.android"
        assertFalse(BlacklistManager.hasActiveEmergencyPass(targetApp))

        BlacklistManager.grantEmergencyPass(targetApp, durationSeconds = 60)
        assertTrue(BlacklistManager.hasActiveEmergencyPass(targetApp))

        BlacklistManager.clearEmergencyPasses()
        assertFalse(BlacklistManager.hasActiveEmergencyPass(targetApp))
    }

    @Test
    fun testUnblacklistedAppReturnsFalse() {
        assertFalse(BlacklistManager.isBlacklisted("com.google.android.gm"))
        assertFalse(BlacklistManager.isBlacklisted("org.thoughtcrime.securesms"))
        assertFalse(BlacklistManager.isBlacklisted(null))
        assertFalse(BlacklistManager.hasActiveEmergencyPass(null))
        assertFalse(BlacklistManager.hasActiveEmergencyPass("com.unknown.app"))
    }
}
