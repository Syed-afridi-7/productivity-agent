package com.syedafridi.productivity_agent.services

import android.content.Context
import android.content.pm.ApplicationInfo

object SubScreenClassifier {

    private val WHITELISTED_PREFIXES = listOf(
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.android.phone",
        "com.google.android.googlequicksearchbox",
        // com.android.chrome REMOVED — browsers now monitored by ContentIntelligenceEngine
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.openai.chatgpt",
        "com.anthropic.claude"
    )

    private val KNOWN_GAME_PACKAGES = setOf(
        "com.king.candycrushsaga",
        "com.supercell.clashofclans",
        "com.supercell.brawlstars",
        "com.dts.freefireth",
        "com.pubg.imobile",
        "com.tencent.ig",
        "com.kiloo.subwaysurf",
        "com.activision.callofduty.shooter",
        "com.ea.gp.fifamobile",
        "com.roblox.client"
    )

    fun isGame(context: Context, packageName: String?): Boolean {
        if (packageName == null) return false
        if (KNOWN_GAME_PACKAGES.contains(packageName)) return true
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else {
                (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun isWhitelistedActivity(packageName: String?): Boolean {
        if (packageName == null) return false
        return WHITELISTED_PREFIXES.any { packageName.startsWith(it) }
    }

    fun isDirectMessage(
        packageName: String,
        className: String?,
        contentDescription: String?
    ): Boolean {
        val classLower = className?.lowercase() ?: ""
        val descLower = contentDescription?.lowercase() ?: ""

        val dmTokens = listOf("direct", "inbox", "thread", "chat", "message", "composer")
        return dmTokens.any { classLower.contains(it) || descLower.contains(it) }
    }

    fun isReelsOrShorts(
        packageName: String,
        className: String?,
        contentDescription: String?,
        text: String?
    ): Boolean {
        val classLower = className?.lowercase() ?: ""
        val descLower = contentDescription?.lowercase() ?: ""
        val textLower = text?.lowercase() ?: ""

        // If direct messaging tokens are present, it is strictly NOT Reels
        val isDm = isDirectMessage(packageName, className, contentDescription) ||
                textLower.contains("message") || textLower.contains("chat")
        if (isDm) {
            return false
        }

        when (packageName) {
            "com.instagram.android" -> {
                if (classLower.contains("clips") || classLower.contains("reel")) return true
                if (descLower.contains("reel") || descLower.contains("clips")) return true
                if (textLower.contains("watch reels") || textLower == "reels") return true
                return false
            }
            "com.google.android.youtube" -> {
                if (classLower.contains("shorts") || classLower.contains("reelplayer")) return true
                if (descLower.contains("shorts") && !descLower.contains("video player:")) return true
                if (textLower == "shorts") return true
                return false
            }
            "com.facebook.katana" -> {
                if (classLower.contains("reel") || classLower.contains("watch")) return true
                if (descLower.contains("reel") || descLower.contains("watch")) return true
                if (textLower == "reels") return true
                return false
            }
            else -> return false
        }
    }
}
