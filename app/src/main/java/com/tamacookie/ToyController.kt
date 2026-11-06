package com.tamacookie

import android.annotation.SuppressLint
import android.graphics.Color
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
class ToyController(private val svc: BunnyService) {
    val props = ArrayList<Prop>()

    private var toyHeldByUser = false
    private var toyHoldTime = 0f
    private var hugTime = 0f
    private var catchCooldown = 0f
    private var balloonGroundSaid = 0L
    /** Ce qu'il est en train de manger (Act.EAT). */
    var eatingFood = Food.CARROT
        private set

    private val d get() = svc.d

    fun currentToy(): Prop? = props.firstOrNull { it.toy != null }
    fun hasToy(): Boolean = props.any { it.toy != null }
    fun hasFood(): Boolean = props.any { it.view.kind.isFood }
    fun hasToyOrFood(): Boolean = props.any { it.toy != null || it.view.kind.isFood }
    fun poopCount(): Int = props.count { it.view.kind == PropView.Kind.POOP }

    /** Un objet bouge (en vol, tenu par l'utilisateur) : animation fluide. */
    fun anyMoving(): Boolean = toyHeldByUser || props.any { it.view.kind != PropView.Kind.POOP && !it.onGround }

    // =====================================================================
    //  Fenêtres des objets
    // =====================================================================
    fun addProp(kind: PropView.Kind, wDp: Int, hDp: Int = wDp, touchable: Boolean): Prop {
        val v = PropView(svc, kind)
        val lp = svc.windows.params((wDp * d).toInt(), (hDp * d).toInt(), touchable)
        val p = Prop(v, lp)
        props.add(p)
        if (!svc.hidden) { placeProp(p, layout = false); svc.windows.add(v, lp); p.attached = true }
        svc.requestFrames()
        return p
    }

    fun removeProp(p: Prop) {
        if (p.attached) { svc.windows.safeRemove(p.view); p.attached = false }
        props.remove(p)
        // La fenêtre du jouet a disparu : l'utilisateur ne peut plus le tenir
        if (p.toy != null) toyHeldByUser = false
    }

    fun placeProp(p: Prop, layout: Boolean = true) {
        val nx = (p.x - p.lp.width / 2f).roundToInt()
        val ny = (p.y - p.lp.height).roundToInt()
        if (nx != p.lp.x || ny != p.lp.y) {
            p.lp.x = nx; p.lp.y = ny
            if (layout && p.attached) svc.windows.update(p.view, p.lp)
        }
    }

    /** Réaffiche les objets (après "Montrer") : tout sauf les crottes d'autres vues. */
    fun reattach() {
        val here = svc.pages.currentView
        for (p in props) if (!p.attached && (p.view.kind != PropView.Kind.POOP || p.anchor == here)) {
            placeProp(p, layout = false); svc.windows.add(p.view, p.lp); p.attached = true
        }
    }

    fun detachAll() {
        for (p in props) if (p.attached) { svc.windows.safeRemove(p.view); p.attached = false }
        toyHeldByUser = false
    }

    /** Nouvelle taille d'écran : objets ramenés dans l'écran et au sol. */
    fun onScreenResized() {
        for (p in props) {
            p.x = p.x.coerceIn(svc.minX, svc.maxX)
            if (p.onGround || p.view.kind == PropView.Kind.POOP) p.y = svc.groundY
        }
    }

    /** Les crottes restent sur la vue (page du bureau) où elles sont tombées. */
    fun layoutPoops() {
        val pages = svc.pages
        for (i in props.indices) {
            val p = props[i]
            if (p.view.kind != PropView.Kind.POOP) continue
            if (p.anchor.isEmpty()) p.anchor = pages.currentView
            val off = pages.visibleOffset(p.anchor)
            if (!off.isNaN()) {
                val x = (p.x - p.lp.width / 2f + off).roundToInt()
                p.lp.y = (p.y - p.lp.height).roundToInt()
                if (!p.attached) { p.lp.x = x; svc.windows.add(p.view, p.lp); p.attached = true }
                else if (p.lp.x != x) { p.lp.x = x; svc.windows.update(p.view, p.lp) }
            } else if (p.attached) { svc.windows.safeRemove(p.view); p.attached = false }
        }
    }

