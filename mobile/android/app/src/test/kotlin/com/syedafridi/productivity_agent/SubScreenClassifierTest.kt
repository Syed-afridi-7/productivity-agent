package com.syedafridi.productivity_agent

import com.syedafridi.productivity_agent.services.SubScreenClassifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubScreenClassifierTest {

    @Test
    fun testInstagramReelsDetected() {
        // Typical Instagram Reels activities/classes/nodes
        assertTrue(
            SubScreenClassifier.isReelsOrShorts(
                packageName = "com.instagram.android",
                className = "com.instagram.modal.ModalActivity",
                contentDescription = "Reels",
                text = "Watch Reels"
            )
        )

        assertTrue(
            SubScreenClassifier.isReelsOrShorts(
                packageName = "com.instagram.android",
                className = "com.instagram.clips.viewer.ClipsViewerActivity",
                contentDescription = null,
                text = null
            )
        )
    }

    @Test
    fun testInstagramDirectMessagesPermitted() {
        // Direct messages / chat must NOT be classified as Reels, and must be classified as DM
        assertTrue(
            SubScreenClassifier.isDirectMessage(
                packageName = "com.instagram.android",
                className = "com.instagram.direct.inbox.DirectInboxActivity",
                contentDescription = "Messages"
            )
        )

        assertTrue(
            SubScreenClassifier.isDirectMessage(
                packageName = "com.instagram.android",
                className = "com.instagram.direct.thread.DirectThreadActivity",
                contentDescription = "Chat with Alex"
            )
        )

        assertFalse(
            SubScreenClassifier.isReelsOrShorts(
                packageName = "com.instagram.android",
                className = "com.instagram.direct.thread.DirectThreadActivity",
                contentDescription = "Chat",
                text = "Hey bro how are you"
            )
        )
    }

    @Test
    fun testYouTubeShortsDetected() {
        assertTrue(
            SubScreenClassifier.isReelsOrShorts(
                packageName = "com.google.android.youtube",
                className = "com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity",
                contentDescription = "Shorts",
                text = "Shorts"
            )
        )

        assertTrue(
            SubScreenClassifier.isReelsOrShorts(
                packageName = "com.google.android.youtube",
                className = "com.google.android.apps.youtube.app.extensions.reel.watch.player.ReelPlayerActivity",
                contentDescription = null,
                text = null
            )
        )
    }

    @Test
    fun testYouTubeLongFormVideoPermitted() {
        // Educational video / standard watch / search must NOT be classified as Reels/Shorts
        assertFalse(
            SubScreenClassifier.isReelsOrShorts(
                packageName = "com.google.android.youtube",
                className = "com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity",
                contentDescription = "Video player: Clean Code Architecture in Flutter",
                text = "Clean Code Architecture in Flutter"
            )
        )

        assertFalse(
            SubScreenClassifier.isReelsOrShorts(
                packageName = "com.google.android.youtube",
                className = "com.google.android.apps.youtube.app.search.SearchActivity",
                contentDescription = "Search YouTube",
                text = "how to build android agent"
            )
        )
    }

    @Test
    fun testFacebookReelsVsMessenger() {
        // Facebook Reels
        assertTrue(
            SubScreenClassifier.isReelsOrShorts(
                packageName = "com.facebook.katana",
                className = "com.facebook.katana.ReelsFeedActivity",
                contentDescription = "Reels",
                text = "Reels"
            )
        )

        // Facebook Messenger / Chat
        assertTrue(
            SubScreenClassifier.isDirectMessage(
                packageName = "com.facebook.katana",
                className = "com.facebook.messaging.threadview.ThreadViewActivity",
                contentDescription = "Message"
            )
        )

        assertFalse(
            SubScreenClassifier.isReelsOrShorts(
                packageName = "com.facebook.katana",
                className = "com.facebook.messaging.threadview.ThreadViewActivity",
                contentDescription = "Message",
                text = "See you soon"
            )
        )
    }

    @Test
    fun testWhitelistedUtilities() {
        assertTrue(SubScreenClassifier.isWhitelistedActivity("com.google.android.dialer"))
        assertTrue(SubScreenClassifier.isWhitelistedActivity("com.samsung.android.dialer"))
        assertTrue(SubScreenClassifier.isWhitelistedActivity("com.google.android.googlequicksearchbox"))
        assertTrue(SubScreenClassifier.isWhitelistedActivity("com.openai.chatgpt"))
        assertFalse(SubScreenClassifier.isWhitelistedActivity("com.instagram.android"))
        assertFalse(SubScreenClassifier.isWhitelistedActivity("com.android.chrome"))
    }
}
