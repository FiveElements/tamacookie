package com.tamacookie.species

import com.tamacookie.Personality
import com.tamacookie.PetRenderer
import com.tamacookie.feature.station.StationKind
import com.tamacookie.feature.toy.Toy
import com.tamacookie.species.rabbit.Rabbit

/**
 * Une espèce d'animal (plugin) : ce qui la distingue — dessin ([renderer]), gabarit et vitesses ([spec]),
 * influence du caractère sur le comportement ([behavior]), fonctionnalités acceptées ([accepts]) et
 * répliques ([lines]). Un `object` par espèce dans son sous-package (`species/rabbit/`), enregistré dans
 * [ALL] ; un animal la retrouve par son identifiant sauvegardé ([byId]).
 *
 * Le reste du jeu (jauges, stations, jouets, bêtises, boucle) est commun à toutes les espèces.
 */
interface Species {
    /** Identifiant sauvegardé (`pet.<id>.species`) et échangé (`PetSnapshot`) : ne jamais le changer. */
    val id: String
    val label: String
    val emoji: String
    val spec: SpeciesSpec
    val lines: SpeciesLines

    /** Influence de [p] sur le comportement (hors jauges : celles-ci sont dans `Personality.modifiers`). */
    fun behavior(p: Personality): BehaviorMods

    /** Peut entrer dans la station [k] (faux : le glisser ne la surligne pas, il n'y entre pas). */
    fun accepts(k: StationKind): Boolean = true

    /** Joue avec le jouet [t] (proposé par le coffre, l'adoption). */
    fun accepts(t: Toy): Boolean = true

    /** Dessin (Android) d'un animal de cette espèce ; un par vue. [density] = px par dp. */
    fun renderer(density: Float): PetRenderer

    companion object {
        /** Les espèces connues ; la première est celle par défaut (et celle des anciennes sauvegardes). */
        val ALL: List<Species> by lazy { listOf<Species>(Rabbit) }

        val DEFAULT: Species get() = ALL.first()

        /** L'espèce d'identifiant [id] (null = inconnue : version plus récente, donnée corrompue). */
        fun byId(id: String?): Species? = ALL.firstOrNull { it.id == id }

        /** Comme [byId], mais [DEFAULT] si inconnue. */
        fun of(id: String?): Species = byId(id) ?: DEFAULT
    }
}

/**
 * Gabarit et vitesses d'une espèce (dp, dp/s). La fenêtre d'un animal fait [windowWDp] × [windowHDp] ;
 * ses pieds sont à [feetPadDp] du bas, le haut de sa tête à [headTopDp] au-dessus (endormi : [headTopSleepingDp]).
 */
data class SpeciesSpec(
    val windowWDp: Float,
    val windowHDp: Float,
    val feetPadDp: Float,
    val walkSlowDp: Float,
    val walkNormalDp: Float,
    val walkHappyDp: Float,
    val travelOutDp: Float,
    val travelInDp: Float,
    val pageOutDp: Float,
    val hideRunDp: Float,
    val headTopDp: Float,
    val headTopSleepingDp: Float,
)

/**
 * Influence du caractère sur le **comportement** d'une espèce (les jauges : `Personality.modifiers`).
 * Valeurs par défaut = animal sans trait particulier.
 */
data class BehaviorMods(
    /** Facteur de vitesse de marche. */
    val walkSpeedFactor: Float = 1f,
    /** Durée maximale d'une partie de jeu (s). */
    val playSeconds: Float = 35f,
    /** Facteur du temps d'ignorance avant une bêtise. */
    val attentionFactor: Float = 1f,
    /** Probabilité de partir explorer une page voisine. */
    val pageWanderChance: Float = 0.06f,
    /** Probabilité de sauter sur une icône (au sol). */
    val platformChance: Float = 0.35f,
    /** Il s'endort sous cette énergie. */
    val sleepEnergyBelow: Float = 12f,
    /** La nuit, il peut s'endormir sous cette énergie. */
    val nightSleepEnergyBelow: Float = 55f,
    /** Sieste possible sous cette énergie (0 = jamais), avec la probabilité [napChance]. */
    val napEnergyBelow: Float = 0f,
    val napChance: Float = 0.12f,
    /** Au coffre, probabilité de prendre à manger même sans faim (0 = jamais). */
    val chestGreedChance: Float = 0f,
)
