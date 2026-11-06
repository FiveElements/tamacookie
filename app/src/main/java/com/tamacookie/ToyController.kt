package com.tamacookie

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.VelocityTracker
import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.BunnyView.Pose
import com.tamacookie.feature.ActHandler
import com.tamacookie.feature.ActRegistry
import com.tamacookie.feature.Travel
import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.FoodSpec
import com.tamacookie.feature.toy.Poop
import com.tamacookie.feature.toy.PropSpec
import com.tamacookie.feature.toy.Toy
import com.tamacookie.feature.toy.ToyCtx
import com.tamacookie.feature.toy.ToySpec
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Objets posés à l'écran : nourriture, crottes et jouets — leurs **fenêtres**, la partie en cours et les
 * activités `GOTO_FOOD` / `EAT` / `PLAY` (enregistrées dans [ActRegistry] par [registerActs]).
 * Ce qui est propre à chaque objet (taille, lancer, dessin, jeu, physique, répliques) est dans son
 * spec (`feature/toy/` : [ToySpec], [FoodSpec], [Poop]).
 */
class ToyController(private val ctx: PetCtx) {
    /** Objets posés : ils appartiennent au monde (partagés entre animaux). */
    private val props get() = ctx.world.props

    private var toyHeldByUser = false
    /** Ce qu'il est en train de manger (Act.EAT). */
    var eatingFood = Food.CARROT
        private set

    private val arena get() = ctx.arena
    private val d get() = arena.d
    private val pet: Pet get() = ctx.pet

    /** Ce que reçoivent les jouets : la façade + l'état de la partie en cours (un seul jouet à la fois). */
    private val tctx = object : ToyCtx, PetCtx by ctx {
        override val userHolds: Boolean get() = toyHeldByUser
        override var holdTime = 0f
        override var catchCooldown = 0f
        override var groundSaidAt = 0L
    }

    private val playing = object : ActHandler {
        override val travel = Travel.BUSY
        override fun update(pet: Pet, dt: Float) = updatePlay(pet, dt)
    }
    private val goingToFood = object : ActHandler {
        override val travel = Travel.BUSY
        override fun update(pet: Pet, dt: Float) = updateGoToFood(pet, dt)
    }
    private val eating = object : ActHandler {
        override val travel = Travel.BUSY
        override fun update(pet: Pet, dt: Float) = updateEat(pet, dt)
    }

    /** Jouer, aller manger, manger : la boucle et le toucher délèguent à ce contrôleur. */
    fun registerActs(acts: ActRegistry) {
        acts.register(Act.PLAY) { playing }
        acts.register(Act.GOTO_FOOD) { goingToFood }
        acts.register(Act.EAT) { eating }
    }

    fun poopCount(): Int = props.count { it.isPoop }

    /** Un objet bouge (en vol, tenu par l'utilisateur) : animation fluide. */
    fun anyMoving(): Boolean = toyHeldByUser || props.any { !it.isPoop && !it.onGround }

    // =====================================================================
    //  Fenêtres des objets
    // =====================================================================
    fun addProp(spec: PropSpec): Prop {
        val v = PropView(ctx.context, spec)
        val lp = ctx.windows.params((spec.wDp * d).toInt(), (spec.hDp * d).toInt(), spec.touchable)
        val p = Prop(v, lp)
        props.add(p)
        if (!ctx.hidden) { placeProp(p, layout = false); ctx.windows.add(v, lp); p.attached = true }
        ctx.requestFrames()
        return p
    }

    fun removeProp(p: Prop) {
        if (p.attached) { ctx.windows.safeRemove(p.view); p.attached = false }
        props.remove(p)
        // La fenêtre du jouet a disparu : l'utilisateur ne peut plus le tenir
        if (p.toy != null) toyHeldByUser = false
    }

