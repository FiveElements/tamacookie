package com.tamacookie.feature.toy

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.PropView

/** Dessin de [BallToy] (Android : `Canvas`). */
object BallToyDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val r = pen.r
        c.save(); c.rotate(v.spin, w / 2, h / 2)
        val rad = w * 0.44f
        p.color = Palette.BALL; c.drawCircle(w / 2, h / 2, rad, p)
        p.color = Palette.BALL_STRIPE
        r.set(w / 2 - rad, h / 2 - rad * 0.3f, w / 2 + rad, h / 2 + rad * 0.3f)
        c.drawRect(r, p)
        c.drawCircle(w / 2, h / 2, rad, pen.stroke)
        c.restore()
    }
}
