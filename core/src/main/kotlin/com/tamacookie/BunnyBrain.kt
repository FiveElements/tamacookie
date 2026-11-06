package com.tamacookie

import com.tamacookie.species.Species
import java.util.EnumSet
import kotlin.math.abs
import kotlin.random.Random

/** Activité courante du lapin (machine à états du service). */
enum class Act {
    IDLE, WALK, AIR, DRAGGED, SLEEP, EAT, GOTO_FOOD, PLAY, TRAVEL_OUT, TRAVEL_IN, SIT, DIZZY, DANCE,
    /**
     * Dans une station (bain, véto, cage…) : l'update et les propriétés (cadence, voyage…) sont celles
     * du comportement de la station occupée (`StationBehavior`), via `ActRegistry`.
     */
    IN_STATION,
    SQUAT, KNOCK, HIDE_RUN, HIDDEN, PEEK, PAGE_OUT, GOTO_CHEST,
    /**
     * Avec un autre animal (suivre, câlin, dispute…) : l'update et les propriétés sont celles de l'interaction
     * en cours (`SocialInteraction`), via `ActRegistry`.
     */
    SOCIAL,
    /** Bêtises de l'évolution 4a : il tire la langue (réaction à un toucher), il mâchouille le bord de l'écran. */
    TEASE, CHEW_EDGE,
    /** Il va de lui-même à la gamelle (faim, gamelle libre sur sa page) : il y entre en arrivant. */
    GOTO_BOWL;

    companion object {
        /** En mouvement : animation fluide (~60 i/s). */
        val MOVING: Set<Act> = EnumSet.of(AIR, WALK, PLAY, DRAGGED, TRAVEL_OUT, TRAVEL_IN, PAGE_OUT, GOTO_FOOD, GOTO_CHEST, HIDE_RUN, KNOCK, SOCIAL, CHEW_EDGE,
            GOTO_BOWL)
        /** Immobile et endormi : animation très économe (~12 i/s). Dans une station : `ActHandler.slowFrames`. */
        val SLOW: Set<Act> = EnumSet.of(SLEEP)
        /** Il ne suit pas le défilement des pages (il est tenu, ou caché). */
        val NO_PAGE_FOLLOW: Set<Act> = EnumSet.of(DRAGGED, HIDE_RUN, HIDDEN, PEEK)
        /** Activités tranquilles pendant lesquelles il peut revenir sur la page affichée. */
        val CAN_COME_BACK: Set<Act> = EnumSet.of(IDLE, WALK, SIT, DANCE)
        val CAN_COME_BACK_OR_PAGE_OUT: Set<Act> = EnumSet.of(IDLE, WALK, SIT, DANCE, PAGE_OUT)
        /** Activités qui le font accourir tout de suite sur la page affichée. */
        val RUSH_BACK: Set<Act> = EnumSet.of(KNOCK, SQUAT, GOTO_FOOD, PLAY, CHEW_EDGE)
        /** Occupé : il ne suit pas l'utilisateur dans une autre application. Dans une station : `ActHandler.travel`. */
        val BUSY_FOR_TRAVEL: Set<Act> = EnumSet.of(DRAGGED, SLEEP, EAT, GOTO_FOOD, PLAY, AIR, SQUAT, KNOCK, TEASE, CHEW_EDGE)
        val HIDING: Set<Act> = EnumSet.of(HIDE_RUN, HIDDEN, PEEK)
        /** Libre de faire une bêtise. */
        val FREE: Set<Act> = EnumSet.of(IDLE, WALK, SIT)
    }
}

/** Instantané de tout ce dont le cerveau a besoin pour décider (aucune référence Android). */
data class BrainInput(
    val energy: Float,
    val satiety: Float,
    val happiness: Float,
    val personality: Personality,
    /** Heure locale 0..23. */
    val hour: Int,
    /** Position des pieds (px). */
    val x: Float,
    /** 1 = regarde à droite, -1 = à gauche. */
    val facing: Float,
    val minX: Float,
    val maxX: Float,
    val screenW: Float,
    val density: Float = 1f,
    /** Icône sous ses pattes (null = au sol). */
    val platform: Box? = null,
    /** Icônes visibles sur lesquelles il peut sauter. */
    val platforms: List<Box> = emptyList(),
    /** Il est sur une autre page du bureau que celle affichée. */
    val elsewhere: Boolean = false,
    /** Il peut partir explorer une page voisine du bureau. */
    val canPageWander: Boolean = false,
    /** Le coffre est visible sur sa page. */
    val chestVisible: Boolean = false,
    /** Un jouet ou de la nourriture est déjà sorti. */
    val hasToyOrFood: Boolean = false,
    val nowMs: Long = 0L,
    /** Dernière fois qu'il s'est servi seul dans le coffre. */
    val lastSelfServeMs: Long = 0L,
    /**
     * La gamelle l'attend : affichée sur sa page, libre, acceptée par son espèce, personne d'autre n'y va, et
     * il a faim sans refuser le repas (calculé par l'hôte, sans aléatoire). Faux = aucun tirage de plus.
     */
    val bowlAvailable: Boolean = false,
    /** L'animal le plus proche sur sa page (null = il est seul) : perception sociale. */
    val nearest: Neighbor? = null,
)

