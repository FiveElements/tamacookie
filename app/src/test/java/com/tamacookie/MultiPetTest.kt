package com.tamacookie

import com.tamacookie.feature.toy.Food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plusieurs animaux dans le même monde : animal courant, plafond d'affichage, état par animal
 * (page, aléatoire, bulle, crottes, bêtise) et boucle par animal ([PetBehavior]).
 */
class MultiPetTest {

    /** Hooks neutres du comportement (pas de coffre, pas de bêtise). */
    private class Hooks : PetBehavior.Hooks {
        override fun chestVisible(p: Pet) = false
        override val lastSelfServe = 0L
        override fun goToChest(p: Pet, now: Long) {}
        override fun seekAttention(p: Pet) = false
        override fun startPageWander(p: Pet) {}
        override fun hour() = 12
    }

    /** Ce que fait la boucle du service pour chaque animal affiché. */
    private fun frame(ctx: FakePetCtx, b: PetBehavior, dt: Float) {
        val pets = ArrayList(ctx.world.shown(ArrayList()))
        for (p in pets) { p.body.actTime += dt; p.mind.tick(dt) }
        for (p in pets) ctx.world.asPet(p) { b.checkPlatform(p); b.update(p, dt) }
    }

    @Test fun animalCourant_residentParDefaut_asPetImbrique() {
        val ctx = FakePetCtx()
        val a = ctx.first
        val b = ctx.addPet("b")
        val c = ctx.addPet("c")
        assertSame(a, ctx.pet)
        ctx.world.asPet(b) {
            assertSame(b, ctx.pet)
            ctx.world.asPet(c) { assertSame(c, ctx.pet) }
            assertSame(b, ctx.pet)
        }
        assertSame(a, ctx.pet)
        // une exception ne laisse pas le monde sur le mauvais animal
        runCatching { ctx.world.asPet(b) { error("boum") } }
        assertSame(a, ctx.pet)
    }

    @Test fun plafondDAffichage_residentEnTete() {
        val ctx = FakePetCtx()
        val extra = (1..4).map { ctx.addPet("c$it") }
        val shown = ctx.world.shown(ArrayList())
        assertEquals(Tuning.MAX_PETS_SHOWN, shown.size)
        assertSame(ctx.first, shown[0])
        assertEquals(listOf(ctx.first, extra[0], extra[1]), shown)
        assertTrue(ctx.world.isShown(ctx.first))
        assertTrue(ctx.world.isShown(extra[1]))
        assertFalse(ctx.world.isShown(extra[2]))
        assertFalse(ctx.world.isShown(extra[3]))
        // un compagnon affiché part : le suivant prend sa place
        ctx.world.remove(extra[0].id)
        assertTrue(ctx.world.isShown(extra[2]))
    }

    @Test fun resident_explicite_etRetrait() {
        val ctx = FakePetCtx()
        val b = ctx.addPet("b")
        ctx.world.residentId = b.id
        assertSame(b, ctx.world.resident)
        assertSame(b, ctx.world.shown(ArrayList())[0])
        ctx.world.remove(b.id)
        assertSame(ctx.first, ctx.world.resident)
        assertNull(ctx.world.pets[b.id])
    }

    @Test fun chaqueAnimalASonAleatoire_etSaBulle() {
        val ctx = FakePetCtx()
        val b = ctx.addPet("b", seed = 7)
        assertSame(ctx.first.mind.rnd, ctx.rnd)
        ctx.world.asPet(b) {
            assertSame(b.mind.rnd, ctx.rnd)
            ctx.say("coucou")
        }
        ctx.say("moi aussi")
        assertEquals(listOf(b.id, ctx.first.id), ctx.speakers)
    }

    /** Ce que décide un animal ne dépend pas de la présence d'un autre (chacun son `Random`). */
    @Test fun decisionsDuResident_inchangeesParUnCompagnon() {
        fun run(withCompanion: Boolean): List<String> {
            val ctx = FakePetCtx(seed = 3)
            if (withCompanion) ctx.addPet("b", x = 800f, seed = 99)
            val beh = PetBehavior(ctx, Hooks())
            val trace = ArrayList<String>()
            repeat(600) {
                frame(ctx, beh, 0.05f)
                trace += "${ctx.first.act}:${"%.2f".format(ctx.first.body.fx)}"
            }
            return trace
        }
        assertEquals(run(false), run(true))
    }

    @Test fun deuxAnimaux_vivent_chacunSaVie() {
        val ctx = FakePetCtx(seed = 5)
        val b = ctx.addPet("b", x = 800f, seed = 11)
        val beh = PetBehavior(ctx, Hooks())
        val acts = HashSet<Pair<PetId, Act>>()
        repeat(2000) {
            frame(ctx, beh, 0.05f)
            acts += ctx.first.id to ctx.first.act
            acts += b.id to b.act
        }
        // les deux ont marché (le compagnon n'est pas figé)
        assertTrue(ctx.first.id to Act.WALK in acts)
        assertTrue(b.id to Act.WALK in acts)
    }

