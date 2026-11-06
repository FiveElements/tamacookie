package com.tamacookie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.tamacookie.feature.toy.PropPen
import com.tamacookie.feature.toy.PropSpec

/** Fenêtre d'un objet posé : affiche l'état de son [Prop] (dessin porté par son spec). */
@SuppressLint("ViewConstructor")
class PropView(context: Context, val prop: Prop) : View(context) {
    val spec: PropSpec get() = prop.spec
    /** Rotation du dessin (lue dans le modèle). */
    val spin: Float get() = prop.spin
    val tint: Int get() = prop.tint

    /** Pinceaux partagés par les dessins des objets ([PropSpec.draw]). */
    internal val pen = PropPen(resources.displayMetrics.density)

    override fun onDraw(c: Canvas) {
        spec.draw(c, this, width.toFloat(), height.toFloat())
    }
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
