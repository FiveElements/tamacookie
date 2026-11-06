package com.tamacookie

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Interactions tactiles avec le lapin : toucher (caresses, chatouilles, réveil),
 * glisser / lancer (dépôt dans une station), et menu de soins (double toucher / appui long).
 *
 * Reçoit encore le service (étape 1) : il lit / modifie le corps et l'activité du lapin.
 * À terme (étape 2) il passera par une façade étroite (`PetCtx`).
 */
class TouchHandler(private val svc: BunnyService) {

    private val tapTimes = ArrayDeque<Long>()
    private var menu: PetMenu? = null
    private var lastMenuClose = 0L

    /** Écouteur posé sur la fenêtre du lapin (réutilisé par la cage pour lui transmettre les gestes). */
    var listener: View.OnTouchListener? = null
        private set

    val menuOpen: Boolean get() = menu != null

    fun refreshMenu() { menu?.refresh() }

    // =====================================================================
    //  Toucher, glisser, lancer
    // =====================================================================
    @SuppressLint("ClickableViewAccessibility")
    fun attach(bunny: BunnyView) {
        val slop = ViewConfiguration.get(svc).scaledTouchSlop * 1.5f
        val gd = GestureDetector(svc, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onPetTap(); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean { toggleMenu(); return true }
            override fun onLongPress(e: MotionEvent) {
                svc.bunny.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                toggleMenu()
            }
        })
        var downX = 0f; var downY = 0f
        var offX = 0f; var offY = 0f
        var dragging = false
        var lastRawX = 0f
        var vt: VelocityTracker? = null

