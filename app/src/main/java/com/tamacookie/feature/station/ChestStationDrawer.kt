package com.tamacookie.feature.station

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.StationView

/** Dessin de [ChestStation] (Android : `Canvas`). */
object ChestStationDrawer : StationDrawer {
    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d; val t = v.t
        val wood = Palette.WOOD
        val dark = Palette.WOOD_DARK
        val gold = Palette.GOLD
        val boxTop = h * 0.45f
        // couvercle (levé si ouvert)
        c.save()
        if (v.open) c.rotate(-28f, w * 0.12f, boxTop)
        r.set(w * 0.08f, h * 0.14f, w * 0.92f, boxTop + 4 * d)
        p.color = wood; c.drawRoundRect(r, 14 * d, 14 * d, p); c.drawRoundRect(r, 14 * d, 14 * d, stroke)
        p.color = dark
        c.drawRect(w * 0.08f, boxTop - 6 * d, w * 0.92f, boxTop + 4 * d, p)
        c.restore()
        // intérieur lumineux quand ouvert
        if (v.open) {
            p.color = Palette.CHEST_GLOW
            r.set(w * 0.12f, boxTop - 8 * d, w * 0.88f, boxTop + 6 * d); c.drawOval(r, p)
            p.color = Palette.YELLOW
            for (i in 0..2) c.drawCircle(w * (0.3f + i * 0.2f), boxTop - 14 * d - (t * 20 + i * 7) % 14 * d, 2.4f * d, p)
        }
        // caisse
        r.set(w * 0.08f, boxTop, w * 0.92f, h - 4 * d)
        p.color = wood; c.drawRoundRect(r, 6 * d, 6 * d, p); c.drawRoundRect(r, 6 * d, 6 * d, stroke)
        p.color = dark
        c.drawRect(w * 0.08f, boxTop + (h - boxTop) * 0.45f, w * 0.92f, boxTop + (h - boxTop) * 0.55f, p)
        // ferrures
        p.color = gold
        c.drawRect(w * 0.16f, boxTop, w * 0.22f, h - 4 * d, p)
        c.drawRect(w * 0.78f, boxTop, w * 0.84f, h - 4 * d, p)
        r.set(w * 0.43f, boxTop - 4 * d, w * 0.57f, boxTop + 14 * d)
        c.drawRoundRect(r, 3 * d, 3 * d, p); c.drawRoundRect(r, 3 * d, 3 * d, stroke)
    }
}
