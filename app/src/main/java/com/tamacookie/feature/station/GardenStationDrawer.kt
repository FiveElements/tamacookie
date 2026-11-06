package com.tamacookie.feature.station

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.StationView

/** Dessin de [GardenStation] (Android : `Canvas`). */
object GardenStationDrawer : StationDrawer {
    private val FLOWERS_X = floatArrayOf(0.22f, 0.58f, 0.82f)

    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        // bac de terre
        r.set(4 * d, h * 0.55f, w - 4 * d, h - 3 * d)
        p.color = Palette.SOIL; c.drawRoundRect(r, 6 * d, 6 * d, p); c.drawRoundRect(r, 6 * d, 6 * d, stroke)
        // herbe
        stroke.color = Palette.GRASS
        stroke.strokeWidth = 2.6f * d
        for (i in 0..13) {
            val x = w * (0.07f + i * 0.066f)
            c.drawLine(x, h * 0.62f, x + (i % 3 - 1) * 4 * d, h * (0.34f + (i % 4) * 0.05f), stroke)
        }
        stroke.color = Palette.OUTLINE; stroke.strokeWidth = 2f * d
        // fleurs : quatre pétales autour d'un cœur jaune
        for (i in FLOWERS_X.indices) {
            val x = w * FLOWERS_X[i]; val y = h * 0.32f
            p.color = if (i % 2 == 0) Palette.FLOWER_A else Palette.FLOWER_B
            c.drawCircle(x - 4 * d, y, 3.2f * d, p); c.drawCircle(x + 4 * d, y, 3.2f * d, p)
            c.drawCircle(x, y - 4 * d, 3.2f * d, p); c.drawCircle(x, y + 4 * d, 3.2f * d, p)
            p.color = Palette.YELLOW
            c.drawCircle(x, y, 2.4f * d, p)
        }
    }
}
