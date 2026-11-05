package com.tamacookie

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process

/**
 * Détecte l'application au premier plan via UsageStatsManager
 * (permission "Accès aux données d'utilisation" accordée par l'utilisateur).
 */
class ForegroundAppWatcher(private val context: Context) {

    data class AppInfo(val pkg: String, val label: String, val category: Int, val isHome: Boolean)

    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val pm = context.packageManager
    private var current: String? = null
    val currentPkg: String? get() = current
    private val homePackages: Set<String> by lazy {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }.toSet()
    }
    private val ignored = setOf(context.packageName, "com.android.systemui")

    /** Retourne la nouvelle app si elle a changé depuis le dernier appel, sinon null. */
    fun poll(): AppInfo? {
        if (!hasPermission(context)) return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 15_000, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        if (last == null || last == current || last in ignored) return null
        current = last
        return describe(last)
    }

    private fun describe(pkg: String): AppInfo {
        var label = pkg.substringAfterLast('.')
        var cat = ApplicationInfo.CATEGORY_UNDEFINED
        try {
            val ai = pm.getApplicationInfo(pkg, 0)
            label = pm.getApplicationLabel(ai).toString()
            cat = ai.category
        } catch (_: PackageManager.NameNotFoundException) {
        }
        return AppInfo(pkg, label, cat, pkg in homePackages)
    }

    companion object {
        fun hasPermission(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val mode = ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
