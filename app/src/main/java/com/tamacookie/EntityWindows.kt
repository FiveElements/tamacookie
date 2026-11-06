package com.tamacookie

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import kotlin.math.hypot

/**
 * Renderers des entités (adaptateur Android) : fabrique la fenêtre d'un objet ou d'une station la
 * première fois que l'[OverlayManager] l'affiche, et traduit les gestes sur ces fenêtres en appels aux
 * contrôleurs (Kotlin pur). [forwardToPet] : transmet un geste à la fenêtre de l'animal qui occupe la station (la cage lui
 * passe les touchers).
 */
class EntityWindows(
    private val context: Context,
    private val windows: OverlayWindows,
    private val world: World,
    private val toys: ToyController,
    private val stations: StationController,
    private val forwardToPet: (Station, MotionEvent) -> Boolean,
    /** Lecteur d'écran : caresse l'occupant de la station (faux = station vide). */
    private val caressOccupant: (Station) -> Boolean,
    /** Lecteur d'écran : [block] est une réponse à l'utilisateur (sa bulle est annoncée). */
    private val asUser: (block: () -> Unit) -> Unit,
) {
    fun create(id: WindowId): Win? = when (id) {
        is WindowId.Prop -> world.props.firstOrNull { it.id == id.id }?.let(::propWindow)
        is WindowId.Station -> stationWindow(stations[id.kind])
        else -> null
    }

    // ---------------------------------------------------------------------
    //  Objets : on lance un jouet, on ramasse une crotte
    // ---------------------------------------------------------------------
    private fun propWindow(p: Prop): Win {
        val v = PropView(context, p)
        val lp = windows.params(p.w, p.h, p.spec.touchable)
        if (p.toy != null) setupToyTouch(v, p)
        else if (p.isPoop) v.setOnClickListener { toys.pickUp(p) }
        describeProp(v, p)
        return Win(v, lp)
    }

    /**
     * Lecteur d'écran (étape 8d) : « Balle, jouet » (action « Lancer » : l'équivalent d'un lancer au doigt, vers
     * le milieu de l'écran) ; « Crotte de Cookie » (double toucher = ramasser) ; la nourriture est seulement
     * décrite.
     */
    private fun describeProp(v: PropView, p: Prop) {
        v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        v.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = A11yTexts.prop(p, p.owner?.let { world.pets[it]?.name })
                if (p.toy != null) {
                    val label = context.getString(R.string.access_action_throw)
                    info.addAction(AccessibilityAction(R.id.access_action_throw, label))
                } else if (p.isPoop) {
                    info.addAction(AccessibilityAction(ACTION_CLICK, context.getString(R.string.access_action_pick_up)))
                }
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action != R.id.access_action_throw || p.toy == null) {
                    return super.performAccessibilityAction(host, action, args)
                }
                val m = context.resources.displayMetrics
                val dir = if (p.x < m.widthPixels / 2f) 1f else -1f
                toys.grab(p)
                toys.release(p, dir * THROW_VX_DP * m.density, -THROW_VY_DP * m.density)
                return true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupToyTouch(v: PropView, t: Prop) {
        var vt: VelocityTracker? = null
        v.setOnTouchListener { _, ev ->
            val e2 = MotionEvent.obtain(ev); e2.setLocation(ev.rawX, ev.rawY)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    toys.grab(t)
                    vt?.recycle()
                    vt = VelocityTracker.obtain(); vt?.addMovement(e2)
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e2)
                    toys.drag(t, ev.rawX, ev.rawY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vt?.addMovement(e2)
                    vt?.computeCurrentVelocity(1000)
                    val vx = vt?.xVelocity ?: 0f
                    val vy = vt?.yVelocity ?: 0f
                    vt?.recycle(); vt = null
                    toys.release(t, vx, vy)
                }
            }
            e2.recycle()
            true
        }
    }

    // ---------------------------------------------------------------------
    //  Stations : appui long pour déplacer, tap pour le coffre / une explication
    // ---------------------------------------------------------------------
    private fun stationWindow(s: Station): Win {
        val v = StationView(context, s)
        val lp = windows.params(s.w, s.h, touchable = true)
        setupStationTouch(v, s)
        describeStation(v, s)
        return Win(v, lp)
    }

    /**
     * Lecteur d'écran (étape 8d) : « Baignoire, vide » / « Baignoire, Cookie dedans » ; double toucher = le tap
     * (explication, ouverture du coffre) ou, occupée, une caresse à l'occupant. Pour y mettre l'animal : action
     * « Mettre dans … » de l'animal ([TouchHandler]). Déplacer une station (appui long + glisser) : panneau 🏠.
     */
    private fun describeStation(v: StationView, s: Station) {
        v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        // Les vrais touchers sont consommés par l'écouteur : ce clic ne vient que du lecteur d'écran
        v.setOnClickListener { if (!caressOccupant(s)) asUser { stations.tapStation(s) } }
        v.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                val b = s.behavior
                val occupants = world.station(s.kind).occupants.mapNotNull { world.pets[it]?.name }
                info.contentDescription = A11yTexts.station(b.name, occupants, b.acceptsPet)
                val label = when {
                    occupants.isNotEmpty() -> R.string.access_action_pet
                    b.acceptsPet -> R.string.access_action_explain
                    else -> R.string.access_action_open
                }
                info.addAction(AccessibilityAction(ACTION_CLICK, context.getString(label)))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupStationTouch(v: StationView, s: Station) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        var downX = 0f; var downY = 0f; var startCx = 0f; var startCy = 0f
        var moving = false
        var forwarding = false
        val longPress = Runnable {
            moving = true
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(100).start()
        }
        v.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) forwarding = stations.touchGoesToPet(s)
            // Lapin à l'intérieur : les touchers vont au lapin (pour le sortir / le caresser)
            if (forwarding) return@setOnTouchListener forwardToPet(s, ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startCx = s.cx; startCy = s.cy
                    moving = false
                    v.postDelayed(longPress, 350)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (moving) stations.dragStation(s, startCx + ev.rawX - downX, startCy + ev.rawY - downY)
                    else if (hypot(ev.rawX - downX, ev.rawY - downY) > slop) v.removeCallbacks(longPress)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPress)
                    val tap = ev.actionMasked == MotionEvent.ACTION_UP && hypot(ev.rawX - downX, ev.rawY - downY) < slop
                    if (moving) {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        stations.dropStation(s)
                    } else if (tap) {
                        stations.tapStation(s)
                    }
                    moving = false
                }
            }
            true
        }
    }

    private companion object {
        /** Lancer d'un jouet au lecteur d'écran (dp/s) : un lancer moyen, sans « Quel lancer ! ». */
        const val THROW_VX_DP = 500f
        const val THROW_VY_DP = 900f
    }
}
