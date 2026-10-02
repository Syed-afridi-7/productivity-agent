package com.syedafridi.productivity_agent.services

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class QuotaWindow(val label: String, val startHour: Int, val endHour: Int) {
    MORNING("Morning", 6, 12),
    AFTERNOON("Afternoon", 12, 18),
    EVENING("Evening", 18, 24),
    NIGHT_LOCKED("Night Locked", 0, 6)
}

data class QuotaSnapshot(
    val gamingSecondsUsed: Long,
    val gamingLimitSeconds: Long,
    val reelsSecondsUsed: Long,
    val reelsLimitSeconds: Long,
    val morningReelsUsed: Long,
    val afternoonReelsUsed: Long,
    val eveningReelsUsed: Long,
    val currentWindow: QuotaWindow,
    val currentWindowUsed: Long,
    val currentWindowLimit: Long,
    val currentWindowRemaining: Long,
    val isCurrentWindowExhausted: Boolean
)

object AutonomousQuotaManager {
    const val MAX_GAMING_SECONDS = 1800L             // 30 minutes daily
    const val WINDOW_LIMIT_SECONDS = 1200L           // 20 minutes per window
    const val DAILY_ENTERTAINMENT_LIMIT_SECONDS = 3600L // 60 minutes total (3 x 20m)

    val HARD_BLOCKED_PACKAGES = setOf(
        // Messaging (no productive use)
        "org.telegram.messenger",
        "org.thunderdog.challegram",
        // Streaming / Movies
        "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient",
        "in.startv.hotstar",
        "com.disney.disneyplus",
        // Music apps (zero tolerance)
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.jio.media.jiobeats",
        "com.bsbportal.music",
        "com.gaana",
        "com.apple.android.music",
        "com.amazon.mp3",
        "com.soundcloud.android"
    )

    private const val PREFS_NAME = "autonomous_quotas"
    private const val KEY_LAST_DATE = "last_quota_date"
    private const val KEY_GAMING_USED = "gaming_seconds_used"
    private const val KEY_MORNING_REELS = "morning_reels_used"
    private const val KEY_AFTERNOON_REELS = "afternoon_reels_used"
    private const val KEY_EVENING_REELS = "evening_reels_used"

    private var sharedPreferences: SharedPreferences? = null
    private var lastRecordedDate: String = getTodayDate()
    private val gamingSecondsUsed = AtomicLong(0L)
    private val morningReelsUsed = AtomicLong(0L)
    private val afternoonReelsUsed = AtomicLong(0L)
    private val eveningReelsUsed = AtomicLong(0L)

    private var testHourOverride: Int? = null

    fun init(context: Context) {
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun getCurrentWindow(calendar: Calendar = Calendar.getInstance()): QuotaWindow {
        val hour = testHourOverride ?: calendar.get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 6..11 -> QuotaWindow.MORNING
            hour in 12..17 -> QuotaWindow.AFTERNOON
            hour in 18..23 -> QuotaWindow.EVENING
            else -> QuotaWindow.NIGHT_LOCKED
        }
    }

