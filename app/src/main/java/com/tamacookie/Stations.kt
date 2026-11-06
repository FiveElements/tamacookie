package com.tamacookie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.WindowManager
import com.tamacookie.feature.station.StationKind

@SuppressLint("ViewConstructor")
class StationView(context: Context, val kind: StationKind) : View(context) {
    var highlight = false
        set(v) { if (field != v) { field = v; animate().scaleX(if (v) 1.12f else 1f).scaleY(if (v) 1.12f else 1f).setDuration(120).start() } }
    var occupied = false
        set(v) { field = v; invalidate() }
    /** Coffre ouvert (couvercle levé). */
    var open = false
        set(v) { field = v; invalidate() }
    var t = 0f

    /** Pinceaux partagés par les dessins des stations ([StationKind.behavior] `.draw`). */
    internal val pen = StationPen(resources.displayMetrics.density)

    override fun onDraw(c: Canvas) {
        kind.behavior.draw(c, this, width.toFloat(), height.toFloat())
    }
}

/** Pinceaux et objets de dessin réutilisés (aucune allocation pendant le rendu). */
class StationPen(val d: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.2f * d; color = Palette.OUTLINE
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    val r = RectF()
    val path = Path()
}

/** Une station flottante et sa fenêtre. */
class Station(val kind: StationKind, val view: StationView, val lp: WindowManager.LayoutParams) {
    val behavior get() = kind.behavior
    var shown = false
    /** Vue où la station est installée (null = rangée). */
    var anchor: String? = null
    /** Centre de la station en coordonnées écran. */
    var cx = 0f
    var cy = 0f
    fun contains(x: Float, y: Float, slack: Float): Boolean =
        x > cx - lp.width / 2f - slack && x < cx + lp.width / 2f + slack &&
            y > cy - lp.height / 2f - slack && y < cy + lp.height / 2f + slack
    val bottom: Float get() = cy + lp.height / 2f
}
