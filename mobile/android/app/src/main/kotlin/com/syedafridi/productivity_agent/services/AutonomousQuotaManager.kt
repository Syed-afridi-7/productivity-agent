package com.syedafridi.productivity_agent.services

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class QuotaSnapshot(
    val gamingSecondsUsed: Long,
    val gamingLimitSeconds: Long,
    val reelsSecondsUsed: Long,
    val reelsLimitSeconds: Long
)

object AutonomousQuotaManager {
    const val MAX_GAMING_SECONDS = 1800L // 30 minutes
    const val MAX_REELS_SECONDS = 1200L  // 20 minutes

    val HARD_BLOCKED_PACKAGES = setOf(
        "org.telegram.messenger",
        "org.thunderdog.challegram",
        "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient",
        "in.startv.hotstar",
        "com.disney.disneyplus"
    )

    private const val PREFS_NAME = "autonomous_quotas"
    private const val KEY_LAST_DATE = "last_quota_date"
    private const val KEY_GAMING_USED = "gaming_seconds_used"
    private const val KEY_REELS_USED = "reels_seconds_used"

    private var sharedPreferences: SharedPreferences? = null
    private var lastRecordedDate: String = getTodayDate()
    private val gamingSecondsUsed = AtomicLong(0L)
    private val reelsSecondsUsed = AtomicLong(0L)

    fun init(context: Context) {
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    @Synchronized
    private fun checkDateReset() {
        val today = getTodayDate()
        if (today != lastRecordedDate) {
            lastRecordedDate = today
            gamingSecondsUsed.set(0L)
            reelsSecondsUsed.set(0L)
            persistToPrefs()
        }
    }

    fun recordGamingTick(seconds: Long = 1) {
        checkDateReset()
        gamingSecondsUsed.addAndGet(seconds)
        persistToPrefs()
    }

    fun recordReelsTick(seconds: Long = 1) {
        checkDateReset()
        reelsSecondsUsed.addAndGet(seconds)
        persistToPrefs()
    }

    fun isGamingExhausted(): Boolean {
        checkDateReset()
        return gamingSecondsUsed.get() >= MAX_GAMING_SECONDS
    }

    fun isReelsExhausted(): Boolean {
        checkDateReset()
        return reelsSecondsUsed.get() >= MAX_REELS_SECONDS
    }

    fun isHardBlocked(packageName: String?): Boolean {
        if (packageName == null) return false
        return HARD_BLOCKED_PACKAGES.contains(packageName)
    }

    fun getSnapshot(): QuotaSnapshot {
        checkDateReset()
        return QuotaSnapshot(
            gamingSecondsUsed = gamingSecondsUsed.get(),
            gamingLimitSeconds = MAX_GAMING_SECONDS,
            reelsSecondsUsed = reelsSecondsUsed.get(),
            reelsLimitSeconds = MAX_REELS_SECONDS
        )
    }

    private fun loadFromPrefs() {
        val prefs = sharedPreferences ?: return
        val savedDate = prefs.getString(KEY_LAST_DATE, getTodayDate()) ?: getTodayDate()
        val today = getTodayDate()
        lastRecordedDate = today
        if (savedDate == today) {
            gamingSecondsUsed.set(prefs.getLong(KEY_GAMING_USED, 0L))
            reelsSecondsUsed.set(prefs.getLong(KEY_REELS_USED, 0L))
        } else {
            gamingSecondsUsed.set(0L)
            reelsSecondsUsed.set(0L)
            persistToPrefs()
        }
    }

    private fun persistToPrefs() {
        sharedPreferences?.edit()?.apply {
            putString(KEY_LAST_DATE, lastRecordedDate)
            putLong(KEY_GAMING_USED, gamingSecondsUsed.get())
            putLong(KEY_REELS_USED, reelsSecondsUsed.get())
            apply()
        }
    }

    fun resetForTesting() {
        gamingSecondsUsed.set(0L)
        reelsSecondsUsed.set(0L)
        lastRecordedDate = getTodayDate()
    }

    fun checkDateResetForTesting(newDate: String, oldDate: String) {
        lastRecordedDate = oldDate
        if (newDate != lastRecordedDate) {
            lastRecordedDate = newDate
            gamingSecondsUsed.set(0L)
            reelsSecondsUsed.set(0L)
        }
    }
}
