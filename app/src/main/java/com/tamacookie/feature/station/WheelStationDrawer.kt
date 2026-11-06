package com.tamacookie.feature.station

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.StationView
import kotlin.math.cos
import kotlin.math.sin

/** Dessin de [WheelStation] (Android : `Canvas`). */
object WheelStationDrawer : StationDrawer {
    /** Tours de roue par seconde quand il court (dessin). */
    private const val TURNS_PER_S = 0.8f

    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        val cx = w / 2; val cy = h * 0.46f
        val rad = minOf(w, h) * 0.42f
        // pied
        p.color = Palette.WHEEL_STAND
        r.set(cx - 5 * d, cy, cx + 5 * d, h - 6 * d); c.drawRect(r, p)
        r.set(cx - rad * 0.8f, h - 10 * d, cx + rad * 0.8f, h - 2 * d)
        c.drawRoundRect(r, 4 * d, 4 * d, p); c.drawRoundRect(r, 4 * d, 4 * d, stroke)
        // rayons : elle tourne quand il court
        val turn = if (v.occupied) v.t * TURNS_PER_S * 6.2832f else 0f
        stroke.color = Palette.WHEEL_SPOKE
        for (i in 0 until 6) {
            val a = turn + i * 1.0472f
            c.drawLine(cx, cy, cx + cos(a) * rad, cy + sin(a) * rad, stroke)
        }
        // jante
        stroke.color = Palette.WHEEL; stroke.strokeWidth = 5f * d
        c.drawCircle(cx, cy, rad, stroke)
        stroke.color = Palette.OUTLINE; stroke.strokeWidth = 2f * d
        p.color = Palette.WHEEL
        c.drawCircle(cx, cy, 5 * d, p); c.drawCircle(cx, cy, 5 * d, stroke)
    }
}
