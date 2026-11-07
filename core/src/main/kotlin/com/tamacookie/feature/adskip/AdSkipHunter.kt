package com.tamacookie.feature.adskip

import com.tamacookie.Act
import com.tamacookie.Box
import com.tamacookie.Expr
import com.tamacookie.Haptic
import com.tamacookie.PType
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.PetEvent
import com.tamacookie.PetId
import com.tamacookie.Pose
import com.tamacookie.Prefs
import com.tamacookie.ScreenActions
import com.tamacookie.feature.ActHandler
import com.tamacookie.feature.ActRegistry
import com.tamacookie.feature.Travel
import java.util.EnumSet
import kotlin.math.abs
import kotlin.math.sin

/**
 * **Chasseur de pubs** : quand le bouton « Ignorer l'annonce » d'une publicité est à l'écran
 * ([com.tamacookie.ScreenSnapshot.adSkip], liste blanche stricte de `ScreenScan.isAdSkip`), un animal libre
 * court vers lui, saute dessus, tape du pied — l'appui passe par le port [ScreenActions] — et redescend.
 *
 * - **Qui** : un seul animal, le plus proche parmi ceux qui sont affichés, sur la page affichée, éveillés,
 *   tranquilles ([READY] : ni tenu, ni caché, ni dans une station, ni en pleine bêtise) — arbitré par le
 *   `Director` ([com.tamacookie.Director.AdHunt]), déduit du monde comme `mayHeadTo`.
 * - **Priorité** : [check] passe avant les bêtises et le bavardage (appelé avant eux à chaque image) et même
 *   avant le sommeil : le plus proche **endormi** (hors station) est réveillé par la pub, et il y va.
 * - **Une tentative par bouton** : réussie ou abandonnée, on ne recommence qu'une fois le bouton disparu.
 * - **Abandon sans un mot** si le bouton disparaît (ou s'écarte de la cible) en route, ou s'il n'est pas
 *   dessus en [TIMEOUT_MS].
 * - **Sans bouton** (réglage coupé, ou aucune pub) : [check] ne fait rien — aucun tirage, aucune décision
 *   changée.
 *
 * L'état de la chasse (cible, phase) est tenu ici : une seule à la fois dans le monde.
 */
class AdSkipHunter(private val ctx: PetCtx, private val actions: ScreenActions) : ActHandler {

    /** Réglage `Prefs.AD_SKIP` (relu par l'hôte) ; coupé, il ne voit même pas le bouton. */
    var enabled = Prefs.AD_SKIP_DEFAULT

    private enum class Phase { RUN, LEAP, PRESS }

    private var hunter: PetId? = null
    private var target: Box? = null
    private var phase = Phase.RUN
    private var startedAt = 0L
    private var pressed = false

    /** Le bouton à l'écran a déjà été visé (appuyé ou abandonné) : pas d'autre tentative avant qu'il ait disparu. */
    var attempted = false
        private set

    /** L'animal parti appuyer (null = aucun). */
    val hunterId: PetId? get() = hunter

    /** Occupé : il ne suit pas l'utilisateur dans une autre app (le bouton disparaît de toute façon). */
    override val travel: Travel get() = Travel.BUSY

    fun registerActs(acts: ActRegistry) {
        acts.register(Act.SKIP_AD) { p -> this.takeIf { p.id == hunter } }
    }

    /**
     * Une fois par image, **avant** les bêtises : un bouton est là, personne n'y va encore et il n'a pas déjà
     * été visé → l'animal choisi part. Sans bouton : rien (aucun tirage).
     */
    fun check() {
        // L'animal parti a été interrompu (attrapé, étourdi, retiré…) : la chasse est finie
        if (hunter != null && ctx.director.adHunt.hunter()?.id != hunter) clear()
        val box = if (enabled) ctx.screen.snapshot.adSkip else null
        when {
            box == null -> attempted = false
            !attempted && !ctx.hidden -> ctx.director.adHunt.hunterFor(box) { eligible(it) }?.let { p ->
                attempted = true
                ctx.world.asPet(p) {
                    if (p.stats.sleeping) wake(p)
                    start(p, box)
                }
            }
        }
    }

    /**
     * Il peut partir : affiché, sur la page affichée, hors station, et tranquille ([READY]) — ou endormi
     * ([Act.SLEEP] : la pub le réveille).
     */
    private fun eligible(p: Pet): Boolean =
        ctx.world.isShown(p) && !ctx.isAway(p) && (p.act in READY || (p.stats.sleeping && p.act == Act.SLEEP)) &&
            ctx.world.stationOf(p) == null && ctx.world.asPet(p) { !ctx.pages.bunnyElsewhere() }

    /** La pub le réveille (sans mauvaise humeur : c'est pour la bonne cause). */
    private fun wake(p: Pet) {
        ctx.on(p, PetEvent.Woken(grumpy = false))
        ctx.say(ctx.pick(*WOKEN), 2f)
    }

    private fun start(p: Pet, box: Box) {
        hunter = p.id; target = box; phase = Phase.RUN; pressed = false
        startedAt = ctx.clock.uptimeMs()
        // Les coordonnées du bouton sont celles de l'écran affiché
        ctx.pages.rebaseToActivePage()
        p.express(Expr.SURPRISED, 0.8f)
        p.setAct(Act.SKIP_AD)
        ctx.requestFrames()
    }

