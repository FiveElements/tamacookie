package com.tamacookie

import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.Toy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformsTest {

    private fun page(desc: String): Pair<Int, Int>? = Platforms.parsePage(desc)

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
        assertEquals(2 to 3, page("Pagina 2 van 3"))       // néerlandais
        assertEquals(2 to 3, page("Página 2 de 3"))        // portugais
        assertEquals(2 to 5, page("Strona 2 z 5"))         // polonais
        assertEquals(3 to 4, page("Pagina 3 di 4"))        // italien
        assertEquals(1 to 3, page("Sida 1 av 3"))          // suédois
        assertEquals(2 to 2, page("Страница 2 из 2"))      // russe
    }

    @Test
    fun `indicateur de page Pixel non selectionne`() {
        // Pixel Launcher : la description est sur l'indicateur lui-même
        assertEquals(2 to 3, page("Page 2 of 3"))
    }

    @Test
    fun `forme generique N sur M`() {
        assertEquals(2 to 3, page("2 / 3"))
        assertEquals(1 to 4, page("1/4"))
        // pas dans une phrase quelconque
        assertNull(page("Il est 12/05 aujourd'hui"))
    }

    @Test
    fun `numeros de page incoherents rejetes`() {
        assertNull(page("Page 5 of 3"))
        assertNull(page("Page 0 of 3"))
        assertNull(page(""))
    }

    @Test
    fun `zone de detection non vide en paysage et en portrait`() {
        val d = 2.75f
        // Téléphone en paysage : 1080 px de haut
        val (minL, maxL) = Platforms.detectionBand(1080, d)
        assertTrue("paysage : $minL..$maxL", maxL - minL > 300)
        // Portrait : 2400 px de haut
        val (minP, maxP) = Platforms.detectionBand(2400, d)
        assertTrue(minP >= 120 * d && maxP <= 2400 - 90 * d && maxP > minP)
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
        assertEquals(Food.CARROT, Food.CARROT.spec.food)
        assertNull(Toy.BALL.spec.food)
        assertNull(com.tamacookie.feature.toy.Poop.food)
        assertNull(com.tamacookie.feature.toy.Poop.toy)
    }
}
