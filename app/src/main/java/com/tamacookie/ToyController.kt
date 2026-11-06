package com.tamacookie

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.VelocityTracker
import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.BunnyView.Pose
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * Objets posés à l'écran : nourriture (carotte, salade, fraise), crottes, et jouets
 * (balle, ballon, frisbee, doudou) avec leurs jeux.
 */
class ToyController(private val ctx: PetCtx) {
    /** Objets posés : ils appartiennent au monde (partagés entre animaux). */
    private val props get() = ctx.world.props

    private var toyHeldByUser = false
    private var toyHoldTime = 0f
    private var hugTime = 0f
    private var catchCooldown = 0f
    private var balloonGroundSaid = 0L
    /** Ce qu'il est en train de manger (Act.EAT). */
    var eatingFood = Food.CARROT
        private set

    private val arena get() = ctx.arena
    private val d get() = arena.d
    private val pet: Pet get() = ctx.pet

    fun poopCount(): Int = props.count { it.view.kind == PropView.Kind.POOP }

    /** Un objet bouge (en vol, tenu par l'utilisateur) : animation fluide. */
    fun anyMoving(): Boolean = toyHeldByUser || props.any { it.view.kind != PropView.Kind.POOP && !it.onGround }

    // =====================================================================
    //  Fenêtres des objets
    // =====================================================================
    fun addProp(kind: PropView.Kind, wDp: Int, hDp: Int = wDp, touchable: Boolean): Prop {
        val v = PropView(ctx.context, kind)
        val lp = ctx.windows.params((wDp * d).toInt(), (hDp * d).toInt(), touchable)
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
        for (p in props) if (!p.attached && (p.view.kind != PropView.Kind.POOP || p.anchor == here)) {
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
            if (p.onGround || p.view.kind == PropView.Kind.POOP) p.y = arena.groundY
        }
    }

    /** Les crottes restent sur la vue (page du bureau) où elles sont tombées. */
    fun layoutPoops() {
        val pages = ctx.pages
        for (i in props.indices) {
            val p = props[i]
            if (p.view.kind != PropView.Kind.POOP) continue
            if (p.anchor.isEmpty()) p.anchor = pages.currentView
            val off = pages.visibleOffset(p.anchor)
            if (!off.isNaN()) {
                val x = (p.x - p.lp.width / 2f + off).roundToInt()
                p.lp.y = (p.y - p.lp.height).roundToInt()
                if (!p.attached) { p.lp.x = x; ctx.windows.add(p.view, p.lp); p.attached = true }
                else if (p.lp.x != x) { p.lp.x = x; ctx.windows.update(p.view, p.lp) }
            } else if (p.attached) { ctx.windows.safeRemove(p.view); p.attached = false }
        }
    }

    // =====================================================================
    //  Nourriture
    // =====================================================================

    /** Donne à manger. [from] = le coffre d'où sort la nourriture (sinon elle tombe du ciel). */
    fun feed(food: Food = Food.CARROT, from: Station? = null) {
        val st = pet.stats
        if (ctx.busyInStation()) return
        ctx.noteInteraction()
        if (st.sleeping) { ctx.say("Chut… il dort 💤"); return }
        if (st.satiety > 95f && food != Food.STRAWBERRY) { ctx.say(ctx.pick("J'ai plus faim !", "Mon ventre va exploser !")); return }
        if (ctx.world.hasFood()) return
        val kind = when (food) {
            Food.CARROT -> PropView.Kind.CARROT
            Food.LETTUCE -> PropView.Kind.LETTUCE
            Food.STRAWBERRY -> PropView.Kind.STRAWBERRY
        }
        val c = addProp(kind, 38, touchable = false)
        val rnd = ctx.rnd
        if (from != null) {
            // jaillit du coffre en direction du lapin
            c.x = from.cx; c.y = from.cy
            c.vy = -900 * d
            c.vx = ((pet.body.fx - from.cx) * 0.9f).coerceIn(-600 * d, 600 * d)
        } else {
            c.x = (pet.body.fx + (if (rnd.nextBoolean()) 1 else -1) * (90 + rnd.nextFloat() * 120) * d).coerceIn(arena.minX, arena.maxX)
            c.y = arena.topY + 60 * d
            c.vy = 0f
        }
        placeProp(c)
        pet.express(Expr.SURPRISED, 1f)
        ctx.say(when (food) {
            Food.CARROT -> ctx.pick("Une carotte !! 🥕", "Miam en vue !", "C'est pour moi ?")
            Food.LETTUCE -> ctx.pick("De la salade ! 🥬 Croquante !", "Plein de vitamines !")
            Food.STRAWBERRY -> ctx.pick("UNE FRAISE ?! 🍓😍", "Mon dessert préféré !")
        }, 2f)
        pet.setActOrAfterAir(Act.GOTO_FOOD)
    }

