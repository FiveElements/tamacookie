package com.tamacookie.feature.station

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.StationView

/** Dessin de [CageStation] (Android : `Canvas`). */
object CageStationDrawer : StationDrawer {
    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        val left = 8 * d; val right = w - 8 * d
        val top = 22 * d; val bottom = h - 6 * d
        // socle
        r.set(left - 4 * d, bottom - 10 * d, right + 4 * d, bottom)
        p.color = Palette.CAGE_BASE; c.drawRoundRect(r, 4 * d, 4 * d, p); c.drawRoundRect(r, 4 * d, 4 * d, stroke)
        // dôme
        r.set(left, top - 18 * d, right, top + 18 * d)
        stroke.color = Palette.CAGE_BARS; stroke.strokeWidth = 3f * d
        c.drawArc(r, 180f, 180f, false, stroke)
        // barreaux
        val n = 7
        for (i in 0..n) {
            val x = left + (right - left) * i / n
            c.drawLine(x, top, x, bottom - 10 * d, stroke)
        }
        c.drawLine(left, top + (bottom - top) * 0.45f, right, top + (bottom - top) * 0.45f, stroke)
        // anneau
        c.drawCircle(w / 2, top - 22 * d, 5 * d, stroke)
        stroke.color = Palette.OUTLINE; stroke.strokeWidth = 2.2f * d
        // cadenas
        p.color = Palette.GOLD
        r.set(right - 22 * d, bottom - 40 * d, right - 8 * d, bottom - 26 * d)
        c.drawRoundRect(r, 3 * d, 3 * d, p); c.drawRoundRect(r, 3 * d, 3 * d, stroke)
    }
}
