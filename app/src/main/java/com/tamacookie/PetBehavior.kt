package com.tamacookie

import android.view.HapticFeedbackConstants
import com.tamacookie.feature.Travel
import kotlin.math.abs
import kotlin.math.sign

/**
 * Le corps et l'humeur d'un animal, image par image (Kotlin pur, testé en JVM) : la machine à états de
 * ce qui n'appartient à aucune fonctionnalité — locomotion (repos, marche, vol, glisser), sommeil, voyage
 * d'app en app, assis, étourdi, danse — et la décision suivante ([BunnyBrain]). Sorti de `BunnyService`
 * (étape 7) : la boucle l'appelle **pour chaque animal affiché**, avec lui pour animal courant
 * (`World.asPet`) ; les activités d'une fonctionnalité passent avant par [PetCtx.acts].
 *
 * Ce qui touche d'autres domaines (coffre, bêtises, pages, autres animaux) arrive par [Hooks].
 */
class PetBehavior(private val ctx: PetCtx, private val hooks: Hooks) {

    /** Ce que le comportement demande aux autres domaines (le service les câble). */
    interface Hooks {
        /** Le coffre est visible sur sa page et libre pour lui (`Director`). */
        fun chestVisible(p: Pet): Boolean
        /** Dernière fois qu'un animal s'est servi seul dans le coffre. */
        val lastSelfServe: Long
        /** Il part se servir au coffre. */
        fun goToChest(p: Pet, now: Long)
        /** Ignoré depuis longtemps : il tente une bêtise (true = lancée). */
        fun seekAttention(p: Pet): Boolean
        fun startPageWander(p: Pet)
        /** Heure locale 0..23. */
        fun hour(): Int
        /** Ce qu'il perçoit des autres animaux (null = seul) : `SocialSystem.nearest`. */
        fun nearest(p: Pet): Neighbor? = null
        /** Il va vers [other] (interaction sociale) ; false = rien n'a commencé. */
        fun socialize(p: Pet, other: Neighbor): Boolean = false
    }

    private val arena get() = ctx.arena
    private val actor get() = ctx.actor
    private val speech get() = ctx.speech
    private val rnd get() = ctx.rnd

    /** Une image pour [p] (animal courant) : son activité, ou celle d'une fonctionnalité. */
    fun update(p: Pet, dt: Float) {
        val b = p.body
        val m = p.mind
        val st = p.stats
        val d = arena.d
        val lines = p.species.lines
        // Activité prise en charge par une fonctionnalité (stations…) : elle fait tout
        ctx.acts.handlerFor(p)?.let { it.update(p, dt); return }
        when (b.act) {
            Act.IDLE -> {
                b.pose = Pose.IDLE
                b.fy = actor.surfaceY(p)
                if (ctx.world.hasToy()) { p.setAct(Act.PLAY); return }
                if (ctx.world.hasFood() && ctx.director.mayChaseFood(p)) { p.setAct(Act.GOTO_FOOD); return }
                m.chatterIn -= dt
                if (m.chatterIn <= 0f) { m.chatterIn = 50f + rnd.nextFloat() * 80f; chatter(p) }
                if (b.actTime > b.actDuration) decideNext(p)
            }
            Act.WALK -> {
                val spec = p.species.spec
                val speed = when {
                    st.isSick || st.energy < ctx.rules.walkSlowEnergyBelow -> spec.walkSlowDp * d
                    st.happiness > ctx.rules.walkHappyAbove -> spec.walkHappyDp * d
                    else -> spec.walkNormalDp * d
                } * p.species.behavior(p.personality).walkSpeedFactor
                if (actor.walkToward(p, b.targetX, speed, dt)) p.setAct(Act.IDLE, 2f + rnd.nextFloat() * 4f)
            }
            Act.AIR -> stepAir(p, dt)
            Act.DRAGGED -> b.pose = Pose.DRAGGED
            Act.SLEEP -> {
                b.pose = Pose.SLEEP
                b.fy = actor.surfaceY(p)
                if (!st.sleeping) {
                    p.express(Expr.HAPPY, 2f)
                    speech.sayOneOf(lines.WOKE_UP)
                    p.setAct(Act.IDLE, 3f)
                }
            }
            Act.TRAVEL_OUT -> {
                if (actor.walkToward(p, b.targetX, p.species.spec.travelOutDp * d, dt, clamp = false)) {
                    // Il "passe" dans l'application suivante : réapparaît de l'autre côté
                    b.fx = if (b.targetX < 0) arena.screenW + arena.bw else -arena.bw.toFloat()
                    b.targetX = arena.screenW * (0.2f + rnd.nextFloat() * 0.6f)
                    if (m.travelThen == Act.SIT) b.targetX = arena.maxX - 10 * d
                    p.setAct(Act.TRAVEL_IN)
                }
            }
            Act.TRAVEL_IN -> {
                if (actor.walkToward(p, b.targetX, p.species.spec.travelInDp * d, dt, clamp = false)) {
                    m.travelComment?.let { ctx.say(it, 4.5f) }
                    m.travelComment = null
                    when (m.travelThen) {
                        Act.SIT -> p.setAct(Act.SIT, 40f)
                        Act.DANCE -> startDance(p)
                        else -> p.setAct(Act.IDLE, 4f)
                    }
                }
            }
            Act.PAGE_OUT -> ctx.pages.updatePageOut(p, dt)
            Act.SIT -> {
                b.pose = Pose.SIT
                b.facing = -1f
                if (b.actTime > b.actDuration) p.setAct(Act.IDLE, 2f)
            }
            Act.DIZZY -> {
                b.pose = Pose.IDLE
                p.express(Expr.DIZZY, 0.2f)
                if (b.actTime > 2.2f) { m.exprOverride = null; p.setAct(Act.IDLE, 2f) }
            }
            Act.DANCE -> {
                b.pose = Pose.IDLE
                m.danceTime += dt
                m.danceHopIn -= dt
                if (m.danceTime > 9f) { p.setAct(Act.IDLE, 3f); return }
                if (m.danceHopIn <= 0f) {
                    m.danceHopIn = 0.55f
                    b.facing = -b.facing
                    ctx.emit(p, PType.NOTE)
                    actor.jump(p, -420 * d, 0f, then = Act.DANCE)
                }
            }
            // Activité d'une fonctionnalité sans gestionnaire (station introuvable…) : il ne reste pas figé
            else -> p.setAct(Act.IDLE, 2f)
        }
    }