    /** Act.GOTO_FOOD : il attend que la nourriture tombe puis court la manger. */
    fun updateGoToFood(pet: Pet, dt: Float) {
        val c = props.firstOrNull { it.view.kind.isFood }
        if (c == null) { pet.setAct(Act.IDLE); return }
        if (!c.onGround) {
            pet.body.pose = Pose.IDLE
            pet.body.facing = sign(c.x - pet.body.fx).takeIf { it != 0f } ?: pet.body.facing
            pet.express(Expr.SURPRISED, 0.3f)
        } else if (ctx.actor.walkToward(pet, c.x, 130 * d, dt, stopDist = 26 * d)) {
            eatingFood = c.view.kind.food
            ctx.showFood(pet, eatingFood)
            removeProp(c)
            pet.setAct(Act.EAT, 3f)
        }
    }

    // =====================================================================
    //  Crottes
    // =====================================================================
    fun spawnPoop(x: Float) {
        val p = addProp(PropView.Kind.POOP, 34, touchable = true)
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
        if (ctx.busyInStation()) return
        if (st.sleeping) { ctx.say("Chut… il dort"); return }
        if (!st.startPlay()) { ctx.say(ctx.pick("Trop fatigué… 😴", "Plus tard, j'ai plus d'énergie")); return }
        props.filter { it.toy != null }.forEach { removeProp(it) }
        toyHeldByUser = false
        val p = when (toy) {
            Toy.BALL -> addProp(PropView.Kind.BALL, 42, 42, touchable = true)
            Toy.BALLOON -> addProp(PropView.Kind.BALLOON, 52, 86, touchable = true)
            Toy.FRISBEE -> addProp(PropView.Kind.FRISBEE, 52, 30, touchable = true)
            Toy.PLUSH -> addProp(PropView.Kind.PLUSH, 40, 44, touchable = true)
        }
        if (toy == Toy.BALLOON || toy == Toy.FRISBEE) p.view.tint = Options.accColor(rnd.nextInt(Options.accColors.size))
        val fx = pet.body.fx; val fy = pet.body.fy
        p.x = (fx + pet.body.facing * 70 * d).coerceIn(arena.minX, arena.maxX)
        p.y = fy - 120 * d
        p.vx = pet.body.facing * 250 * d; p.vy = -500 * d
        when (toy) {
            Toy.BALLOON -> { p.y = fy - 220 * d; p.vy = -120 * d; p.vx = pet.body.facing * 60 * d }
            Toy.FRISBEE -> { p.vx = pet.body.facing * 650 * d; p.vy = -380 * d }
            else -> {}
        }
        if (from != null) {
            // le jouet jaillit du coffre
            p.x = from.cx; p.y = from.cy
            p.vx = ((fx - from.cx) * 0.9f).coerceIn(-700 * d, 700 * d)
            p.vy = if (toy == Toy.BALLOON) -250 * d else -950 * d
        }
        placeProp(p)
        setupToyTouch(p)
        pet.mind.playTime = 0f; toyHoldTime = 0f; hugTime = 0f; catchCooldown = 0f
        ctx.noteInteraction()
        val fav = toy == st.favoriteToy
        ctx.say(
            if (fav) ctx.pick("Mon jouet préféré !! ⭐", "Ouiii, le ${toy.label.lowercase()} !! ⭐")
            else when (toy) {
                Toy.BALL -> ctx.pick("Une balle !! 🎾", "Lance-la moi !")
                Toy.BALLOON -> ctx.pick("Un ballon ! 🎈 Il ne doit pas toucher le sol !", "Je vais le garder en l'air !")
                Toy.FRISBEE -> ctx.pick("Un frisbee ! 🥏 Lance-le, je l'attrape !", "Je suis un champion du frisbee !")
                Toy.PLUSH -> ctx.pick("Un doudou ! 🧸", "Oh, un nouveau copain !")
            }
        )
        if (fav) { ctx.emit(pet, PType.HEART, 4); pet.express(Expr.HAPPY, 2f) }
        pet.setActOrAfterAir(Act.PLAY)
    }

