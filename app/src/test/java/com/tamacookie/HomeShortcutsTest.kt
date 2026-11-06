package com.tamacookie

import com.tamacookie.tile.TileTexts
import com.tamacookie.widget.UpdateThrottle
import com.tamacookie.widget.WidgetTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Widget d'écran d'accueil et tuile Réglages rapides (évolution 4c) : textes et cadence de mise à jour. */
class HomeShortcutsTest {

    @Test
    fun `widget - nom, humeur et jauges comme la notification`() {
        val s = PetStats(satiety = 80.6f, happiness = 90f, energy = 55.2f, cleanliness = 70f)
        val c = WidgetTexts.content("Cookie", s, running = true)
        assertEquals("${WidgetTexts.moodEmoji(s.mood, s.sleeping)} Cookie", c.title)
        assertEquals(
            NotifierTexts.moodText(s.mood, s.sleeping, hiding = false).replaceFirstChar { it.uppercaseChar() },
            c.subtitle,
        )
        assertEquals(listOf("🥕", "💖", "⚡", "🛁"), c.gauges.map { it.emoji })
        assertEquals(listOf(80, 90, 55, 70), c.gauges.map { it.value })
        assertEquals(NotifierTexts.statusLine(80.6f, 90f, 55.2f, 70f), c.statusLine)
        assertEquals("", c.status)
    }

    @Test
    fun `widget - service arrete, en cage, jauges bornees`() {
        val s = PetStats(satiety = -3f, happiness = 140f, caged = true)
        val c = WidgetTexts.content("Noisette", s, running = false)
        assertEquals(WidgetTexts.STOPPED, c.status)
        assertTrue(c.subtitle.endsWith("(en cage 🔒)"))
        assertEquals(0, c.gauges[0].value)
        assertEquals(100, c.gauges[1].value)
        assertTrue(c.subtitle.first().isUpperCase())
    }

    @Test
    fun `widget - un emoji par humeur, endormi a part`() {
        val all = Mood.entries.map { WidgetTexts.moodEmoji(it, sleeping = false) }
        assertEquals(Mood.entries.size, all.toSet().size)
        assertEquals("😴", WidgetTexts.moodEmoji(Mood.SLEEPY, sleeping = true))
        assertTrue(WidgetTexts.NO_PET.gauges.isEmpty())
    }

    @Test
    fun `cadence - au plus une fois par minute, la demande trop tot est reportee`() {
        val t = UpdateThrottle(60_000)
        assertEquals(0L, t.request(1_000))           // première : tout de suite
        assertEquals(50_000L, t.request(11_000))     // trop tôt : reportée à la fin de la minute
        assertTrue(t.pending)
        assertEquals(-1L, t.request(20_000))         // déjà programmée
        t.fired(61_000)
        assertFalse(t.pending)
        assertEquals(30_000L, t.request(91_000))     // compte depuis la mise à jour reportée
        t.cancel()
        assertEquals(0L, t.request(121_000))         // une minute après : tout de suite
    }

    @Test
    fun `tuile - active si affiche, sinon cachee ou rangee`() {
        assertEquals(TileTexts.State(true, "Sur l'écran", "Cacher"), TileTexts.state(running = true, visible = true))
        assertEquals(TileTexts.State(false, "Caché", "Montrer"), TileTexts.state(running = true, visible = false))
        assertFalse(TileTexts.state(running = false, visible = true).active)
        assertEquals("Rangé", TileTexts.state(running = false, visible = false).subtitle)
        assertEquals("Cookie", TileTexts.label(null, "Cookie"))
        assertEquals("Cookie", TileTexts.label(" ", "Cookie"))
        assertEquals("Noisette", TileTexts.label("Noisette", "Cookie"))
    }
}
