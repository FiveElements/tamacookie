package com.tamacookie.feature.toy

import android.graphics.Canvas
import android.graphics.Color
import com.tamacookie.Palette
import com.tamacookie.PropView

/** Dessin de [CarrotFood] (Android : `Canvas`). */
object CarrotFoodDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val path = pen.path; val r = pen.r
        c.save(); c.rotate(v.spin, w / 2, h / 2)
        path.reset()
        path.moveTo(w * 0.30f, h * 0.30f); path.lineTo(w * 0.70f, h * 0.30f)
        path.lineTo(w * 0.52f, h * 0.95f); path.lineTo(w * 0.48f, h * 0.95f); path.close()
        p.color = Palette.CARROT; c.drawPath(path, p); c.drawPath(path, stroke)
        p.color = Palette.LEAF
        r.set(w * 0.38f, h * 0.02f, w * 0.50f, h * 0.32f); c.drawOval(r, p)
        r.set(w * 0.50f, h * 0.02f, w * 0.62f, h * 0.32f); c.drawOval(r, p)
        c.restore()
    }
}

/** Dessin de [LettuceFood] (Android : `Canvas`). */
object LettuceFoodDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r
        c.save(); c.rotate(v.spin * 0.3f, w / 2, h / 2)
        p.color = Palette.LETTUCE_DARK
        r.set(w * 0.10f, h * 0.30f, w * 0.90f, h * 0.95f); c.drawOval(r, p); c.drawOval(r, stroke)
        p.color = Palette.LETTUCE
        r.set(w * 0.22f, h * 0.18f, w * 0.62f, h * 0.80f); c.drawOval(r, p); c.drawOval(r, stroke)
        r.set(w * 0.42f, h * 0.22f, w * 0.82f, h * 0.82f); c.drawOval(r, p); c.drawOval(r, stroke)
        p.color = Palette.LETTUCE_VEIN
        c.drawLine(w * 0.5f, h * 0.35f, w * 0.5f, h * 0.85f, stroke)
        c.restore()
    }
}

/** Dessin de [StrawberryFood] (Android : `Canvas`). */
object StrawberryFoodDrawer : PropDrawer {
    private val SEEDS = arrayOf(0.35f to 0.5f, 0.6f to 0.48f, 0.48f to 0.65f, 0.38f to 0.75f, 0.62f to 0.7f)

    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val path = pen.path; val r = pen.r; val d = pen.d
        c.save(); c.rotate(v.spin * 0.3f, w / 2, h / 2)
        path.reset()
        path.moveTo(w * 0.5f, h * 0.95f)
        path.cubicTo(w * 0.05f, h * 0.70f, w * 0.10f, h * 0.25f, w * 0.5f, h * 0.30f)
        path.cubicTo(w * 0.90f, h * 0.25f, w * 0.95f, h * 0.70f, w * 0.5f, h * 0.95f)
        path.close()
        p.color = Palette.STRAWBERRY; c.drawPath(path, p); c.drawPath(path, stroke)
        p.color = Palette.SEED
        for ((sx, sy) in SEEDS) c.drawCircle(w * sx, h * sy, 1.3f * d, p)
        p.color = Palette.LEAF
        r.set(w * 0.30f, h * 0.18f, w * 0.70f, h * 0.36f); c.drawOval(r, p)
        c.restore()
    }
}

/** Dessin de [AppleFood] (Android : `Canvas`). */
object AppleFoodDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        c.save(); c.rotate(v.spin * 0.3f, w / 2, h / 2)
        p.color = Palette.APPLE
        r.set(w * 0.12f, h * 0.26f, w * 0.56f, h * 0.94f); c.drawOval(r, p)
        r.set(w * 0.44f, h * 0.26f, w * 0.88f, h * 0.94f); c.drawOval(r, p)
        r.set(w * 0.12f, h * 0.26f, w * 0.88f, h * 0.94f); c.drawOval(r, stroke)
        p.color = Color.WHITE
        c.drawCircle(w * 0.34f, h * 0.44f, 2.4f * d, p)
        p.color = Palette.APPLE_STEM
        r.set(w * 0.47f, h * 0.08f, w * 0.53f, h * 0.32f); c.drawRect(r, p)
        p.color = Palette.LEAF
        r.set(w * 0.53f, h * 0.08f, w * 0.78f, h * 0.22f); c.drawOval(r, p)
        c.restore()
    }
}

/** Dessin de [HayFood] (Android : `Canvas`). */
object HayFoodDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r
        c.save(); c.rotate(v.spin * 0.2f, w / 2, h / 2)
        p.color = Palette.HAY
        r.set(w * 0.08f, h * 0.40f, w * 0.92f, h * 0.95f); c.drawOval(r, p); c.drawOval(r, stroke)
        stroke.color = Palette.HAY_DARK
        for (i in 0..6) {
            val x = w * (0.18f + i * 0.11f)
            c.drawLine(x, h * 0.52f, x + (i % 3 - 1) * w * 0.08f, h * 0.20f + (i % 2) * h * 0.08f, stroke)
        }
        stroke.color = Palette.OUTLINE
        c.restore()
    }
}

/** Dessin de [TreatFood] (Android : `Canvas`). */
object TreatFoodDrawer : PropDrawer {
    private val CHIPS = arrayOf(0.35f to 0.40f, 0.62f to 0.36f, 0.48f to 0.58f, 0.32f to 0.66f, 0.66f to 0.64f)

    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val d = pen.d
        c.save(); c.rotate(v.spin, w / 2, h / 2)
        p.color = Palette.TREAT
        c.drawCircle(w / 2, h / 2, w * 0.42f, p); c.drawCircle(w / 2, h / 2, w * 0.42f, stroke)
        p.color = Palette.TREAT_CHIP
        for ((sx, sy) in CHIPS) c.drawCircle(w * sx, h * sy, 1.8f * d, p)
        c.restore()
    }
}

/** Dessin de [Poop] (Android : `Canvas`). */
object PoopDrawer : PropDrawer {
    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        p.color = Palette.POOP
        r.set(w * 0.12f, h * 0.62f, w * 0.88f, h * 0.95f); c.drawOval(r, p); c.drawOval(r, stroke)
        r.set(w * 0.22f, h * 0.38f, w * 0.78f, h * 0.70f); c.drawOval(r, p); c.drawOval(r, stroke)
        r.set(w * 0.34f, h * 0.18f, w * 0.66f, h * 0.45f); c.drawOval(r, p); c.drawOval(r, stroke)
        p.color = Color.WHITE
        c.drawCircle(w * 0.42f, h * 0.55f, 2.4f * d, p); c.drawCircle(w * 0.58f, h * 0.55f, 2.4f * d, p)
        p.color = Color.BLACK
        c.drawCircle(w * 0.42f, h * 0.56f, 1.2f * d, p); c.drawCircle(w * 0.58f, h * 0.56f, 1.2f * d, p)
    }
}
