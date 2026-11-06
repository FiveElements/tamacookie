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
        Personality.CUDDLY -> BehaviorMods(attentionFactor = 0.6f)
    }

    override fun behavior(p: Personality): BehaviorMods = mods.getValue(p)

    override fun renderer(density: Float): PetRenderer = RabbitRenderer(density)
}