    // =====================================================================
    //  Nourriture
    // =====================================================================

    /** Donne à manger. [from] = le coffre d'où sort la nourriture (sinon elle tombe du ciel). */
    fun feed(food: Food = Food.CARROT, from: Station? = null) {
        val pet = svc.pet
        if (svc.stations.busyInStation()) return
        svc.mischief.noteInteraction()
        if (pet.sleeping) { svc.say("Chut… il dort 💤"); return }
        if (pet.satiety > 95f && food != Food.STRAWBERRY) { svc.say(svc.pick("J'ai plus faim !", "Mon ventre va exploser !")); return }
        if (hasFood()) return
        val kind = when (food) {
            Food.CARROT -> PropView.Kind.CARROT
            Food.LETTUCE -> PropView.Kind.LETTUCE
            Food.STRAWBERRY -> PropView.Kind.STRAWBERRY
        }
        val c = addProp(kind, 38, touchable = false)
        val rnd = svc.rnd
        if (from != null) {
            // jaillit du coffre en direction du lapin
            c.x = from.cx; c.y = from.cy
            c.vy = -900 * d
            c.vx = ((svc.fx - from.cx) * 0.9f).coerceIn(-600 * d, 600 * d)
        } else {
            c.x = (svc.fx + (if (rnd.nextBoolean()) 1 else -1) * (90 + rnd.nextFloat() * 120) * d).coerceIn(svc.minX, svc.maxX)
            c.y = svc.topY + 60 * d
            c.vy = 0f
        }
        placeProp(c)
        svc.express(Expr.SURPRISED, 1f)
        svc.say(when (food) {
            Food.CARROT -> svc.pick("Une carotte !! 🥕", "Miam en vue !", "C'est pour moi ?")
            Food.LETTUCE -> svc.pick("De la salade ! 🥬 Croquante !", "Plein de vitamines !")
            Food.STRAWBERRY -> svc.pick("UNE FRAISE ?! 🍓😍", "Mon dessert préféré !")
        }, 2f)
        svc.setActOrAfterAir(Act.GOTO_FOOD)
    }

    /** Act.GOTO_FOOD : il attend que la nourriture tombe puis court la manger. */
    fun updateGoToFood(dt: Float) {
        val bunny = svc.bunny
        val c = props.firstOrNull { it.view.kind.isFood }
        if (c == null) { svc.setAct(Act.IDLE); return }
        if (!c.onGround) {
            bunny.pose = Pose.IDLE
            bunny.facing = sign(c.x - svc.fx).takeIf { it != 0f } ?: bunny.facing
            svc.express(Expr.SURPRISED, 0.3f)
        } else if (svc.walkToward(c.x, 130 * d, dt, stopDist = 26 * d)) {
            eatingFood = c.view.kind.food
            bunny.foodColor = Color.parseColor(eatingFood.color)
            removeProp(c)
            svc.setAct(Act.EAT, 3f)
        }
    }

    // =====================================================================
    //  Crottes
    // =====================================================================
    fun spawnPoop(x: Float) {
        val p = addProp(PropView.Kind.POOP, 34, touchable = true)
        p.anchor = svc.pages.bunnyView()
        p.x = x; p.y = svc.groundY
        placeProp(p)
        p.view.setOnClickListener {
            removeProp(p)
            svc.pet.removePoop()
            svc.bunny.emit(PType.SPARKLE, 4)
            svc.express(Expr.HAPPY, 1.5f)
            svc.say(svc.pick("Merci ! ✨", "Ouf, ça sentait…", "Tu es le meilleur !"), 2f)
        }
    }

    fun restorePoops() {
        repeat(svc.pet.poops) {
            spawnPoop(svc.minX + svc.rnd.nextFloat() * (svc.maxX - svc.minX))
        }
    }

