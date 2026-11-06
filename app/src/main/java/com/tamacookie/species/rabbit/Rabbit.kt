package com.tamacookie.species.rabbit

import com.tamacookie.Personality
import com.tamacookie.PetRenderer
import com.tamacookie.species.BehaviorMods
import com.tamacookie.species.Species
import com.tamacookie.species.SpeciesLines
import com.tamacookie.species.SpeciesSpec
import java.util.EnumMap

/**
 * Le lapin : première espèce (et celle de toutes les sauvegardes d'avant l'étape 7).
 * Gabarit et vitesses (ex-`Tuning.BUNNY_*` / `WALK_*`…), influence du caractère (ex-littéraux de
 * `BunnyBrain`, `ToyController`, `StationController`, du service), dessin ([RabbitRenderer]) et
 * répliques ([RabbitLines]). Il accepte toutes les stations et tous les jouets.
 */
object Rabbit : Species {
    const val ID = "rabbit"

    override val id: String get() = ID
    override val label: String get() = "Lapin"
    override val emoji: String get() = "🐰"

    override val spec = SpeciesSpec(
        windowWDp = 130f, windowHDp = 140f, feetPadDp = 6f,
        walkSlowDp = 45f, walkNormalDp = 75f, walkHappyDp = 95f,
        travelOutDp = 260f, travelInDp = 200f, pageOutDp = 150f, hideRunDp = 300f,
        headTopDp = 112f, headTopSleepingDp = 70f,
    )

    override val lines: SpeciesLines get() = RabbitLines

    private val mods: Map<Personality, BehaviorMods> by lazy {
        EnumMap<Personality, BehaviorMods>(Personality::class.java).apply {
            for (p in Personality.entries) put(p, modsFor(p))
        }
    }

    private fun modsFor(p: Personality): BehaviorMods = when (p) {
        Personality.PLAYFUL -> BehaviorMods(playSeconds = 50f)
        Personality.GLUTTON -> BehaviorMods(chestGreedChance = 0.6f)
        Personality.LAZY -> BehaviorMods(
            walkSpeedFactor = 0.7f, sleepEnergyBelow = 30f, nightSleepEnergyBelow = 75f, napEnergyBelow = 60f,
        )
        Personality.CURIOUS -> BehaviorMods(pageWanderChance = 0.12f, platformChance = 0.6f)
        Personality.CUDDLY -> BehaviorMods(attentionFactor = 0.6f, socialChance = 0.4f)
        // Peureux : sursaute, se cache deux fois plus, va peu vers les autres, saute moins, réclame plus tard
        Personality.SHY -> BehaviorMods(
            attentionFactor = 1.3f, socialChance = 0.1f, platformChance = 0.25f,
            hideChanceFactor = 2f, startleChance = 0.35f,
        )
        // Farceur : réclame deux fois plus tôt et chaque bêtise de la cascade est plus probable
        Personality.PRANKSTER -> BehaviorMods(attentionFactor = 0.5f, mischiefChanceFactor = 1.5f)
        // Explorateur : pages voisines, icônes, voyages deux fois plus rapprochés
        Personality.EXPLORER -> BehaviorMods(
            pageWanderChance = 0.15f, platformChance = 0.7f, travelCooldownFactor = 0.5f,
        )
    }

    override fun behavior(p: Personality): BehaviorMods = mods.getValue(p)

    override fun renderer(density: Float): PetRenderer = RabbitRenderer(density)
}
