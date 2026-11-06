package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Test

/** Seuils de comportement sortis des littéraux (valeurs d'avant l'étape 5). */
class GameRulesTest {
    @Test
    fun `seuils de comportement inchanges`() {
        val r = GameRules.DEFAULT
        assertEquals(
            listOf(45f, 20f, 20f, 75f, 8f, 35f, 92f, 90f, 60f, 25f, 60f, 40f),
            listOf(
                r.looksDirtyBelow, r.looksVeryDirtyBelow, r.walkSlowEnergyBelow, r.walkHappyAbove,
                r.playStopEnergyBelow, r.plushNapEnergyBelow, r.notTiredAbove, r.healthyAbove,
                r.chestFoodSatietyBelow, r.chestFoodEnergyBelow, r.chestLettuceHealthBelow, r.chestTreatJoyBelow,
            ),
        )
        assertEquals(3, r.dirtyPoops)
        assertEquals(0f to 100f, r.min to r.max)
    }

    @Test
    fun `des regles modifiees changent le resultat - un seul endroit`() {
        val strict = GameRules.DEFAULT.copy(max = 50f, satietyLossPerH = 0f)
        val r = PetRules.apply(PetStats(satiety = 80f), PetEvent.Elapsed(1f), 0L, Traits(), strict)
        assertEquals(50f, r.stats.satiety)                       // borne prise dans les règles passées
        assertEquals(50f, r.stats.health)
    }
}