    /** Act.PLAY */
    fun updatePlay(pet: Pet, dt: Float) {
        val p = ctx.world.currentToy()
        pet.mind.playTime += dt
        if (p == null) { pet.setAct(Act.IDLE); return }
        val limit = if (pet.stats.personality == Personality.PLAYFUL) 50f else 35f
        if (pet.mind.playTime > limit || pet.stats.energy < 8f) { endPlay(p); return }
        when (p.toy!!) {
            Toy.BALL -> playBall(p, dt)
            Toy.BALLOON -> playBalloon(p, dt)
            Toy.FRISBEE -> playFrisbee(p, dt)
            Toy.PLUSH -> playPlush(p, dt)
        }
    }

    private fun endPlay(p: Prop) {
        removeProp(p)
        ctx.say(ctx.pick("C'était trop bien ! 💕", "Pfiou, je suis épuisé !", "Encore demain ?"))
        pet.express(Expr.HAPPY, 2f)
        pet.setAct(Act.IDLE, 4f)
    }

    private fun hit(toy: Toy, amount: Float = 2f) {
        pet.stats.playHit(toy, amount)
        ctx.emit(pet, if (ctx.rnd.nextBoolean()) PType.HEART else PType.NOTE)
        pet.express(Expr.HAPPY, 0.8f)
    }

    // --- Balle : il la pousse du museau ---
    private fun playBall(ball: Prop, dt: Float) {
        val rnd = ctx.rnd
        val dx = ball.x - pet.body.fx
        if (abs(dx) > 30 * d) {
            ctx.actor.walkToward(pet, ball.x, 170 * d, dt, stopDist = 30 * d)
        } else {
            pet.body.pose = Pose.IDLE
            ctx.actor.faceToward(pet, ball.x)
            if (ball.y > arena.groundY - 60 * d && pet.mind.kickCooldown <= 0f && !toyHeldByUser) {
                val dir = if (rnd.nextFloat() < 0.75f) pet.body.facing else -pet.body.facing
                ball.vx = dir * (350 + rnd.nextFloat() * 600) * d
                ball.vy = -(500 + rnd.nextFloat() * 700) * d
                ball.onGround = false
                pet.mind.kickCooldown = 0.7f
                hit(Toy.BALL)
                if (rnd.nextFloat() < 0.25f) ctx.say(ctx.pick("Bim !", "Attrape !", "Hop là !", "Goaaal ⚽"), 1.6f)
                ctx.actor.jump(pet, -420 * d, 0f, then = Act.PLAY)
            }
        }
    }

    // --- Ballon : il le garde en l'air à coups de tête ---
    private fun balloonCenterY(p: Prop) = p.y - p.lp.height + p.lp.width / 2f

    private fun playBalloon(p: Prop, dt: Float) {
        val dx = p.x - pet.body.fx
        if (abs(dx) > 26 * d) { ctx.actor.walkToward(pet, p.x, 190 * d, dt, stopDist = 26 * d); return }
        pet.body.pose = Pose.IDLE
        ctx.actor.faceToward(pet, p.x)
        val h = pet.body.fy - balloonCenterY(p)
        if (h < 320 * d && p.vy > -60 * d && pet.mind.kickCooldown <= 0f && !toyHeldByUser) {
            val need = (h - 75 * d).coerceAtLeast(15 * d)
            pet.mind.kickCooldown = 0.5f
            ctx.actor.jump(pet, BunnyPhysics.impulseForHeight(pet.body.phys.gravity, need), 0f, then = Act.PLAY)
        }
    }

