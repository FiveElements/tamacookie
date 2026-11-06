package com.tamacookie

import com.tamacookie.feature.station.StationKind
import com.tamacookie.feature.station.StationPanels
import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.Toy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Faux panneaux des stations : retient ce qui est ouvert et les rappels. */
class FakeStationPanels : StationPanels {
    override var vetOpen = false
    override var chestOpen = false
    override var stationsOpen = false
    var vetRefreshes = 0
    var onFood: ((Food) -> Unit)? = null
    var onToy: ((Toy) -> Unit)? = null
    var onToggle: ((StationKind) -> Unit)? = null

    override fun openVet(pet: Pet, vet: Station, onHeal: () -> Unit, onClose: () -> Unit) { vetOpen = true }
    override fun refreshVet() { vetRefreshes++ }
    override fun closeVet() { vetOpen = false }
    override fun openChest(pet: Pet, chest: Station, onFood: (Food) -> Unit, onToy: (Toy) -> Unit, onClose: () -> Unit) {
        chestOpen = true; this.onFood = onFood; this.onToy = onToy
    }
    override fun closeChest() { chestOpen = false }
    override fun openStations(status: (StationKind) -> Pair<Int, String>, onToggle: (StationKind) -> Unit, onClose: () -> Unit) {
        stationsOpen = true; this.onToggle = onToggle
    }
    override fun refreshStations() {}
    override fun closeStations() { stationsOpen = false }
    override fun reposition() {}
}

/** [StationController] en JVM pur : fenêtres via le faux [OverlayHost], panneaux via [FakeStationPanels]. */
class StationControllerTest {
    private lateinit var ctx: FakePetCtx
    private lateinit var panels: FakeStationPanels
    private lateinit var sc: StationController
    private val overlay get() = ctx.overlay
    private val pet get() = ctx.pet
    private val here = "com.app.notes"

    @Before fun setUp() {
        ctx = FakePetCtx()
        panels = FakeStationPanels()
        val toys = ToyController(ctx)
        toys.registerActs(ctx.acts)
        sc = StationController(ctx, toys, panels)
        sc.registerActs(ctx.acts)
        sc.create()
        ctx.pages.currentView = here
    }

    /** Installe [k] sur la vue courante (panneau 🏠) et la montre. */
    private fun install(k: StationKind): Station {
        sc.toggleStation(k)
        sc.layout()
        overlay.flush()
        return sc[k]
    }

    @Test fun `une station rangee n'a pas de fenetre, installee elle apparait en fondu`() {
        sc.layout()
        for (k in StationKind.entries) assertFalse(overlay.isAttached(sc[k].windowId))
        val s = install(StationKind.BATH)
        assertEquals(here, s.anchor)
        assertEquals(here, ctx.prefs.getString(Prefs.stationAnchor(StationKind.BATH), null))
        assertEquals(WinOp("add+fade", s.windowId, s.x, s.y), overlay.backend.ops.last())
        // Chaque image : rien ne bouge, rien n'est envoyé
        val ops = overlay.backend.ops.size
        repeat(5) { sc.layout(); overlay.flush() }
        assertEquals(ops, overlay.backend.ops.size)
    }

    @Test fun `changer de vue retire la station, revenir la remet`() {
        val s = install(StationKind.VET)
        ctx.pages.currentView = "com.app.autre"
        sc.layout()
        assertFalse(overlay.isAttached(s.windowId))
        ctx.pages.currentView = here
        sc.layout()
        assertTrue(overlay.isAttached(s.windowId))
        assertEquals(1, overlay.backend.count("remove"))
    }

    @Test fun `deplacer une station au doigt, position sauvegardee`() {
        val s = install(StationKind.BATH)
        sc.dragStation(s, 400f, 900f)
        sc.dragStation(s, 410f, 905f)
        overlay.flush()
        assertEquals(1, overlay.backend.count("move"))
        assertEquals(s.x to s.y, overlay.shownAt(s.windowId))
        assertEquals(410f, s.cx)
        sc.dropStation(s)
        assertEquals(410f / ctx.arena.screenW, ctx.prefs.getFloat(Prefs.stationFx(StationKind.BATH), -1f), 1e-6f)
    }

