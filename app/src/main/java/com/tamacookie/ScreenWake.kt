package com.tamacookie

/**
 * Veille de l'écran (pur) : quand la boucle doit reprendre après un écran éteint.
 *
 * Écran éteint → endormi. Écran rallumé et déverrouillé → réveillé. Écran rallumé mais verrouillé : on attend le
 * déverrouillage. `ACTION_USER_PRESENT` ne suffit pas : sur certaines tablettes (Galaxy Tab S10+, Android 16) il
 * n'arrive pas toujours, et le lapin restait figé (fenêtre immobile, seule la bulle suivait le doigt). D'où :
 * - [poll] : tant que l'écran est allumé et qu'on attend, l'hôte relit l'état du verrou (toutes les [POLL_MS]) ;
 * - [touched] : un toucher sur une de nos fenêtres prouve que l'écran est utilisable.
 * Chaque méthode renvoie `true` si l'animal vient de se réveiller (l'hôte relance alors la boucle).
 */
class ScreenWake {
    var awake = true
        private set
    /** Écran allumé mais verrouillé : l'hôte doit appeler [poll] régulièrement. */
    var waitingUnlock = false
        private set

    fun screenOff() {
        awake = false
        waitingUnlock = false
    }

    fun screenOn(locked: Boolean): Boolean {
        if (awake) return false
        if (locked) { waitingUnlock = true; return false }
        return wake()
    }

    fun userPresent(): Boolean = if (awake) false else wake()

    /** Relecture du verrou pendant l'attente. */
    fun poll(locked: Boolean): Boolean {
        if (awake || !waitingUnlock) return false
        return if (locked) false else wake()
    }

    fun touched(): Boolean = if (awake) false else wake()

    private fun wake(): Boolean {
        awake = true
        waitingUnlock = false
        return true
    }

    companion object {
        const val POLL_MS = 1000L
    }
}
