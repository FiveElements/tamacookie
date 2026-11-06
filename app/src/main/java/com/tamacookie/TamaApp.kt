package com.tamacookie

import android.app.Application
import android.content.Context
import android.os.PowerManager

/**
 * Application : porte le [AppGraph] (injection manuelle), créé au démarrage du processus avant tout
 * service. Le service d'accessibilité et [BunnyService] tournent dans ce même processus.
 */
class TamaApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

/**
 * Graphe des dépendances (DI manuelle) : les adaptateurs du processus. `ScreenSensorService`
 * **publie** dans [screen] ; `BunnyService` le reçoit comme port [ScreenSensor] (et le passe aux
 * fonctionnalités par `PetCtx.screen`). Fil principal uniquement.
 */
class AppGraph(private val app: Context) {
    /** Ce que voit le service d'accessibilité (remplace l'ancien objet global `Platforms`). Unique. */
    val screen = ScreenSensorHub()

    /**
     * Application au premier plan : l'accessibilité si son service est connecté, sinon UsageStats
     * ([ConnectionAwareForegroundSource]) ; rien écran éteint. Un par `BunnyService` (il retient l'app courante).
     */
    fun foregroundWatcher(): ForegroundAppWatcher {
        val power = app.getSystemService(PowerManager::class.java)
        val describer = AppDescriber(app)
        return ForegroundAppWatcher(
            ConnectionAwareForegroundSource(screen, UsageStatsForegroundSource(app)),
            awake = { screen.snapshot.screenOn && power?.isInteractive != false },
            ignored = setOf(app.packageName, "com.android.systemui"),
            describe = describer::describe,
        )
    }

    companion object {
        fun of(ctx: Context): AppGraph = (ctx.applicationContext as TamaApp).graph
    }
}