    @Synchronized
    private fun checkDateReset() {
        val today = getTodayDate()
        if (today != lastRecordedDate) {
            lastRecordedDate = today
            gamingSecondsUsed.set(0L)
            morningReelsUsed.set(0L)
            afternoonReelsUsed.set(0L)
            eveningReelsUsed.set(0L)
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
        when (getCurrentWindow()) {
            QuotaWindow.MORNING -> morningReelsUsed.addAndGet(seconds)
            QuotaWindow.AFTERNOON -> afternoonReelsUsed.addAndGet(seconds)
            QuotaWindow.EVENING -> eveningReelsUsed.addAndGet(seconds)
            QuotaWindow.NIGHT_LOCKED -> { /* Night is locked, cannot add */ }
        }
        persistToPrefs()
    }

    fun isGamingExhausted(): Boolean {
        checkDateReset()
        return gamingSecondsUsed.get() >= MAX_GAMING_SECONDS
    }

    fun isCurrentWindowExhausted(): Boolean {
        checkDateReset()
        return when (getCurrentWindow()) {
            QuotaWindow.MORNING -> morningReelsUsed.get() >= WINDOW_LIMIT_SECONDS
            QuotaWindow.AFTERNOON -> afternoonReelsUsed.get() >= WINDOW_LIMIT_SECONDS
            QuotaWindow.EVENING -> eveningReelsUsed.get() >= WINDOW_LIMIT_SECONDS
            QuotaWindow.NIGHT_LOCKED -> true // Always exhausted at night!
        }
    }

    fun isReelsExhausted(): Boolean {
        return isCurrentWindowExhausted()
    }

    fun isHardBlocked(packageName: String?): Boolean {
        if (packageName == null) return false
        return HARD_BLOCKED_PACKAGES.contains(packageName)
    }

    fun getSnapshot(): QuotaSnapshot {
        checkDateReset()
        val window = getCurrentWindow()
        val windowUsed = when (window) {
            QuotaWindow.MORNING -> morningReelsUsed.get()
            QuotaWindow.AFTERNOON -> afternoonReelsUsed.get()
            QuotaWindow.EVENING -> eveningReelsUsed.get()
            QuotaWindow.NIGHT_LOCKED -> 0L
        }
        val windowLimit = if (window == QuotaWindow.NIGHT_LOCKED) 0L else WINDOW_LIMIT_SECONDS
        val windowRemaining = (windowLimit - windowUsed).coerceAtLeast(0L)
        val isExhausted = if (window == QuotaWindow.NIGHT_LOCKED) true else windowUsed >= windowLimit
        val totalReelsUsed = morningReelsUsed.get() + afternoonReelsUsed.get() + eveningReelsUsed.get()

        return QuotaSnapshot(
            gamingSecondsUsed = gamingSecondsUsed.get(),
            gamingLimitSeconds = MAX_GAMING_SECONDS,
            reelsSecondsUsed = totalReelsUsed,
            reelsLimitSeconds = DAILY_ENTERTAINMENT_LIMIT_SECONDS,
            morningReelsUsed = morningReelsUsed.get(),
            afternoonReelsUsed = afternoonReelsUsed.get(),
            eveningReelsUsed = eveningReelsUsed.get(),
            currentWindow = window,
            currentWindowUsed = windowUsed,
            currentWindowLimit = windowLimit,
            currentWindowRemaining = windowRemaining,
            isCurrentWindowExhausted = isExhausted
        )
    }

    private fun loadFromPrefs() {
        val prefs = sharedPreferences ?: return
        val savedDate = prefs.getString(KEY_LAST_DATE, getTodayDate()) ?: getTodayDate()
        val today = getTodayDate()
        lastRecordedDate = today
        if (savedDate == today) {
            gamingSecondsUsed.set(prefs.getLong(KEY_GAMING_USED, 0L))
            morningReelsUsed.set(prefs.getLong(KEY_MORNING_REELS, 0L))
            afternoonReelsUsed.set(prefs.getLong(KEY_AFTERNOON_REELS, 0L))
            eveningReelsUsed.set(prefs.getLong(KEY_EVENING_REELS, 0L))
        } else {
            gamingSecondsUsed.set(0L)
            morningReelsUsed.set(0L)
            afternoonReelsUsed.set(0L)
            eveningReelsUsed.set(0L)
            persistToPrefs()
        }
    }

    private fun persistToPrefs() {
        sharedPreferences?.edit()?.apply {
            putString(KEY_LAST_DATE, lastRecordedDate)
            putLong(KEY_GAMING_USED, gamingSecondsUsed.get())
            putLong(KEY_MORNING_REELS, morningReelsUsed.get())
            putLong(KEY_AFTERNOON_REELS, afternoonReelsUsed.get())
            putLong(KEY_EVENING_REELS, eveningReelsUsed.get())
            apply()
        }
    }

    fun setHourForTesting(hour: Int?) {
        testHourOverride = hour
    }

    fun resetForTesting() {
        gamingSecondsUsed.set(0L)
        morningReelsUsed.set(0L)
        afternoonReelsUsed.set(0L)
        eveningReelsUsed.set(0L)
        lastRecordedDate = getTodayDate()
        testHourOverride = null
    }

    fun checkDateResetForTesting(newDate: String, oldDate: String) {
        lastRecordedDate = oldDate
        if (newDate != lastRecordedDate) {
            lastRecordedDate = newDate
            gamingSecondsUsed.set(0L)
            morningReelsUsed.set(0L)
            afternoonReelsUsed.set(0L)
            eveningReelsUsed.set(0L)
        }
    }
}
