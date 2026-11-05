package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollMathTest {

    private val launcher = "com.sec.android.app.launcher"

    @Test
    fun `cle de vue avec page`() {
        assertEquals(launcher to 2, ViewKey.split("$launcher#p2"))
        assertEquals("$launcher#p3", ViewKey.of(launcher, 3))
    }

    @Test
    fun `cle de vue d'une application`() {
        val (pkg, page) = ViewKey.split("com.android.chrome")
        assertEquals("com.android.chrome", pkg)
        assertNull(page)
        assertEquals("com.android.chrome", ViewKey.of("com.android.chrome", null))
    }

    @Test
    fun `estimation sans vitesse`() {
        assertEquals(2450f, ScrollMath.estimate(2450, 1000, 2450, 1100, 1150), 0.01f)
    }

    @Test
    fun `estimation extrapolee entre deux evenements`() {
        // 400 px en 100 ms = 4 px/ms ; 50 ms après le dernier événement -> +200
        assertEquals(1491f + 200f, ScrollMath.estimate(1091, 1000, 1491, 1100, 1150), 0.01f)
    }

    @Test
    fun `l'anticipation est plafonnee`() {
        val v = ScrollMath.estimate(1091, 1000, 1491, 1100, 1100 + 110)
        assertEquals(1491f + 4f * 110f, v, 0.01f)
    }

    @Test
    fun `doigt immobile - l'estimation revient sur la derniere position`() {
        // Plus d'événement depuis longtemps : pas de dépassement résiduel
        assertEquals(1491f, ScrollMath.estimate(1091, 1000, 1491, 1100, 1100 + 500), 0.01f)
    }

    @Test
    fun `evenements trop espaces - pas d'extrapolation`() {
        assertEquals(1491f, ScrollMath.estimate(0, 0, 1491, 1000, 1050), 0.01f)
    }

    @Test
    fun `largeur de page plausible`() {
        assertTrue(ScrollMath.plausiblePageWidth(2450f, 2800f))
        assertFalse("3068 = page + doigt immobile", ScrollMath.plausiblePageWidth(3068f, 2800f))
        assertFalse(ScrollMath.plausiblePageWidth(0f, 2800f))
        assertFalse(ScrollMath.plausiblePageWidth(1000f, 2800f))
    }

    @Test
    fun `page affichee selon le defilement`() {
        assertEquals(1, ScrollMath.pageAt(0f, 2450f, 4))
        assertEquals(1, ScrollMath.pageAt(1200f, 2450f, 4))
        assertEquals(2, ScrollMath.pageAt(1300f, 2450f, 4))
        assertEquals(4, ScrollMath.pageAt(7350f, 2450f, 4))
        assertEquals(4, ScrollMath.pageAt(99999f, 2450f, 4))
        assertEquals(1, ScrollMath.pageAt(-500f, 2450f, 4))
    }

    @Test
    fun `les objets suivent le swipe dans le meme sens que les icones`() {
        // Objet sur la page 2, on glisse de la page 2 vers la page 3 : il part vers la gauche
        assertEquals(0f, ScrollMath.offset(2, 2450f, 2450f), 0.01f)
        assertTrue(ScrollMath.offset(2, 2450f, 2450f + 600f) < 0f)
        assertEquals(-600f, ScrollMath.offset(2, 2450f, 3050f), 0.01f)
        // Objet sur la page 3 vu depuis la page 2 : à droite, hors écran
        assertEquals(2450f, ScrollMath.offset(3, 2450f, 2450f), 0.01f)
    }

    @Test
    fun `fin d'animation ou doigt immobile`() {
        assertTrue(ScrollMath.ended(2449, 2450))
        assertFalse(ScrollMath.ended(249, 599))
    }
}
