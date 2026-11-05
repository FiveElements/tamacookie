package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformsTest {

    private fun page(desc: String): Pair<Int, Int>? =
        Platforms.PAGE_RE.find(desc)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    @Test
    fun `indicateur de page Samsung en francais`() {
        assertEquals(1 to 4, page("Page 1 sur 4, Page par défaut"))
        assertEquals(2 to 3, page("Écran d'accueil Page 2 sur 3"))
    }

    @Test
    fun `indicateur de page dans d'autres langues`() {
        assertEquals(3 to 5, page("Page 3 of 5"))
        assertEquals(2 to 4, page("Seite 2 von 4"))
        assertEquals(1 to 2, page("Página 1 de 2"))
    }

    @Test
    fun `texte sans indicateur de page`() {
        assertNull(page("Chrome"))
        assertNull(page("Paramètres"))
    }

    @Test
    fun `defilement - les deux derniers evenements sont conserves`() {
        Platforms.pushScroll(100, 10)
        Platforms.pushScroll(300, 110)
        assertEquals(100, Platforms.scrollX0)
        assertEquals(300, Platforms.scrollX1)
        assertEquals(10L, Platforms.scrollT0)
        assertEquals(110L, Platforms.scrollT1)
    }
}

class CatalogTest {

    @Test
    fun `chaque nourriture rassasie`() {
        for (f in Food.values()) assertTrue("${f.name}", f.satiety > 0f)
    }

    @Test
    fun `la fraise rend plus heureux que la carotte et la laitue soigne`() {
        assertTrue(Food.STRAWBERRY.happiness > Food.CARROT.happiness)
        assertTrue(Food.LETTUCE.health > 0f)
    }

    @Test
    fun `options de personnalisation non vides`() {
        assertTrue(Options.furs.isNotEmpty())
        assertEquals(3, Options.ears.size)
        assertTrue(Options.eyes.isNotEmpty())
        assertTrue(Options.accessories.first() == "Aucun")
    }

    @Test
    fun `les objets nourriture et jouets sont bien classes`() {
        assertTrue(PropView.Kind.CARROT.isFood)
        assertTrue(!PropView.Kind.BALL.isFood)
        assertTrue(!PropView.Kind.POOP.isFood)
    }
}