    override fun update(pet: Pet, dt: Float) {
        val t = target
        if (pet.id != hunter || t == null) { pet.setAct(Act.IDLE, 2f); return }
        if (!pressed) {
            val cur = if (enabled) ctx.screen.snapshot.adSkip else null
            if (cur == null || !cur.intersects(t) || ctx.clock.uptimeMs() - startedAt > TIMEOUT_MS) {
                giveUp(pet); return
            }
            target = cur                                     // le bouton a pu bouger un peu
        }
        when (phase) {
            Phase.RUN -> run(pet, dt)
            Phase.LEAP -> { phase = Phase.PRESS; press(pet) }   // il vient d'atterrir dessus
            Phase.PRESS -> press(pet)
        }
    }

    /** Il court vers le bouton ; assez près, il saute pour atterrir pile dessus. */
    private fun run(pet: Pet, dt: Float) {
        val t = target ?: return
        val a = ctx.arena
        val b = pet.body
        val tx = t.centerX.coerceIn(a.minX, a.maxX)
        if (abs(tx - b.fx) <= LEAP_RANGE_DP * a.d) {
            ctx.actor.faceToward(pet, tx)
            ctx.actor.jumpTo(pet, tx, t.top, Act.SKIP_AD)
            b.phys.airLandY = t.top                           // il se pose sur le bouton, pas au travers
            phase = Phase.LEAP
            return
        }
        // Perché sur une icône, walkToward le fait d'abord sauter au sol (puis il reprend la course)
        ctx.actor.walkToward(pet, tx, pet.species.spec.hideRunDp * a.d, dt)
    }

    /** Sur le bouton : il tape du pied, appuie une fois, dit sa réplique, puis redescend. */
    private fun press(pet: Pet) {
        val t = target ?: return
        val b = pet.body
        b.pose = Pose.IDLE
        if (!pressed) {
            b.fy = t.top - STOMP_DP * ctx.arena.d * abs(sin(b.actTime * STOMP_RATE))
            if (b.actTime < PRESS_AT_S) return
            b.fy = t.top
            pressed = true
            if (actions.pressAdSkip(t)) {
                ctx.haptic(pet, Haptic.KEYBOARD_TAP)
                ctx.emit(pet, PType.STAR, 3)
                pet.express(Expr.HAPPY, 1.5f)
                ctx.say(ctx.pick(*PRESSED), 2.5f)
            }
            return
        }
        b.fy = t.top
        if (b.actTime >= HOP_OFF_AT_S) {
            val a = ctx.arena
            clear()
            ctx.actor.jumpTo(pet, (b.fx + b.facing * 80 * a.d).coerceIn(a.minX, a.maxX), a.groundY, Act.IDLE)
        }
    }

    /** Il renonce sans rien dire : redescend s'il est en l'air (posé sur le bouton disparu), sinon se repose. */
    private fun giveUp(pet: Pet) {
        clear()
        val a = ctx.arena
        val b = pet.body
        if (b.platform == null && b.fy < a.groundY - 1f) ctx.actor.jumpTo(pet, b.fx, a.groundY, Act.IDLE)
        else pet.setAct(Act.IDLE, 2f)
    }

    /** Une caresse en route : il apprécie, et continue. */
    override fun onPet(pet: Pet): Boolean {
        ctx.on(pet, PetEvent.Petted)
        ctx.emit(pet, PType.HEART, 2)
        return true
    }

    private fun clear() {
        hunter = null; target = null; phase = Phase.RUN; pressed = false
    }

    companion object {
        /** Ce qu'il dit quand la pub le réveille. */
        val WOKEN = arrayOf("Hein ?! Une pub ?! 😳", "Mmh… quoi ? Une pub ! J'arrive !", "Une pub ? Pas question ! 😤")

        /** Ce qu'il dit en appuyant. */
        val PRESSED = arrayOf("Hop ! Au revoir la pub ! 🐾", "Et hop, pub passée ! 🐾", "Boing ! Plus de pub ! 🐾")

        /** Activités qu'il quitte pour aller appuyer (tranquille : ni bêtise, ni jeu, ni sommeil). */
        val READY: Set<Act> = EnumSet.of(Act.IDLE, Act.WALK, Act.SIT, Act.DANCE)

        /** Il abandonne s'il n'a pas appuyé au bout de ce temps (ms, depuis son départ). */
        const val TIMEOUT_MS = 6_000L
        /** Distance horizontale (dp) à partir de laquelle il saute directement sur le bouton. */
        const val LEAP_RANGE_DP = 220f
        /** Délai (s) entre l'atterrissage sur le bouton et l'appui. */
        const val PRESS_AT_S = 0.35f
        /** Délai (s) entre l'atterrissage et le saut pour redescendre. */
        const val HOP_OFF_AT_S = 1.4f
        /** Hauteur (dp) et rythme (rad/s) du pied qui tape avant l'appui. */
        const val STOMP_DP = 6f
        const val STOMP_RATE = 18f
    }
}