    fun placeProp(p: Prop, layout: Boolean = true) {
        val nx = (p.x - p.lp.width / 2f).roundToInt()
        val ny = (p.y - p.lp.height).roundToInt()
        if (nx != p.lp.x || ny != p.lp.y) {
            p.lp.x = nx; p.lp.y = ny
            if (layout && p.attached) ctx.windows.update(p.view, p.lp)
        }
    }

    /** Réaffiche les objets (après "Montrer") : tout sauf les crottes d'autres vues. */
    fun reattach() {
        val here = ctx.pages.currentView
        for (p in props) if (!p.attached && (!p.isPoop || p.anchor == here)) {
            placeProp(p, layout = false); ctx.windows.add(p.view, p.lp); p.attached = true
        }
    }

    fun detachAll() {
        for (p in props) if (p.attached) { ctx.windows.safeRemove(p.view); p.attached = false }
        toyHeldByUser = false
    }

    /** Nouvelle taille d'écran : objets ramenés dans l'écran et au sol. */
    fun onScreenResized() {
        for (p in props) {
            p.x = p.x.coerceIn(arena.minX, arena.maxX)
            if (p.onGround || p.isPoop) p.y = arena.groundY
        }
    }

    /** Les crottes restent sur la vue (page du bureau) où elles sont tombées. */
    fun layoutPoops() {
        val pages = ctx.pages
        for (i in props.indices) {
            val p = props[i]
            if (!p.isPoop) continue
            if (p.anchor.isEmpty()) p.anchor = pages.currentView
            val off = pages.visibleOffset(p.anchor)
            if (!off.isNaN()) p.lp.y = (p.y - p.lp.height).roundToInt()
            p.attached = ctx.windows.placeAnchored(p.view, p.lp, p.x, off, p.attached)
        }
    }

    // =====================================================================
    //  Nourriture
    // =====================================================================

    /**
     * Occupé (station, cache-cache) ou endormi : il le dit et refuse (nourriture, jouet).
     * [noteFirst] : l'interaction compte même s'il dort (nourriture).
     */
    private fun refuses(pet: Pet, asleepLine: String, noteFirst: Boolean): Boolean {
        if (ctx.sayIfBusy(pet)) return true
        if (noteFirst) ctx.noteInteraction()
        if (pet.stats.sleeping) { ctx.say(asleepLine); return true }
        return false
    }

    /** L'objet jaillit du coffre [from] en direction de l'animal. */
    private fun popFrom(p: Prop, from: Station, maxVxDp: Float, vyDp: Float) {
        p.x = from.cx; p.y = from.cy
        p.vx = ((pet.body.fx - from.cx) * 0.9f).coerceIn(-maxVxDp * d, maxVxDp * d)
        p.vy = vyDp * d
    }

    /** Donne à manger. [from] = le coffre d'où sort la nourriture (sinon elle tombe du ciel). */
    fun feed(food: Food = Food.CARROT, from: Station? = null) {
        val st = pet.stats
        if (refuses(pet, "Chut… il dort 💤", noteFirst = true)) return
        if (st.satiety > 95f && food != Food.STRAWBERRY) { ctx.say(ctx.speech.pickFrom(FULL)); return }
        if (ctx.world.hasFood()) return
        val spec = food.spec
        val c = addProp(spec)
        val rnd = ctx.rnd
        if (from != null) {
            popFrom(c, from, maxVxDp = 600f, vyDp = -900f)
        } else {
            c.x = (pet.body.fx + (if (rnd.nextBoolean()) 1 else -1) * (90 + rnd.nextFloat() * 120) * d).coerceIn(arena.minX, arena.maxX)
            c.y = arena.topY + 60 * d
            c.vy = 0f
        }
        placeProp(c)
        pet.express(Expr.SURPRISED, 1f)
        ctx.say(ctx.speech.pickFrom(spec.lines), 2f)
        pet.setActOrAfterAir(Act.GOTO_FOOD)
    }