        val l = View.OnTouchListener { _, ev ->
            val e2 = MotionEvent.obtain(ev); e2.setLocation(ev.rawX, ev.rawY)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; lastRawX = ev.rawX
                    dragging = false
                    vt?.recycle(); vt = VelocityTracker.obtain()
                    svc.requestFrames()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(ev.rawX - downX, ev.rawY - downY) > slop) {
                        dragging = true
                        val cancel = MotionEvent.obtain(ev); cancel.action = MotionEvent.ACTION_CANCEL
                        gd.onTouchEvent(cancel); cancel.recycle()
                        svc.pages.rebaseToActivePage()
                        offX = downX - svc.fx; offY = downY - svc.fy
                        startDrag()
                    }
                    if (dragging) {
                        val d = svc.d
                        svc.fx = (ev.rawX - offX).coerceIn(0f, svc.screenW)
                        svc.fy = min(ev.rawY - offY, svc.groundY).coerceAtLeast(svc.topY + 90 * d)
                        if (abs(ev.rawX - lastRawX) > 2 * d) svc.bunny.facing = sign(ev.rawX - lastRawX)
                        lastRawX = ev.rawX
                        svc.stations.updateHover()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vt?.addMovement(e2)
                    if (dragging) {
                        vt?.computeCurrentVelocity(1000)
                        val target = svc.stations.takeHover()
                        if (target != null) svc.stations.enterStation(target)
                        else release(vt?.xVelocity ?: 0f, vt?.yVelocity ?: 0f)
                        dragging = false
                        vt?.recycle(); vt = null
                        e2.recycle()
                        return@OnTouchListener true   // pas de "tap" après un lancer
                    }
                    vt?.recycle(); vt = null
                }
            }
            vt?.addMovement(e2)
            e2.recycle()
            if (!dragging) gd.onTouchEvent(ev)
            true
        }
        listener = l
        bunny.setOnTouchListener(l)
    }

    private fun startDrag() {
        val pet = svc.pet
        if (svc.act == Act.PEEK) {
            svc.mischief.endHiding()
            pet.happiness = (pet.happiness + 15f).coerceAtMost(100f)
            svc.say(Lines.FOUND_WHILE_DRAGGED, 1.5f)
        } else if (svc.mischief.isHiding()) svc.mischief.endHiding()
        closeMenu()
        svc.platform = null
        svc.phys.airLandY = null
        if (svc.stations.inStation != null) svc.stations.leaveStation(hopOut = false)
        if (pet.sleeping) { pet.sleeping = false; pet.happiness = (pet.happiness - 3f).coerceAtLeast(0f) }
        svc.toys.dropHeldToy()
        svc.mischief.noteInteraction()
        svc.setAct(Act.DRAGGED)
        svc.speech.sayOneOf(Lines.DRAGGED, 1.8f)
        svc.bunny.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun release(vxPx: Float, vyPx: Float) {
        val phys = svc.phys
        phys.release(vxPx, vyPx)
        if (hypot(phys.vx, phys.vy) > Tuning.THROW_SHOUT_DP * svc.d) svc.speech.sayOneOf(Lines.THROWN, 1.6f)
        svc.afterAir = Act.IDLE
        svc.setAct(Act.AIR)
    }

    private fun onPetTap() {
        val now = System.currentTimeMillis()
        val pet = svc.pet
        val bunny = svc.bunny
        bunny.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        svc.mischief.noteInteraction()
        when (svc.act) {
            Act.PEEK -> { svc.mischief.foundBunny(); return }
            Act.HIDE_RUN -> return
            Act.SQUAT -> { svc.mischief.onSquatPetted(); return }
            Act.CAGED -> {
                pet.pet()
                bunny.emit(PType.HEART, 1)
                svc.speech.sayOneOf(Lines.PETTED_IN_CAGE, 2f)
                return
            }
            Act.BATH, Act.VET -> { bunny.emit(PType.HEART, 1); return }
            else -> {}
        }
        if (svc.act == Act.SLEEP || pet.sleeping) {
            pet.sleeping = false
            pet.happiness = (pet.happiness - 3f).coerceAtLeast(0f)
            svc.express(Expr.ANGRY, 2.5f)
            svc.speech.sayOneOf(Lines.WOKEN_BY_TAP)
            svc.setActOrAfterAir(Act.IDLE, 3f)
            return
        }
        tapTimes.addLast(now)
        while (tapTimes.isNotEmpty() && now - tapTimes.first() > Tuning.TAP_WINDOW_MS) tapTimes.removeFirst()
        if (tapTimes.size >= Tuning.TICKLE_TAPS) {
            tapTimes.clear()
            svc.express(Expr.HAPPY, 1.5f)
            svc.speech.sayOneOf(Lines.TICKLED)
            val fx = svc.fx; val screenW = svc.screenW
            svc.targetX = if (fx > screenW / 2) fx - screenW * 0.35f else fx + screenW * 0.35f
            svc.setActOrAfterAir(Act.WALK)
            return
        }
        pet.pet()
        bunny.emit(PType.HEART, 2 + svc.rnd.nextInt(3))
        svc.express(Expr.HAPPY, 1.6f)
        if (svc.rnd.nextFloat() < 0.45f) svc.speech.sayOneOf(Lines.PETTED, 2f)
        val act = svc.act
        if (act == Act.IDLE || act == Act.WALK || act == Act.SIT) {
            if (svc.rnd.nextFloat() < 0.35f) svc.jump(-560 * svc.d, 0f, then = Act.IDLE) else svc.setAct(Act.IDLE, 2.5f)
        }
    }

    // =====================================================================
    //  Menu de soins
    // =====================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun toggleMenu() {
        if (menu != null) { closeMenu(); return }
        if (System.currentTimeMillis() - lastMenuClose < Tuning.MENU_REOPEN_GUARD_MS) return
        val windows = svc.windows
        val d = svc.d
        lateinit var m: PetMenu
        m = PetMenu(svc, svc.pet) { a ->
            // Sécurité : si ce panneau n'est plus le menu courant, on le retire quand même
            if (menu !== m) { windows.safeRemove(m); if (a != PetMenu.Action.CLOSE) onMenuAction(a) }
            else onMenuAction(a)
        }
        svc.mischief.noteInteraction()
        val w = windows.panelWidth(Tuning.MENU_W_DP)
        val lp = windows.panelParams(w, watchOutside = true)
        val mh = windows.measurePanel(m, w)
        // position à l'écran du lapin (il peut être décalé par le défilement du bureau)
        val sx = svc.fx + svc.pages.bunnyShift
        lp.x = windows.clampPanelX(sx - w / 2f, w)
        var y = (svc.fy - 125 * d - mh).roundToInt()
        if (y < svc.topY + 8 * d) y = (svc.topY + 8 * d).roundToInt()
        lp.y = y
        m.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                if (menu === m) closeMenu() else windows.safeRemove(m); true
            } else false
        }
        windows.add(m, lp)
        m.alpha = 0f; m.scaleX = 0.9f; m.scaleY = 0.9f
        m.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start()
        menu = m
        if (svc.act == Act.WALK) svc.setAct(Act.IDLE, 6f)
    }

    fun closeMenu() {
        menu?.let {
            svc.windows.safeRemove(it)
            lastMenuClose = System.currentTimeMillis()
        }
        menu = null
    }

    private fun onMenuAction(a: PetMenu.Action) {
        val pet = svc.pet
        when (a) {
            PetMenu.Action.STATIONS -> { closeMenu(); svc.stations.toggleStationsPanel() }
            PetMenu.Action.HIDE_SEEK -> { closeMenu(); svc.mischief.startHideSeek(fromMenu = true) }
            PetMenu.Action.SLEEP -> {
                closeMenu()
                if (pet.sleeping) {
                    pet.sleeping = false; pet.save()
                    svc.express(Expr.SLEEPY, 2f)
                    svc.say(Lines.GETTING_UP)
                    svc.setActOrAfterAir(Act.IDLE, 3f)
                } else if (pet.energy > 92f) {
                    svc.speech.sayOneOf(Lines.NOT_TIRED)
                } else svc.startSleep()
            }
            PetMenu.Action.HIDE -> { closeMenu(); svc.hide() }
            PetMenu.Action.CLOSE -> closeMenu()
        }
    }
}
