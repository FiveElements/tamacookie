package com.tamacookie.feature.toy

import android.graphics.Canvas
import android.graphics.Color
import com.tamacookie.Palette
import com.tamacookie.PropView
import kotlin.math.cos
import kotlin.math.sin

/** Dessin de [FrisbeeToy] (Android : `Canvas`). */
object FrisbeeToyDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        c.save(); c.rotate(v.spin * 0.08f, w / 2, h / 2)
        p.color = v.tint
        r.set(w * 0.04f, h * 0.30f, w * 0.96f, h * 0.70f); c.drawOval(r, p); c.drawOval(r, stroke)
        p.color = Palette.FRISBEE_SHINE
        r.set(w * 0.22f, h * 0.38f, w * 0.78f, h * 0.58f); c.drawOval(r, p)
        // petit repère qui tourne
        val a = Math.toRadians(v.spin.toDouble())
        p.color = Color.WHITE
        c.drawCircle(w / 2 + (cos(a) * w * 0.32f).toFloat(), h / 2 + (sin(a) * h * 0.12f).toFloat(), 2.2f * d, p)
        c.restore()
    }
}
