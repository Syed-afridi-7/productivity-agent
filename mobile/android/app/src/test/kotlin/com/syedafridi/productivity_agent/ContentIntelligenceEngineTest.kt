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
