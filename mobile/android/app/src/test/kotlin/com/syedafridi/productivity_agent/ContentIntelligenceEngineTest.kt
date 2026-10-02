package com.syedafridi.productivity_agent

import com.syedafridi.productivity_agent.services.ContentIntelligenceEngine
import com.syedafridi.productivity_agent.services.ContentVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentIntelligenceEngineTest {

    @Test
    fun testProductiveYouTubeVideoDetected() {
        val verdict = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = "Video player: DSA Sorting Algorithm Explained",
            className = "com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity",
            text = "DSA Sorting Algorithm Explained"
        )
        assertEquals(ContentVerdict.PRODUCTIVE, verdict)
    }

    @Test
    fun testUnproductiveYouTubeVideoDetected() {
        val verdict = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = "Video player: Funniest Prank Compilation 2026",
            className = "com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity",
            text = "Funniest Prank Compilation 2026"
        )
        assertEquals(ContentVerdict.UNPRODUCTIVE, verdict)
    }

    @Test
    fun testMusicVideoAndSongsDetectedAsUnproductive() {
        val verdict1 = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = "Video player: Taylor Swift - Anti-Hero (Official Music Video)",
            className = "com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity",
            text = "Taylor Swift Official Music Video"
        )
        assertEquals(ContentVerdict.UNPRODUCTIVE, verdict1)

        val verdict2 = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = null,
            className = null,
            text = "Coldplay - Yellow (Lyrics Video) full song",
            hierarchyText = "Coldplay Yellow official audio track"
        )
        assertEquals(ContentVerdict.UNPRODUCTIVE, verdict2)

        val verdict3 = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = null,
            className = null,
            text = "Lofi Hip Hop Radio - Beats to relax to"
        )
        assertEquals(ContentVerdict.UNPRODUCTIVE, verdict3)
    }

    @Test
    fun testNodeHierarchyTextInspection() {
        // When event.text and contentDescription are empty, hierarchyText provides the signal
        val productiveVerdict = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = null,
            className = "android.widget.FrameLayout",
            text = null,
            hierarchyText = "YouTube Dynamic Programming LeetCode Hard Solutions In Depth"
        )
        assertEquals(ContentVerdict.PRODUCTIVE, productiveVerdict)

        val unproductiveVerdict = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = null,
            className = "android.widget.FrameLayout",
            text = null,
            hierarchyText = "YouTube Top 10 Funniest Celebrity Roasts compilation"
        )
        assertEquals(ContentVerdict.UNPRODUCTIVE, unproductiveVerdict)
    }

    @Test
    fun testUnknownYouTubeVideoReturnsUnknown() {
        val verdict = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = "Video player: Some Random Title",
            className = "com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity",
            text = "Some Random Title"
        )
        assertEquals(ContentVerdict.UNKNOWN, verdict)
    }

    @Test
    fun testProductiveBrowserSearchDetected() {
        val verdict = ContentIntelligenceEngine.classify(
            packageName = "com.android.chrome",
            contentDescription = "Binary Search Tree Implementation in Java - GeeksforGeeks",
            className = "org.chromium.chrome.browser.ChromeTabbedActivity",
            text = "Binary Search Tree Implementation in Java"
        )
        assertEquals(ContentVerdict.PRODUCTIVE, verdict)
    }

    @Test
    fun testUnproductiveBrowserPageDetected() {
        val verdict = ContentIntelligenceEngine.classify(
            packageName = "com.android.chrome",
            contentDescription = "Top 10 Funniest Prank Videos - Entertainment Daily",
            className = "org.chromium.chrome.browser.ChromeTabbedActivity",
            text = "Top 10 Funniest Prank Videos"
        )
        assertEquals(ContentVerdict.UNPRODUCTIVE, verdict)
    }

    @Test
    fun testYouTubeSearchActivityAlwaysProductive() {
        val verdict = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = "Search YouTube",
            className = "com.google.android.apps.youtube.app.search.SearchActivity",
            text = null
        )
        assertEquals(ContentVerdict.PRODUCTIVE, verdict)
    }

    @Test
    fun testYouTubeHomePageIsUnknown() {
        val verdict = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = null,
            className = "com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity",
            text = "Home"
        )
        assertEquals(ContentVerdict.UNKNOWN, verdict)
    }

    @Test
    fun testMultipleProductiveKeywordsBoostConfidence() {
        val verdict = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = "Video player: Flutter Tutorial - Building REST API with Node.js",
            className = null,
            text = "Flutter Tutorial REST API Node.js"
        )
        assertEquals(ContentVerdict.PRODUCTIVE, verdict)
    }

    @Test
    fun testBrowserMonitoredPackages() {
        assertEquals(true, ContentIntelligenceEngine.isMonitoredBrowser("com.android.chrome"))
        assertEquals(true, ContentIntelligenceEngine.isMonitoredBrowser("com.brave.browser"))
        assertEquals(true, ContentIntelligenceEngine.isMonitoredBrowser("com.opera.browser"))
        assertEquals(true, ContentIntelligenceEngine.isMonitoredBrowser("org.mozilla.firefox"))
        assertEquals(false, ContentIntelligenceEngine.isMonitoredBrowser("com.whatsapp"))
    }

    @Test
    fun testNullInputsReturnUnknown() {
        val verdict = ContentIntelligenceEngine.classify(
            packageName = "com.google.android.youtube",
            contentDescription = null,
            className = null,
            text = null
        )
        assertEquals(ContentVerdict.UNKNOWN, verdict)
    }
}
