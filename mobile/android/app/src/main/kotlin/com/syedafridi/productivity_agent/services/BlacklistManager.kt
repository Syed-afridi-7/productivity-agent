package com.syedafridi.productivity_agent.services

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

object BlacklistManager {
    val DEFAULT_PACKAGES = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.twitter.android",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.reddit.frontpage"
    )

    private val blacklist = CopyOnWriteArraySet<String>(DEFAULT_PACKAGES)
    private val emergencyPasses = ConcurrentHashMap<String, Long>()

    fun isBlacklisted(packageName: String?): Boolean {
        return packageName != null && blacklist.contains(packageName)
    }

    fun setBlacklist(packages: Collection<String>) {
        blacklist.clear()
        blacklist.addAll(packages)
    }

    fun getBlacklist(): Set<String> {
        return blacklist.toSet()
    }

    fun resetToDefaults() {
        blacklist.clear()
        blacklist.addAll(DEFAULT_PACKAGES)
    }

    fun grantEmergencyPass(packageName: String, durationSeconds: Long = 60) {
        emergencyPasses[packageName] = System.currentTimeMillis() + durationSeconds * 1000L
    }

    fun hasActiveEmergencyPass(packageName: String?): Boolean {
        if (packageName == null) return false
        return (emergencyPasses[packageName] ?: 0L) > System.currentTimeMillis()
    }

    fun clearEmergencyPasses() {
        emergencyPasses.clear()
    }
}
