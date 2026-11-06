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
 * Adaptateur [ForegroundAppSource] par UsageStatsManager (permission « Accès aux données d'utilisation ») :
 * au plus une requête toutes les [MIN_QUERY_INTERVAL_MS], depuis le passage précédent.
 */
class UsageStatsForegroundSource(private val context: Context, private val clock: Clock = AndroidClock) : ForegroundAppSource {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    // Cache de la permission (appel AppOps relativement coûteux)
    private var permCached = false
    private var permCheckedAt = 0L
    // Fenêtre de requête UsageStats : depuis le dernier passage
    private var lastQueryAt = 0L          // uptime
    private var lastQueryWall = 0L        // horloge murale (UsageStats)

    /** Dernière activité reprise depuis le passage précédent, null si aucune (ou pas de requête). */
    override fun foreground(): String? {
        val up = clock.uptimeMs()
        if (lastQueryAt != 0L && up - lastQueryAt < MIN_QUERY_INTERVAL_MS) return null
        if (!hasPermissionCached(up)) return null
        lastQueryAt = up

        val now = clock.wallMs()
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
        return last
    }

    private fun hasPermissionCached(up: Long): Boolean {
        if (permCheckedAt == 0L || up - permCheckedAt > PERMISSION_RECHECK_MS) {
            permCached = hasPermission(context)
            permCheckedAt = up
        }
        return permCached
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

/** Décrit une application (nom, catégorie, bureau ?) par le PackageManager. */
class AppDescriber(context: Context) {
    private val pm = context.packageManager
    private val homePackages: Set<String> by lazy {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }.toSet()
    }

    fun describe(pkg: String): ForegroundAppWatcher.AppInfo {
        var label = pkg.substringAfterLast('.')
        var cat = ApplicationInfo.CATEGORY_UNDEFINED
        try {
            val ai = pm.getApplicationInfo(pkg, 0)
            label = pm.getApplicationLabel(ai).toString()
            cat = ai.category
        } catch (_: PackageManager.NameNotFoundException) {
        }
        return ForegroundAppWatcher.AppInfo(pkg, label, cat, pkg in homePackages)
    }
}