    // --- Frisbee : il l'attrape au vol et le relance ---
    private fun playFrisbee(p: Prop, dt: Float) {
        val rnd = ctx.rnd
        if (p.held) {
            toyHoldTime += dt
            pet.body.pose = Pose.IDLE
            if (toyHoldTime > 1.3f) {
                val dir = if (pet.body.fx < arena.screenW / 2) 1f else -1f
                pet.body.facing = dir
                p.held = false; p.onGround = false
                p.x = pet.body.fx + dir * 30 * d; p.y = pet.body.fy - 40 * d
                p.vx = dir * (750 + rnd.nextFloat() * 450) * d
                p.vy = -(250 + rnd.nextFloat() * 250) * d
                catchCooldown = 0.9f
                if (rnd.nextFloat() < 0.35f) ctx.say(ctx.pick("À toi ! 🥏", "Attrape !", "Super lancer !"), 1.4f)
            }
            return
        }
        val airborne = !p.onGround
        val tx = if (airborne) p.x + p.vx * 0.25f else p.x
        if (abs(tx - pet.body.fx) > 24 * d) ctx.actor.walkToward(pet, tx, 230 * d, dt, stopDist = 24 * d) else pet.body.pose = Pose.IDLE
        if (pet.body.act != Act.PLAY) return
        val dx = p.x - pet.body.fx
        val h = pet.body.fy - (p.y - p.lp.height / 2f)
        if (airborne && abs(dx) < 90 * d && h > 60 * d && h < 240 * d && pet.mind.kickCooldown <= 0f && !toyHeldByUser) {
            pet.mind.kickCooldown = 0.6f
            ctx.actor.jump(pet, BunnyPhysics.impulseForHeight(pet.body.phys.gravity, (h - 30 * d).coerceAtLeast(10 * d)), dx * 2.5f, then = Act.PLAY)
        }
    }

