package com.tamacookie.feature.mischief

import com.tamacookie.Box
import com.tamacookie.Act
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.feature.Travel
import kotlin.random.Random

/**
 * Une bêtise : il cherche l'attention ou taquine l'utilisateur (toc-toc, s'asseoir sur un bouton,
 * cache-cache…). Un `object` par bêtise, dans son fichier, enregistré dans [Mischiefs.ALL] ;
 * aucun état (il est dans [MischiefState]). Les activités qu'elle pilote ([acts]) sont confiées à la
 * boucle et au toucher par `ActRegistry` ; [travel] et [slowFrames] sont lus par le cadre.
 */
interface Mischief {
    /** Activités qu'elle pilote (enregistrées dans `ActRegistry` par le contrôleur). Peut être vide. */
    val acts: Set<Act> get() = emptySet()
    val travel: Travel get() = Travel.BUSY
    val slowFrames: Boolean get() = false

    /**
     * Quand il cherche l'attention : probabilité d'être retenue si on arrive jusqu'à elle dans la
     * cascade ([Mischiefs.seek]) ; 0 = jamais de cette façon, 1 = sans tirage.
     */
    fun chance(level: Int): Float = 0f

    /** Possible maintenant (écran, page, capteurs) ? Ne consomme pas d'aléatoire. */
    fun canStart(ctx: MischiefCtx, pet: Pet): Boolean = true

    /** Il la commence (elle vient d'être tirée) ; false = finalement impossible, on passe à la suivante. */
    fun start(ctx: MischiefCtx, pet: Pet): Boolean = false

    /** Réaction à ce que fait l'utilisateur ; true = elle a réagi (les suivantes ne sont pas consultées). */
    fun react(ctx: MischiefCtx, pet: Pet, ev: UserEvents): Boolean = false

    /** Chaque image pendant une de ses [acts]. */
    fun update(ctx: MischiefCtx, pet: Pet, dt: Float) {}

    /** Caresse pendant une de ses [acts] ; true = traitée (sinon caresse ordinaire). */
    fun onPet(ctx: MischiefCtx, pet: Pet): Boolean = false
}

/** Ce que le contrôleur des bêtises offre aux bêtises, en plus de [PetCtx]. */
interface MischiefCtx : PetCtx {
    /** 0 sage, 1 taquin, 2 petit monstre. */
    val level: Int
    /** Pas de nouvelle bêtise avant cette heure (murale, ms). */
    var cooldownUntil: Long
    /** État des bêtises en cours (un seul animal pour l'instant ; à porter par le `Pet` à l'étape 7). */
    val state: MischiefState
}

/** État des bêtises en cours : s'asseoir sur un bouton, toc-toc, cache-cache. */
class MischiefState {
    // S'asseoir sur un bouton / une icône / le clavier
    var squatLimit = 20f
    var squatRect = Box(0f, 0f, 0f, 0f)
    var squatKbd = false
    var squatVersion = 0

    // Toc-toc
    var knockDone = false

    // Cache-cache
    /** Vue où il s'est caché (null = pas de cache-cache). */
    var hidingView: String? = null
    var hideStarted = 0L
    var giggleIn = 5f
}

/** Ce que l'utilisateur vient de faire (signaux du port `ScreenSensor`, lus par le contrôleur). */
class UserEvents {
    /** Le bouton qu'il vient de toucher (null = pas de clic). */
    var click: Box? = null
    var scroll = false
    /** Frappes au clavier accumulées depuis la dernière réaction à la frappe. */
    var typing = 0
    /** Heure murale (ms). */
    var now = 0L

    /** Assez de frappe pour réagir ([TYPING_BURST] touches) : le compte repart de zéro. */
    fun takeTypingBurst(): Boolean {
        if (typing < TYPING_BURST) return false
        typing = 0
        return true
    }

    companion object {
        const val TYPING_BURST = 12
    }
}

/** Les bêtises enregistrées, et le choix de la bêtise. */
object Mischiefs {
    /** Dans l'ordre de la cascade (attention) et des réactions (clic, frappe, défilement). */
    val ALL: List<Mischief> = listOf(HideSeekMischief, SquatMischief, KnockMischief, ScrollDizzy)

    /**
     * Cascade sur [list] : chaque bêtise ([Mischief.chance] > 0) possible ([possible]), dans l'ordre, est
     * tirée avec sa probabilité (≥ 1 : sans tirage) ; la première tirée qui démarre ([start]) est renvoyée.
     * Une probabilité par bêtise plutôt que des poids normalisés : c'est la cascade d'origine, à l'identique.
     */
    fun seek(
        list: List<Mischief>, level: Int, rnd: Random,
        possible: (Mischief) -> Boolean, start: (Mischief) -> Boolean,
    ): Mischief? {
        for (m in list) {
            val c = m.chance(level)
            if (c <= 0f || !possible(m)) continue
            if (c < 1f && rnd.nextFloat() >= c) continue
            if (start(m)) return m
        }
        return null
    }

    /** Première bêtise de [list] qui réagit ([react]) à ce que vient de faire l'utilisateur (null = aucune). */
    fun react(list: List<Mischief>, react: (Mischief) -> Boolean): Mischief? {
        for (m in list) if (react(m)) return m
        return null
    }
}
