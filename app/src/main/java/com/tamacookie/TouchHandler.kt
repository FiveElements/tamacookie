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
 * Adaptateur d'entrée : passe par [PetCtx] pour l'animal, et reçoit explicitement les deux
 * contrôleurs qu'il pilote ([StationController] : survol / dépôt / panneau ; [MischiefController] :
 * trouvé, caresse pendant une bêtise, cache-cache depuis le menu). Reste lié à Android (gestes, vues).
 */
class TouchHandler(
    private val ctx: PetCtx,
    private val stations: StationController,
    private val mischief: MischiefController,
) {
    private val arena get() = ctx.arena
    private val pet: Pet get() = ctx.pet

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
        val slop = ViewConfiguration.get(ctx.context).scaledTouchSlop * 1.5f
        val gd = GestureDetector(ctx.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onPetTap(); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean { toggleMenu(); return true }
            override fun onLongPress(e: MotionEvent) {
                ctx.haptic(pet, HapticFeedbackConstants.LONG_PRESS)
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
                    ctx.requestFrames()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(ev.rawX - downX, ev.rawY - downY) > slop) {
                        dragging = true
                        val cancel = MotionEvent.obtain(ev); cancel.action = MotionEvent.ACTION_CANCEL
                        gd.onTouchEvent(cancel); cancel.recycle()
                        ctx.pages.rebaseToActivePage()
                        offX = downX - pet.body.fx; offY = downY - pet.body.fy
                        startDrag()
                    }
                    if (dragging) {
                        val d = arena.d
                        pet.body.fx = (ev.rawX - offX).coerceIn(0f, arena.screenW)
                        pet.body.fy = min(ev.rawY - offY, arena.groundY).coerceAtLeast(arena.topY + 90 * d)
                        if (abs(ev.rawX - lastRawX) > 2 * d) pet.body.facing = sign(ev.rawX - lastRawX)
                        lastRawX = ev.rawX
                        stations.updateHover()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vt?.addMovement(e2)
                    if (dragging) {
                        vt?.computeCurrentVelocity(1000)
                        val target = stations.takeHover()
                        if (target != null) stations.enterStation(target)
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
        val st = pet.stats
        if (pet.body.act == Act.PEEK) {
            ctx.endHiding()
            st.happiness = (st.happiness + 15f).coerceAtMost(100f)
            ctx.say(Lines.FOUND_WHILE_DRAGGED, 1.5f)
        } else if (pet.isHiding) ctx.endHiding()
        closeMenu()
        pet.body.platform = null
        pet.body.phys.airLandY = null
        stations.leaveStation(pet, hopOut = false)
        if (st.sleeping) { st.sleeping = false; st.happiness = (st.happiness - 3f).coerceAtLeast(0f) }
        ctx.world.dropHeldToy()
        ctx.noteInteraction()
        pet.setAct(Act.DRAGGED)
        ctx.speech.sayOneOf(Lines.DRAGGED, 1.8f)
        ctx.haptic(pet, HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun release(vxPx: Float, vyPx: Float) {
        val phys = pet.body.phys
        phys.release(vxPx, vyPx)
        if (hypot(phys.vx, phys.vy) > Tuning.THROW_SHOUT_DP * arena.d) ctx.speech.sayOneOf(Lines.THROWN, 1.6f)
        pet.body.afterAir = Act.IDLE
        pet.setAct(Act.AIR)
    }

    private fun onPetTap() {
        val now = ctx.clock.wallMs()
        val st = pet.stats
        ctx.haptic(pet, HapticFeedbackConstants.CONFIRM)
        ctx.noteInteraction()
        // Activité prise en charge par une fonctionnalité (dans une station…) : elle réagit à la caresse
        ctx.acts.handlerFor(pet)?.let { if (it.onPet(pet)) return }
        when (pet.body.act) {
            Act.PEEK -> { mischief.foundBunny(); return }
            Act.HIDE_RUN -> return
            Act.SQUAT -> { mischief.onSquatPetted(); return }
            else -> {}
        }
        if (pet.body.act == Act.SLEEP || st.sleeping) {
            st.sleeping = false
            st.happiness = (st.happiness - 3f).coerceAtLeast(0f)
            pet.express(Expr.ANGRY, 2.5f)
            ctx.speech.sayOneOf(Lines.WOKEN_BY_TAP)
            pet.setActOrAfterAir(Act.IDLE, 3f)
            return
        }
        tapTimes.addLast(now)
        while (tapTimes.isNotEmpty() && now - tapTimes.first() > Tuning.TAP_WINDOW_MS) tapTimes.removeFirst()
        if (tapTimes.size >= Tuning.TICKLE_TAPS) {
            tapTimes.clear()
            pet.express(Expr.HAPPY, 1.5f)
            ctx.speech.sayOneOf(Lines.TICKLED)
            val fx = pet.body.fx; val screenW = arena.screenW
            pet.body.targetX = if (fx > screenW / 2) fx - screenW * 0.35f else fx + screenW * 0.35f
            pet.setActOrAfterAir(Act.WALK)
            return
        }
        st.pet()
        ctx.emit(pet, PType.HEART, 2 + ctx.rnd.nextInt(3))
        pet.express(Expr.HAPPY, 1.6f)
        if (ctx.rnd.nextFloat() < 0.45f) ctx.speech.sayOneOf(Lines.PETTED, 2f)
        val act = pet.body.act
        if (act == Act.IDLE || act == Act.WALK || act == Act.SIT) {
            if (ctx.rnd.nextFloat() < 0.35f) ctx.actor.jump(pet, -560 * arena.d, 0f, then = Act.IDLE) else pet.setAct(Act.IDLE, 2.5f)
        }
    }

    // =====================================================================
    //  Menu de soins
    // =====================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun toggleMenu() {
        if (menu != null) { closeMenu(); return }
        if (ctx.clock.wallMs() - lastMenuClose < Tuning.MENU_REOPEN_GUARD_MS) return
        val windows = ctx.windows
        val d = arena.d
        lateinit var m: PetMenu
        m = PetMenu(ctx.context, pet.stats) { a ->
            // Sécurité : si ce panneau n'est plus le menu courant, on le retire quand même
            if (menu !== m) { windows.safeRemove(m); if (a != PetMenu.Action.CLOSE) onMenuAction(a) }
            else onMenuAction(a)
        }
        ctx.noteInteraction()
        val w = windows.panelWidth(Tuning.MENU_W_DP)
        val lp = windows.panelParams(w, watchOutside = true)
        val mh = windows.measurePanel(m, w)
        // position à l'écran du lapin (il peut être décalé par le défilement du bureau)
        val sx = pet.body.fx + ctx.pages.bunnyShift
        lp.x = windows.clampPanelX(sx - w / 2f, w)
        var y = (pet.body.fy - 125 * d - mh).roundToInt()
        if (y < arena.topY + 8 * d) y = (arena.topY + 8 * d).roundToInt()
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
        if (pet.body.act == Act.WALK) pet.setAct(Act.IDLE, 6f)
    }

    fun closeMenu() {
        menu?.let {
            ctx.windows.safeRemove(it)
            lastMenuClose = ctx.clock.wallMs()
        }
        menu = null
    }

    private fun onMenuAction(a: PetMenu.Action) {
        val st = pet.stats
        when (a) {
            PetMenu.Action.STATIONS -> { closeMenu(); stations.toggleStationsPanel() }
            PetMenu.Action.HIDE_SEEK -> { closeMenu(); mischief.startHideSeek(fromMenu = true) }
            PetMenu.Action.SLEEP -> {
                closeMenu()
                if (st.sleeping) {
                    st.sleeping = false; st.save()
                    pet.express(Expr.SLEEPY, 2f)
                    ctx.say(Lines.GETTING_UP)
                    pet.setActOrAfterAir(Act.IDLE, 3f)
                } else if (st.energy > 92f) {
                    ctx.speech.sayOneOf(Lines.NOT_TIRED)
                } else ctx.startSleep(pet)
            }
            PetMenu.Action.HIDE -> { closeMenu(); ctx.hide() }
            PetMenu.Action.CLOSE -> closeMenu()
        }
    }
}
