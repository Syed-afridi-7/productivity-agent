package com.syedafridi.productivity_agent.services

object PackageFilter {
    private val SYSTEM_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "android"
    )

    fun isTargetUserApp(
        packageName: String?,
        selfPackage: String = "com.syedafridi.productivity_agent"
    ): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == selfPackage) return false
        if (packageName in SYSTEM_PACKAGES) return false
        if (packageName.startsWith("com.android.launcher")) return false
        return true
    }
}
