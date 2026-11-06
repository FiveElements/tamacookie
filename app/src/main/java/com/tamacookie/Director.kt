package com.tamacookie

import com.tamacookie.feature.mischief.Mischiefs
import com.tamacookie.feature.station.StationKind
import java.util.EnumSet
import kotlin.math.abs

/** Importance d'une réplique quand plusieurs animaux veulent parler ([Director.BubbleQueue]). */
enum class SpeechPriority {
    /** Bavardage, échanges entre animaux : abandonné s'il doit trop attendre. */
    LOW,
    /** Ce que fait l'animal (repas, chute, voyage…). */
    NORMAL,
    /** Réponse à l'utilisateur (toucher, menu, commande) : passe devant. */
    HIGH,
}

/**
 * L'arbitre global quand plusieurs animaux vivent ensemble (Kotlin pur, testé en JVM) : ce qui ne peut
 * arriver qu'**une fois à la fois** sur l'écran.
 *
 * - **Bêtises** : un seul animal en fait une ([mayStartMischief], [mischiefOwner]) ;
 * - **coffre** : un seul animal va s'y servir ([mayUseChest]) ; les stations de capacité 1 sont gardées
 *   par l'occupation du monde (`Occupancy.canEnter`) ;
 * - **nourriture** : quand deux animaux courent vers la même, le plus proche l'emporte ([foodWinner]),
 *   l'autre renonce à celle-ci ([mayChaseFood]) ;
 * - **bulles** : une seule réplique à l'écran à la fois, les autres attendent par priorité ([bubbles]) ;
 * - **notifications** : un titre, une ligne et des alertes qui résument tous les animaux ([title], [alerts]).
 *
 * Tout est **déduit du monde** (activités, occupation) plutôt que réservé : rien à libérer, rien qui fuie
 * quand un animal part. Avec un seul animal, chaque question répond « oui » sans tirage : comportement
 * identique à avant.
 */
class Director(private val world: World) {

    // =====================================================================
    //  Bêtises : une à la fois
    // =====================================================================
    /** L'animal en pleine bêtise (squat, toc-toc, cache-cache…), null = aucun. */
    fun mischiefOwner(): Pet? {
        for (p in world.pets.values) if (inMischief(p)) return p
        return null
    }

    /** [p] peut commencer une bêtise : aucun autre animal n'en fait une. */
    fun mayStartMischief(p: Pet): Boolean {
        for (q in world.pets.values) if (q !== p && inMischief(q)) return false
        return true
    }

    private fun inMischief(p: Pet) = p.act in MISCHIEF_ACTS || p.mind.mischief.hidingView != null

    // =====================================================================
    //  Coffre : un seul animal à la fois
    // =====================================================================
    /** [p] peut aller se servir au coffre : aucun autre n'y va ni n'y est. */
    fun mayUseChest(p: Pet): Boolean {
        val chest = world.station(StationKind.CHEST)
        for (q in world.pets.values) {
            if (q === p) continue
            if (q.act == Act.GOTO_CHEST || chest.has(q.id)) return false
        }
        return true
    }

    // =====================================================================
    //  Nourriture : le plus proche l'emporte
    // =====================================================================
    /** Animal → nourriture ([Prop.id]) qu'il a perdue au profit d'un autre (il n'y retourne pas). */
    private val lostFood = HashMap<PetId, Int>()

    /**
     * Parmi les animaux qui courent vers [food] (`GOTO_FOOD`), celui qui l'aura : le plus proche ; à égalité,
     * le plus affamé ; puis l'ordre du monde (le résident d'abord).
     */
    fun foodWinner(food: Prop): Pet? {
        var best: Pet? = null
        for (p in world.pets.values) {
            if (p.act != Act.GOTO_FOOD || lostFood[p.id] == food.id) continue
            val b = best
            if (b == null) { best = p; continue }
            val dp = abs(p.body.fx - food.x); val db = abs(b.body.fx - food.x)
            if (dp < db || (dp == db && p.stats.satiety < b.stats.satiety)) best = p
        }
        return best
    }

    /** Un différend tranché : [loser] a perdu la nourriture au profit de [winner] (mis en scène par `SocialSystem`). */
    data class Dispute(val loser: PetId, val winner: PetId)
    private val disputes = ArrayList<Dispute>()

    /**
     * [p] a perdu [food] au profit de [winner] : il n'y retourne pas (jusqu'à la prochaine nourriture), et le
     * différend est noté pour [takeDisputes].
     */
    fun loseFood(p: Pet, food: Prop, winner: Pet? = null) {
        lostFood[p.id] = food.id
        if (winner != null) disputes += Dispute(p.id, winner.id)
    }

    /** Les différends tranchés depuis le dernier appel (la file est vidée). */
    fun takeDisputes(): List<Dispute> {
        if (disputes.isEmpty()) return emptyList()
        return disputes.toList().also { disputes.clear() }
    }

