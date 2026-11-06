package com.tamacookie

import com.tamacookie.feature.station.StationKind
import kotlin.math.roundToInt

/**
 * Identifiant d'une fenêtre overlay, typé et rangé par entité : deux animaux ont chacun leur fenêtre
 * et leur bulle, chaque objet posé et chaque station la sienne. Sert de clé à [OverlayHost].
 */
sealed interface WindowId {
    /** La fenêtre d'un animal. */
    data class Pet(val pet: PetId) : WindowId
    /** La bulle de dialogue d'un animal. */
    data class Bubble(val pet: PetId) : WindowId
    data class Station(val kind: StationKind) : WindowId
    /** Un objet posé ([com.tamacookie.Prop.id]). */
    data class Prop(val id: Int) : WindowId
    /** Le bouton 🏠. */
    data object Dock : WindowId
    /** Le menu de soins. */
    data object Menu : WindowId
    /** Un panneau flottant (véto, coffre, 🏠…). */
    data class Panel(val name: String) : WindowId
}

/**
 * Port : la seule autorité sur les fenêtres overlay (implémentée par `OverlayManager`, faux hôte en test).
 *
 * Idempotent : attacher une fenêtre déjà affichée ne fait que la déplacer ; détacher une fenêtre absente
 * ne fait rien. Les déplacements sont **regroupés** : au plus une mise à jour par fenêtre à chaque [flush]
 * (fin d'image), et aucune si la position n'a pas changé.
 */
interface OverlayHost {
    fun isAttached(id: WindowId): Boolean

    /**
     * Affiche la fenêtre d'une entité (objet, station) en ([x], [y]) ; sa vue est fabriquée par l'adaptateur
     * d'après l'entité du monde. [fadeIn] : apparition en fondu. Déjà affichée : simple [move].
     */
    fun attach(id: WindowId, x: Int, y: Int, fadeIn: Boolean = false)

    /** Déplace la fenêtre (appliqué au prochain [flush]) ; une fenêtre non affichée garde seulement la position. */
    fun move(id: WindowId, x: Int, y: Int)

    /** L'état affiché par la fenêtre a changé : à redessiner. */
    fun redraw(id: WindowId)

    /** Retire la fenêtre (sa vue est gardée pour un prochain [attach]). */
    fun detach(id: WindowId)

    /** Retire la fenêtre et oublie sa vue (l'entité a disparu). */
    fun release(id: WindowId)

    /** Retire toutes les fenêtres (« Cacher », arrêt du service). */
    fun detachAll()

    /** Applique les déplacements en attente. */
    fun flush()
}

/**
 * Fenêtre ancrée à une vue (app / page du bureau) : affichée, déplacée ou retirée selon [off]
 * ([PageTracker.visibleOffset] ; NaN = vue pas affichée). [left] = bord gauche sur sa vue.
 * Renvoie le x à l'écran, ou null si la fenêtre a été retirée.
 */
fun OverlayHost.placeAnchored(id: WindowId, left: Float, y: Int, off: Float, fadeIn: Boolean = false): Int? {
    if (off.isNaN()) { detach(id); return null }
    val x = (left + off).roundToInt()
    if (isAttached(id)) move(id, x, y) else attach(id, x, y, fadeIn)
    return x
}

/** Ce que l'adaptateur sait faire d'une fenêtre (WindowManager côté Android, enregistreur en test). */
interface WindowBackend<W> {
    fun add(w: W, x: Int, y: Int, fadeIn: Boolean)
    fun move(w: W, x: Int, y: Int)
    fun remove(w: W)
    fun redraw(w: W)
}

/**
 * Registre des fenêtres (Kotlin pur) : qui est affiché, où, et regroupement des déplacements.
 * [factory] fabrique la fenêtre d'une entité au premier [attach] ; les autres sont fournies par [attach] (avec `w`).
 */
open class WindowTable<W : Any>(private val backend: WindowBackend<W>) : OverlayHost {
    private class Entry<W>(var w: W) {
        var attached = false
        /** Position appliquée. */
        var x = 0
        var y = 0
        /** Position demandée, en attente du prochain flush. */
        var pending = false
        var px = 0
        var py = 0
    }

    private val entries = HashMap<WindowId, Entry<W>>()
    private val dirty = ArrayList<Entry<W>>()

    var factory: ((WindowId) -> W?)? = null

    /** Nombre de mises à jour de position envoyées (statistique, tests). */
    var moves = 0L
        private set

    /** La fenêtre enregistrée sous [id] (affichée ou non). */
    fun window(id: WindowId): W? = entries[id]?.w

    override fun isAttached(id: WindowId): Boolean = entries[id]?.attached == true

    /** Affiche [w] sous [id] ; une autre fenêtre déjà affichée sous cet id est d'abord retirée. */
    fun attach(id: WindowId, w: W, x: Int, y: Int, fadeIn: Boolean = false) {
        val e = entries[id]
        if (e != null && e.w !== w) { detach(id); e.w = w }
        place(id, e ?: Entry(w).also { entries[id] = it }, x, y, fadeIn)
    }

    override fun attach(id: WindowId, x: Int, y: Int, fadeIn: Boolean) {
        val e = entries[id] ?: (factory?.invoke(id) ?: return).let { Entry(it).also { n -> entries[id] = n } }
        place(id, e, x, y, fadeIn)
    }

    private fun place(id: WindowId, e: Entry<W>, x: Int, y: Int, fadeIn: Boolean) {
        if (e.attached) { move(id, x, y); return }
        e.pending = false
        backend.add(e.w, x, y, fadeIn)       // peut lever : la fenêtre reste alors non affichée
        e.x = x; e.y = y
        e.attached = true
    }

    override fun move(id: WindowId, x: Int, y: Int) {
        val e = entries[id] ?: return
        if (!e.attached) { e.x = x; e.y = y; return }
        if (!e.pending) {
            if (x == e.x && y == e.y) return
            e.pending = true
            dirty.add(e)
            if (dirty.size == 1) onPending()
        }
        e.px = x; e.py = y
    }

    /** Position appliquée immédiatement (taille ou paramètres changés : panneau replacé, bulle). */
    fun relayout(id: WindowId, x: Int, y: Int) {
        val e = entries[id] ?: return
        e.pending = false
        e.x = x; e.y = y
        if (e.attached) { backend.move(e.w, x, y); moves++ }
    }

    override fun flush() {
        if (dirty.isEmpty()) return
        for (i in dirty.indices) {
            val e = dirty[i]
            if (!e.pending) continue
            e.pending = false
            if (!e.attached || (e.px == e.x && e.py == e.y)) continue
            e.x = e.px; e.y = e.py
            backend.move(e.w, e.x, e.y)
            moves++
        }
        dirty.clear()
    }

    override fun redraw(id: WindowId) {
        val e = entries[id] ?: return
        backend.redraw(e.w)
    }

    override fun detach(id: WindowId) {
        val e = entries[id] ?: return
        e.pending = false
        if (!e.attached) return
        e.attached = false
        backend.remove(e.w)
    }

    override fun release(id: WindowId) {
        detach(id)
        entries.remove(id)
    }

    override fun detachAll() {
        for (id in entries.keys.toList()) detach(id)
        dirty.clear()
    }

    /** Un premier déplacement attend : l'adaptateur peut programmer un [flush] (hors de la boucle d'images). */
    protected open fun onPending() {}
}
