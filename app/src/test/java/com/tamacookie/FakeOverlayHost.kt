package com.tamacookie

/** Une opération reçue par le faux gestionnaire de fenêtres. */
data class WinOp(val op: String, val id: WindowId, val x: Int = 0, val y: Int = 0)

/** Faux WindowManager : enregistre les ajouts, déplacements (= updateViewLayout), retraits et redessins. */
class RecordingBackend : WindowBackend<WindowId> {
    val ops = ArrayList<WinOp>()
    val shown = HashMap<WindowId, Pair<Int, Int>>()

    override fun add(w: WindowId, x: Int, y: Int, fadeIn: Boolean) {
        check(w !in shown) { "addView sur une fenêtre déjà affichée : $w" }
        shown[w] = x to y; ops += WinOp(if (fadeIn) "add+fade" else "add", w, x, y)
    }
    override fun move(w: WindowId, x: Int, y: Int) {
        check(w in shown) { "updateViewLayout sur une fenêtre absente : $w" }
        shown[w] = x to y; ops += WinOp("move", w, x, y)
    }
    override fun remove(w: WindowId) {
        check(shown.remove(w) != null) { "removeView sur une fenêtre absente : $w" }
        ops += WinOp("remove", w)
    }
    override fun redraw(w: WindowId) { ops += WinOp("redraw", w) }

    fun count(op: String) = ops.count { it.op == op }
}

/**
 * Faux hôte des fenêtres (JVM pur) : le vrai registre [WindowTable], branché sur un [RecordingBackend]
 * (chaque fenêtre est représentée par son id). Toute entité peut être affichée.
 */
class FakeOverlayHost(val backend: RecordingBackend = RecordingBackend()) : WindowTable<WindowId>(backend) {
    init { factory = { it } }

    /** Position affichée d'une fenêtre (après flush), null = pas affichée. */
    fun shownAt(id: WindowId): Pair<Int, Int>? = backend.shown[id]
}
