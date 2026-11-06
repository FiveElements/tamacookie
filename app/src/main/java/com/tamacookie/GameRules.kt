package com.tamacookie

import com.tamacookie.feature.toy.Food

/**
 * Les nombres du jeu (jauges) : taux par heure, gains, coûts et seuils d'humeur. Kotlin pur.
 * [PetRules] les applique ; les modificateurs de caractère sont portés par [Personality.modifiers].
 * Toutes les jauges vont de [min] (mauvais) à [max] (parfait).
 */
data class GameRules(
    val min: Float = 0f,
    val max: Float = 100f,

    // --- Temps qui passe (par heure) ---
    /** Rattrapage maximal d'un coup (hors-ligne, écran éteint). */
    val maxElapsedHours: Float = 48f,
    /** En dormant, faim et ennui avancent moins vite. */
    val sleepSlowdown: Float = 0.4f,
    val satietyLossPerH: Float = 7f,
    val happinessLossPerH: Float = 4f,
    /** Perte de bonheur par heure quand il y a au moins une crotte. */
    val happinessLossWithPoopPerH: Float = 6f,
    /** Enfermé dans la cage : son bonheur chute ce nombre de fois plus vite. */
    val cagedHappinessFactor: Float = 3f,
    val cleanlinessLossPerH: Float = 2.5f,
    val cleanlinessLossPerPoopPerH: Float = 2f,
    val energyLossPerH: Float = 6f,
    val energyGainAsleepPerH: Float = 25f,
    /** Une jauge (satiété, propreté, bonheur) sous ce seuil = négligé. */
    val neglectBelow: Float = 10f,
    val healthLossPerNeglectPerH: Float = 5f,
    val healthGainPerH: Float = 3f,
    /** Environ une crotte toutes les [poopEveryHours] heures… */
    val poopEveryHours: Float = 3f,
    /** … s'il ne dort pas et a mangé (satiété au-dessus de ce seuil). */
    val poopAboveSatiety: Float = 30f,
    val maxPoops: Int = 5,

    // --- Interactions ---
    /** Repu au-dessus : il refuse à manger, sauf [alwaysWelcome]. */
    val fullAbove: Float = 95f,
    val alwaysWelcome: Set<Food> = setOf(Food.STRAWBERRY, Food.TREAT),
    val eatingCleanlinessCost: Float = 3f,
    /** Caresses répétées à moins de [petCooldownMs] : elles rapportent moins. */
    val petCooldownMs: Long = 4000L,
    val petJoy: Float = 6f,
    val petJoyRepeated: Float = 1.5f,
    /** Trop fatigué pour jouer en dessous. */
    val playMinEnergy: Float = 15f,
    val playStartJoy: Float = 6f,
    val playStartEnergyCost: Float = 4f,
    val playHitEnergyCost: Float = 0.6f,
    val playHitSatietyCost: Float = 0.25f,
    val favoriteToyFactor: Float = 2f,
    val bathJoy: Float = 3f,
    val poopRemovedCleanliness: Float = 8f,
    val poopRemovedJoy: Float = 2f,
    val healHealth: Float = 40f,
    /** Il n'aime pas les médicaments. */
    val healJoyCost: Float = 5f,
    val appVisitJoy: Float = 1f,
    val hardLandingJoyCost: Float = 4f,
    /** Réveillé de force (caresse, glisser). */
    val wokenJoyCost: Float = 3f,
    /** Trouvé à cache-cache. */
    val foundJoy: Float = 15f,
    /** Personne ne l'a trouvé : il abandonne. */
    val notFoundJoyCost: Float = 3f,

    // --- Stations de l'évolution 4a ---
    /** Endormi dans le lit : énergie regagnée par heure **en plus** de [energyGainAsleepPerH]. */
    val bedEnergyGainPerH: Float = 25f,
    /** Un tour de roue : il se fatigue, ça le rend heureux (×[StatMod.PLAY_JOY]), ça creuse un peu. */
    val wheelEnergyCost: Float = 10f,
    val wheelJoy: Float = 10f,
    val wheelSatietyCost: Float = 3f,
    /** Le jardin : petite joie, quelques brins d'herbe, un peu de terre sur le pelage. */
    val gardenJoy: Float = 5f,
    val gardenSatiety: Float = 3f,
    val gardenCleanlinessCost: Float = 4f,
    /** La gamelle : il y mange s'il a faim (satiété sous ce seuil), sinon il renifle et ressort. */
    val bowlHungryBelow: Float = 70f,
    /** Brossé (menu 🪥) : propreté, et un peu de joie (×[StatMod.PET_JOY], comme une caresse). */
    val brushCleanliness: Float = 8f,
    val brushJoy: Float = 3f,

    // --- Humeur (ordre de priorité dans [PetRules.mood]) ---
    val sickBelow: Float = 35f,
    val hungryBelow: Float = 20f,
    val sleepyBelow: Float = 15f,
    val dirtyBelow: Float = 25f,
    val dirtyPoops: Int = 3,
    val sadBelow: Float = 30f,
    val happyAbove: Float = 75f,
    val happySatietyAbove: Float = 50f,

    // --- Comportement et affichage selon les jauges (lus par les fonctionnalités, pas par PetRules) ---
    /** Il a l'air sale (taches) sous ce seuil de propreté, ou avec [dirtyPoops] crottes. */
    val looksDirtyBelow: Float = 45f,
    /** Très sale (mouches). */
    val looksVeryDirtyBelow: Float = 20f,
    /** Il marche lentement s'il est malade ou sous cette énergie… */
    val walkSlowEnergyBelow: Float = 20f,
    /** … vite s'il est heureux au-dessus de ce bonheur. */
    val walkHappyAbove: Float = 75f,
    /** La partie s'arrête sous cette énergie. */
    val playStopEnergyBelow: Float = 8f,
    /** Le câlin au doudou finit en sieste sous cette énergie. */
    val plushNapEnergyBelow: Float = 35f,
    /** « Pas fatigué » : il refuse d'aller dormir au-dessus. */
    val notTiredAbove: Float = 92f,
    /** Le véto le trouve en pleine forme (pas de soin) à partir de cette santé. */
    val healthyAbove: Float = 90f,
    /** Le coffre lui donne à manger sous cette satiété ou sous [chestFoodEnergyBelow]… */
    val chestFoodSatietyBelow: Float = 60f,
    val chestFoodEnergyBelow: Float = 25f,
    /** … de la salade si sa santé est sous ce seuil, une fraise si son bonheur est sous [chestTreatJoyBelow]. */
    val chestLettuceHealthBelow: Float = 60f,
    val chestTreatJoyBelow: Float = 40f,
) {
    companion object {
        val DEFAULT = GameRules()
    }
}

/** Ce que le caractère d'un animal module dans les règles (facteur multiplicatif, 1 = sans effet). */
enum class StatMod {
    /** Perte de satiété avec le temps. */
    SATIETY_LOSS,
    /** Perte de bonheur avec le temps. */
    HAPPINESS_LOSS,
    /** Perte d'énergie éveillé. */
    ENERGY_LOSS,
    /** Bonheur apporté par la nourriture. */
    FOOD_JOY,
    /** Bonheur apporté par chaque coup de jeu. */
    PLAY_JOY,
    /** Bonheur apporté par une caresse. */
    PET_JOY,
    /** Bonheur apporté par la visite d'une nouvelle application. */
    APP_VISIT_JOY,
}
