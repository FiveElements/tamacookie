package com.tamacookie

import com.tamacookie.feature.station.BowlStation
import com.tamacookie.feature.station.StationKind
import com.tamacookie.feature.toy.Food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Il va de lui-même à la gamelle ([Act.GOTO_BOWL]) : le cerveau seul (tirage seulement si elle l'attend),
 * les conditions ([StationController.bowlAvailable]), la boucle ([PetBehavior]) et deux animaux.
 */
class BowlVisitTest {

    // =====================================================================
    //  Cerveau seul
    // =====================================================================
    private fun input(
        bowl: Boolean, elsewhere: Boolean = false, chestVisible: Boolean = false, satiety: Float = 30f,
    ) = BrainInput(
        energy = 80f, satiety = satiety, happiness = 80f, personality = Personality.CUDDLY, hour = 12,
        x = 500f, facing = 1f, minX = 40f, maxX = 1040f, screenW = 1080f,
        elsewhere = elsewhere, chestVisible = chestVisible, nowMs = 1_000_000L, bowlAvailable = bowl,
    )

    @Test fun `cerveau - gamelle absente ou hors de sa page, memes decisions et memes tirages`() {
        for (seed in 0 until 300) {
            val ref = Random(seed)
            val refDec = BunnyBrain(ref).decide(input(bowl = false))
            assertNotEquals(Decision.GoToBowl, refDec)
            // Sur une autre page, la gamelle n'est pas tirée : même consommation de l'aléatoire
            val refElsewhere = Random(seed)
            val decElsewhere = BunnyBrain(refElsewhere).decide(input(bowl = false, elsewhere = true))
            val r = Random(seed)
            assertEquals(decElsewhere, BunnyBrain(r).decide(input(bowl = true, elsewhere = true)))
            assertEquals(refElsewhere.nextLong(), r.nextLong())
        }
    }

    @Test fun `cerveau - la gamelle qui l'attend est choisie environ une fois sur deux`() {
        var n = 0
        for (seed in 0 until 400) if (BunnyBrain(Random(seed)).decide(input(bowl = true)) == Decision.GoToBowl) n++
        assertTrue("$n", n in 150..250)
    }

    @Test fun `cerveau - jamais depuis une autre page`() {
        for (seed in 0 until 300) {
            assertNotEquals(Decision.GoToBowl, BunnyBrain(Random(seed)).decide(input(bowl = true, elsewhere = true)))
        }
    }

    @Test fun `cerveau - le coffre garde sa priorite`() {
        for (seed in 0 until 300) {
            val chestOnly = BunnyBrain(Random(seed)).decide(input(bowl = false, chestVisible = true))
            val both = BunnyBrain(Random(seed)).decide(input(bowl = true, chestVisible = true))
            if (chestOnly == Decision.GoToChest) assertEquals(Decision.GoToChest, both)
        }
    }

    // =====================================================================
    //  Conditions et boucle (FakePetCtx)
    // =====================================================================
    private class World1(seed: Int = 42, installBowl: Boolean = true, satiety: Float = 30f) {
        val ctx = FakePetCtx(seed)
        val toys = ToyController(ctx)
        val sc = StationController(ctx, toys, FakeStationPanels())
        val hooks = object : PetBehavior.Hooks {
            override fun chestVisible(p: Pet) = false
            override val lastSelfServe = 0L
            override fun goToChest(p: Pet, now: Long) {}
            override fun seekAttention(p: Pet) = false
            override fun startPageWander(p: Pet) {}
            override fun hour() = 12
            override fun bowlAvailable(p: Pet) = sc.bowlAvailable(p)
            override fun goToBowl(p: Pet) = sc.goToBowl(p)
        }
        val behavior = PetBehavior(ctx, hooks)
        val pet get() = ctx.pet

        init {
            toys.registerActs(ctx.acts)
            sc.registerActs(ctx.acts)
            sc.create()
            ctx.pages.currentView = "com.app.notes"
            if (installBowl) { sc.toggleStation(StationKind.BOWL); sc.layout(); ctx.overlay.flush() }
            for (p in ctx.world.pets.values) p.stats = p.stats.copy(satiety = satiety)
        }

        /** Ce que fait la boucle du service pour chaque animal affiché. */
        fun frame(dt: Float = 1f / 60f) {
            val pets = ArrayList(ctx.world.shown(ArrayList()))
            for (p in pets) { p.body.actTime += dt; p.mind.tick(dt) }
            for (p in pets) ctx.world.asPet(p) { behavior.update(p, dt) }
        }

        /** Il a fini ce qu'il faisait : il décide. */
        fun decide(p: Pet = pet) = ctx.world.asPet(p) { p.setAct(Act.IDLE, 0f); behavior.decideNext(p) }
    }

    @Test fun `conditions - gamelle affichee et faim`() {
        val w = World1()
        assertTrue(w.sc.bowlAvailable(w.pet))
        assertFalse(World1(installBowl = false).let { it.sc.bowlAvailable(it.pet) })
        assertFalse(World1(satiety = 85f).let { it.sc.bowlAvailable(it.pet) })
        // Nourriture au sol : elle passe avant
        w.toys.feed(Food.CARROT)
        assertFalse(w.sc.bowlAvailable(w.pet))
    }

    @Test fun `conditions - gamelle occupee ou sur une autre page`() {
        val w = World1()
        val other = w.ctx.addPet("b")
        w.sc.enterStation(StationKind.BOWL, p = other)
        assertFalse(w.sc.bowlAvailable(w.pet))
        val w2 = World1()
        w2.ctx.pages.currentView = "com.app.other"
        w2.sc.layout(); w2.ctx.overlay.flush()
        assertFalse(w2.sc.bowlAvailable(w2.pet))
    }

    @Test fun `boucle - sans gamelle affichee, aucun tirage de plus`() {
        for (seed in 0 until 100) {
            // gamelle rangée / pas faim / hooks par défaut (comme toutes les traces seedées d'avant)
            val cases = listOf(World1(seed, installBowl = false), World1(seed, satiety = 90f))
            val ref = World1(seed, installBowl = false)
            val refBehavior = PetBehavior(ref.ctx, object : PetBehavior.Hooks by ref.hooks {
                override fun bowlAvailable(p: Pet) = false
            })
            ref.ctx.world.asPet(ref.pet) { ref.pet.setAct(Act.IDLE, 0f); refBehavior.decideNext(ref.pet) }
            val refNext = ref.pet.mind.rnd.nextLong()
            for (w in cases) {
                w.decide()
                assertEquals(ref.pet.act, w.pet.act)
                assertEquals(ref.pet.body.targetX, w.pet.body.targetX, 0f)
                assertEquals(refNext, w.pet.mind.rnd.nextLong())
            }
        }
    }

    /** Une graine où le cerveau choisit la gamelle. */
    private fun seedThatGoes(): Int = (0 until 200).first { s ->
        World1(s).also { it.decide() }.pet.act == Act.GOTO_BOWL
    }

    @Test fun `boucle - il y va, y entre et mange`() {
        val w = World1(seedThatGoes())
        val before = w.ctx.lastInteraction
        w.ctx.clock.advance(5_000)
        w.decide()
        assertEquals(Act.GOTO_BOWL, w.pet.act)
        assertTrue(w.ctx.said.any { it in BowlStation.GOING })
        var n = 0
        while (w.ctx.world.stationOf(w.pet) == null && n++ < 600) w.frame()
        assertEquals(StationKind.BOWL, w.ctx.world.stationOf(w.pet))
        assertEquals(Act.IN_STATION, w.pet.act)
        // ce n'est pas l'utilisateur qui s'est occupé de lui : le compteur d'attention ne bouge pas
        assertEquals(before, w.ctx.lastInteraction)
        repeat(60 * 5) { w.frame() }
        assertEquals(30f + BowlStation.FOOD.satiety, w.pet.stats.satiety, 0.5f)
        assertEquals(null, w.ctx.world.stationOf(w.pet))
    }

    @Test fun `boucle - il renonce si on range la gamelle en chemin`() {
        val w = World1(seedThatGoes())
        w.decide()
        assertEquals(Act.GOTO_BOWL, w.pet.act)
        w.sc.toggleStation(StationKind.BOWL)        // rangée
        w.sc.layout(); w.ctx.overlay.flush()
        w.frame()
        assertEquals(Act.IDLE, w.pet.act)
        assertEquals(null, w.ctx.world.stationOf(w.pet))
    }

    @Test fun `deux animaux - un seul part vers la gamelle`() {
        val w = World1(seedThatGoes())
        val b = w.ctx.addPet("b", x = 700f)
        b.stats = b.stats.copy(satiety = 30f)
        w.decide()
        assertEquals(Act.GOTO_BOWL, w.pet.act)
        assertFalse(w.ctx.world.asPet(b) { w.sc.bowlAvailable(b) })
        // Il ne tire même pas au sort : décisions et tirages de b identiques à un monde sans gamelle
        for (seed in 0 until 50) {
            val ref = World1(seed, installBowl = false)
            val rb = ref.ctx.addPet("b", x = 700f, seed = seed)
            val w2 = World1(seedThatGoes())
            w2.decide()
            val b2 = w2.ctx.addPet("b", x = 700f, seed = seed)
            b2.stats = b2.stats.copy(satiety = 30f); rb.stats = rb.stats.copy(satiety = 30f)
            ref.decide(rb); w2.decide(b2)
            assertEquals(rb.act, b2.act)
            assertEquals(rb.mind.rnd.nextLong(), b2.mind.rnd.nextLong())
        }
        // Une fois le premier dans la gamelle, l'occupation la garde (capacité 1)
        var n = 0
        while (w.ctx.world.stationOf(w.pet) == null && n++ < 600) w.frame()
        assertFalse(w.ctx.world.asPet(b) { w.sc.bowlAvailable(b) })
    }

    @Test fun `director - un seul en route`() {
        val ctx = FakePetCtx()
        val a = ctx.first
        val b = ctx.addPet("b")
        assertTrue(ctx.director.mayHeadTo(a, StationKind.BOWL))
        a.setAct(Act.GOTO_BOWL)
        assertFalse(ctx.director.mayHeadTo(b, StationKind.BOWL))
        assertTrue(ctx.director.mayHeadTo(a, StationKind.BOWL))
    }

    @Test fun `lecteur d'ecran - il va a la gamelle`() {
        assertEquals("va à la gamelle", A11yTexts.activity(Act.GOTO_BOWL))
    }
}
