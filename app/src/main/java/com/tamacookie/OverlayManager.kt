package com.tamacookie

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager

/** Une fenêtre Android : sa vue et ses paramètres. */
class Win(val view: View, val lp: WindowManager.LayoutParams)

/** Vue qui relit son modèle quand on lui demande de se redessiner (ex. surlignage d'une station). */
interface Refreshable {
    fun refresh()
}

/**
 * Adaptateur Android de [OverlayHost] : **seul** code qui appelle `addView` / `updateViewLayout` /
 * `removeViewImmediate` pour les fenêtres du service. Les déplacements demandés hors de la boucle
 * d'images (glisser une station, le dock) sont appliqués juste après, par un message posté.
 */
class OverlayManager(private val wm: WindowManager) : WindowTable<Win>(Backend(wm)) {
    private val handler = Handler(Looper.getMainLooper())
    private var flushPosted = false
    private val flushTask = Runnable { flushPosted = false; flush() }

    /** Affiche [view] sous [id] avec ses paramètres ([lp].x / [lp].y = position). */
    fun attach(id: WindowId, view: View, lp: WindowManager.LayoutParams, fadeIn: Boolean = false) {
        val w = window(id)?.takeIf { it.view === view && it.lp === lp } ?: Win(view, lp)
        attach(id, w, lp.x, lp.y, fadeIn)
    }

    /** Paramètres changés (taille, position) : appliqués tout de suite. */
    fun relayout(id: WindowId) {
        val w = window(id) ?: return
        relayout(id, w.lp.x, w.lp.y)
    }

    override fun onPending() {
        if (flushPosted) return
        flushPosted = true
        handler.post(flushTask)
    }

    private class Backend(private val wm: WindowManager) : WindowBackend<Win> {
        override fun add(w: Win, x: Int, y: Int, fadeIn: Boolean) {
            w.lp.x = x; w.lp.y = y
            wm.addView(w.view, w.lp)
            if (fadeIn) { w.view.alpha = 0f; w.view.animate().alpha(1f).setDuration(160).start() }
            else w.view.alpha = 1f
        }

        override fun move(w: Win, x: Int, y: Int) {
            w.lp.x = x; w.lp.y = y
            wm.updateViewLayout(w.view, w.lp)
        }

        /**
         * Retrait sans dépendre de isAttachedToWindow (qui ne passe à true qu'après la première frame :
         * un retrait trop rapide laissait une fenêtre orpheline).
         */
        override fun remove(w: Win) {
            try { wm.removeViewImmediate(w.view) } catch (e: Exception) {
                Log.w("TamaCookie", "removeView", e)
            }
        }

        /** Taille affichée (ou demandée, avant la première mise en page) ; ≤ 0 si encore inconnue. */
        override fun width(w: Win): Int = if (w.view.width > 0) w.view.width else w.lp.width
        override fun height(w: Win): Int = if (w.view.height > 0) w.view.height else w.lp.height

        override fun redraw(w: Win) {
            val v = w.view
            if (v is Refreshable) v.refresh() else v.invalidate()
        }
    }
}
