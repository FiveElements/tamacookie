package com.tamacookie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/** Petit objet flottant : carotte, crotte ou balle. */
@SuppressLint("ViewConstructor")
class PropView(context: Context, val kind: Kind) : View(context) {
    enum class Kind { CARROT, LETTUCE, STRAWBERRY, POOP, BALL, BALLOON, FRISBEE, PLUSH;
        val isFood get() = this == CARROT || this == LETTUCE || this == STRAWBERRY
        val food get() = when (this) { LETTUCE -> Food.LETTUCE; STRAWBERRY -> Food.STRAWBERRY; else -> Food.CARROT }
    }

    var spin = 0f
    var tint = Color.parseColor("#FF6F61")
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * resources.displayMetrics.density
        color = Color.parseColor("#5B4A55")
    }
    private val path = Path()
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        when (kind) {
            Kind.CARROT -> {
                c.save(); c.rotate(spin, w / 2, h / 2)
                path.reset()
                path.moveTo(w * 0.30f, h * 0.30f); path.lineTo(w * 0.70f, h * 0.30f)
                path.lineTo(w * 0.52f, h * 0.95f); path.lineTo(w * 0.48f, h * 0.95f); path.close()
                p.color = Color.parseColor("#F28C28"); c.drawPath(path, p); c.drawPath(path, stroke)
                p.color = Color.parseColor("#4CAF50")
                r.set(w * 0.38f, h * 0.02f, w * 0.50f, h * 0.32f); c.drawOval(r, p)
                r.set(w * 0.50f, h * 0.02f, w * 0.62f, h * 0.32f); c.drawOval(r, p)
                c.restore()
            }
            Kind.LETTUCE -> {
                c.save(); c.rotate(spin * 0.3f, w / 2, h / 2)
                p.color = Color.parseColor("#5FAE4E")
                r.set(w * 0.10f, h * 0.30f, w * 0.90f, h * 0.95f); c.drawOval(r, p); c.drawOval(r, stroke)
                p.color = Color.parseColor("#8FD67E")
                r.set(w * 0.22f, h * 0.18f, w * 0.62f, h * 0.80f); c.drawOval(r, p); c.drawOval(r, stroke)
                r.set(w * 0.42f, h * 0.22f, w * 0.82f, h * 0.82f); c.drawOval(r, p); c.drawOval(r, stroke)
                p.color = Color.parseColor("#C9EFC0")
                c.drawLine(w * 0.5f, h * 0.35f, w * 0.5f, h * 0.85f, stroke)
                c.restore()
            }
            Kind.STRAWBERRY -> {
                c.save(); c.rotate(spin * 0.3f, w / 2, h / 2)
                path.reset()
                path.moveTo(w * 0.5f, h * 0.95f)
                path.cubicTo(w * 0.05f, h * 0.70f, w * 0.10f, h * 0.25f, w * 0.5f, h * 0.30f)
                path.cubicTo(w * 0.90f, h * 0.25f, w * 0.95f, h * 0.70f, w * 0.5f, h * 0.95f)
                path.close()
                p.color = Color.parseColor("#E5484D"); c.drawPath(path, p); c.drawPath(path, stroke)
                p.color = Color.parseColor("#FFE08A")
                for ((sx, sy) in listOf(0.35f to 0.5f, 0.6f to 0.48f, 0.48f to 0.65f, 0.38f to 0.75f, 0.62f to 0.7f))
                    c.drawCircle(w * sx, h * sy, 1.3f * d, p)
                p.color = Color.parseColor("#4CAF50")
                r.set(w * 0.30f, h * 0.18f, w * 0.70f, h * 0.36f); c.drawOval(r, p)
                c.restore()
            }
            Kind.POOP -> {
                p.color = Color.parseColor("#8B5E3C")
                r.set(w * 0.12f, h * 0.62f, w * 0.88f, h * 0.95f); c.drawOval(r, p); c.drawOval(r, stroke)
                r.set(w * 0.22f, h * 0.38f, w * 0.78f, h * 0.70f); c.drawOval(r, p); c.drawOval(r, stroke)
                r.set(w * 0.34f, h * 0.18f, w * 0.66f, h * 0.45f); c.drawOval(r, p); c.drawOval(r, stroke)
                p.color = Color.WHITE
                c.drawCircle(w * 0.42f, h * 0.55f, 2.4f * d, p); c.drawCircle(w * 0.58f, h * 0.55f, 2.4f * d, p)
                p.color = Color.BLACK
                c.drawCircle(w * 0.42f, h * 0.56f, 1.2f * d, p); c.drawCircle(w * 0.58f, h * 0.56f, 1.2f * d, p)
            }
            Kind.BALL -> {
                c.save(); c.rotate(spin, w / 2, h / 2)
                val rad = w * 0.44f
                p.color = Color.parseColor("#FF6F61"); c.drawCircle(w / 2, h / 2, rad, p)
                p.color = Color.parseColor("#FFD166")
                r.set(w / 2 - rad, h / 2 - rad * 0.3f, w / 2 + rad, h / 2 + rad * 0.3f)
                c.drawRect(r, p)
                c.drawCircle(w / 2, h / 2, rad, stroke)
                c.restore()
            }
            Kind.BALLOON -> {
                // ballon en haut de la vue, ficelle en dessous
                c.save(); c.rotate(spin, w / 2, w * 0.5f)
                path.reset()
                path.moveTo(w / 2, w * 0.98f)
                path.cubicTo(w * 0.45f, h * 0.75f, w * 0.62f, h * 0.85f, w * 0.5f, h)
                stroke.strokeWidth = 1.4f * d; c.drawPath(path, stroke); stroke.strokeWidth = 2f * d
                p.color = tint
                r.set(w * 0.08f, w * 0.02f, w * 0.92f, w * 0.98f); c.drawOval(r, p); c.drawOval(r, stroke)
                path.reset()
                path.moveTo(w * 0.44f, w * 1.05f); path.lineTo(w * 0.56f, w * 1.05f); path.lineTo(w * 0.5f, w * 0.96f); path.close()
                c.drawPath(path, p)
                p.color = Color.argb(110, 255, 255, 255)
                r.set(w * 0.24f, w * 0.16f, w * 0.40f, w * 0.40f); c.drawOval(r, p)
                c.restore()
            }
            Kind.FRISBEE -> {
                c.save(); c.rotate(spin * 0.08f, w / 2, h / 2)
                p.color = tint
                r.set(w * 0.04f, h * 0.30f, w * 0.96f, h * 0.70f); c.drawOval(r, p); c.drawOval(r, stroke)
                p.color = Color.argb(90, 255, 255, 255)
                r.set(w * 0.22f, h * 0.38f, w * 0.78f, h * 0.58f); c.drawOval(r, p)
                // petit repère qui tourne
                val a = Math.toRadians(spin.toDouble())
                p.color = Color.WHITE
                c.drawCircle(w / 2 + (kotlin.math.cos(a) * w * 0.32f).toFloat(), h / 2 + (kotlin.math.sin(a) * h * 0.12f).toFloat(), 2.2f * d, p)
                c.restore()
            }
            Kind.PLUSH -> {
                // petit ours en peluche
                c.save(); c.rotate(spin, w / 2, h / 2)
                val brown = Color.parseColor("#B07A4F")
                val light = Color.parseColor("#E8C39E")
                p.color = brown
                c.drawCircle(w * 0.28f, h * 0.20f, w * 0.11f, p); c.drawCircle(w * 0.28f, h * 0.20f, w * 0.11f, stroke)
                c.drawCircle(w * 0.72f, h * 0.20f, w * 0.11f, p); c.drawCircle(w * 0.72f, h * 0.20f, w * 0.11f, stroke)
                r.set(w * 0.20f, h * 0.48f, w * 0.80f, h * 0.98f); c.drawOval(r, p); c.drawOval(r, stroke)
                c.drawCircle(w / 2, h * 0.36f, w * 0.26f, p); c.drawCircle(w / 2, h * 0.36f, w * 0.26f, stroke)
                p.color = light
                r.set(w * 0.38f, h * 0.38f, w * 0.62f, h * 0.52f); c.drawOval(r, p)
                r.set(w * 0.34f, h * 0.62f, w * 0.66f, h * 0.88f); c.drawOval(r, p)
                p.color = Color.parseColor("#3A2E39")
                c.drawCircle(w * 0.41f, h * 0.31f, 1.8f * d, p); c.drawCircle(w * 0.59f, h * 0.31f, 1.8f * d, p)
                c.drawCircle(w / 2, h * 0.42f, 1.8f * d, p)
                c.restore()
            }
        }
    }
}

