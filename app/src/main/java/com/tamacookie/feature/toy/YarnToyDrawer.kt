package com.tamacookie.feature.toy

import android.graphics.Canvas
import com.tamacookie.PropView

/** Dessin de [YarnToy] (Android : `Canvas`). */
object YarnToyDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r
        c.save(); c.rotate(v.spin, w / 2, h / 2)
        val rad = w * 0.42f
        p.color = v.tint; c.drawCircle(w / 2, h / 2, rad, p)
        // fils enroulés
        r.set(w / 2 - rad * 0.8f, h / 2 - rad * 0.5f, w / 2 + rad * 0.8f, h / 2 + rad * 0.5f)
        c.drawArc(r, 200f, 140f, false, stroke)
        r.set(w / 2 - rad * 0.5f, h / 2 - rad * 0.85f, w / 2 + rad * 0.5f, h / 2 + rad * 0.85f)
        c.drawArc(r, 290f, 140f, false, stroke)
        c.drawCircle(w / 2, h / 2, rad, stroke)
        // le bout du fil
        c.drawLine(w / 2 + rad * 0.7f, h / 2 + rad * 0.7f, w * 0.98f, h * 0.98f, stroke)
        c.restore()
    }
}