    // =====================================================================
    //  Jouets
    // =====================================================================
    fun startPlay(toy: Toy, from: Station? = null) {
        val pet = svc.pet
        val bunny = svc.bunny
        val rnd = svc.rnd
        if (svc.stations.busyInStation()) return
        if (pet.sleeping) { svc.say("Chut… il dort"); return }
        if (!pet.startPlay()) { svc.say(svc.pick("Trop fatigué… 😴", "Plus tard, j'ai plus d'énergie")); return }
        props.filter { it.toy != null }.forEach { removeProp(it) }
        toyHeldByUser = false
        val p = when (toy) {
            Toy.BALL -> addProp(PropView.Kind.BALL, 42, 42, touchable = true)
            Toy.BALLOON -> addProp(PropView.Kind.BALLOON, 52, 86, touchable = true)
            Toy.FRISBEE -> addProp(PropView.Kind.FRISBEE, 52, 30, touchable = true)
            Toy.PLUSH -> addProp(PropView.Kind.PLUSH, 40, 44, touchable = true)
        }
        if (toy == Toy.BALLOON || toy == Toy.FRISBEE) p.view.tint = Options.accColor(rnd.nextInt(Options.accColors.size))
        val fx = svc.fx; val fy = svc.fy
        p.x = (fx + bunny.facing * 70 * d).coerceIn(svc.minX, svc.maxX)
        p.y = fy - 120 * d
        p.vx = bunny.facing * 250 * d; p.vy = -500 * d
        when (toy) {
            Toy.BALLOON -> { p.y = fy - 220 * d; p.vy = -120 * d; p.vx = bunny.facing * 60 * d }
            Toy.FRISBEE -> { p.vx = bunny.facing * 650 * d; p.vy = -380 * d }
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
        svc.playTime = 0f; toyHoldTime = 0f; hugTime = 0f; catchCooldown = 0f
        svc.mischief.noteInteraction()
        val fav = toy == pet.favoriteToy
        svc.say(
            if (fav) svc.pick("Mon jouet préféré !! ⭐", "Ouiii, le ${toy.label.lowercase()} !! ⭐")
            else when (toy) {
                Toy.BALL -> svc.pick("Une balle !! 🎾", "Lance-la moi !")
                Toy.BALLOON -> svc.pick("Un ballon ! 🎈 Il ne doit pas toucher le sol !", "Je vais le garder en l'air !")
                Toy.FRISBEE -> svc.pick("Un frisbee ! 🥏 Lance-le, je l'attrape !", "Je suis un champion du frisbee !")
                Toy.PLUSH -> svc.pick("Un doudou ! 🧸", "Oh, un nouveau copain !")
            }
        )
        if (fav) { bunny.emit(PType.HEART, 4); svc.express(Expr.HAPPY, 2f) }
        svc.setActOrAfterAir(Act.PLAY)
    }

    /** Act.PLAY */
    fun updatePlay(dt: Float) {
        val p = currentToy()
        svc.playTime += dt
        if (p == null) { svc.setAct(Act.IDLE); return }
        val limit = if (svc.pet.personality == Personality.PLAYFUL) 50f else 35f
        if (svc.playTime > limit || svc.pet.energy < 8f) { endPlay(p); return }
        when (p.toy!!) {
            Toy.BALL -> playBall(p, dt)
            Toy.BALLOON -> playBalloon(p, dt)
            Toy.FRISBEE -> playFrisbee(p, dt)
            Toy.PLUSH -> playPlush(p, dt)
        }
    }

    private fun endPlay(p: Prop) {
        removeProp(p)
        svc.say(svc.pick("C'était trop bien ! 💕", "Pfiou, je suis épuisé !", "Encore demain ?"))
        svc.express(Expr.HAPPY, 2f)
        svc.setAct(Act.IDLE, 4f)
    }

    private fun hit(toy: Toy, amount: Float = 2f) {
        svc.pet.playHit(toy, amount)
        svc.bunny.emit(if (svc.rnd.nextBoolean()) PType.HEART else PType.NOTE)
        svc.express(Expr.HAPPY, 0.8f)
    }

    // --- Balle : il la pousse du museau ---
    private fun playBall(ball: Prop, dt: Float) {
        val rnd = svc.rnd
        val dx = ball.x - svc.fx
        if (abs(dx) > 30 * d) {
            svc.walkToward(ball.x, 170 * d, dt, stopDist = 30 * d)
        } else {
            svc.bunny.pose = Pose.IDLE
            svc.faceToward(ball.x)
            if (ball.y > svc.groundY - 60 * d && svc.kickCooldown <= 0f && !toyHeldByUser) {
                val dir = if (rnd.nextFloat() < 0.75f) svc.bunny.facing else -svc.bunny.facing
                ball.vx = dir * (350 + rnd.nextFloat() * 600) * d
                ball.vy = -(500 + rnd.nextFloat() * 700) * d
                ball.onGround = false
                svc.kickCooldown = 0.7f
                hit(Toy.BALL)
                if (rnd.nextFloat() < 0.25f) svc.say(svc.pick("Bim !", "Attrape !", "Hop là !", "Goaaal ⚽"), 1.6f)
                svc.jump(-420 * d, 0f, then = Act.PLAY)
            }
        }
    }

    // --- Ballon : il le garde en l'air à coups de tête ---
    private fun balloonCenterY(p: Prop) = p.y - p.lp.height + p.lp.width / 2f

    private fun playBalloon(p: Prop, dt: Float) {
        val dx = p.x - svc.fx
        if (abs(dx) > 26 * d) { svc.walkToward(p.x, 190 * d, dt, stopDist = 26 * d); return }
        svc.bunny.pose = Pose.IDLE
        svc.faceToward(p.x)
        val h = svc.fy - balloonCenterY(p)
        if (h < 320 * d && p.vy > -60 * d && svc.kickCooldown <= 0f && !toyHeldByUser) {
            val need = (h - 75 * d).coerceAtLeast(15 * d)
            svc.kickCooldown = 0.5f
            svc.jump(BunnyPhysics.impulseForHeight(svc.phys.gravity, need), 0f, then = Act.PLAY)
        }
    }

    // --- Frisbee : il l'attrape au vol et le relance ---
    private fun playFrisbee(p: Prop, dt: Float) {
        val rnd = svc.rnd
        if (p.held) {
            toyHoldTime += dt
            svc.bunny.pose = Pose.IDLE
            if (toyHoldTime > 1.3f) {
                val dir = if (svc.fx < svc.screenW / 2) 1f else -1f
                svc.bunny.facing = dir
                p.held = false; p.onGround = false
                p.x = svc.fx + dir * 30 * d; p.y = svc.fy - 40 * d
                p.vx = dir * (750 + rnd.nextFloat() * 450) * d
                p.vy = -(250 + rnd.nextFloat() * 250) * d
                catchCooldown = 0.9f
                if (rnd.nextFloat() < 0.35f) svc.say(svc.pick("À toi ! 🥏", "Attrape !", "Super lancer !"), 1.4f)
            }
            return
        }
        val airborne = !p.onGround
        val tx = if (airborne) p.x + p.vx * 0.25f else p.x
        if (abs(tx - svc.fx) > 24 * d) svc.walkToward(tx, 230 * d, dt, stopDist = 24 * d) else svc.bunny.pose = Pose.IDLE
        if (svc.act != Act.PLAY) return
        val dx = p.x - svc.fx
        val h = svc.fy - (p.y - p.lp.height / 2f)
        if (airborne && abs(dx) < 90 * d && h > 60 * d && h < 240 * d && svc.kickCooldown <= 0f && !toyHeldByUser) {
            svc.kickCooldown = 0.6f
            svc.jump(BunnyPhysics.impulseForHeight(svc.phys.gravity, (h - 30 * d).coerceAtLeast(10 * d)), dx * 2.5f, then = Act.PLAY)
        }
    }

    // --- Doudou : câlins, promenade, lancers ---
    private fun playPlush(p: Prop, dt: Float) {
        val rnd = svc.rnd
        if (!p.held) {
            if (!p.onGround) { svc.bunny.pose = Pose.IDLE; svc.faceToward(p.x); return }
            if (svc.walkToward(p.x, 150 * d, dt, stopDist = 22 * d)) {
                p.held = true; hugTime = 0f
                hit(Toy.PLUSH, 3f)
                svc.say(svc.pick("Mon doudou ! 🧸", "Je t'ai !", "Viens là toi !"), 1.6f)
            }
            return
        }
        val before = hugTime
        hugTime += dt
        when {
            hugTime < 4f -> {
                svc.bunny.pose = Pose.IDLE
                svc.express(Expr.HAPPY, 0.2f)
                if (rnd.nextFloat() < dt * 2.5f) { svc.bunny.emit(PType.HEART); svc.pet.playHit(Toy.PLUSH, 0.6f) }
            }
            hugTime < 10f -> {
                if (before < 4f) svc.targetX = (svc.fx + (rnd.nextFloat() * 2f - 1f) * svc.screenW * 0.3f).coerceIn(svc.minX, svc.maxX)
                if (svc.walkToward(svc.targetX, 80 * d, dt)) svc.bunny.pose = Pose.IDLE
            }
            else -> {
                if (svc.pet.energy < 35f) { svc.say("Dodo avec mon doudou… 💤"); svc.startSleep(); return }
                p.held = false; p.onGround = false
                p.vy = -(650 + rnd.nextFloat() * 250) * d
                p.vx = svc.bunny.facing * rnd.nextFloat() * 120 * d
                catchCooldown = 0.5f
                hugTime = 0f
                svc.say(svc.pick("Wiii, vole doudou !", "Hop !", "Il vole !"), 1.4f)
            }
        }
    }

    /** Le lapin est attrapé par l'utilisateur : il lâche son jouet. */
    fun dropHeldToy() {
        currentToy()?.let { if (it.held) { it.held = false; it.onGround = false } }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupToyTouch(t: Prop) {
        var vt: VelocityTracker? = null
        t.view.setOnTouchListener { _, ev ->
            val e2 = MotionEvent.obtain(ev); e2.setLocation(ev.rawX, ev.rawY)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    toyHeldByUser = true
                    svc.mischief.noteInteraction()
                    svc.requestFrames()
                    if (t.held) {
                        t.held = false
                        if (t.toy == Toy.PLUSH || t.toy == Toy.FRISBEE) {
                            svc.express(Expr.ANGRY, 1.2f)
                            svc.say(svc.pick("Hé ! C'est à moi !", "Rends-le moi ! 😾", "Mon jouet !"), 1.6f)
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
                    if (svc.act == Act.PLAY && hypot(t.vx, t.vy) > 1500 * d) svc.say(svc.pick("Waouh, quel lancer !", "J'y vais !"), 1.6f)
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
        val bunny = svc.bunny
        val fx = svc.fx; val fy = svc.fy
        val act = svc.act
        val g = svc.phys.gravity
        val groundY = svc.groundY
        val mouthX = fx + bunny.facing * 18 * d
        val rnd = svc.rnd
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
                        if (BunnyPhysics.stepFalling(p, dt, g, svc.minX, svc.maxX, groundY)) {
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
                        if (rnd.nextFloat() < 0.15f) svc.say(svc.pick("Boing !", "En l'air !", "Hop !"), 1.2f)
                    }
                    if (p.onGround && act == Act.PLAY && System.currentTimeMillis() - balloonGroundSaid > 8000) {
                        balloonGroundSaid = System.currentTimeMillis()
                        svc.say(svc.pick("Oh non, il a touché le sol !", "Raté ! On recommence ?"), 1.8f)
                    }
                }
                PropView.Kind.FRISBEE -> if (!userHolds) {
                    if (p.held) {
                        p.x = mouthX + bunny.facing * 8 * d
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
                            if (wasFlying && rnd.nextFloat() < 0.5f) svc.say(svc.pick("Attrapé ! 🥏", "Trop fort !", "Dans les dents !"), 1.5f)
                        }
                    }
                }
                PropView.Kind.PLUSH -> if (!userHolds) {
                    if (p.held) {
                        val sleepingNow = act == Act.SLEEP
                        p.x = fx + bunny.facing * (if (sleepingNow) 30 else 16) * d
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
            p, dt, svc.phys.gravity, gFactor, bounce, wallBounce, friction,
            p.lp.width / 2f, p.lp.height.toFloat(), svc.screenW, svc.topY, svc.groundY, Tuning.PROP_SETTLE_DP * d,
        )
    }
}