    /** Act.GOTO_FOOD : il attend que la nourriture tombe puis court la manger. */
    private fun updateGoToFood(pet: Pet, dt: Float) {
        val c = props.firstOrNull { it.food != null }
        if (c == null) { pet.setAct(Act.IDLE); return }
        if (!c.onGround) {
            pet.body.pose = Pose.IDLE
            pet.body.facing = sign(c.x - pet.body.fx).takeIf { it != 0f } ?: pet.body.facing
            pet.express(Expr.SURPRISED, 0.3f)
        } else if (ctx.actor.walkToward(pet, c.x, 130 * d, dt, stopDist = 26 * d)) {
            eatingFood = c.food!!
            ctx.showFood(pet, eatingFood)
            removeProp(c)
            pet.setAct(Act.EAT, 3f)
        }
    }

    /** Act.EAT : il croque pendant 3 s (avancement lu par la vue), puis les jauges montent. */
    private fun updateEat(pet: Pet, dt: Float) {
        val b = pet.body
        b.pose = Pose.EAT
        b.eatProgress = b.actTime / 3f
        if (b.actTime >= 3f) {
            b.eatProgress = 0f
            pet.stats.feed(eatingFood)
            ctx.emit(pet, PType.HEART, 3)
            pet.express(Expr.HAPPY, 2f)
            ctx.speech.sayOneOf(Lines.ATE)
            pet.setAct(Act.IDLE, 3f)
        }
    }

    // =====================================================================
    //  Crottes
    // =====================================================================
    fun spawnPoop(x: Float) {
        val p = addProp(Poop)
        p.anchor = ctx.pages.bunnyView()
        p.x = x; p.y = arena.groundY
        placeProp(p)
        p.view.setOnClickListener {
            removeProp(p)
            pet.stats.removePoop()
            ctx.emit(pet, PType.SPARKLE, 4)
            pet.express(Expr.HAPPY, 1.5f)
            ctx.say(ctx.pick("Merci ! ✨", "Ouf, ça sentait…", "Tu es le meilleur !"), 2f)
        }
    }

    fun restorePoops() {
        repeat(pet.stats.poops) {
            spawnPoop(arena.minX + ctx.rnd.nextFloat() * (arena.maxX - arena.minX))
        }
    }

    // =====================================================================
    //  Jouets
    // =====================================================================
    fun startPlay(toy: Toy, from: Station? = null) {
        val st = pet.stats
        val rnd = ctx.rnd
        if (refuses(pet, "Chut… il dort", noteFirst = false)) return
        if (!st.startPlay()) { ctx.say(ctx.speech.pickFrom(TOO_TIRED)); return }
        props.filter { it.toy != null }.forEach { removeProp(it) }
        toyHeldByUser = false
        val spec = toy.spec
        val p = addProp(spec)
        if (spec.randomTint) p.view.tint = Options.accColor(rnd.nextInt(Options.accColors.size))
        val fx = pet.body.fx; val fy = pet.body.fy; val facing = pet.body.facing
        val launch = spec.launch
        p.x = (fx + facing * 70 * d).coerceIn(arena.minX, arena.maxX)
        p.y = fy - launch.aboveDp * d
        p.vx = facing * launch.vxDp * d; p.vy = launch.vyDp * d
        if (from != null) popFrom(p, from, maxVxDp = 700f, vyDp = launch.chestVyDp)
        placeProp(p)
        setupToyTouch(p)
        pet.mind.playTime = 0f; tctx.holdTime = 0f; tctx.catchCooldown = 0f
        ctx.noteInteraction()
        val fav = toy == st.favoriteToy
        ctx.say(
            if (fav) ctx.pick("Mon jouet préféré !! ⭐", "Ouiii, le ${toy.label.lowercase()} !! ⭐")
            else ctx.speech.pickFrom(spec.startLines)
        )
        if (fav) { ctx.emit(pet, PType.HEART, 4); pet.express(Expr.HAPPY, 2f) }
        pet.setActOrAfterAir(Act.PLAY)
    }

