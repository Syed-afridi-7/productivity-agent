package com.syedafridi.productivity_agent.services

enum class GraceState {
    INACTIVE,
    GRACE_PERIOD,
    WARNING_PHASE,
    PRODUCTIVE_CONFIRMED
}

/**
 * Manages the timing for content-intelligence decisions.
 *
 * Flow:
 *  1. Content classified as UNKNOWN / loading → startGracePeriod() (3s buffer)
 *  2. If content becomes explicitly PRODUCTIVE → markProductive() (unrestricted)
 *  3. If 3s expires with no productive signal → transition to WARNING_PHASE
 *  4. Warning overlay shown for 12s. If still no productive signal → kick out.
 *  Total elapsed time for unproductive content: 15s max.
 */
object ContentGraceMonitor {

    const val BUFFER_PERIOD_MS = 3_000L               // 3s node loading buffer
    const val GRACE_PERIOD_MS = BUFFER_PERIOD_MS      // Alias
    const val WARNING_PERIOD_MS = 12_000L             // 12s visible countdown overlay

    var currentState: GraceState = GraceState.INACTIVE
        private set

    var trackedPackage: String? = null
        private set

    private var graceStartTimestamp: Long = 0L
    private var warningStartTimestamp: Long = 0L

    fun startGracePeriod(packageName: String) {
        trackedPackage = packageName
        currentState = GraceState.GRACE_PERIOD
        graceStartTimestamp = System.currentTimeMillis()
        warningStartTimestamp = 0L
    }

    fun startGracePeriodWithTimestamp(packageName: String, timestamp: Long) {
        trackedPackage = packageName
        currentState = GraceState.GRACE_PERIOD
        graceStartTimestamp = timestamp
        warningStartTimestamp = 0L
    }

    fun startWarningPhase() {
        currentState = GraceState.WARNING_PHASE
        warningStartTimestamp = System.currentTimeMillis()
    }

    fun startWarningPhaseWithTimestamp(timestamp: Long) {
        currentState = GraceState.WARNING_PHASE
        warningStartTimestamp = timestamp
    }

    fun markProductive() {
        currentState = GraceState.PRODUCTIVE_CONFIRMED
        graceStartTimestamp = 0L
        warningStartTimestamp = 0L
    }

    fun isGracePeriodActive(): Boolean {
        return currentState == GraceState.GRACE_PERIOD || currentState == GraceState.WARNING_PHASE
    }

    fun isGracePeriodExpired(): Boolean {
        if (currentState != GraceState.GRACE_PERIOD) return false
        return (System.currentTimeMillis() - graceStartTimestamp) >= GRACE_PERIOD_MS
    }

    fun isWarningExpired(): Boolean {
        if (currentState != GraceState.WARNING_PHASE) return false
        return (System.currentTimeMillis() - warningStartTimestamp) >= WARNING_PERIOD_MS
    }

    fun deactivate() {
        currentState = GraceState.INACTIVE
        trackedPackage = null
        graceStartTimestamp = 0L
        warningStartTimestamp = 0L
    }

    fun resetForTesting() {
        deactivate()
    }
}
