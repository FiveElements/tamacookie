package com.tamacookie.feature.station

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.StationView

/** Dessin de [BedStation] (Android : `Canvas`). */
object BedStationDrawer : StationDrawer {
    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        // tête de lit (à gauche) et pied de lit (à droite)
        r.set(2 * d, h * 0.18f, 14 * d, h - 2 * d)
        p.color = Palette.BED_WOOD; c.drawRoundRect(r, 5 * d, 5 * d, p); c.drawRoundRect(r, 5 * d, 5 * d, stroke)
        r.set(w - 12 * d, h * 0.45f, w - 2 * d, h - 2 * d)
        c.drawRoundRect(r, 4 * d, 4 * d, p); c.drawRoundRect(r, 4 * d, 4 * d, stroke)
        // oreillers
        p.color = Palette.BED_PILLOW
        r.set(16 * d, h * 0.42f, w * 0.38f, h * 0.62f); c.drawRoundRect(r, 8 * d, 8 * d, p); c.drawRoundRect(r, 8 * d, 8 * d, stroke)
        r.set(w * 0.40f, h * 0.42f, w * 0.64f, h * 0.62f); c.drawRoundRect(r, 8 * d, 8 * d, p); c.drawRoundRect(r, 8 * d, 8 * d, stroke)
        // couverture (devant l'animal : il est dessous)
        r.set(12 * d, h * 0.58f, w - 10 * d, h - 8 * d)
        p.color = Palette.BED_BLANKET; c.drawRoundRect(r, 6 * d, 6 * d, p); c.drawRoundRect(r, 6 * d, 6 * d, stroke)
        p.color = Palette.BED_BLANKET_DOT
        for (i in 0..5) c.drawCircle(w * (0.16f + i * 0.14f), h * (0.72f + (i % 2) * 0.08f), 2.4f * d, p)
    }
}