    /** Act.PLAY : la partie dure un moment (plus s'il est joueur), le jouet fait le reste. */
    private fun updatePlay(pet: Pet, dt: Float) {
        val p = ctx.world.currentToy()
        pet.mind.playTime += dt
        if (p == null) { pet.setAct(Act.IDLE); return }
        val limit = if (pet.stats.personality == Personality.PLAYFUL) 50f else 35f
        if (pet.mind.playTime > limit || pet.stats.energy < 8f) { endPlay(p); return }
        p.toy!!.spec.play(tctx, pet, p, dt)
    }

    private fun endPlay(p: Prop) {
        removeProp(p)
        ctx.say(ctx.pick("C'était trop bien ! 💕", "Pfiou, je suis épuisé !", "Encore demain ?"))
        pet.express(Expr.HAPPY, 2f)
        pet.setAct(Act.IDLE, 4f)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupToyTouch(t: Prop) {
        var vt: VelocityTracker? = null
        t.view.setOnTouchListener { _, ev ->
            val e2 = MotionEvent.obtain(ev); e2.setLocation(ev.rawX, ev.rawY)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    toyHeldByUser = true
                    ctx.noteInteraction()
                    ctx.requestFrames()
                    if (t.held) {
                        t.held = false
                        if (t.toy?.spec?.complainsWhenTaken == true) {
                            pet.express(Expr.ANGRY, 1.2f)
                            ctx.say(ctx.pick("Hé ! C'est à moi !", "Rends-le moi ! 😾", "Mon jouet !"), 1.6f)
                        }
                    }
                    vt?.recycle()
                    vt = VelocityTracker.obtain(); vt?.addMovement(e2)
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e2)
                    t.x = ev.rawX; t.y = ev.rawY + t.view.height / 2f
                    t.vx = 0f; t.vy = 0f; t.onGround = false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val max = Tuning.TOY_THROW_MAX_DP * d
                    vt?.addMovement(e2)
                    vt?.computeCurrentVelocity(1000)
                    t.vx = (vt?.xVelocity ?: 0f).coerceIn(-max, max)
                    t.vy = (vt?.yVelocity ?: 0f).coerceIn(-max, max)
                    vt?.recycle(); vt = null
                    toyHeldByUser = false
                    t.onGround = false
                    tctx.catchCooldown = 0.3f
                    if (pet.body.act == Act.PLAY && hypot(t.vx, t.vy) > 1500 * d) ctx.say(ctx.pick("Waouh, quel lancer !", "J'y vais !"), 1.6f)
                }
            }
            e2.recycle()
            true
        }
    }


    // =====================================================================
    //  Physique des objets (à chaque image)
    // =====================================================================
    fun update(dt: Float) {
        if (tctx.catchCooldown > 0f) tctx.catchCooldown -= dt
        val g = pet.body.phys.gravity
        val groundY = arena.groundY
        // Parcours à l'envers : un objet peut être retiré pendant la boucle, sans copier la liste
        var i = props.size - 1
        while (i >= 0) {
            val p = props[i--]
            p.age += dt
            val spin0 = p.view.spin
            when (val spec = p.spec) {
                is ToySpec -> if (!toyHeldByUser) spec.step(tctx, pet, p, dt)
                is FoodSpec -> {
                    if (!p.onGround) {
                        p.view.spin += 200f * dt
                        if (BunnyPhysics.stepFalling(p, dt, g, arena.minX, arena.maxX, groundY)) p.view.spin = spec.restSpin
                    }
                    if (p.age > Tuning.FOOD_LIFETIME_S) { removeProp(p); continue }
                }
                else -> p.y = groundY            // crotte
            }
            // Le dessin ne dépend que de la rotation : on ne redessine que si elle a changé
            if (p.view.spin != spin0) p.view.invalidate()
            if (p.attached && !p.isPoop) placeProp(p)
        }
    }

    private companion object {
        val FULL = arrayOf("J'ai plus faim !", "Mon ventre va exploser !")
        val TOO_TIRED = arrayOf("Trop fatigué… 😴", "Plus tard, j'ai plus d'énergie")
    }
}
