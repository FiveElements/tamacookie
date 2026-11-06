package com.tamacookie.feature.toy

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.PropView

/** Dessin de [BalloonToy] (Android : `Canvas`). */
object BalloonToyDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val path = pen.path; val r = pen.r; val d = pen.d
        // ballon en haut de la vue, ficelle en dessous
        c.save(); c.rotate(v.spin, w / 2, w * 0.5f)
        path.reset()
        path.moveTo(w / 2, w * 0.98f)
        path.cubicTo(w * 0.45f, h * 0.75f, w * 0.62f, h * 0.85f, w * 0.5f, h)
        stroke.strokeWidth = 1.4f * d; c.drawPath(path, stroke); stroke.strokeWidth = 2f * d
        p.color = v.tint
        r.set(w * 0.08f, w * 0.02f, w * 0.92f, w * 0.98f); c.drawOval(r, p); c.drawOval(r, stroke)
        path.reset()
        path.moveTo(w * 0.44f, w * 1.05f); path.lineTo(w * 0.56f, w * 1.05f); path.lineTo(w * 0.5f, w * 0.96f); path.close()
        c.drawPath(path, p)
        p.color = Palette.BALLOON_SHINE
        r.set(w * 0.24f, w * 0.16f, w * 0.40f, w * 0.40f); c.drawOval(r, p)
        c.restore()
    }
}