    @Test fun chaqueAnimalASaPage() {
        val ctx = FakePetCtx()
        val b = ctx.addPet("b")
        ctx.world.asPet(b) { ctx.pages.bunnyPage = 3; ctx.pages.bunnyShift = -120f }
        assertEquals(0, ctx.pages.bunnyPage)
        assertEquals(0f, ctx.pages.bunnyShift)
        assertEquals(3, b.body.page)
        assertEquals(-120f, b.body.pageShift)
    }

    @Test fun fenetreParAnimal_awayEtPlace() {
        val ctx = FakePetCtx()
        val b = ctx.addPet("b")
        ctx.world.asPet(b) { ctx.setBunnyAway(true); ctx.placeBunny() }
        assertTrue(ctx.isAway(b))
        assertFalse(ctx.bunnyAway)
        assertEquals(listOf(b.id), ctx.placed)
    }

    @Test fun crottes_parAnimal_ramasseeParSonAuteur() {
        val ctx = FakePetCtx()
        val b = ctx.addPet("b", seed = 4)
        val toys = ToyController(ctx)
        ctx.on(b, PetEvent.Fed(Food.CARROT))
        ctx.world.asPet(b) { toys.spawnPoop(300f, b) }
        toys.spawnPoop(600f)
        assertEquals(1, toys.poopCount(b.id))
        assertEquals(1, toys.poopCount(ctx.first.id))
        assertEquals(2, toys.poopCount())
        val bPoop = ctx.world.props.first { it.owner == b.id }
        ctx.saved.clear(); ctx.speakers.clear()
        toys.pickUp(bPoop)
        assertEquals(listOf(b.id), ctx.saved)       // PoopRemoved compte pour lui (et le sauvegarde)
        assertEquals(listOf(b.id), ctx.speakers)    // c'est lui qui remercie
        toys.removePoopsOf(ctx.first.id)
        assertEquals(0, toys.poopCount())
    }

    @Test fun etatDeBetise_parAnimal() {
        val ctx = FakePetCtx()
        val b = ctx.addPet("b")
        val m = MischiefController(ctx)
        b.mind.mischief.hidingView = "launcher#p2"
        assertEquals("launcher#p2", m.hidingView)
        assertNull(ctx.first.mind.mischief.hidingView)
        m.endHiding(b)
        assertNull(m.hidingView)
    }

    @Test fun voyage_chacunDecide() {
        val ctx = FakePetCtx()
        val b = ctx.addPet("b", x = 900f, seed = 2)
        val beh = PetBehavior(ctx, Hooks())
        val app = ForegroundAppWatcher.AppInfo("com.app.notes", "Notes", -1, isHome = false)
        for (p in listOf(ctx.first, b)) ctx.world.asPet(p) { beh.onAppChanged(p, app) }
        assertEquals(Act.TRAVEL_OUT, ctx.first.act)
        assertEquals(Act.TRAVEL_OUT, b.act)
        // chacun court vers son bord le plus proche
        assertTrue(ctx.first.body.targetX < 0f)
        assertTrue(b.body.targetX > ctx.arena.screenW)
        assertNotNull(b.mind.travelComment)
    }

    /** Évolution 4b : l'explorateur repart deux fois plus tôt (3 s), les autres gardent la pause de 6 s. */
    @Test fun voyage_explorateurRepartPlusTot() {
        val ctx = FakePetCtx()
        val e = ctx.addPet("e", x = 900f, seed = 2, traits = Traits(Personality.EXPLORER))
        val beh = PetBehavior(ctx, Hooks())
        val app = ForegroundAppWatcher.AppInfo("com.app.notes", "Notes", -1, isHome = false)
        fun travel() = listOf(ctx.first, e).onEach { p ->
            p.setAct(Act.IDLE)
            ctx.world.asPet(p) { beh.onAppChanged(p, app) }
        }.map { it.act }
        assertEquals(listOf(Act.TRAVEL_OUT, Act.TRAVEL_OUT), travel())
        ctx.clock.advance(Tuning.TRAVEL_COOLDOWN_MS / 2)
        assertEquals(listOf(Act.IDLE, Act.TRAVEL_OUT), travel())            // 3 s : seul l'explorateur repart
        ctx.clock.advance(Tuning.TRAVEL_COOLDOWN_MS / 2 - 1)
        assertEquals(listOf(Act.IDLE, Act.IDLE), travel())
        ctx.clock.advance(1)
        assertEquals(Act.TRAVEL_OUT, travel().first())                       // 6 s : le résident aussi
    }
}