    @Test fun `entrer et sortir, la station sait qu'elle est occupee`() {
        val s = install(StationKind.BATH)
        sc.enterStation(StationKind.BATH)
        assertTrue(s.occupied)
        assertEquals(Act.IN_STATION, pet.act)
        assertEquals(StationKind.BATH, ctx.world.stationOf(pet))
        assertTrue(sc.touchGoesToPet(s))
        assertTrue(overlay.backend.ops.any { it.op == "redraw" && it.id == s.windowId })
        sc.leaveStation(pet, hopOut = false)
        assertFalse(s.occupied)
        assertNull(ctx.world.stationOf(pet))
        assertFalse(sc.touchGoesToPet(s))
    }

    @Test fun `glisser l'animal au-dessus d'une station la surligne`() {
        val s = install(StationKind.CAGE)
        pet.body.fx = s.cx; pet.body.fy = s.cy + 45f
        sc.updateHover()
        assertTrue(s.highlight)
        assertEquals(StationKind.CAGE, sc.takeHover())
        assertFalse(s.highlight)
        assertNull(sc.takeHover())
    }

    @Test fun `une station retiree perd son surlignage`() {
        val s = install(StationKind.CAGE)
        pet.body.fx = s.cx; pet.body.fy = s.cy + 45f
        sc.updateHover()
        ctx.pages.currentView = "com.app.autre"
        sc.layout()
        assertFalse(s.highlight)
    }

    @Test fun `toucher le coffre l'ouvre, le refermer abaisse le couvercle`() {
        val s = install(StationKind.CHEST)
        sc.tapStation(s)
        assertTrue(panels.chestOpen)
        assertTrue(s.open)
        assertNull(sc.visibleChest())          // ouvert par l'utilisateur : il ne va pas s'y servir
        pet.stats = pet.stats.copy(satiety = 50f)
        panels.onFood!!(Food.CARROT)
        assertFalse(panels.chestOpen)
        assertFalse(s.open)
        val c = ctx.world.props.single()
        assertEquals(Food.CARROT, c.food)
        assertEquals(s.cx, c.x)                // jaillit du coffre
    }

    @Test fun `toucher une autre station l'explique`() {
        val s = install(StationKind.BATH)
        sc.tapStation(s)
        assertTrue(ctx.said.last().startsWith(StationKind.BATH.behavior.tapHint))
    }

    @Test fun `on ne range pas la cage avec lui dedans`() {
        install(StationKind.CAGE)
        sc.enterStation(StationKind.CAGE)
        ctx.pages.currentView = "com.app.autre"
        ctx.said.clear()
        sc.toggleStation(StationKind.CAGE)
        assertEquals(StationKind.CAGE.behavior.stayLine, ctx.said.single())
        assertEquals(here, sc[StationKind.CAGE].anchor)
    }

    @Test fun `le veto ouvre sa fiche et la rafraichit chaque seconde`() {
        install(StationKind.VET)
        sc.enterStation(StationKind.VET)
        assertTrue(panels.vetOpen)
        sc.update(1.1f)
        assertEquals(1, panels.vetRefreshes)
        panels.vetOpen = false
        sc.reopenVetPanelIfNeeded()
        assertTrue(panels.vetOpen)
        sc.closePanels()
        assertFalse(panels.vetOpen || panels.chestOpen || panels.stationsOpen)
    }

    @Test fun `la baignoire affichee s'anime dans le modele`() {
        val s = install(StationKind.BATH)
        sc.update(0.5f)
        assertEquals(0.5f, s.t, 1e-6f)
        assertTrue(sc.animating())
        ctx.pages.currentView = "com.app.autre"
        sc.layout()
        sc.update(0.5f)
        assertEquals(0.5f, s.t, 1e-6f)          // pas affichée : pas animée
    }
}
