package com.tamacookie.feature.toy

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.PropView

/** Dessin de [PlushToy] (Android : `Canvas`). */
object PlushToyDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        // petit ours en peluche
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        c.save(); c.rotate(v.spin, w / 2, h / 2)
        p.color = Palette.WOOD
        c.drawCircle(w * 0.28f, h * 0.20f, w * 0.11f, p); c.drawCircle(w * 0.28f, h * 0.20f, w * 0.11f, stroke)
        c.drawCircle(w * 0.72f, h * 0.20f, w * 0.11f, p); c.drawCircle(w * 0.72f, h * 0.20f, w * 0.11f, stroke)
        r.set(w * 0.20f, h * 0.48f, w * 0.80f, h * 0.98f); c.drawOval(r, p); c.drawOval(r, stroke)
        c.drawCircle(w / 2, h * 0.36f, w * 0.26f, p); c.drawCircle(w / 2, h * 0.36f, w * 0.26f, stroke)
        p.color = Palette.PLUSH_LIGHT
        r.set(w * 0.38f, h * 0.38f, w * 0.62f, h * 0.52f); c.drawOval(r, p)
        r.set(w * 0.34f, h * 0.62f, w * 0.66f, h * 0.88f); c.drawOval(r, p)
        p.color = Palette.INK
        c.drawCircle(w * 0.41f, h * 0.31f, 1.8f * d, p); c.drawCircle(w * 0.59f, h * 0.31f, 1.8f * d, p)
        c.drawCircle(w / 2, h * 0.42f, 1.8f * d, p)
        c.restore()
    }
}