/** Ce qu'un animal perçoit d'un autre : qui, ce qu'il fait, à quelle distance (px, horizontale). */
data class Neighbor(val id: PetId, val act: Act, val distance: Float)

/** Ce que le lapin décide de faire ensuite ; le service l'exécute. */
sealed class Decision {
    object Sleep : Decision()
    /** Il a fait une bêtise pour attirer l'attention (déjà exécutée par le rappel). */
    object AttentionSought : Decision()
    object GoToChest : Decision()
    /** Il va à la gamelle ([BrainInput.bowlAvailable]). */
    object GoToBowl : Decision()
    object PageWander : Decision()
    /** Il va vers [other] (une interaction sociale : suivre, câlin…). */
    data class Socialize(val other: Neighbor) : Decision()
    /** Redescend de son icône vers le sol en [targetX]. */
    data class JumpDown(val targetX: Float) : Decision()
    /** Saute sur l'icône [index] de [BrainInput.platforms]. */
    data class JumpToPlatform(val index: Int, val target: Box) : Decision()
    /** Se retourne puis reste tranquille [idleSeconds] s. */
    data class TurnAround(val idleSeconds: Float) : Decision()
    data class Idle(val seconds: Float) : Decision()
    data class Walk(val targetX: Float) : Decision()
    /** Petit saut sur place avec cette vitesse initiale (px/s). */
    data class Hop(val vy: Float, val vx: Float) : Decision()
}

/**
 * Décisions d'un animal, sans Android : [Random] injecté, heure et état passés en paramètre ; l'influence du
 * caractère vient de son espèce ([Species.behavior]). Nom historique (il sert à toutes les espèces).
 */
class BunnyBrain(private val rnd: Random, private val species: Species = Species.DEFAULT) {

    /**
     * Choisit l'activité suivante. [seekAttention] est appelé (après la vérification du sommeil) :
     * s'il renvoie true, une bêtise a été lancée et la décision est [Decision.AttentionSought].
     */
    fun decide(s: BrainInput, seekAttention: () -> Boolean = { false }): Decision {
        if (wantsSleep(s)) return Decision.Sleep
        if (seekAttention()) return Decision.AttentionSought
        if (!s.elsewhere && wantsChest(s.nowMs, s.lastSelfServeMs, s.hasToyOrFood, s.chestVisible, s.satiety, s.happiness, s.energy)) {
            return Decision.GoToChest
        }
        // Après le coffre (sa logique ne change pas) : la gamelle, si elle l'attend — tirage seulement dans ce cas
        if (!s.elsewhere && s.bowlAvailable && rnd.nextFloat() < BOWL_CHANCE) return Decision.GoToBowl
        // Un autre animal tranquille, pas trop loin : il va le voir (seulement s'il n'est pas seul : un
        // animal seul ne tire pas ce sort, ses décisions sont celles d'avant l'étape 7)
        val n = s.nearest
        if (n != null && !s.elsewhere && n.act in Act.FREE && n.distance < SOCIAL_RANGE_DP * s.density &&
            rnd.nextFloat() < species.behavior(s.personality).socialChance
        ) return Decision.Socialize(n)
        if (!s.elsewhere && s.platform == null &&
            rnd.nextFloat() < species.behavior(s.personality).pageWanderChance && s.canPageWander
        ) return Decision.PageWander

        val d = s.density
        val r = rnd.nextFloat()
        // Sur une icône : redescendre, sauter sur une autre, ou rester perché
        if (s.platform != null) {
            if (r < 0.30f) return Decision.JumpDown((s.x + (rnd.nextFloat() * 2f - 1f) * 160 * d).coerceIn(s.minX, s.maxX))
            // Sur une autre page, les icônes listées sont celles de la page affichée : pas de saut.
            if (r < 0.60f && s.energy > 20f && !s.elsewhere) pickPlatform(s)?.let { return it }
            if (r < 0.75f) return Decision.TurnAround(2f + rnd.nextFloat() * 3f)
            return Decision.Idle(3f + rnd.nextFloat() * 5f)
        }
        val platChance = species.behavior(s.personality).platformChance
        if (!s.elsewhere && r < platChance && s.energy > 25f) pickPlatform(s)?.let { return it }
        return when {
            r < 0.55f -> Decision.Walk((s.x + (rnd.nextFloat() * 2f - 1f) * s.screenW * 0.45f).coerceIn(s.minX, s.maxX))
            r < 0.68f && s.energy > 25f -> Decision.Hop(-650 * d, s.facing * 90 * d)
            r < 0.80f -> Decision.TurnAround(1.5f + rnd.nextFloat() * 2f)
            else -> Decision.Idle(3f + rnd.nextFloat() * 5f)
        }
    }

    /** Envie de dormir : énergie basse, nuit, ou paresse. */
    fun wantsSleep(s: BrainInput): Boolean {
        val m = species.behavior(s.personality)
        return s.energy < m.sleepEnergyBelow ||
            (isNight(s.hour) && s.energy < m.nightSleepEnergyBelow && rnd.nextFloat() < 0.5f) ||
            (m.napEnergyBelow > 0f && s.energy < m.napEnergyBelow && rnd.nextFloat() < m.napChance)
    }

