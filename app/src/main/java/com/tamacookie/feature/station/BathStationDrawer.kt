package com.tamacookie.feature.station

import android.graphics.Canvas
import android.graphics.Color
import com.tamacookie.Palette
import com.tamacookie.StationView

/** Dessin de [BathStation] (Android : `Canvas`). */
object BathStationDrawer : StationDrawer {
    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val path = pen.path; val d = pen.d; val t = v.t
        // pieds
        p.color = Palette.BATH_FEET
        c.drawCircle(w * 0.22f, h - 5 * d, 5 * d, p); c.drawCircle(w * 0.78f, h - 5 * d, 5 * d, p)
        // cuve
        val top = h * 0.38f
        path.reset()
        path.moveTo(w * 0.06f, top)
        path.lineTo(w * 0.94f, top)
        path.cubicTo(w * 0.94f, h * 0.95f, w * 0.80f, h - 8 * d, w * 0.5f, h - 8 * d)
        path.cubicTo(w * 0.20f, h - 8 * d, w * 0.06f, h * 0.95f, w * 0.06f, top)
        path.close()
        p.color = Color.WHITE
        c.drawPath(path, p); c.drawPath(path, stroke)
        // rebord + mousse
        r.set(w * 0.03f, top - 5 * d, w * 0.97f, top + 5 * d)
        p.color = Palette.BATH_RIM; c.drawRoundRect(r, 5 * d, 5 * d, p); c.drawRoundRect(r, 5 * d, 5 * d, stroke)
        p.color = Color.WHITE
        for (i in 0..6) {
            val bx = w * (0.12f + i * 0.13f)
            val by = top - 6 * d + kotlin.math.sin(t * 2f + i) * 1.5f * d
            c.drawCircle(bx, by, (5 + (i % 3)) * d, p)
        }
        p.color = Palette.BATH_BUBBLE
        c.drawCircle(w * 0.30f, top - 14 * d - (t * 12 % 20) * d, 3 * d, p)
        c.drawCircle(w * 0.70f, top - 10 * d - ((t * 9 + 7) % 20) * d, 2.4f * d, p)
        // robinet
        p.color = Palette.METAL
        r.set(w * 0.86f, top - 22 * d, w * 0.92f, top - 4 * d); c.drawRoundRect(r, 2 * d, 2 * d, p)
    }
}
