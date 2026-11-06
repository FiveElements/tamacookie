package com.tamacookie

import com.tamacookie.feature.station.StationKind

/**
 * Une station flottante (Kotlin pur) : où elle est installée, sa position et ce qu'affiche sa fenêtre
 * ([highlight], [occupied], [open], [t]). La fenêtre ([StationView]) est fabriquée par l'adaptateur et lit cet état.
 * [w] × [h] = taille de la fenêtre (px).
 */
class Station(val kind: StationKind, val w: Int, val h: Int) {
    val behavior get() = kind.behavior
    val windowId = WindowId.Station(kind)
    /** Vue où la station est installée (null = rangée). */
    var anchor: String? = null
    /** Centre de la station en coordonnées de sa vue. */
    var cx = 0f
    var cy = 0f
    /** Dernière position de la fenêtre à l'écran (coin haut gauche, décalage du défilement compris). */
    var x = 0
    var y = 0

    /** Surlignée (on glisse l'animal au-dessus). */
    var highlight = false
    /** Un animal est dedans. */
    var occupied = false
    /** Coffre ouvert (couvercle levé). */
    var open = false
    /** Temps de l'animation du décor (baignoire), s. */
    var t = 0f

    fun contains(x: Float, y: Float, slack: Float): Boolean =
        x > cx - w / 2f - slack && x < cx + w / 2f + slack &&
            y > cy - h / 2f - slack && y < cy + h / 2f + slack
    val bottom: Float get() = cy + h / 2f
}
