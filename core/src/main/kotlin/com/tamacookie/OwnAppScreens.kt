package com.tamacookie

/**
 * Un écran (activité) de notre propre app est-il visible ? Tenu par l'adaptateur (`TamaApp` : rappels
 * `onActivityStarted` / `onActivityStopped` de **toutes** les activités, même processus que le service), lu par
 * le service : pendant qu'un de nos écrans est visible, les stations vides sont retirées pour ne pas le recouvrir
 * ([StationController.ownAppVisible]).
 *
 * Visible = entre `onStart` et `onStop` (et non `onResume` / `onPause`) : une fenêtre pop-up ou un écran partagé
 * restent visibles sans avoir le focus. Les jetons (une activité) sont comptés un par un : en passant de l'écran
 * principal à l'adoption, la seconde démarre avant que la première s'arrête — l'app ne cesse pas d'être visible.
 * Fil principal uniquement.
 */
class OwnAppScreens {
    private val started = HashSet<Any>()

    /** Vrai tant qu'au moins un écran de l'app est visible ; les écouteurs sont prévenus à chaque bascule. */
    val visible = Observable(false)

    fun started(token: Any) {
        started += token
        visible.set(true)
    }

    fun stopped(token: Any) {
        started -= token
        visible.set(started.isNotEmpty())
    }
}
