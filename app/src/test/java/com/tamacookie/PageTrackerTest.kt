package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PageTrackerTest {

    private lateinit var ctx: FakePetCtx
    private lateinit var pages: PageTracker
    private val pet get() = ctx.pet

    @Before fun setUp() {
        ctx = FakePetCtx()
        pages = ctx.pages
    }

    /** Bureau de [count] pages de la largeur de l'écran, au repos sur la page 1. */
    private fun desktop(count: Int = 3) {
        val t = ctx.clock.uptime - 100_000
        ctx.screen.update {
            copy(launcherPkg = "launcher", pageCount = count, pageW = ctx.arena.screenW, viewKey = "launcher#p1",
                scroll = ScrollSamples(0, t, 0, t))
        }
        assertTrue(pages.refresh())
    }

    @Test
    fun `nom lisible d'une vue`() {
        ctx.labels["com.example.chat"] = "Chat"
        assertEquals("page 2 du bureau", pages.viewLabel("launcher#p2"))
        assertEquals("Chat", pages.viewLabel("com.example.chat"))
        assertEquals("inconnue", pages.viewLabel("com.example.inconnue"))
        assertEquals("?", pages.viewLabel(null))
    }

    @Test
    fun `le defilement recent se mesure avec l'horloge injectee`() {
        assertFalse(pages.scrollingRecently())
        ctx.screen.update { copy(scroll = ScrollSamples(t1 = ctx.clock.uptime)) }
        ctx.clock.advance(100)
        assertTrue(pages.scrollingRecently())
        ctx.clock.advance(Tuning.SCROLL_ACTIVE_MS)
        assertFalse(pages.scrollingRecently())
    }

    @Test
    fun `la page affichee vient du defilement`() {
        desktop()
        assertEquals("launcher#p1", pages.currentView)
        assertEquals(1, pages.launcherPage())
    }

    @Test
    fun `sur une autre page que celle affichee`() {
        desktop()
        pages.bunnyPage = 2
        assertTrue(pages.bunnyElsewhere())
        pages.bunnyPage = 1
        assertFalse(pages.bunnyElsewhere())
    }

    @Test
    fun `il part explorer la page voisine puis s'y promene`() {
        desktop()
        pages.bunnyPage = 1
        assertTrue(pages.canPageWander())
        assertTrue(pages.startPageWander())
        assertEquals(Act.PAGE_OUT, pet.act)
        assertEquals(1f, pet.body.facing, 0f)                       // page 1 : seulement vers la droite
        assertEquals(ctx.arena.screenW + ctx.arena.bw, pet.body.targetX, 0f)
        assertEquals(1, ctx.said.size)

        var steps = 0
        while (pet.act == Act.PAGE_OUT) {
            pages.updatePageOut(pet, 0.1f)
            assertTrue(++steps < 500)
        }
        assertEquals(Act.WALK, pet.act)
        assertEquals(2, pages.bunnyPage)
        assertEquals(ctx.arena.screenW + ctx.arena.bw - ctx.arena.screenW, pet.body.fx, 1e-3f)
        assertTrue(pages.returnAt > ctx.clock.uptime)
        assertTrue(pet.body.targetX in ctx.arena.minX..ctx.arena.maxX)
    }

    @Test
    fun `quand on quitte le bureau il arrive par le bord`() {
        pages.currentView = "com.example.app"
        pages.bunnyPage = 2
        pages.bunnyShift = ctx.arena.screenW                         // il était sur la page de droite
        pages.updateBunnyPage()
        assertEquals(Act.TRAVEL_IN, pet.act)
        assertEquals(ctx.arena.screenW + ctx.arena.bw, pet.body.fx, 0f)
        assertEquals(0, pages.bunnyPage)
        assertEquals(0f, pages.bunnyShift, 0f)
    }

    @Test
    fun `il revient sur la page affichee apres son escapade`() {
        desktop()
        pages.bunnyPage = 2
        pet.setAct(Act.IDLE)
        // Rien n'est programmé : un retour est planifié
        pages.checkPageReturn()
        val at = pages.returnAt
        assertTrue(at > ctx.clock.uptime)
        // Une fois l'heure venue (et le défilement calme), il revient en marchant
        ctx.clock.uptime = at + Tuning.PAGE_RETURN_SCROLL_QUIET_MS + 1
        pages.checkPageReturn()
        assertEquals(Act.TRAVEL_IN, pet.act)
        assertEquals(1, pages.bunnyPage)
        assertTrue(pet.mind.travelComment != null)
    }

    // --- Alimenté par le port ScreenSensor ---

    @Test
    fun `largeur de page memorisee - envoyee au capteur, puis la largeur apprise est memorisee`() {
        val key = Prefs.pageWidth(1080)
        ctx.prefs.edit { putFloat(key, 1000f) }
        pages.refresh()
        assertEquals(1000f, ctx.screen.snapshot.pageW, 0f)
        ctx.screen.setPageWidth(1070f)                               // apprise par le service d'accessibilité
        pages.refresh()
        assertEquals(1070f, ctx.prefs.getFloat(key, 0f), 0f)
    }

    @Test
    fun `largeur memorisee invraisemblable ignoree, rotation - largeur oubliee`() {
        ctx.prefs.edit { putFloat(Prefs.pageWidth(1080), 300f) }
        pages.refresh()
        assertEquals(0f, ctx.screen.snapshot.pageW, 0f)
        desktop()
        pages.onScreenResized()
        assertEquals(0f, ctx.screen.snapshot.pageW, 0f)
    }

    @Test
    fun `la page affichee suit le defilement avant l'indicateur`() {
        desktop()
        val t = ctx.clock.uptime - 50_000
        ctx.screen.update { copy(scroll = ScrollSamples(1080, t, 1080, t)) }   // page 2, indicateur encore sur 1
        var n = 0
        while (!pages.refresh()) assertTrue(++n < 20)
        assertEquals("launcher#p2", pages.currentView)
        assertEquals(2, pages.launcherPage())
        pages.bunnyPage = 3
        assertEquals("launcher#p3", pages.bunnyView())               // sa page, sur le bureau du capteur
    }

    @Test
    fun `sans accessibilite la vue vient de l'application au premier plan`() {
        ctx.foreground = "com.example.chat"
        assertTrue(pages.refresh())
        assertEquals("com.example.chat", pages.currentView)
        assertEquals(0, pages.launcherPage())
    }
}
