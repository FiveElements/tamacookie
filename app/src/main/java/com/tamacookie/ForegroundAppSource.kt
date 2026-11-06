package com.tamacookie

/**
 * Port **`ForegroundAppSource`** : quelle application est au premier plan.
 * Adaptateurs : [AccessibilityForegroundSource] (service d'accessibilité, sans coût) et
 * `UsageStatsForegroundSource` (Android, requête plafonnée) ; [ConnectionAwareForegroundSource] choisit
 * entre les deux. Lu par [ForegroundAppWatcher] (fil principal).
 */
fun interface ForegroundAppSource {
    /** Package au premier plan selon cette source, null = inconnu ou rien de nouveau. */
    fun foreground(): String?
}

/** L'application vue par le service d'accessibilité (dernier scan). */
class AccessibilityForegroundSource(private val screen: ScreenSensor) : ForegroundAppSource {
    override fun foreground(): String? = screen.snapshot.foregroundPkg
}

/**
 * Composite : l'accessibilité quand son service est connecté (elle sait déjà l'app : [fallback] n'est
 * pas interrogé), sinon [fallback] (UsageStats).
 */
class ConnectionAwareForegroundSource(
    private val screen: ScreenSensor,
    private val fallback: ForegroundAppSource,
    private val accessibility: ForegroundAppSource = AccessibilityForegroundSource(screen),
) : ForegroundAppSource {
    override fun foreground(): String? =
        if (screen.snapshot.connected) accessibility.foreground() else fallback.foreground()
}
