package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Apparence par identifiants stables : ces ids sont sauvegardés et échangés, ils ne doivent jamais changer. */
class LookTest {

    @Test
    fun `identifiants figes`() {
        assertEquals(listOf("snow", "cream", "caramel", "chocolate", "grey", "licorice", "pink",
            "ginger", "spotted_cream", "blue_grey"), Fur.entries.map { it.id })
        assertEquals(listOf("up", "lop", "one_folded"), Ears.entries.map { it.id })
        assertEquals(listOf("hazel", "blue", "green", "ruby"), EyeColor.entries.map { it.id })
        assertEquals(listOf("plain", "eye_patch", "dutch"), Pattern.entries.map { it.id })
        assertEquals(listOf("none", "bow", "hat", "glasses", "scarf", "flowers",
            "crown", "bandana", "bow_tie"), Accessory.entries.map { it.id })
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
        assertEquals(listOf("Neige", "Crème", "Caramel", "Chocolat", "Gris", "Réglisse", "Rose"),
            Fur.entries.take(Fur.LEGACY_COUNT).map { it.label })
        assertEquals(listOf("Aucun", "Nœud", "Chapeau", "Lunettes", "Écharpe", "Fleurs"),
            Accessory.entries.take(Accessory.LEGACY_COUNT).map { it.label })
        // hors bornes : comme avant l'évolution 4b (dernier pelage d'alors, pas d'accessoire), jamais une option ajoutée
        assertEquals(Look(Fur.PINK, accessory = Accessory.NONE), Look.fromLegacyIndexes(9, 0, 0, 0, 7, 0))
        assertEquals(Look(Fur.PINK), Look.fromLegacyIndexes(99, 0, 0, 0, -1, 0))
    }

    @Test
    fun `evolution 4b - nouvelles options a la fin, relues par leur id`() {
        assertEquals(listOf(Fur.GINGER, Fur.SPOTTED_CREAM, Fur.BLUE_GREY), Fur.entries.drop(Fur.LEGACY_COUNT))
        assertEquals(listOf(Accessory.CROWN, Accessory.BANDANA, Accessory.BOW_TIE), Accessory.entries.drop(Accessory.LEGACY_COUNT))
        assertEquals(Fur.SPOTTED_CREAM, Look.byId(Fur.entries, "spotted_cream", Fur.SNOW))
        assertEquals(Accessory.BOW_TIE, Look.byId(Accessory.entries, "bow_tie", Accessory.NONE))
        assertEquals(listOf(Fur.SPOTTED_CREAM), Fur.entries.filter { it.spotted })
    }

    @Test
    fun `id inconnu - valeur par defaut`() {
        assertEquals(Fur.CARAMEL, Look.byId(Fur.entries, "caramel", Fur.SNOW))
        assertEquals(Fur.SNOW, Look.byId(Fur.entries, "licorne", Fur.SNOW))
        assertEquals(Fur.SNOW, Look.byId(Fur.entries, null, Fur.SNOW))
    }

    @Test
    fun `couleur ARGB opaque calculee en Kotlin pur`() {
        assertEquals(0xFFFF6F9C.toInt(), AccColor.PINK.argb)
        assertEquals(0xFF3E363B.toInt(), Fur.LICORICE.argb)
        assertEquals(0xFFFFFBF5.toInt(), Fur.SNOW.argb)
    }
}
