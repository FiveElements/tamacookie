package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Modificateurs de caractère : exhaustivité et valeurs de l'ancien `p(k, yes)` de `PetState`. */
class PersonalityTest {

    /** Table attendue (caractère → modificateur → facteur), 1 partout ailleurs. */
    private val expected = mapOf(
        Personality.PLAYFUL to mapOf(StatMod.HAPPINESS_LOSS to 1.25f, StatMod.PLAY_JOY to 1.5f),
        Personality.GLUTTON to mapOf(StatMod.SATIETY_LOSS to 1.35f, StatMod.FOOD_JOY to 2f),
        Personality.LAZY to mapOf(StatMod.ENERGY_LOSS to 1.3f),
        Personality.CURIOUS to mapOf(StatMod.APP_VISIT_JOY to 3f),
        Personality.CUDDLY to mapOf(StatMod.HAPPINESS_LOSS to 1.2f, StatMod.PET_JOY to 1.6f),
        // évolution 4b
        Personality.SHY to mapOf(StatMod.HAPPINESS_LOSS to 1.15f, StatMod.PET_JOY to 1.2f),
        Personality.PRANKSTER to mapOf(StatMod.HAPPINESS_LOSS to 1.1f, StatMod.PLAY_JOY to 1.2f),
        Personality.EXPLORER to mapOf(StatMod.ENERGY_LOSS to 1.15f, StatMod.APP_VISIT_JOY to 2f),
    )

    @Test
    fun `chaque caractere a sa table et chaque modificateur une valeur`() {
        assertEquals(Personality.entries.toSet(), expected.keys)
        for (p in Personality.entries) {
            assertTrue(p.label.isNotBlank() && p.emoji.isNotBlank() && p.desc.isNotBlank())
            for (m in StatMod.entries) {
                val want = expected.getValue(p)[m] ?: 1f
                assertEquals("$p.$m", want, p.mod(m))
                assertTrue("$p.$m > 0", p.mod(m) > 0f)
            }
        }
    }

    @Test
    fun `chaque modificateur sert a au moins un caractere`() {
        for (m in StatMod.entries) assertTrue("$m", Personality.entries.any { it.mod(m) != 1f })
    }

    @Test
    fun `noms sauvegardes inchanges`() {
        // les nouveaux caractères sont ajoutés à la fin : les ordinaux (et les graines des traces) ne bougent pas
        assertEquals(listOf("PLAYFUL", "GLUTTON", "LAZY", "CURIOUS", "CUDDLY", "SHY", "PRANKSTER", "EXPLORER"),
            Personality.entries.map { it.name })
    }
}