    /** Ce qu'il fait ensuite : le cerveau décide, le comportement exécute. */
    fun decideNext(p: Pet) {
        val b = p.body
        val st = p.stats
        val boxes = ctx.screen.snapshot.platforms.boxes
        val input = BrainInput(
            energy = st.energy, satiety = st.satiety, happiness = st.happiness,
            personality = p.personality, hour = hooks.hour(),
            x = b.fx, facing = b.facing, minX = arena.minX, maxX = arena.maxX, screenW = arena.screenW, density = arena.d,
            platform = b.platform, platforms = boxes,
            elsewhere = ctx.pages.bunnyElsewhere(),
            canPageWander = ctx.pages.canPageWander(),
            chestVisible = hooks.chestVisible(p),
            hasToyOrFood = ctx.world.hasToyOrFood(),
            nowMs = ctx.clock.wallMs(),
            lastSelfServeMs = hooks.lastSelfServe,
            nearest = hooks.nearest(p),
        )
        when (val dec = p.mind.brain.decide(input) { hooks.seekAttention(p) }) {
            Decision.Sleep -> ctx.startSleep(p)
            Decision.AttentionSought -> {}
            Decision.GoToChest -> { hooks.goToChest(p, input.nowMs); p.setAct(Act.GOTO_CHEST) }
            Decision.PageWander -> hooks.startPageWander(p)
            is Decision.Socialize -> if (!hooks.socialize(p, dec.other)) p.setAct(Act.IDLE, 2f + rnd.nextFloat() * 3f)
            is Decision.JumpDown -> actor.jumpTo(p, dec.targetX, arena.groundY, Act.IDLE)
            is Decision.JumpToPlatform -> {
                b.facing = sign(dec.target.centerX - b.fx)
                actor.jumpTo(p, dec.target.centerX, dec.target.top, Act.IDLE)
            }
            is Decision.TurnAround -> { b.facing = -b.facing; p.setAct(Act.IDLE, dec.idleSeconds) }
            is Decision.Idle -> p.setAct(Act.IDLE, dec.seconds)
            is Decision.Walk -> { b.targetX = dec.targetX; p.setAct(Act.WALK) }
            is Decision.Hop -> actor.jump(p, dec.vy, dec.vx, then = Act.IDLE)
        }
    }

    /** Si l'icône sous ses pattes a bougé ou disparu (défilement, changement d'app) : il tombe. */
    fun checkPlatform(p: Pet) {
        val b = p.body
        val cur = b.platform ?: return
        if (b.act == Act.AIR || b.act == Act.DRAGGED) return
        val set = ctx.screen.snapshot.platforms
        if (set.version == b.platVersionSeen) return
        b.platVersionSeen = set.version
        val same = set.boxes.firstOrNull {
            abs(it.top - cur.top) < Tuning.PLATFORM_SAME_DP * arena.d && b.fx >= it.left && b.fx <= it.right
        }
        if (same != null) {
            b.platform = same
            b.fy = same.top
            return
        }
        b.platform = null
        b.phys.stop()
        b.afterAir = if (b.act == Act.SLEEP) Act.SLEEP else Act.IDLE
        p.express(Expr.SURPRISED, 1f)
        if (b.act != Act.SLEEP) speech.sayOneOf(p.species.lines.PLATFORM_LOST, 1.5f)
        p.setAct(Act.AIR)
    }

