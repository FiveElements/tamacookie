package com.tamacookie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.WindowManager

/**
 * "Widgets" flottants dans lesquels on dépose le lapin :
 * baignoire (le laver), vétérinaire (voir ses stats / soigner), cage (l'isoler).
 */
enum class StationKind(val label: String, val wDp: Int, val hDp: Int) {
    BATH("Bain", 130, 84),
    VET("Véto", 130, 130),
    CAGE("Cage", 130, 136),
    CHEST("Coffre", 110, 92),
}

@SuppressLint("ViewConstructor")
class StationView(context: Context, val kind: StationKind) : View(context) {
    var highlight = false
        set(v) { if (field != v) { field = v; animate().scaleX(if (v) 1.12f else 1f).scaleY(if (v) 1.12f else 1f).setDuration(120).start() } }
    var occupied = false
        set(v) { field = v; invalidate() }
    /** Coffre ouvert (couvercle levé). */
    var open = false
        set(v) { field = v; invalidate() }
    var t = 0f

    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.2f * d; color = Color.parseColor("#5B4A55")
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 12f * resources.displayMetrics.scaledDensity
        isFakeBoldText = true; color = Color.parseColor("#3A2E39")
    }
    private val r = RectF()
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        when (kind) {
            StationKind.BATH -> drawBath(c, w, h)
            StationKind.VET -> drawVet(c, w, h)
            StationKind.CAGE -> drawCage(c, w, h)
            StationKind.CHEST -> drawChest(c, w, h)
        }
    }

    private fun drawChest(c: Canvas, w: Float, h: Float) {
        val wood = Color.parseColor("#B07A4F")
        val dark = Color.parseColor("#7A5236")
        val gold = Color.parseColor("#E9B949")
        val boxTop = h * 0.45f
        // couvercle (levé si ouvert)
        c.save()
        if (open) c.rotate(-28f, w * 0.12f, boxTop)
        r.set(w * 0.08f, h * 0.14f, w * 0.92f, boxTop + 4 * d)
        p.color = wood; c.drawRoundRect(r, 14 * d, 14 * d, p); c.drawRoundRect(r, 14 * d, 14 * d, stroke)
        p.color = dark
        c.drawRect(w * 0.08f, boxTop - 6 * d, w * 0.92f, boxTop + 4 * d, p)
        c.restore()
        // intérieur lumineux quand ouvert
        if (open) {
            p.color = Color.parseColor("#FFE9A8")
            r.set(w * 0.12f, boxTop - 8 * d, w * 0.88f, boxTop + 6 * d); c.drawOval(r, p)
            p.color = Color.parseColor("#FFD54A")
            for (i in 0..2) c.drawCircle(w * (0.3f + i * 0.2f), boxTop - 14 * d - (t * 20 + i * 7) % 14 * d, 2.4f * d, p)
        }
        // caisse
        r.set(w * 0.08f, boxTop, w * 0.92f, h - 4 * d)
        p.color = wood; c.drawRoundRect(r, 6 * d, 6 * d, p); c.drawRoundRect(r, 6 * d, 6 * d, stroke)
        p.color = dark
        c.drawRect(w * 0.08f, boxTop + (h - boxTop) * 0.45f, w * 0.92f, boxTop + (h - boxTop) * 0.55f, p)
        // ferrures
        p.color = gold
        c.drawRect(w * 0.16f, boxTop, w * 0.22f, h - 4 * d, p)
        c.drawRect(w * 0.78f, boxTop, w * 0.84f, h - 4 * d, p)
        r.set(w * 0.43f, boxTop - 4 * d, w * 0.57f, boxTop + 14 * d)
        c.drawRoundRect(r, 3 * d, 3 * d, p); c.drawRoundRect(r, 3 * d, 3 * d, stroke)
        if (!open) tag(c, w / 2, h * 0.12f, "🧰 Coffre")
    }

    private fun tag(c: Canvas, x: Float, y: Float, text: String) {
        val tw = label.measureText(text) / 2 + 7 * d
        r.set(x - tw, y - 11 * d, x + tw, y + 5 * d)
        p.color = Color.argb(230, 255, 253, 249)
        c.drawRoundRect(r, 8 * d, 8 * d, p); c.drawRoundRect(r, 8 * d, 8 * d, stroke)
        c.drawText(text, x, y, label)
    }

    private fun drawBath(c: Canvas, w: Float, h: Float) {
        // pieds
        p.color = Color.parseColor("#C9A227")
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
        p.color = Color.parseColor("#E8F4FB"); c.drawRoundRect(r, 5 * d, 5 * d, p); c.drawRoundRect(r, 5 * d, 5 * d, stroke)
        p.color = Color.WHITE
        for (i in 0..6) {
            val bx = w * (0.12f + i * 0.13f)
            val by = top - 6 * d + kotlin.math.sin(t * 2f + i) * 1.5f * d
            c.drawCircle(bx, by, (5 + (i % 3)) * d, p)
        }
        p.color = Color.parseColor("#8FD3F4")
        c.drawCircle(w * 0.30f, top - 14 * d - (t * 12 % 20) * d, 3 * d, p)
        c.drawCircle(w * 0.70f, top - 10 * d - ((t * 9 + 7) % 20) * d, 2.4f * d, p)
        // robinet
        p.color = Color.parseColor("#B8B4BE")
        r.set(w * 0.86f, top - 22 * d, w * 0.92f, top - 4 * d); c.drawRoundRect(r, 2 * d, 2 * d, p)
        if (!occupied) tag(c, w / 2, h * 0.72f, "🛁 Bain")
    }

    private fun drawVet(c: Canvas, w: Float, h: Float) {
        // panneau avec croix (en haut à gauche, laisse la place au lapin)
        r.set(4 * d, 4 * d, 44 * d, 40 * d)
        p.color = Color.WHITE; c.drawRoundRect(r, 6 * d, 6 * d, p); c.drawRoundRect(r, 6 * d, 6 * d, stroke)
        p.color = Color.parseColor("#3DBE7A")
        r.set(19 * d, 10 * d, 29 * d, 34 * d); c.drawRect(r, p)
        r.set(12 * d, 17 * d, 36 * d, 27 * d); c.drawRect(r, p)
        // table d'examen
        val top = h - 26 * d
        r.set(6 * d, top, w - 6 * d, top + 10 * d)
        p.color = Color.parseColor("#9FD8F2"); c.drawRoundRect(r, 4 * d, 4 * d, p); c.drawRoundRect(r, 4 * d, 4 * d, stroke)
        p.color = Color.parseColor("#B8B4BE")
        r.set(w * 0.22f, top + 10 * d, w * 0.22f + 6 * d, h - 2 * d); c.drawRect(r, p)
        r.set(w * 0.78f - 6 * d, top + 10 * d, w * 0.78f, h - 2 * d); c.drawRect(r, p)
        // stéthoscope décoratif
        stroke.color = Color.parseColor("#6E7B8B")
        c.drawArc(RectF(w - 40 * d, 8 * d, w - 12 * d, 36 * d), 0f, 180f, false, stroke)
        stroke.color = Color.parseColor("#5B4A55")
        if (!occupied) tag(c, w / 2, h * 0.55f, "🩺 Véto")
    }

    private fun drawCage(c: Canvas, w: Float, h: Float) {
        val left = 8 * d; val right = w - 8 * d
        val top = 22 * d; val bottom = h - 6 * d
        // socle
        r.set(left - 4 * d, bottom - 10 * d, right + 4 * d, bottom)
        p.color = Color.parseColor("#8C5F3F"); c.drawRoundRect(r, 4 * d, 4 * d, p); c.drawRoundRect(r, 4 * d, 4 * d, stroke)
        // dôme
        r.set(left, top - 18 * d, right, top + 18 * d)
        stroke.color = Color.parseColor("#7A7480"); stroke.strokeWidth = 3f * d
        c.drawArc(r, 180f, 180f, false, stroke)
        // barreaux
        val n = 7
        for (i in 0..n) {
            val x = left + (right - left) * i / n
            c.drawLine(x, top, x, bottom - 10 * d, stroke)
        }
        c.drawLine(left, top + (bottom - top) * 0.45f, right, top + (bottom - top) * 0.45f, stroke)
        // anneau
        c.drawCircle(w / 2, top - 22 * d, 5 * d, stroke)
        stroke.color = Color.parseColor("#5B4A55"); stroke.strokeWidth = 2.2f * d
        // cadenas
        p.color = Color.parseColor("#E9B949")
        r.set(right - 22 * d, bottom - 40 * d, right - 8 * d, bottom - 26 * d)
        c.drawRoundRect(r, 3 * d, 3 * d, p); c.drawRoundRect(r, 3 * d, 3 * d, stroke)
        if (!occupied) tag(c, w / 2, h * 0.62f, "🔒 Cage")
    }
}

/** Une station flottante et sa fenêtre. */
class Station(val kind: StationKind, val view: StationView, val lp: WindowManager.LayoutParams) {
    var shown = false
    /** Vue où la station est installée (null = rangée). */
    var anchor: String? = null
    /** Centre de la station en coordonnées écran. */
    var cx = 0f
    var cy = 0f
    fun contains(x: Float, y: Float, slack: Float): Boolean =
        x > cx - lp.width / 2f - slack && x < cx + lp.width / 2f + slack &&
            y > cy - lp.height / 2f - slack && y < cy + lp.height / 2f + slack
    val bottom: Float get() = cy + lp.height / 2f
}
