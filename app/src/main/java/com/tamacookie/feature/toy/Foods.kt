package com.tamacookie.feature.toy

import android.graphics.Canvas
import android.graphics.Color
import com.tamacookie.Palette
import com.tamacookie.PropView

object CarrotFood : FoodSpec {
    override val food get() = Food.CARROT
    override val restSpin = 90f
    override val lines = arrayOf("Une carotte !! 🥕", "Miam en vue !", "C'est pour moi ?")

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

object LettuceFood : FoodSpec {
    override val food get() = Food.LETTUCE
    override val lines = arrayOf("De la salade ! 🥬 Croquante !", "Plein de vitamines !")

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

object StrawberryFood : FoodSpec {
    override val food get() = Food.STRAWBERRY
    override val lines = arrayOf("UNE FRAISE ?! 🍓😍", "Mon dessert préféré !")
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

/** Crotte : ramassée d'un tap ; reste sur la vue où elle est tombée (voir `ToyController.spawnPoop`). */
object Poop : PropSpec {
    override val wDp = 34
    override val hDp = 34
    override val touchable = true

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
