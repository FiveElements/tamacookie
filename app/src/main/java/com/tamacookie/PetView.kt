package com.tamacookie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.tamacookie.species.Species
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dessin d'un animal : la vue (générique) n'affiche qu'un [PetRenderState] immuable ([render]) — elle
 * n'écrit ni ne lit l'état du jeu. Le corps est dessiné par le renderer de l'espèce ([Species.renderer] :
 * `species/rabbit/RabbitRenderer`) ; l'animation ambiante (clignement, oreilles, particules) est calculée
 * par son [AmbientAnimator] (aléatoire injectable) et les particules, communes à toutes les espèces, sont
 * dessinées ici.
 */
@SuppressLint("ViewConstructor")
class PetView(
    context: Context,
    species: Species = Species.DEFAULT,
    private val ambient: AmbientAnimator = AmbientAnimator(Random(System.nanoTime())),
) : View(context) {

    /** Dernier état affiché. */
    var state = PetRenderState()
        private set
    private val t get() = ambient.t

    private val d = resources.displayMetrics.density
    private val renderer: PetRenderer = species.renderer(d)

    private val pPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val path = Path()

    /**
     * Affiche [s] et avance l'animation ambiante de [dt] secondes. Ne redessine que si l'image change
     * (état différent, clignement, oreille, particules, ou dessin qui dépend du temps).
     */
    fun render(s: PetRenderState, dt: Float) {
        val old = state
        state = s
        renderer.onState(old, s)
        if (ambient.frame(dt, old, s, width.toFloat(), height.toFloat(), d)) invalidate()
    }

    /** Ajoute des particules au-dessus de la tête (cœurs, notes…), [facing] = sens du regard. */
    fun emit(type: PType, count: Int = 1, facing: Float = state.facing) {
        ambient.emit(type, count, width.toFloat(), height.toFloat(), d, facing)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        renderer.draw(c, state, ambient, width, height)
        drawParticles(c)
    }

    // ------------------------------------------------------------------
    //  Particules
    // ------------------------------------------------------------------
    private fun drawParticles(c: Canvas) {
        for (p in ambient.particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            val alpha = (min(1f, a * 2.5f) * 255).toInt()
            when (p.type) {
                PType.HEART -> { pPaint.color = Palette.HEART; pPaint.alpha = alpha; heart(c, p.x, p.y, p.size) }
                PType.ZZZ -> text(c, "z", p.x, p.y, p.size * (1.4f - a * 0.5f), Palette.ZZZ, alpha)
                PType.NOTE -> text(c, if (p.rot > 3f) "♪" else "♫", p.x, p.y, p.size, Palette.NOTE, alpha)
                PType.SPARKLE -> { pPaint.color = Palette.SPARKLE; pPaint.alpha = alpha; sparkle(c, p.x, p.y, p.size * (0.6f + a)) }
                PType.DROP -> { pPaint.color = Palette.DROP; pPaint.alpha = alpha; c.drawCircle(p.x, p.y, p.size, pPaint) }
                PType.CRUMB -> { pPaint.color = Palette.CARROT; pPaint.alpha = alpha; c.drawCircle(p.x, p.y, p.size, pPaint) }
                PType.STAR -> {
                    val ang = p.rot + t * 6f
                    text(c, "★", p.x + cos(ang) * 24f * d, p.y + sin(ang) * 7f * d, 11f * d, Palette.STAR, alpha)
                }
                PType.BUBBLE -> {
                    pPaint.color = Palette.DIRT_BUBBLE; pPaint.alpha = alpha / 2
                    c.drawCircle(p.x, p.y, p.size, pPaint)
                }
            }
        }
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, alpha: Int) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.alpha = alpha
        c.drawText(s, x, y, textPaint)
    }

    private fun heart(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y + s * 0.35f)
        path.cubicTo(x - s * 1.1f, y - s * 0.35f, x - s * 0.45f, y - s * 1.05f, x, y - s * 0.45f)
        path.cubicTo(x + s * 0.45f, y - s * 1.05f, x + s * 1.1f, y - s * 0.35f, x, y + s * 0.35f)
        path.close()
        c.drawPath(path, pPaint)
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y - s); path.lineTo(x + s * 0.25f, y - s * 0.25f); path.lineTo(x + s, y)
        path.lineTo(x + s * 0.25f, y + s * 0.25f); path.lineTo(x, y + s); path.lineTo(x - s * 0.25f, y + s * 0.25f)
        path.lineTo(x - s, y); path.lineTo(x - s * 0.25f, y - s * 0.25f); path.close()
        c.drawPath(path, pPaint)
    }

    /** Vrai si le point (coordonnées de la vue) touche approximativement l'animal. */
    fun hitTest(x: Float, y: Float): Boolean = renderer.hitTest(x, y, width, height)
}

/**
 * Dessin du corps d'une espèce (Android) : un par vue, il garde ses pinceaux. [onState] est appelé à
 * chaque nouvel état (apparence changée…), [draw] à chaque image redessinée.
 */
interface PetRenderer {
    fun onState(old: PetRenderState, s: PetRenderState) {}
    fun draw(c: Canvas, s: PetRenderState, ambient: AmbientAnimator, width: Int, height: Int)
    fun hitTest(x: Float, y: Float, width: Int, height: Int): Boolean
}
