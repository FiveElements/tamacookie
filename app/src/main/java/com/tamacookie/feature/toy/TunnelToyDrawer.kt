package com.tamacookie.feature.toy

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.PropView

/** Dessin de [TunnelToy] (Android : `Canvas`). */
object TunnelToyDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        // tube couché
        r.set(w * 0.06f, h * 0.10f, w * 0.94f, h * 0.98f)
        p.color = Palette.TUNNEL; c.drawRoundRect(r, h * 0.45f, h * 0.45f, p); c.drawRoundRect(r, h * 0.45f, h * 0.45f, stroke)
        // anneaux
        p.color = Palette.TUNNEL_RING
        for (i in 1..4) {
            val x = w * (0.06f + 0.88f * i / 5f)
            r.set(x - 2 * d, h * 0.12f, x + 2 * d, h * 0.96f); c.drawRect(r, p)
        }
        // ouvertures aux deux bouts
        p.color = Palette.TUNNEL_HOLE
        r.set(w * 0.02f, h * 0.22f, w * 0.14f, h * 0.98f); c.drawOval(r, p); c.drawOval(r, stroke)
        r.set(w * 0.86f, h * 0.22f, w * 0.98f, h * 0.98f); c.drawOval(r, p); c.drawOval(r, stroke)
    }
}
