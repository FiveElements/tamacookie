package com.tamacookie

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Un panneau flottant (fenêtre overlay [id]) : vue + paramètres de fenêtre + placement.
 * Remplace le trio « vue nullable + lp nullable + layoutX/closeX » répété pour chaque panneau.
 *
 * [widthDp] : largeur souhaitée (réduite sur un écran étroit) ; [watchOutside] : un toucher à
 * l'extérieur appelle [onOutside] (en général : fermer). [place] calcule `lp.x` / `lp.y` à partir
 * de la largeur et de la hauteur mesurées du panneau.
 */
class FloatingPanel<V : View>(
    private val windows: OverlayWindows,
    private val id: WindowId,
    private val widthDp: Float,
    private val watchOutside: Boolean,
    private val place: (lp: WindowManager.LayoutParams, w: Int, h: Int) -> Unit,
) {
    private val overlay get() = windows.overlay

    /** Le panneau affiché (null = fermé). */
    var view: V? = null
        private set
    private var lp: WindowManager.LayoutParams? = null

    val isOpen: Boolean get() = view != null && overlay.isAttached(id)

    /** Affiche [v] (le panneau déjà ouvert est d'abord retiré). */
    fun open(v: V, onOutside: (() -> Unit)? = null) {
        close()
        val p = windows.panelParams(windows.panelWidth(widthDp), watchOutside)
        if (onOutside != null) v.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { onOutside(); true } else false
        }
        view = v; lp = p
        layout()
        overlay.attach(id, v, p)
    }

    fun close() {
        overlay.detach(id)
        view = null; lp = null
    }

    /** Nouvelle taille d'écran (ou station déplacée) : replace le panneau ouvert. */
    fun reposition() {
        if (!isOpen) return
        layout()
        overlay.relayout(id)
    }

    private fun layout() {
        val v = view ?: return
        val p = lp ?: return
        val w = windows.panelWidth(widthDp)
        p.width = w
        place(p, w, windows.measurePanel(v, w))
    }
}
