package com.tamacookie

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.Log
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
 * Fenêtres en surimpression : paramètres, ajout / retrait sûrs, bulle de dialogue,
 * bouton 🏠 (dock) et placement des panneaux.
 */
class OverlayWindows(private val ctx: PetCtx) {
    val wm: WindowManager = ctx.context.getSystemService(WindowManager::class.java)
    private val arena get() = ctx.arena
    private val d get() = arena.d
    private val pet: Pet get() = ctx.pet

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

    fun add(v: View, lp: WindowManager.LayoutParams) = wm.addView(v, lp)

    fun update(v: View, lp: WindowManager.LayoutParams) = wm.updateViewLayout(v, lp)

    /**
     * Fenêtre ancrée à une vue (app / page du bureau) : ajoutée, déplacée ou retirée selon [off]
     * ([PageTracker.visibleOffset] ; NaN = vue pas affichée). [cx] = centre horizontal sur sa vue.
     * Renvoie si elle est affichée (à ranger dans son `shown` / `attached`).
     */
    fun placeAnchored(v: View, lp: WindowManager.LayoutParams, cx: Float, off: Float, shown: Boolean): Boolean {
        if (off.isNaN()) {
            if (shown) safeRemove(v)
            return false
        }
        val x = (cx - lp.width / 2f + off).roundToInt()
        if (!shown) { lp.x = x; add(v, lp) }
        else if (lp.x != x) { lp.x = x; update(v, lp) }
        return true
    }

    /**
     * Retire une fenêtre sans dépendre de isAttachedToWindow (qui ne passe à true
     * qu'après la première frame : un retrait trop rapide laissait une fenêtre orpheline).
     */
    fun safeRemove(v: View) {
        try { wm.removeViewImmediate(v) } catch (e: Exception) {
            Log.w("TamaCookie", "removeView", e)
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
    //  Bulle de dialogue
    // ---------------------------------------------------------------------
    private var bubble: SpeechBubble? = null
    private lateinit var bubbleLp: WindowManager.LayoutParams
    var bubbleShown = false
        private set
    private var bubbleTime = 0f
    private var bubbleW = 0
    private var bubbleH = 0

    fun createBubble() {
        bubble = SpeechBubble(ctx.context)
        bubbleLp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, touchable = false)
        bubbleLp.alpha = 0.8f
    }

    fun say(text: String, seconds: Float) {
        val b = bubble ?: return
        if (ctx.hidden || ctx.bunnyAway) return
        b.text.text = text
        b.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        bubbleW = b.measuredWidth; bubbleH = b.measuredHeight
        bubbleTime = seconds
        positionBubble()
        if (!bubbleShown) { wm.addView(b, bubbleLp); bubbleShown = true }
        else wm.updateViewLayout(b, bubbleLp)
    }

    private fun positionBubble() {
        val headTop = if (pet.body.act == Act.SLEEP) 70 * d else 112 * d
        val lo = (4 * d).toInt()
        val hi = (arena.screenW - bubbleW - 4 * d).toInt().coerceAtLeast(lo)
        bubbleLp.x = (pet.body.fx + ctx.pages.bunnyShift - bubbleW / 2f).roundToInt().coerceIn(lo, hi)
        bubbleLp.y = (pet.body.fy - headTop - bubbleH).roundToInt().coerceAtLeast(arena.topY.toInt())
    }

    fun updateBubble(dt: Float) {
        val b = bubble ?: return
        if (!bubbleShown) return
        bubbleTime -= dt
        if (bubbleTime <= 0f) { hideBubble(); return }
        val ox = bubbleLp.x; val oy = bubbleLp.y
        positionBubble()
        if (ox != bubbleLp.x || oy != bubbleLp.y) wm.updateViewLayout(b, bubbleLp)
    }

    fun hideBubble() {
        val b = bubble ?: return
        if (bubbleShown) { safeRemove(b); bubbleShown = false }
    }

    // ---------------------------------------------------------------------
    //  Bouton 🏠 : ouvre le panneau des stations ; 🫣 pendant le cache-cache
    // ---------------------------------------------------------------------
    lateinit var dock: TextView
        private set
    lateinit var dockLp: WindowManager.LayoutParams
        private set
    private var dockAdded = false

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun createDock(onTap: () -> Unit) {
        val size = (Tuning.DOCK_SIZE_DP * d).toInt()
        dock = TextView(ctx.context).apply {
            text = DOCK_HOME
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFFDF9"))
                setStroke((2 * d).toInt(), Color.parseColor("#5B4A55"))
            }
        }
        dockLp = params(size, size, touchable = true)
        reloadDockPosition()
        val slop = ViewConfiguration.get(ctx.context).scaledTouchSlop.toFloat()
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        dock.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = dockLp.x; startY = dockLp.y; moved = false
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
                        val snapX = if (dockLp.x + size / 2f < arena.screenW / 2) 4 * d else arena.screenW - size - 4 * d
                        placeDock(snapX, dockLp.y.toFloat())
                        ctx.prefs.edit()
                            .putFloat(Prefs.DOCK_FX, dockLp.x / arena.screenW)
                            .putFloat(Prefs.DOCK_FY, dockLp.y / arena.groundY).apply()
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
        val prefs = ctx.prefs
        placeDock(prefs.getFloat(Prefs.DOCK_FX, 1f) * arena.screenW, prefs.getFloat(Prefs.DOCK_FY, 0.42f) * arena.groundY)
    }

    fun placeDock(x: Float, y: Float) {
        if (!::dockLp.isInitialized) return
        dockLp.x = x.roundToInt().coerceIn(0, (arena.screenW - dockLp.width).toInt().coerceAtLeast(0))
        dockLp.y = y.roundToInt().coerceIn(arena.topY.toInt(), (arena.groundY - dockLp.height).toInt().coerceAtLeast(arena.topY.toInt()))
        if (dockAdded) wm.updateViewLayout(dock, dockLp)
    }

    fun addDock() {
        if (dockAdded || !::dock.isInitialized) return
        wm.addView(dock, dockLp); dockAdded = true
    }

    fun removeDock() {
        if (dockAdded) { safeRemove(dock); dockAdded = false }
    }

    /** 🫣 pendant qu'il se cache, 🏠 sinon. */
    fun setDockHiding(hiding: Boolean) {
        if (::dock.isInitialized) dock.text = if (hiding) DOCK_HIDING else DOCK_HOME
    }

    private companion object {
        const val DOCK_HOME = "🏠"
        const val DOCK_HIDING = "🫣"
    }
}
