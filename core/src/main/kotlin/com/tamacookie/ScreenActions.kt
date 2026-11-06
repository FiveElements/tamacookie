package com.tamacookie

/**
 * Port **`ScreenActions`** (Kotlin pur) : ce que le cœur peut **faire** sur l'écran des autres applications.
 * Une seule chose, volontairement : appuyer sur le bouton « Ignorer / Passer l'annonce » d'une publicité
 * (chasseur de pubs). Jamais un geste simulé à une coordonnée : l'adaptateur (`ScreenSensorService`) relit
 * l'arbre d'accessibilité, retrouve un nœud de la liste blanche ([ScreenScan.isAdSkip]) qui recoupe encore
 * [pressAdSkip]`(target)` et lui envoie `ACTION_CLICK` ; sinon il ne fait rien.
 *
 * Implémenté par [AdSkipRelay] (qui passe la demande au service connecté) ; faux en test : `FakeScreenActions`.
 */
interface ScreenActions {
    /**
     * Demande d'appuyer sur le bouton de pub situé en [target] (bornes à l'écran, px). true = demande transmise
     * (réglage actif, bouton toujours connu à cet endroit, service connecté) ; l'appui lui-même est fait plus
     * tard, sur le fil du scan, seulement si le bouton est encore là.
     */
    fun pressAdSkip(target: Box): Boolean
}

/**
 * Le port [ScreenActions] du processus ([AppGraph]) : garde-fou devant le service d'accessibilité. Une demande
 * n'est transmise que si le chasseur de pubs est voulu ([ScreenSensorHub.adSkipWanted]) et que [pressAdSkip]
 * vise le bouton connu ([ScreenSnapshot.adSkip], qu'il doit recouper) ; le service relit ensuite l'arbre et ne
 * clique que sur un nœud de la liste blanche qui recoupe **encore** la cible.
 */
class AdSkipRelay(private val screen: ScreenSensorHub) : ScreenActions {
    /** Ce qui appuie vraiment : branché par le service d'accessibilité à sa connexion, null à sa destruction. */
    var presser: ((Box) -> Boolean)? = null

    override fun pressAdSkip(target: Box): Boolean {
        val cur = screen.snapshot.adSkip
        if (!screen.adSkipWanted || cur == null || !cur.intersects(target)) return false
        return presser?.invoke(target) ?: false
    }
}
