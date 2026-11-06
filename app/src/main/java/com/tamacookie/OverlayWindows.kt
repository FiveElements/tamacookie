package com.tamacookie

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fenêtres Android du service autres que les entités : paramètres, bulles de dialogue (une par animal),
 * bouton 🏠 (dock) et placement des panneaux. Tout ajout / déplacement / retrait passe par [overlay].
 * Ne lit pas l'état du jeu : la position de chaque bulle lui est fournie par [bubbleAnchor].
 */
class OverlayWindows(
    private val context: Context,
    private val arena: Arena,
    private val prefs: () -> SharedPreferences,
    val overlay: OverlayManager,
    private val bubbleAnchor: BubbleAnchor,
) {
    private val d get() = arena.d

    /** Où se place la bulle d'un animal : au-dessus de sa tête, en coordonnées écran. */
    interface BubbleAnchor {
        /** Centre horizontal de la tête. */
        fun headX(pet: PetId): Float
        /** Haut de la tête (la bulle se pose au-dessus). */
        fun headTop(pet: PetId): Float
    }

    fun params(w: Int, h: Int, touchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            // Android 12+ : une fenêtre non tactile doit avoir une opacité <= 0.8
            // pour laisser passer les touchers vers l'application en dessous.
            if (!touchable) alpha = 0.8f
        }
    }

    // ---------------------------------------------------------------------
    //  Panneaux (menu, stations, coffre, véto)
    // ---------------------------------------------------------------------

    /** Largeur d'un panneau : [dp] souhaités, réduite pour tenir dans un écran étroit. */
    fun panelWidth(dp: Float): Int =
        min(dp * d, arena.screenW - 2 * Tuning.PANEL_SCREEN_MARGIN_DP * d).toInt().coerceAtLeast(1)

    /** Paramètres d'un panneau tactile de largeur [w] ; [watchOutside] = fermé par un toucher à l'extérieur. */
    fun panelParams(w: Int, watchOutside: Boolean): WindowManager.LayoutParams {
        val lp = params(w, WindowManager.LayoutParams.WRAP_CONTENT, touchable = true)
        if (watchOutside) lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        return lp
    }

    fun measurePanel(v: View, w: Int): Int {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        return v.measuredHeight
    }

    /** x d'un panneau de largeur [w], borné dans l'écran (jamais de plage vide). */
    fun clampPanelX(x: Float, w: Int): Int {
        val lo = Tuning.PANEL_EDGE_PX
        val hi = (arena.screenW - w - Tuning.PANEL_EDGE_PX).toInt().coerceAtLeast(lo)
        return x.roundToInt().coerceIn(lo, hi)
    }

    /** y d'un panneau de hauteur [h], borné entre le haut de l'écran et le sol. */
    fun clampPanelY(y: Float, h: Int): Int {
        val lo = arena.topY.toInt()
        val hi = (arena.groundY - h).toInt().coerceAtLeast(lo)
        return y.roundToInt().coerceIn(lo, hi)
    }

    // ---------------------------------------------------------------------
    //  Bulles de dialogue (une par animal, créée à sa première réplique)
    // ---------------------------------------------------------------------
    private class Bubble(val id: WindowId, val view: SpeechBubble, val lp: WindowManager.LayoutParams) {
        var time = 0f
        var w = 0
        var h = 0
    }

    private val bubbles = HashMap<PetId, Bubble>()

    private fun bubbleOf(pet: PetId): Bubble = bubbles.getOrPut(pet) {
        val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, touchable = false)
        lp.alpha = 0.8f
        Bubble(WindowId.Bubble(pet), SpeechBubble(context), lp)
    }

    /** Une bulle est affichée. */
    val anyBubbleShown: Boolean get() = bubbles.values.any { overlay.isAttached(it.id) }

    /**
     * [pet] dit [text] pendant [seconds] s ; [announce] : le lecteur d'écran la lit (réponse à l'utilisateur,
     * [A11yTexts.announces]) — sinon elle reste affichée sans être annoncée.
     */
    fun say(pet: PetId, text: String, seconds: Float, announce: Boolean = false) {
        val b = bubbleOf(pet)
        b.view.show(text, announce)
        b.view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        b.w = b.view.measuredWidth; b.h = b.view.measuredHeight
        b.time = seconds
        positionBubble(pet, b)
        // Nouveau texte (nouvelle taille) : appliqué tout de suite
        if (!overlay.isAttached(b.id)) {
            overlay.attach(b.id, b.view, b.lp)
            // Fenêtre toute neuve : le changement de texte a eu lieu avant qu'elle soit affichée
            if (announce) b.view.post {
                // Seulement si elle affiche encore ce texte (une autre réplique a pu la remplacer entre-temps)
                if (overlay.isAttached(b.id) && b.view.text.text.toString() == text) b.view.show(text, announce = true)
            }
        } else {
            overlay.relayout(b.id)
        }
    }

    private fun positionBubble(pet: PetId, b: Bubble) {
        val lo = (4 * d).toInt()
        val hi = (arena.screenW - b.w - 4 * d).toInt().coerceAtLeast(lo)
        b.lp.x = (bubbleAnchor.headX(pet) - b.w / 2f).roundToInt().coerceIn(lo, hi)
        b.lp.y = (bubbleAnchor.headTop(pet) - b.h).roundToInt().coerceAtLeast(arena.topY.toInt())
    }

    /** Fait vieillir les bulles affichées et les garde au-dessus de leur animal (déplacement regroupé). */
    fun updateBubbles(dt: Float) {
        for ((pet, b) in bubbles) {
            if (!overlay.isAttached(b.id)) continue
            b.time -= dt
            if (b.time <= 0f) { overlay.detach(b.id); continue }
            val lo = (4 * d).toInt()
            val hi = (arena.screenW - b.w - 4 * d).toInt().coerceAtLeast(lo)
            val x = (bubbleAnchor.headX(pet) - b.w / 2f).roundToInt().coerceIn(lo, hi)
            val y = (bubbleAnchor.headTop(pet) - b.h).roundToInt().coerceAtLeast(arena.topY.toInt())
            overlay.move(b.id, x, y)
        }
    }

    fun hideBubble(pet: PetId) {
        bubbles[pet]?.let { overlay.detach(it.id) }
    }

    /** L'animal est parti : sa bulle est oubliée. */
    fun forgetBubble(pet: PetId) {
        bubbles.remove(pet)?.let { overlay.release(it.id) }
    }

    // ---------------------------------------------------------------------
    //  Bouton 🏠 : ouvre le panneau des stations ; 🫣 pendant le cache-cache
    // ---------------------------------------------------------------------
    lateinit var dock: TextView
        private set
    private lateinit var dockLp: WindowManager.LayoutParams
    /** Position du dock (à jour même avant l'application regroupée du déplacement). */
    var dockX = 0
        private set
    var dockY = 0
        private set
    val dockSize: Int get() = dockLp.width

    @SuppressLint("ClickableViewAccessibility")
    fun createDock(onTap: () -> Unit) {
        val size = (Tuning.DOCK_SIZE_DP * d).toInt()
        dock = TextView(context).apply {
            setText(R.string.dock_home)
            // Emoji = icône dans un rond de taille fixe : en dp (pas en sp), pour ne pas déborder à 200 %
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Palette.UI_PANEL)
                setStroke((2 * d).toInt(), Palette.OUTLINE)
            }
            contentDescription = context.getString(R.string.access_dock_home)
            // Les vrais touchers sont consommés par l'écouteur ci-dessous : ce clic ne vient que du lecteur d'écran
            setOnClickListener { onTap() }
        }
        dockLp = params(size, size, touchable = true)
        reloadDockPosition()
        val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        dock.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = dockX; startY = dockY; moved = false
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (moved || hypot(ev.rawX - downX, ev.rawY - downY) > slop) {
                        moved = true
                        placeDock(startX + ev.rawX - downX, startY + ev.rawY - downY)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (moved) {
                        // se colle au bord le plus proche
                        val snapX = if (dockX + size / 2f < arena.screenW / 2) 4 * d else arena.screenW - size - 4 * d
                        placeDock(snapX, dockY.toFloat())
                        prefs().edit()
                            .putFloat(Prefs.DOCK_FX, dockX / arena.screenW)
                            .putFloat(Prefs.DOCK_FY, dockY / arena.groundY).apply()
                    } else if (ev.actionMasked == MotionEvent.ACTION_UP) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTap()
                    }
                }
            }
            true
        }
        addDock()
    }

    /** Replace le dock d'après la position sauvegardée (fractions de l'écran). */
    fun reloadDockPosition() {
        if (!::dockLp.isInitialized) return
        val prefs = prefs()
        placeDock(prefs.getFloat(Prefs.DOCK_FX, 1f) * arena.screenW, prefs.getFloat(Prefs.DOCK_FY, 0.42f) * arena.groundY)
    }

    fun placeDock(x: Float, y: Float) {
        if (!::dockLp.isInitialized) return
        dockX = x.roundToInt().coerceIn(0, (arena.screenW - dockLp.width).toInt().coerceAtLeast(0))
        dockY = y.roundToInt().coerceIn(arena.topY.toInt(), (arena.groundY - dockLp.height).toInt().coerceAtLeast(arena.topY.toInt()))
        // Fenêtre affichée : déplacement regroupé (lp mis à jour au flush) ; sinon position pour le prochain ajout
        if (overlay.isAttached(WindowId.Dock)) overlay.move(WindowId.Dock, dockX, dockY) else { dockLp.x = dockX; dockLp.y = dockY }
    }

    fun addDock() {
        if (!::dock.isInitialized) return
        dockLp.x = dockX; dockLp.y = dockY
        overlay.attach(WindowId.Dock, dock, dockLp)
    }

    /** 🫣 pendant qu'il se cache, 🏠 sinon. */
    fun setDockHiding(hiding: Boolean) {
        if (!::dock.isInitialized) return
        dock.setText(if (hiding) R.string.dock_hiding else R.string.dock_home)
        val desc = if (hiding) R.string.access_dock_hiding else R.string.access_dock_home
        dock.contentDescription = context.getString(desc)
    }
}
