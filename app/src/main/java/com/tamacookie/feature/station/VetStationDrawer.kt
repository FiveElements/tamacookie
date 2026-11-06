package com.tamacookie.feature.station

import android.graphics.Canvas
import android.graphics.Color
import com.tamacookie.Palette
import com.tamacookie.StationView

/** Dessin de [VetStation] (Android : `Canvas`). */
object VetStationDrawer : StationDrawer {
    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        // panneau avec croix (en haut à gauche, laisse la place au lapin)
        r.set(4 * d, 4 * d, 44 * d, 40 * d)
        p.color = Color.WHITE; c.drawRoundRect(r, 6 * d, 6 * d, p); c.drawRoundRect(r, 6 * d, 6 * d, stroke)
        p.color = Palette.VET_GREEN
        r.set(19 * d, 10 * d, 29 * d, 34 * d); c.drawRect(r, p)
        r.set(12 * d, 17 * d, 36 * d, 27 * d); c.drawRect(r, p)
        // table d'examen
        val top = h - 26 * d
        r.set(6 * d, top, w - 6 * d, top + 10 * d)
        p.color = Palette.VET_TABLE; c.drawRoundRect(r, 4 * d, 4 * d, p); c.drawRoundRect(r, 4 * d, 4 * d, stroke)
        p.color = Palette.METAL
        r.set(w * 0.22f, top + 10 * d, w * 0.22f + 6 * d, h - 2 * d); c.drawRect(r, p)
        r.set(w * 0.78f - 6 * d, top + 10 * d, w * 0.78f, h - 2 * d); c.drawRect(r, p)
        // stéthoscope décoratif
        stroke.color = Palette.STETHOSCOPE
        r.set(w - 40 * d, 8 * d, w - 12 * d, 36 * d)
        c.drawArc(r, 0f, 180f, false, stroke)
        stroke.color = Palette.OUTLINE
    }
}