    // --- Doudou : câlins, promenade, lancers ---
    private fun playPlush(p: Prop, dt: Float) {
        val rnd = ctx.rnd
        if (!p.held) {
            if (!p.onGround) { pet.body.pose = Pose.IDLE; ctx.actor.faceToward(pet, p.x); return }
            if (ctx.actor.walkToward(pet, p.x, 150 * d, dt, stopDist = 22 * d)) {
                p.held = true; hugTime = 0f
                hit(Toy.PLUSH, 3f)
                ctx.say(ctx.pick("Mon doudou ! 🧸", "Je t'ai !", "Viens là toi !"), 1.6f)
            }
            return
        }
        val before = hugTime
        hugTime += dt
        when {
            hugTime < 4f -> {
                pet.body.pose = Pose.IDLE
                pet.express(Expr.HAPPY, 0.2f)
                if (rnd.nextFloat() < dt * 2.5f) { ctx.emit(pet, PType.HEART); pet.stats.playHit(Toy.PLUSH, 0.6f) }
            }
            hugTime < 10f -> {
                if (before < 4f) pet.body.targetX = (pet.body.fx + (rnd.nextFloat() * 2f - 1f) * arena.screenW * 0.3f).coerceIn(arena.minX, arena.maxX)
                if (ctx.actor.walkToward(pet, pet.body.targetX, 80 * d, dt)) pet.body.pose = Pose.IDLE
            }
            else -> {
                if (pet.stats.energy < 35f) { ctx.say("Dodo avec mon doudou… 💤"); ctx.startSleep(pet); return }
                p.held = false; p.onGround = false
                p.vy = -(650 + rnd.nextFloat() * 250) * d
                p.vx = pet.body.facing * rnd.nextFloat() * 120 * d
                catchCooldown = 0.5f
                hugTime = 0f
                ctx.say(ctx.pick("Wiii, vole doudou !", "Hop !", "Il vole !"), 1.4f)
            }
        }
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
                        if (t.toy == Toy.PLUSH || t.toy == Toy.FRISBEE) {
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
                    catchCooldown = 0.3f
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
        if (catchCooldown > 0f) catchCooldown -= dt
        val fx = pet.body.fx; val fy = pet.body.fy
        val act = pet.body.act
        val g = pet.body.phys.gravity
        val groundY = arena.groundY
        val mouthX = fx + pet.body.facing * 18 * d
        val rnd = ctx.rnd
        // Parcours à l'envers : un objet peut être retiré pendant la boucle, sans copier la liste
        var i = props.size - 1
        while (i >= 0) {
            val p = props[i--]
            p.age += dt
            val spin0 = p.view.spin
            val userHolds = toyHeldByUser && p.toy != null
            when (p.view.kind) {
                PropView.Kind.POOP -> { p.y = groundY }
                PropView.Kind.CARROT, PropView.Kind.LETTUCE, PropView.Kind.STRAWBERRY -> {
                    if (!p.onGround) {
                        p.view.spin += 200f * dt
                        if (BunnyPhysics.stepFalling(p, dt, g, arena.minX, arena.maxX, groundY)) {
                            p.view.spin = if (p.view.kind == PropView.Kind.CARROT) 90f else 0f
                        }
                    }
                    if (p.age > Tuning.FOOD_LIFETIME_S) { removeProp(p); continue }
                }
                PropView.Kind.BALL -> if (!userHolds) {
                    stepToy(p, dt, 1f, 0.62f, 0.8f, 2.2f)
                    p.view.spin += p.vx / d * dt * 2.5f
                }
                PropView.Kind.BALLOON -> if (!userHolds) {
                    p.vx *= (1f - 0.9f * dt); p.vy *= (1f - 0.6f * dt)
                    stepToy(p, dt, 0.08f, 0.3f, 0.9f, 3f)
                    p.view.spin = sin(p.age * 1.7f) * 8f + p.vx / d * 0.03f
                    // coup de tête
                    val cy = balloonCenterY(p)
                    if (hypot(p.x - fx, cy - (fy - 85 * d)) < 42 * d && p.vy > -100 * d && act != Act.DRAGGED && act != Act.SLEEP) {
                        p.vy = -(450 + rnd.nextFloat() * 300) * d
                        p.vx = (p.x - fx) * 4f + (rnd.nextFloat() * 2f - 1f) * 150 * d
                        p.onGround = false
                        hit(Toy.BALLOON)
                        if (rnd.nextFloat() < 0.15f) ctx.say(ctx.pick("Boing !", "En l'air !", "Hop !"), 1.2f)
                    }
                    if (p.onGround && act == Act.PLAY && ctx.clock.wallMs() - balloonGroundSaid > 8000) {
                        balloonGroundSaid = ctx.clock.wallMs()
                        ctx.say(ctx.pick("Oh non, il a touché le sol !", "Raté ! On recommence ?"), 1.8f)
                    }
                }
                PropView.Kind.FRISBEE -> if (!userHolds) {
                    if (p.held) {
                        p.x = mouthX + pet.body.facing * 8 * d
                        p.y = fy - 52 * d + p.lp.height / 2f
                        p.vx = 0f; p.vy = 0f
                    } else {
                        val lift = (abs(p.vx) / (900 * d)).coerceAtMost(1f) * 0.85f
                        p.vx *= (1f - 0.35f * dt)
                        stepToy(p, dt, 1f - lift, 0.15f, 0.6f, 5f)
                        if (!p.onGround) p.view.spin += 900f * dt
                        // attrapé ?
                        val cy = p.y - p.lp.height / 2f
                        if (catchCooldown <= 0f && (act == Act.PLAY || act == Act.AIR) &&
                            hypot(p.x - mouthX, cy - (fy - 55 * d)) < 45 * d
                        ) {
                            val wasFlying = !p.onGround
                            p.held = true; toyHoldTime = 0f
                            hit(Toy.FRISBEE, if (wasFlying) 4f else 1f)
                            if (wasFlying && rnd.nextFloat() < 0.5f) ctx.say(ctx.pick("Attrapé ! 🥏", "Trop fort !", "Dans les dents !"), 1.5f)
                        }
                    }
                }
                PropView.Kind.PLUSH -> if (!userHolds) {
                    if (p.held) {
                        val sleepingNow = act == Act.SLEEP
                        p.x = fx + pet.body.facing * (if (sleepingNow) 30 else 16) * d
                        p.y = fy - (if (sleepingNow) 0 else 6) * d
                        p.vx = 0f; p.vy = 0f; p.view.spin = if (sleepingNow) -70f else 0f
                    } else {
                        stepToy(p, dt, 1f, 0.3f, 0.6f, 6f)
                        if (!p.onGround) p.view.spin += p.vx / d * dt * 3f + 120f * dt
                        else p.view.spin *= (1f - 6f * dt)
                        if (catchCooldown <= 0f && act == Act.PLAY && !p.onGround &&
                            hypot(p.x - fx, (p.y - p.lp.height / 2f) - (fy - 40 * d)) < 40 * d
                        ) {
                            p.held = true; hugTime = 0f
                            hit(Toy.PLUSH, 3f)
                        }
                    }
                }
            }
            // Le dessin ne dépend que de la rotation : on ne redessine que si elle a changé
            if (p.view.spin != spin0) p.view.invalidate()
            if (p.attached && p.view.kind != PropView.Kind.POOP) placeProp(p)
        }
    }

    private fun stepToy(p: Prop, dt: Float, gFactor: Float, bounce: Float, wallBounce: Float, friction: Float) {
        BunnyPhysics.stepProp(
            p, dt, pet.body.phys.gravity, gFactor, bounce, wallBounce, friction,
            p.lp.width / 2f, p.lp.height.toFloat(), arena.screenW, arena.topY, arena.groundY, Tuning.PROP_SETTLE_DP * d,
        )
    }
}