    private fun stepAir(p: Pet, dt: Float) {
        val b = p.body
        b.pose = Pose.AIR
        val set = ctx.screen.snapshot.platforms
        val ev = b.phys.stepAir(dt, set.boxes)
        if (b.phys.facing != 0f) b.facing = b.phys.facing
        when (ev) {
            AirEvent.ForcedLanded -> p.setAct(b.afterAir, 3f)
            AirEvent.Bounced -> ctx.haptic(p, HapticFeedbackConstants.KEYBOARD_TAP)
            is AirEvent.Landed -> onLanded(p, ev, set)
            AirEvent.Flying, AirEvent.HitWall -> {}
        }
    }

    /** Atterrissage sur une icône ou au sol : étourdi si brutal, sinon il reprend l'activité prévue. */
    private fun onLanded(p: Pet, ev: AirEvent.Landed, set: PlatformSet) {
        val b = p.body
        val lines = p.species.lines
        if (ev.onPlatform) {
            b.platform = set.boxes[ev.platform]
            b.platformLabel = set.items.getOrNull(ev.platform)?.label
            b.platVersionSeen = set.version
            ctx.haptic(p, HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (ev.hard) {
            if (ev.onPlatform) speech.sayOneOf(lines.HARD_LANDING_PLATFORM)
            else {
                speech.sayOneOf(lines.HARD_LANDING_GROUND)
                ctx.on(p, PetEvent.HardLanding)
            }
            p.setAct(Act.DIZZY)
            return
        }
        val next = b.afterAir
        val idle = if (ev.onPlatform) 2f + rnd.nextFloat() * 3f else 1.5f + rnd.nextFloat() * 2f
        p.setAct(next, if (next == Act.IDLE) idle else 3f)
        val lbl = b.platformLabel
        if (ev.onPlatform && !lbl.isNullOrBlank() && next == Act.IDLE && rnd.nextFloat() < 0.35f) {
            speech.sayOneOf(lines.onPlatform(lbl), 2.2f)
        }
    }

    fun startDance(p: Pet) {
        p.mind.danceTime = 0f; p.mind.danceHopIn = 0f
        p.setAct(Act.DANCE)
    }

    private fun chatter(p: Pet) {
        speech.at(SpeechPriority.LOW) { ctx.say(speech.chatterLine(p.stats.mood, p.species.lines)) }
        if (p.stats.mood == Mood.HAPPY && rnd.nextFloat() < 0.3f) startDance(p)
    }

    /**
     * L'utilisateur change d'application : [p] le suit (il court vers le bord et « saute » dans la nouvelle
     * app), commente, ou l'ignore selon ce qu'il fait ([Travel]).
     */
    fun onAppChanged(p: Pet, app: ForegroundAppWatcher.AppInfo) {
        ctx.on(p, PetEvent.AppVisited)
        val comment = speech.appComment(app.label, app.pkg, app.category, app.isHome, p.species.lines)
        val then = when (app.category) {
            CATEGORY_VIDEO -> Act.SIT
            CATEGORY_AUDIO -> Act.DANCE
            else -> Act.IDLE
        }
        val act = p.act
        if (p.isHiding) return
        val feature = ctx.acts.handlerFor(p)
        if (feature?.travel == Travel.IGNORES) return
        if (act in Act.BUSY_FOR_TRAVEL || feature?.blocksTravel == true) {
            if (act != Act.SLEEP && rnd.nextFloat() < 0.5f) ctx.say(comment, 3f)
            return
        }
        val now = ctx.clock.wallMs()
        if (now - p.mind.lastTravelAt < Tuning.TRAVEL_COOLDOWN_MS) { ctx.say(comment, 3f); return }
        p.mind.lastTravelAt = now
        ctx.closeMenu()
        p.mind.travelComment = comment
        p.mind.travelThen = then
        // Il court vers le bord le plus proche et "saute" dans la nouvelle app
        p.body.targetX = if (p.body.fx < arena.screenW / 2) -arena.bw.toFloat() else arena.screenW + arena.bw
        speech.sayOneOf(p.species.lines.FOLLOWING, 1.5f)
        p.setAct(Act.TRAVEL_OUT)
        ctx.requestFrames()
    }

    private companion object {
        // Valeurs de android.content.pm.ApplicationInfo.CATEGORY_* (copiées pour rester en Kotlin pur)
        const val CATEGORY_AUDIO = 1
        const val CATEGORY_VIDEO = 2
    }
}
