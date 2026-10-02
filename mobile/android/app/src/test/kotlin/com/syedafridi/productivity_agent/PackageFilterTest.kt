package com.syedafridi.productivity_agent

import com.syedafridi.productivity_agent.services.PackageFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageFilterTest {

    @Test
    fun testIgnoresNullOrBlank() {
        assertFalse(PackageFilter.isTargetUserApp(null))
        assertFalse(PackageFilter.isTargetUserApp(""))
        assertFalse(PackageFilter.isTargetUserApp("   "))
    }

    @Test
    fun testIgnoresSelfAndSystemPackages() {
        // Self package default and custom
        assertFalse(PackageFilter.isTargetUserApp("com.syedafridi.productivity_agent"))
        assertFalse(PackageFilter.isTargetUserApp("com.custom.self", selfPackage = "com.custom.self"))

        // Known system packages
        assertFalse(PackageFilter.isTargetUserApp("com.android.systemui"))
        assertFalse(PackageFilter.isTargetUserApp("com.android.settings"))
        assertFalse(PackageFilter.isTargetUserApp("com.google.android.inputmethod.latin"))
        assertFalse(PackageFilter.isTargetUserApp("com.samsung.android.honeyboard"))
        assertFalse(PackageFilter.isTargetUserApp("com.google.android.apps.nexuslauncher"))
        assertFalse(PackageFilter.isTargetUserApp("com.sec.android.app.launcher"))
        assertFalse(PackageFilter.isTargetUserApp("com.miui.home"))
        assertFalse(PackageFilter.isTargetUserApp("com.huawei.android.launcher"))
        assertFalse(PackageFilter.isTargetUserApp("android"))

        // Launcher prefixes
        assertFalse(PackageFilter.isTargetUserApp("com.android.launcher"))
        assertFalse(PackageFilter.isTargetUserApp("com.android.launcher3"))
    }

    @Test
    fun testAllowsUserApplications() {
        assertTrue(PackageFilter.isTargetUserApp("com.instagram.android"))
        assertTrue(PackageFilter.isTargetUserApp("com.google.android.youtube"))
        assertTrue(PackageFilter.isTargetUserApp("com.twitter.android"))
        assertTrue(PackageFilter.isTargetUserApp("com.spotify.music"))
        assertTrue(PackageFilter.isTargetUserApp("com.zhiliaoapp.musically"))
    }
}