    /** [p] peut courir vers la nourriture posée (il ne l'a pas déjà perdue). */
    fun mayChaseFood(p: Pet): Boolean {
        val lost = lostFood[p.id] ?: return true
        for (prop in world.props) if (prop.id == lost && prop.food != null) return false
        lostFood.remove(p.id)            // cette nourriture-là a disparu : il peut courir vers la suivante
        return true
    }

    // =====================================================================
    //  Bulles : une à la fois, file par priorité
    // =====================================================================
    val bubbles = BubbleQueue()

    /**
     * Une seule bulle « qui parle » à la fois. Un animal remplace toujours sa propre bulle (un seul animal :
     * comportement d'avant) ; un autre passe devant s'il est plus prioritaire, sinon il attend son tour
     * (au plus [MAX_QUEUE], les répliques [SpeechPriority.LOW] trop anciennes sont abandonnées).
     */
    class BubbleQueue {
        data class Line(val pet: PetId, val text: String, val seconds: Float, val priority: SpeechPriority) {
            internal var waited = 0f
        }

        /** Réplique à l'écran (null = personne ne parle). */
        var current: Line? = null
            private set
        private var remaining = 0f
        private val queue = ArrayList<Line>()
        val waiting: List<Line> get() = queue

        /** Demande de parler : true = à afficher maintenant (sinon en attente ou abandonnée). */
        fun request(l: Line): Boolean {
            val c = current
            if (c == null || remaining <= 0f || c.pet == l.pet || l.priority > c.priority) {
                show(l); return true
            }
            queue.removeAll { it.pet == l.pet }            // une seule réplique en attente par animal
            if (queue.size >= MAX_QUEUE) {
                val worst = queue.minByOrNull { it.priority } ?: return false
                if (worst.priority >= l.priority) return false
                queue.remove(worst)
            }
            queue += l
            return false
        }

        private fun show(l: Line) {
            current = l; remaining = l.seconds
            queue.removeAll { it.pet == l.pet }
        }

        /** Fait vieillir la bulle courante ; renvoie la réplique suivante à afficher (null = rien). */
        fun tick(dt: Float): Line? {
            for (l in queue) l.waited += dt
            queue.removeAll { it.priority == SpeechPriority.LOW && it.waited > LOW_MAX_WAIT_S }
            if (current == null) return null
            remaining -= dt
            if (remaining > 0f) return null
            current = null
            val next = queue.maxWithOrNull(compareBy<Line> { it.priority }.thenByDescending { it.waited }) ?: return null
            show(next)
            return next
        }

        /** L'animal [pet] se tait (fenêtre retirée, parti) : sa bulle et ses répliques en attente sont oubliées. */
        fun forget(pet: PetId) {
            queue.removeAll { it.pet == pet }
            if (current?.pet == pet) { current = null; remaining = 0f }
        }

        companion object {
            const val MAX_QUEUE = 3
            const val LOW_MAX_WAIT_S = 5f
        }
    }

    // =====================================================================
    //  Notifications agrégées
    // =====================================================================
    /** Titre de la notification permanente : le résident, et le nombre de compagnons. */
    fun title(moodText: (Pet) -> String): String {
        val r = world.resident
        val n = world.pets.size - 1
        val base = "${r.name} ${moodText(r)}"
        return if (n <= 0) base else base + " · +$n " + (if (n == 1) "compagnon" else "compagnons")
    }

    /** Ligne de la notification : jauges du résident, puis l'humeur de chaque compagnon. */
    fun statusLine(line: (Pet) -> String): String {
        val r = world.resident
        val others = world.pets.values.filter { it !== r }
        if (others.isEmpty()) return line(r)
        return line(r) + "  ·  " + others.joinToString("  ") { "${it.name} ${MOOD_EMOJI[it.stats.mood]}" }
    }

    private val lastMood = HashMap<PetId, Mood>()

    /**
     * Alertes : l'humeur de chaque animal qui vient de basculer vers un besoin ([text] : null = pas
     * d'alerte), réunies en une seule notification (null = rien de neuf).
     */
    fun alerts(text: (Pet) -> String?): String? {
        val out = ArrayList<String>()
        for (p in world.pets.values) {
            val m = p.stats.mood
            if (lastMood[p.id] == m) continue
            lastMood[p.id] = m
            text(p)?.let { out += it }
        }
        lastMood.keys.retainAll(world.pets.keys)
        return if (out.isEmpty()) null else out.joinToString(" · ")
    }

    companion object {
        /** Activités d'une bêtise (celles de [Mischiefs.ALL]). */
        val MISCHIEF_ACTS: Set<Act> = EnumSet.noneOf(Act::class.java).apply { for (m in Mischiefs.ALL) addAll(m.acts) }

        private val MOOD_EMOJI = mapOf(
            Mood.HAPPY to "😊", Mood.OK to "🙂", Mood.HUNGRY to "🥕", Mood.SLEEPY to "😴",
            Mood.DIRTY to "🫧", Mood.SAD to "🥺", Mood.SICK to "🤒",
        )
    }
}
