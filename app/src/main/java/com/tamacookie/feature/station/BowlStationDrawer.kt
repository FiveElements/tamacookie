package com.tamacookie.feature.station

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.StationView

/** Dessin de [BowlStation] (Android : `Canvas`). */
object BowlStationDrawer : StationDrawer {
    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val path = pen.path; val d = pen.d
        val top = h * 0.52f
        // foin qui dépasse
        stroke.color = Palette.HAY_DARK
        for (i in 0..7) {
            val x = w * (0.2f + i * 0.085f)
            c.drawLine(x, top + 4 * d, x + (i % 3 - 1) * 5 * d, top - (6 + i % 3 * 3) * d, stroke)
        }
        p.color = Palette.HAY
        r.set(w * 0.14f, top - 6 * d, w * 0.86f, top + 8 * d); c.drawOval(r, p)
        stroke.color = Palette.OUTLINE
        // bol
        path.reset()
        path.moveTo(w * 0.08f, top)
        path.lineTo(w * 0.92f, top)
        path.cubicTo(w * 0.90f, h - 4 * d, w * 0.70f, h - 4 * d, w * 0.5f, h - 4 * d)
        path.cubicTo(w * 0.30f, h - 4 * d, w * 0.10f, h - 4 * d, w * 0.08f, top)
        path.close()
        p.color = Palette.BOWL; c.drawPath(path, p); c.drawPath(path, stroke)
        r.set(w * 0.06f, top - 3 * d, w * 0.94f, top + 4 * d)
        p.color = Palette.BOWL_RIM; c.drawRoundRect(r, 3 * d, 3 * d, p); c.drawRoundRect(r, 3 * d, 3 * d, stroke)
        // os décoratif
        p.color = Palette.BOWL_RIM
        r.set(w * 0.40f, h * 0.72f, w * 0.60f, h * 0.80f); c.drawRect(r, p)
        c.drawCircle(w * 0.40f, h * 0.72f, 3 * d, p); c.drawCircle(w * 0.40f, h * 0.80f, 3 * d, p)
        c.drawCircle(w * 0.60f, h * 0.72f, 3 * d, p); c.drawCircle(w * 0.60f, h * 0.80f, 3 * d, p)
    }
}
