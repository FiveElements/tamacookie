package com.tamacookie

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.PowerManager

/**
 * Application : porte le [AppGraph] (injection manuelle), créé au démarrage du processus avant tout
 * service. Le service d'accessibilité et [BunnyService] tournent dans ce même processus.
 */
class TamaApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        // Nos écrans visibles (toutes les activités, pop-up et écran partagé compris) : le service y lit
        // s'il doit retirer les stations vides (OwnAppScreens).
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) = graph.ownApp.started(activity)
            override fun onActivityStopped(activity: Activity) = graph.ownApp.stopped(activity)
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

/**
 * Graphe des dépendances (DI manuelle) : les adaptateurs du processus. `ScreenSensorService`
 * **publie** dans [screen] ; `BunnyService` le reçoit comme port [ScreenSensor] (et le passe aux
 * fonctionnalités par `PetCtx.screen`). Fil principal uniquement.
 */
class AppGraph(private val app: Context) {
    /** Ce que voit le service d'accessibilité (remplace l'ancien objet global `Platforms`). Unique. */
    val screen = ScreenSensorHub()

    /** Ce que le cœur peut faire sur l'écran (chasseur de pubs) : garde-fou devant le service d'accessibilité. */
    val screenActions = AdSkipRelay(screen)

    /**
     * Chasseur de pubs : rend nos fenêtres overlay qui recouvrent la zone transparentes et traversables un court
     * instant (le toucher simulé doit atteindre le bouton de l'app). Branché par `BunnyService`, null sans lui.
     */
    var clearOverlayArea: ((Box) -> Unit)? = null

    /** Nos propres écrans visibles (tenu par [TamaApp], lu par `BunnyService`). Unique. */
    val ownApp = OwnAppScreens()

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
