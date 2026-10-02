package com.syedafridi.productivity_agent.services

enum class GraceState {
    INACTIVE,
    GRACE_PERIOD,
    WARNING_PHASE,
    PRODUCTIVE_CONFIRMED
}

/**
 * Manages the grace-period monitoring for content-intelligence decisions.
 *
 * Flow:
 *  1. Content classified as UNKNOWN → startGracePeriod()
 *  2. During grace, if content becomes PRODUCTIVE → markProductive()
 *  3. If 60s expires with no productive signal → transition to WARNING_PHASE
 *  4. Warning overlay shown for 30s. If still no productive signal → kick out.
 */
object ContentGraceMonitor {

    const val GRACE_PERIOD_MS = 60_000L
    const val WARNING_PERIOD_MS = 30_000L

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
