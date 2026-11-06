package com.tamacookie

import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.Pose
import com.tamacookie.feature.toy.Food
import kotlin.random.Random

/** Identifiant stable d'un animal (clé du monde, et plus tard de la persistance et des échanges). */
@JvmInline
value class PetId(val value: String) {
    companion object {
        /**
         * L'animal résident : dérivé de sa date de naissance (déjà sauvegardée), donc stable d'un
         * lancement à l'autre sans nouvelle clé de préférences. Un vrai UUID persistant viendra à l'étape 5.
         */
        fun resident(bornAt: Long) = PetId("resident-$bornAt")
    }
}

/** D'où vient l'animal. Seul le résident existe pour l'instant (les visiteurs viendront avec l'échange). */
sealed interface Origin {
    data object Resident : Origin
}

/** Qui est l'animal. Le nom reste stocké dans [PetState] (scindé à l'étape 5) : on le lit au travers de [nameOf]. */
class Identity(val id: PetId, val origin: Origin = Origin.Resident, private val nameOf: () -> String) {
    val name: String get() = nameOf()
}

/**
 * Corps : position (dans [phys]), activité courante, posture et orientation.
 * Les pieds sont en ([fx], [fy]), dans les coordonnées de sa page (voir [PageTracker.bunnyShift]).
 * [pose] et [facing] sont lus par [BunnyView] (qui ne les possède plus).
 */
class Body(val phys: BunnyPhysics = BunnyPhysics()) {
    var fx: Float
        get() = phys.x
        set(v) { phys.x = v }
    var fy: Float
        get() = phys.y
        set(v) { phys.y = v }

    /** Activité courante : ne change que par [setAct] / [setActOrAfterAir]. */
    var act = Act.IDLE
        private set
    /** Temps passé dans l'activité courante (s). */
    var actTime = 0f
    /** Durée prévue de l'activité courante (s), interprétée par l'activité elle-même. */
    var actDuration = 3f
        private set
    /** Cible horizontale (marche, voyage, cache-cache…). */
    var targetX = 0f
    /** Activité reprise à l'atterrissage. */
    var afterAir = Act.IDLE

    var pose = Pose.IDLE
    /** 1 = vers la droite, -1 = vers la gauche. */
    var facing = 1f
    /** 0..1 : cycle de sautillement pendant la marche. */
    var walkPhase = 0f
    /** 0..1 : avancement du repas en cours (`Act.EAT`). */
    var eatProgress = 0f
    /** Ce qu'il mange (ou a mangé en dernier) : ce qu'il a dans la bouche. */
    var eatingFood = Food.CARROT

    /** Icône / bouton sous ses pattes, null = le sol. */
    var platform: Box? = null
    var platformLabel: String? = null
    /** Version de [Platforms] déjà vérifiée pour la plateforme courante. */
    var platVersionSeen = -1

    fun setAct(a: Act, duration: Float = 3f) {
        act = a; actTime = 0f; actDuration = duration
    }

    /** Comme [setAct], mais en plein vol l'activité est seulement programmée pour l'atterrissage. */
    fun setActOrAfterAir(a: Act, duration: Float = 3f) {
        if (act == Act.AIR) afterAir = a else setAct(a, duration)
    }
}

/** Esprit : cerveau (décisions), son aléatoire, expression forcée et petits minuteurs de comportement. */
class Mind(val rnd: Random) {
    val brain = BunnyBrain(rnd)

    var exprOverride: Expr? = null
    var exprTimer = 0f

    /** Prochain bavardage spontané (s). */
    var chatterIn = 25f
    /** Durée de la partie de jeu en cours (s). */
    var playTime = 0f
    /** Délai avant un nouveau coup de patte / de tête dans un jouet (s). */
    var kickCooldown = 0f
    var danceTime = 0f
    var danceHopIn = 0f
    /** Voyage d'app en app : réplique à l'arrivée et activité ensuite. */
    var travelComment: String? = null
    var travelThen = Act.IDLE
    /** Dernier voyage (heure murale, ms). */
    var lastTravelAt = 0L

    fun express(e: Expr, seconds: Float) { exprOverride = e; exprTimer = seconds }

    /** Fait avancer les minuteurs propres à l'esprit. */
    fun tick(dt: Float) {
        if (exprTimer > 0f) { exprTimer -= dt; if (exprTimer <= 0f) exprOverride = null }
        if (kickCooldown > 0f) kickCooldown -= dt
    }
}

/**
 * Un animal : une entité faite de composants. Jamais de singleton « le lapin » :
 * toute logique qui concerne un animal reçoit son [Pet].
 *
 * [stats] (jauges, personnalité, apparence, persistance) sera scindé à l'étape 5.
 */
class Pet(val identity: Identity, val stats: PetState, val body: Body, val mind: Mind) {
    val id: PetId get() = identity.id
    val look: Look get() = stats.look

    val act: Act get() = body.act
    /** En train de jouer à cache-cache (course, caché, ou tête qui dépasse). */
    val isHiding: Boolean get() = body.act in Act.HIDING

    fun setAct(a: Act, duration: Float = 3f) = body.setAct(a, duration)
    fun setActOrAfterAir(a: Act, duration: Float = 3f) = body.setActOrAfterAir(a, duration)
    fun express(e: Expr, seconds: Float) = mind.express(e, seconds)

    companion object {
        /** L'animal résident, à partir de son état chargé. */
        fun resident(stats: PetState, phys: BunnyPhysics, rnd: Random): Pet =
            Pet(Identity(PetId.resident(stats.bornAt), Origin.Resident) { stats.name }, stats, Body(phys), Mind(rnd))
    }
}
