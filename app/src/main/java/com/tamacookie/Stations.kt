package com.tamacookie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.tamacookie.feature.station.StationDrawer
import com.tamacookie.feature.station.StationDrawers
import com.tamacookie.feature.station.StationKind

/** Fenêtre d'une station : affiche l'état de son [Station] (dessin : [StationDrawers]). */
@SuppressLint("ViewConstructor")
class StationView(context: Context, val station: Station) : View(context), Refreshable {
    val kind: StationKind get() = station.kind
    val occupied: Boolean get() = station.occupied
    /** Coffre ouvert (couvercle levé). */
    val open: Boolean get() = station.open
    val t: Float get() = station.t
    /** Surlignage affiché (agrandissement animé quand le modèle change). */
    private var highlighted = false

    private val drawer: StationDrawer = StationDrawers.of(station.kind)

    /** Pinceaux partagés par les dessins des stations ([StationDrawer.draw]). */
    internal val pen = StationPen(resources.displayMetrics.density)

    override fun refresh() {
        val h = station.highlight
        if (h != highlighted) {
            highlighted = h
            animate().scaleX(if (h) 1.12f else 1f).scaleY(if (h) 1.12f else 1f).setDuration(120).start()
        }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        drawer.draw(c, this, width.toFloat(), height.toFloat())
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
