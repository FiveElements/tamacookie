package com.tamacookie

/**
 * Catégories d'application ([ForegroundAppWatcher.AppInfo.category]) : valeurs de
 * `android.content.pm.ApplicationInfo.CATEGORY_*` recopiées pour rester en Kotlin pur (vérifiées par
 * `AndroidConstantsTest` dans l'app).
 */
object AppCategory {
    const val UNDEFINED = -1
    const val GAME = 0
    const val AUDIO = 1
    const val VIDEO = 2
    const val IMAGE = 3
    const val SOCIAL = 4
    const val NEWS = 5
    const val MAPS = 6
    const val PRODUCTIVITY = 7
}
