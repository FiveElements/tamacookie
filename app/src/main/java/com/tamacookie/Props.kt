package com.tamacookie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.Poop
import com.tamacookie.feature.toy.PropPen
import com.tamacookie.feature.toy.PropSpec
import com.tamacookie.feature.toy.Toy

/** Petit objet flottant (nourriture, crotte, jouet) : son dessin est porté par son [spec]. */
@SuppressLint("ViewConstructor")
class PropView(context: Context, val spec: PropSpec) : View(context) {
    var spin = 0f
    var tint = Palette.BALL

    /** Pinceaux partagés par les dessins des objets ([PropSpec.draw]). */
    internal val pen = PropPen(resources.displayMetrics.density)

    override fun onDraw(c: Canvas) {
        spec.draw(c, this, width.toFloat(), height.toFloat())
    }
}

/** Objet flottant + sa fenêtre et sa petite physique. */
class Prop(val view: PropView, val lp: WindowManager.LayoutParams) : PropBody {
    override var x = 0f
    override var y = 0f
    override var vx = 0f
    override var vy = 0f
    override var onGround = false
    var age = 0f
    var attached = false
    var held = false          // tenu par le lapin
    var anchor = ""           // vue (app / page du bureau) où l'objet est posé
    val spec: PropSpec get() = view.spec
    val toy: Toy? get() = view.spec.toy
    val food: Food? get() = view.spec.food
    val isPoop: Boolean get() = view.spec === Poop
}

/** Bulle de dialogue (fenêtre non tactile). */
@SuppressLint("ViewConstructor")
class SpeechBubble(context: Context) : FrameLayout(context) {
    val text = TextView(context)

    init {
        val d = resources.displayMetrics.density
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        text.setTextColor(Palette.INK)
        text.gravity = Gravity.CENTER
        text.maxWidth = (230 * d).toInt()
        text.setPadding((12 * d).toInt(), (7 * d).toInt(), (12 * d).toInt(), (7 * d).toInt())
        text.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 16 * d
            setStroke((2 * d).toInt(), Palette.OUTLINE)
        }
        addView(text, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }
}
