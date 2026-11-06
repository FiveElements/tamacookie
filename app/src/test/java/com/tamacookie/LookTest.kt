package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Apparence par identifiants stables : ces ids sont sauvegardés et échangés, ils ne doivent jamais changer. */
class LookTest {

    @Test
    fun `identifiants figes`() {
        assertEquals(listOf("snow", "cream", "caramel", "chocolate", "grey", "licorice", "pink"), Fur.entries.map { it.id })
        assertEquals(listOf("up", "lop", "one_folded"), Ears.entries.map { it.id })
        assertEquals(listOf("hazel", "blue", "green", "ruby"), EyeColor.entries.map { it.id })
        assertEquals(listOf("plain", "eye_patch", "dutch"), Pattern.entries.map { it.id })
        assertEquals(listOf("none", "bow", "hat", "glasses", "scarf", "flowers"), Accessory.entries.map { it.id })
        assertEquals(listOf("pink", "blue", "yellow", "violet", "red", "green"), AccColor.entries.map { it.id })
    }

    @Test
    fun `ids uniques, couleurs valides, libelles presents`() {
        val all: List<List<LookOption>> = listOf(Fur.entries, Ears.entries, EyeColor.entries, Pattern.entries, Accessory.entries, AccColor.entries)
        for (opts in all) {
            assertEquals(opts.size, opts.map { it.id }.toSet().size)
            for (o in opts) {
                assertTrue(o.id.matches(Regex("[a-z_]+")))
                assertTrue(o.label.isNotBlank())
                if (o is ColorOption) assertTrue(o.hex, o.hex.matches(Regex("#[0-9A-F]{6}")))
            }
        }
    }

    @Test
    fun `index de l'ancien schema vers ids - meme ordre que les anciennes listes`() {
        assertEquals(Look(), Look.fromLegacyIndexes(0, 0, 0, 0, 0, 0))
        assertEquals(
            Look(Fur.PINK, Ears.ONE_FOLDED, EyeColor.RUBY, Pattern.DUTCH, Accessory.FLOWERS, AccColor.GREEN),
            Look.fromLegacyIndexes(6, 2, 3, 2, 5, 5),
        )
        // Anciennes listes Options (libellés dans l'ordre des index)
        assertEquals(listOf("Neige", "Crème", "Caramel", "Chocolat", "Gris", "Réglisse", "Rose"), Fur.entries.map { it.label })
        assertEquals(listOf("Aucun", "Nœud", "Chapeau", "Lunettes", "Écharpe", "Fleurs"), Accessory.entries.map { it.label })
    }

    @Test
    fun `id inconnu - valeur par defaut`() {
        assertEquals(Fur.CARAMEL, Look.byId(Fur.entries, "caramel", Fur.SNOW))
        assertEquals(Fur.SNOW, Look.byId(Fur.entries, "licorne", Fur.SNOW))
        assertEquals(Fur.SNOW, Look.byId(Fur.entries, null, Fur.SNOW))
    }
}
