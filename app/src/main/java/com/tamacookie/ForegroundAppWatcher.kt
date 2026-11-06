package com.tamacookie

/**
 * Détecte un **changement** d'application au premier plan (Kotlin pur) : interroge [source] (port
 * [ForegroundAppSource]) seulement écran allumé ([awake]), ignore [ignored] (notre app, le volet de
 * notifications) et décrit la nouvelle app ([describe] : nom, catégorie, bureau ?).
 * Adaptateurs Android : `AndroidForegroundApps.kt` ; assemblé par [AppGraph.foregroundWatcher].
 */
class ForegroundAppWatcher(
    private val source: ForegroundAppSource,
    private val awake: () -> Boolean,
    private val ignored: Set<String>,
    private val describe: (String) -> AppInfo,
) {
    /** [category] : valeurs `ApplicationInfo.CATEGORY_*`. */
    data class AppInfo(val pkg: String, val label: String, val category: Int, val isHome: Boolean)

    private var current: String? = null
    val currentPkg: String? get() = current

    /** Retourne la nouvelle app si elle a changé depuis le dernier appel, sinon null. */
    fun poll(): AppInfo? {
        if (!awake()) return null
        val pkg = source.foreground() ?: return null
        if (pkg == current || pkg in ignored) return null
        current = pkg
        return describe(pkg)
    }
}