    /** Une icône à portée (ni trop près ni trop loin, dans l'écran, pas celle où il est déjà). */
    fun pickPlatform(s: BrainInput): Decision.JumpToPlatform? {
        val cur = s.platform
        var count = 0
        for (p in s.platforms) if (isJumpCandidate(p, s, cur)) count++
        if (count == 0) return null
        var k = rnd.nextInt(count)
        for (i in s.platforms.indices) {
            val p = s.platforms[i]
            if (isJumpCandidate(p, s, cur) && k-- == 0) return Decision.JumpToPlatform(i, p)
        }
        return null
    }

    private fun isJumpCandidate(p: Box, s: BrainInput, cur: Box?): Boolean {
        val cx = p.centerX
        return abs(cx - s.x) < s.screenW * 0.5f && abs(cx - s.x) > 30 * s.density &&
            cx in s.minX..s.maxX && (cur == null || p != cur)
    }

    /** Faim ou ennui : il va se servir lui-même dans le coffre (au plus toutes les 2 min). */
    fun wantsChest(
        nowMs: Long, lastSelfServeMs: Long, hasToyOrFood: Boolean, chestVisible: Boolean,
        satiety: Float, happiness: Float, energy: Float,
    ): Boolean {
        if (nowMs - lastSelfServeMs < Tuning.SELF_SERVE_COOLDOWN_MS) return false
        if (hasToyOrFood || !chestVisible) return false
        val hungry = satiety < 45f
        val bored = happiness < 55f && energy > 35f
        return (hungry || bored) && rnd.nextFloat() < 0.5f
    }

    /** Une chance sur deux de faire une bêtise quand le seuil d'ignorance est atteint. */
    fun rollAttention(): Boolean = rnd.nextFloat() <= 0.5f

    /** Délai avant de revenir sur la page affichée. */
    fun pageReturnDelayMs(): Int = Tuning.PAGE_RETURN_MIN_MS + rnd.nextInt(Tuning.PAGE_RETURN_RAND_MS)

    /** Durée d'une exploration de page voisine. */
    fun pageWanderDurationMs(): Int = Tuning.PAGE_WANDER_MIN_MS + rnd.nextInt(Tuning.PAGE_WANDER_RAND_MS)

    /** Ce que fait le lapin resté sur une autre page du bureau. */
    enum class PageReturn {
        /** Défilement en cours : on attend. */
        SCROLLING,
        /** Il a quelque chose à faire ici : il accourt tout de suite. */
        RUSH,
        /** Programmer le retour (délai [pageReturnDelayMs]). */
        SCHEDULE,
        /** Pas encore. */
        WAIT,
        /** Il revient maintenant par le bord. */
        COME_BACK,
    }

    companion object {
        /** Distance (dp) en deçà de laquelle il peut aller voir un autre animal. */
        const val SOCIAL_RANGE_DP = 600f

        /** Chance d'aller à la gamelle quand elle l'attend ([BrainInput.bowlAvailable]), à chaque décision. */
        const val BOWL_CHANCE = 0.5f

        fun isNight(hour: Int) = hour >= 22 || hour < 7

        /** Temps sans interaction avant qu'il cherche l'attention, selon le niveau de bêtises et le caractère. */
        fun attentionThresholdMs(mischief: Int, personality: Personality, species: Species = Species.DEFAULT): Long {
            val minutes = when (mischief) { 0 -> 12f; 1 -> 6f; else -> 3f } *
                species.behavior(personality).attentionFactor
            return (minutes * 60_000).toLong()
        }

        /** Seuil atteint et pas en pause entre deux bêtises. */
        fun canSeekAttention(nowMs: Long, lastInteractionMs: Long, cooldownUntilMs: Long, thresholdMs: Long): Boolean =
            nowMs - lastInteractionMs >= thresholdMs && nowMs >= cooldownUntilMs

        /** Pause après une tentative d'attirer l'attention. */
        fun attentionCooldownMs(mischief: Int): Long = if (mischief == 2) 50_000L else 100_000L

        /**
         * Retour sur la page affichée.
         * @param urgent il a quelque chose à faire ici (toc-toc, bêtise, nourriture, jeu)
         * @param returnAtMs heure prévue du retour, 0 = pas encore programmée
         */
        fun pageReturn(
            nowMs: Long, lastScrollMs: Long, urgent: Boolean, hasToyOrFood: Boolean,
            returnAtMs: Long, canComeBack: Boolean, sleeping: Boolean,
        ): PageReturn {
            if (nowMs - lastScrollMs < Tuning.PAGE_RETURN_SCROLL_QUIET_MS) return PageReturn.SCROLLING
            if (urgent) return PageReturn.RUSH
            val at = if (hasToyOrFood) nowMs else returnAtMs
            if (at == 0L) return PageReturn.SCHEDULE
            if (nowMs < at || !canComeBack || sleeping) return PageReturn.WAIT
            return PageReturn.COME_BACK
        }
    }
}
