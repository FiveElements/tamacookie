package com.tamacookie

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock

/**
 * Détecte l'application au premier plan.
 *  - Si le service d'accessibilité est connecté, il fournit déjà l'app ([ScreenSnapshot.foregroundPkg]) :
 *    on ne touche pas à UsageStats.
 *  - Sinon, via UsageStatsManager (permission "Accès aux données d'utilisation"), au plus
 *    toutes les [MIN_QUERY_INTERVAL_MS], et jamais écran éteint.
 */
class ForegroundAppWatcher(private val context: Context, private val screen: ScreenSensor) {

    data class AppInfo(val pkg: String, val label: String, val category: Int, val isHome: Boolean)

    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val pm = context.packageManager
    private val power = context.getSystemService(PowerManager::class.java)
    private var current: String? = null
    val currentPkg: String? get() = current
    private val homePackages: Set<String> by lazy {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }.toSet()
    }
    private val ignored = setOf(context.packageName, "com.android.systemui")

    // Cache de la permission (appel AppOps relativement coûteux)
    private var permCached = false
    private var permCheckedAt = 0L
    // Fenêtre de requête UsageStats : depuis le dernier passage
    private var lastQueryAt = 0L          // uptime
    private var lastQueryWall = 0L        // horloge murale (UsageStats)

    /** Retourne la nouvelle app si elle a changé depuis le dernier appel, sinon null. */
    fun poll(): AppInfo? {
        val s = screen.snapshot
        if (!s.screenOn || power?.isInteractive == false) return null

        // L'accessibilité donne déjà l'app au premier plan : pas de requête UsageStats.
        if (s.connected) {
            val pkg = s.foregroundPkg ?: return null
            return changedTo(pkg)
        }

        val up = SystemClock.uptimeMillis()
        if (lastQueryAt != 0L && up - lastQueryAt < MIN_QUERY_INTERVAL_MS) return null
        if (!hasPermissionCached(up)) return null
        lastQueryAt = up

        val now = System.currentTimeMillis()
        // Depuis le dernier passage (marge de 2 s), au plus 15 s en arrière
        val from = if (lastQueryWall == 0L) now - 15_000 else maxOf(now - 15_000, lastQueryWall - 2_000)
        lastQueryWall = now
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        return last?.let { changedTo(it) }
    }

    private fun changedTo(pkg: String): AppInfo? {
        if (pkg == current || pkg in ignored) return null
        current = pkg
        return describe(pkg)
    }

    private fun hasPermissionCached(up: Long): Boolean {
        if (permCheckedAt == 0L || up - permCheckedAt > PERMISSION_RECHECK_MS) {
            permCached = hasPermission(context)
            permCheckedAt = up
        }
        return permCached
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
        /** Intervalle minimal entre deux requêtes UsageStats. */
        const val MIN_QUERY_INTERVAL_MS = 3_000L
        /** La permission n'est re-vérifiée qu'au plus toutes les 30 s. */
        const val PERMISSION_RECHECK_MS = 30_000L

        fun hasPermission(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java)
            val mode = ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}