/** Objet flottant + sa fenêtre et sa petite physique. */
class Prop(val view: PropView, val lp: WindowManager.LayoutParams) : Body {
    override var x = 0f
    override var y = 0f
    override var vx = 0f
    override var vy = 0f
    override var onGround = false
    var age = 0f
    var attached = false
    var held = false          // tenu par le lapin
    var anchor = ""           // vue (app / page du bureau) où l'objet est posé
    val toy: Toy? get() = when (view.kind) {
        PropView.Kind.BALL -> Toy.BALL
        PropView.Kind.BALLOON -> Toy.BALLOON
        PropView.Kind.FRISBEE -> Toy.FRISBEE
        PropView.Kind.PLUSH -> Toy.PLUSH
        else -> null
    }
}

/** Bulle de dialogue (fenêtre non tactile). */
@SuppressLint("ViewConstructor")
class SpeechBubble(context: Context) : FrameLayout(context) {
    val text = TextView(context)

    init {
        val d = resources.displayMetrics.density
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        text.setTextColor(Color.parseColor("#3A2E39"))
        text.gravity = Gravity.CENTER
        text.maxWidth = (230 * d).toInt()
        text.setPadding((12 * d).toInt(), (7 * d).toInt(), (12 * d).toInt(), (7 * d).toInt())
        text.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 16 * d
            setStroke((2 * d).toInt(), Color.parseColor("#5B4A55"))
        }
        addView(text, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }
}
