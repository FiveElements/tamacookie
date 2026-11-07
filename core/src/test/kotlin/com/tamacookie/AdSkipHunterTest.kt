package com.tamacookie

import com.tamacookie.feature.adskip.AdSkipHunter
import com.tamacookie.feature.station.StationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chasseur de pubs, IA ([AdSkipHunter]) : un seul animal part (le plus proche), saute sur le bouton, appuie
 * **une** fois par le port [ScreenActions] (avec la cible, rien d'autre), redescend ; abandon sans un mot ;
 * réglage coupé ; animaux indisponibles ; et sans bouton, **rien ne change** (mêmes décisions, même aléatoire).
 */
class AdSkipHunterTest {

    private class Hunt(seed: Int = 42, private val withHunter: Boolean = true, enabled: Boolean = true) {
        val ctx = FakePetCtx(seed)
        val actions = FakeScreenActions()
        val hunter = AdSkipHunter(ctx, actions).also { it.enabled = enabled; it.registerActs(ctx.acts) }
        private val hooks = object : PetBehavior.Hooks {
            override fun chestVisible(p: Pet) = false
            override val lastSelfServe = 0L
            override fun goToChest(p: Pet, now: Long) {}
            override fun seekAttention(p: Pet) = false
            override fun startPageWander(p: Pet) {}
            override fun hour() = 12
        }
        val behavior = PetBehavior(ctx, hooks)
        private val buf = ArrayList<Pet>()

        /** Une image, dans l'ordre du service : minuteurs, chasseur (avant les bêtises), puis chaque animal. */
        fun frame(dt: Float = 1f / 60f) {
            ctx.clock.advance(16)
            val pets = ctx.world.shown(buf).toList()
            for (p in pets) { p.body.actTime += dt; p.mind.tick(dt) }
            if (withHunter) hunter.check()
            for (p in pets) ctx.world.asPet(p) { behavior.checkPlatform(p); behavior.update(p, dt) }
        }

        fun button(b: Box?) = ctx.screen.update { copy(adSkip = b) }

        /** Images jusqu'à [until] (au plus [max]) ; renvoie le nombre d'images jouées. */
        fun runUntil(max: Int = 600, until: () -> Boolean): Int {
            var n = 0
            while (n < max && !until()) { frame(); n++ }
            return n
        }
    }

    private val box = Box(880f, 1200f, 1000f, 1290f)

    // =====================================================================
    //  Sans bouton : rien ne change
    // =====================================================================
    private fun trace(h: Hunt, frames: Int = 900): List<String> {
        val out = ArrayList<String>()
        repeat(frames) {
            h.frame()
            for (p in h.ctx.world.pets.values) out += "${p.id.value}:${p.act}:${p.body.fx}:${p.body.fy}"
        }
        for (p in h.ctx.world.pets.values) out += "rnd ${p.id.value}:${p.mind.rnd.nextLong()}"
        return out
    }

    @Test fun `sans bouton - memes decisions et meme aleatoire qu'un monde sans chasseur`() {
        for (seed in listOf(1, 42, 7_777)) {
            val ref = Hunt(seed, withHunter = false).also { it.ctx.addPet("b", x = 300f) }
            val h = Hunt(seed).also { it.ctx.addPet("b", x = 300f) }
            assertEquals(trace(ref), trace(h))
            assertTrue(h.actions.pressed.isEmpty())
        }
    }

    @Test fun `reglage coupe - il ne voit meme pas le bouton`() {
        val ref = Hunt(42, withHunter = false).also { it.button(box) }
        val h = Hunt(42, enabled = false).also { it.button(box) }
        assertEquals(trace(ref), trace(h))
        assertTrue(h.actions.pressed.isEmpty())
        assertNull(h.hunter.hunterId)
    }

    // =====================================================================
    //  La chasse
    // =====================================================================
    @Test fun `il court, atterrit sur le bouton, appuie une fois avec la cible et redescend`() {
        val h = Hunt()
        val p = h.ctx.first
        p.body.fx = 200f
        h.button(box)
        h.frame()
        assertEquals(Act.SKIP_AD, p.act)
        assertEquals(p.id, h.hunter.hunterId)
        h.runUntil { h.actions.pressed.isNotEmpty() }
        assertEquals(listOf(box), h.actions.pressed)
        assertEquals(Act.SKIP_AD, p.act)
        assertEquals(box.top, p.body.fy, 0.01f)                       // il est debout sur le bouton
        assertTrue(p.body.fx in box.left..box.right)
        assertTrue(h.ctx.said.last() in AdSkipHunter.PRESSED)
        assertTrue(PType.STAR in h.ctx.emitted)
        // il redescend au sol et la chasse est finie ; le bouton toujours là n'est pas visé une deuxième fois
        h.runUntil { p.act != Act.SKIP_AD && p.act != Act.AIR }
        assertNull(h.hunter.hunterId)
        assertEquals(h.ctx.arena.groundY, p.body.fy, 0.01f)
        repeat(600) { h.frame() }
        assertEquals(1, h.actions.pressed.size)
        assertTrue(h.hunter.attempted)
        // le bouton disparaît (pub passée) ; à la pub suivante, il repart
        h.button(null); h.frame()
        assertFalse(h.hunter.attempted)
    }

    @Test fun `tout pres, il saute directement, depuis une icone il descend d'abord`() {
        val h = Hunt()
        val p = h.ctx.first
        p.body.fx = 900f
        h.button(box)
        h.frame()
        h.frame()
        assertEquals(Act.AIR, p.act)                                  // saut immédiat vers le bouton
        assertEquals(box.top, p.body.phys.airLandY!!, 0f)
        val h2 = Hunt()
        val q = h2.ctx.first
        val icon = Box(100f, 1500f, 200f, 1600f)
        h2.ctx.screen.platforms(icon)
        q.body.fx = 150f; q.body.fy = icon.top; q.body.platform = icon
        h2.button(box)
        h2.runUntil { h2.actions.pressed.isNotEmpty() }
        assertEquals(listOf(box), h2.actions.pressed)
    }

    @Test fun `un seul animal part, le plus proche, meme endormi - la pub le reveille`() {
        val h = Hunt()
        h.ctx.first.body.fx = 100f
        val b = h.ctx.addPet("b", x = 600f)
        val c = h.ctx.addPet("c", x = 950f)
        h.button(box)
        h.hunter.check()
        assertEquals(c.id, h.hunter.hunterId)
        assertEquals(listOf(c), h.ctx.world.pets.values.filter { it.act == Act.SKIP_AD })
        h.runUntil { h.actions.pressed.isNotEmpty() }
        assertEquals(1, h.ctx.world.pets.values.count { Director.huntsAd(it) })
        assertEquals(1, h.actions.pressed.size)
        // le plus proche dort : la pub le réveille (sans mauvaise humeur), il le dit et y va
        val h2 = Hunt()
        h2.ctx.first.body.fx = 100f
        h2.ctx.addPet("b", x = 600f)
        val c2 = h2.ctx.addPet("c", x = 950f)
        h2.ctx.on(c2, PetEvent.FellAsleep); c2.setAct(Act.SLEEP)
        val happy = c2.stats.happiness
        h2.button(box)
        h2.hunter.check()
        assertEquals(c2.id, h2.hunter.hunterId)
        assertFalse(c2.stats.sleeping)
        assertEquals(Act.SKIP_AD, c2.act)
        assertTrue(c2.stats.happiness >= happy)
        assertTrue(h2.ctx.speakers.isNotEmpty())
        h2.runUntil { h2.actions.pressed.isNotEmpty() }
        assertEquals(1, h2.actions.pressed.size)
        assertNotEquals(b.id, c.id)
    }

    @Test fun `endormi dans le lit - la pub ne le sort pas`() {
        val h = Hunt()
        h.ctx.on(h.ctx.first, PetEvent.FellAsleep); h.ctx.first.setAct(Act.SLEEP)
        h.ctx.world.station(StationKind.BED).enter(h.ctx.first.id)
        h.button(box)
        h.hunter.check()
        assertNull(h.hunter.hunterId)
        assertTrue(h.ctx.first.stats.sleeping)
    }

    @Test fun `le bouton disparait ou s'ecarte en route - il abandonne sans rien dire`() {
        for (gone in listOf<Box?>(null, Box(10f, 300f, 60f, 340f))) {
            val h = Hunt()
            val p = h.ctx.first
            p.body.fx = 100f
            h.button(box)
            repeat(5) { h.frame() }
            assertEquals(Act.SKIP_AD, p.act)
            val said = h.ctx.said.size
            h.button(gone)
            h.frame()
            assertNull(h.hunter.hunterId)
            assertNotEquals(Act.SKIP_AD, p.act)
            assertEquals(said, h.ctx.said.size)
            assertTrue(h.actions.pressed.isEmpty())
        }
    }

    @Test fun `disparu pendant le saut - il redescend du point d'atterrissage`() {
        val h = Hunt()
        val p = h.ctx.first
        p.body.fx = 900f
        h.button(box)
        h.frame(); h.frame()
        assertEquals(Act.AIR, p.act)
        h.button(null)
        h.runUntil(300) { p.act != Act.AIR && p.act != Act.SKIP_AD && p.body.fy == h.ctx.arena.groundY }
        assertEquals(h.ctx.arena.groundY, p.body.fy, 0.01f)
        assertTrue(h.actions.pressed.isEmpty())
    }

    @Test fun `pas dessus en 6 s - il abandonne, et ne retente pas tant que le bouton reste`() {
        val h = Hunt()
        val p = h.ctx.first
        p.body.fx = 40f
        h.button(box)
        h.frame()
        assertEquals(Act.SKIP_AD, p.act)
        h.ctx.clock.advance(AdSkipHunter.TIMEOUT_MS + 1)
        h.frame()
        assertNull(h.hunter.hunterId)
        assertNotEquals(Act.SKIP_AD, p.act)
        repeat(300) { h.frame() }
        assertTrue(h.actions.pressed.isEmpty())
        assertNull(h.hunter.hunterId)
    }

    @Test fun `interrompu (attrape) ou reglage coupe en route - fini, sans nouvelle tentative`() {
        val h = Hunt()
        val p = h.ctx.first
        p.body.fx = 100f
        h.button(box)
        h.frame()
        p.setAct(Act.DRAGGED)
        h.hunter.check()
        assertNull(h.hunter.hunterId)
        p.setAct(Act.IDLE, 5f)
        h.hunter.check()
        assertNull(h.hunter.hunterId)
        val h2 = Hunt()
        h2.ctx.first.body.fx = 100f
        h2.button(box)
        h2.frame()
        h2.hunter.enabled = false
        h2.frame()
        assertNull(h2.hunter.hunterId)
        assertNotEquals(Act.SKIP_AD, h2.ctx.first.act)
        assertTrue(h2.actions.pressed.isEmpty())
    }

    @Test fun `le port refuse - pas de replique, il redescend quand meme`() {
        val h = Hunt()
        h.actions.accepts = false
        val p = h.ctx.first
        p.body.fx = 900f
        h.button(box)
        h.runUntil { h.actions.pressed.isNotEmpty() }
        assertTrue(h.ctx.said.none { it in AdSkipHunter.PRESSED })
        h.runUntil { p.act != Act.SKIP_AD && p.act != Act.AIR }
        assertNull(h.hunter.hunterId)
        assertEquals(1, h.actions.pressed.size)
    }

    @Test fun `cache, tenu, en station, hors ecran, tout cache ou occupe - personne ne part`() {
        val cases: List<Pair<String, (Hunt) -> Unit>> = listOf(
            "caché" to { h -> h.ctx.first.setAct(Act.HIDDEN) },
            "tenu" to { h -> h.ctx.first.setAct(Act.DRAGGED) },
            "joue" to { h -> h.ctx.first.setAct(Act.PLAY) },
            "bêtise" to { h -> h.ctx.first.setAct(Act.SQUAT) },
            "station" to { h -> h.ctx.world.station(StationKind.BATH).enter(h.ctx.first.id) },
            "fenêtre retirée" to { h -> h.ctx.away += h.ctx.first.id },
            "Cacher" to { h -> h.ctx.hidden = true },
        )
        for ((name, set) in cases) {
            val h = Hunt()
            h.button(box)
            set(h)
            h.hunter.check()
            assertNull(name, h.hunter.hunterId)
            assertTrue(name, h.ctx.world.pets.values.none { it.act == Act.SKIP_AD })
            assertFalse(name, h.hunter.attempted)          // il partira quand il le pourra
        }
    }

    @Test fun `passe avant les betises et le bavardage - un animal au repos part tout de suite`() {
        val h = Hunt()
        val p = h.ctx.first
        p.setAct(Act.IDLE, 0f)                   // il allait décider (bêtise, bavardage…)
        p.mind.chatterIn = 0f
        h.button(box)
        h.frame()
        assertEquals(Act.SKIP_AD, p.act)
        assertTrue(h.ctx.said.isEmpty())
    }

    @Test fun `description au lecteur d'ecran`() {
        assertEquals("passe la pub", A11yTexts.activity(Act.SKIP_AD))
        assertTrue(Act.SKIP_AD in Act.MOVING && Act.SKIP_AD in Act.BUSY_FOR_TRAVEL)
    }
}
