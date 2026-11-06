package com.tamacookie

import android.app.Application
import android.content.Context

/**
 * Application : porte le [AppGraph] (injection manuelle), créé au démarrage du processus avant tout
 * service. Le service d'accessibilité et [BunnyService] tournent dans ce même processus.
 */
class TamaApp : Application() {
    val graph: AppGraph by lazy { AppGraph() }
}

/**
 * Graphe des dépendances (DI manuelle) : les adaptateurs uniques du processus. `ScreenSensorService`
 * **publie** dans [screen] ; `BunnyService` le reçoit comme port [ScreenSensor] (et le passe aux
 * fonctionnalités par `PetCtx.screen`). Fil principal uniquement.
 */
class AppGraph {
    /** Ce que voit le service d'accessibilité (remplace l'ancien objet global `Platforms`). */
    val screen = ScreenSensorHub()

    companion object {
        fun of(ctx: Context): AppGraph = (ctx.applicationContext as TamaApp).graph
    }
}
