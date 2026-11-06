package com.tamacookie.feature.toy

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.PropView

/** Dessin de [RopeToy] (Android : `Canvas`). */
object RopeToyDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        c.save(); c.rotate(v.spin, w / 2, h / 2)
        // corde tressée
        r.set(w * 0.14f, h * 0.34f, w * 0.86f, h * 0.66f)
        p.color = Palette.ROPE; c.drawRoundRect(r, 4 * d, 4 * d, p); c.drawRoundRect(r, 4 * d, 4 * d, stroke)
        stroke.color = Palette.ROPE_STRIPE
        for (i in 0..4) {
            val x = w * (0.24f + i * 0.13f)
            c.drawLine(x - 3 * d, h * 0.38f, x + 3 * d, h * 0.62f, stroke)
        }
        stroke.color = Palette.OUTLINE
        // nœuds aux deux bouts
        c.drawCircle(w * 0.12f, h / 2, h * 0.32f, p); c.drawCircle(w * 0.12f, h / 2, h * 0.32f, stroke)
        c.drawCircle(w * 0.88f, h / 2, h * 0.32f, p); c.drawCircle(w * 0.88f, h / 2, h * 0.32f, stroke)
        c.restore()
    }
}
