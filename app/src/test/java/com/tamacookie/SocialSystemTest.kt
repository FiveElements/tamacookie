package com.tamacookie

import com.tamacookie.feature.station.StationKind
import com.tamacookie.feature.toy.BallToy
import com.tamacookie.social.CarrotDispute
import com.tamacookie.social.FollowSocial
import com.tamacookie.social.HugSocial
import com.tamacookie.social.SocialSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Perception des autres animaux, interactions à deux (suivre, câlin, balle, dispute), avec des `Random` seedés. */
class SocialSystemTest {
    private val ctx = FakePetCtx(seed = 1)
    private val a get() = ctx.first
    private val sys = SocialSystem(ctx).also { it.registerActs(ctx.acts) }

    private fun input(nearest: Neighbor?) = BrainInput(
        energy = 70f, satiety = 70f, happiness = 70f, personality = Personality.CURIOUS, hour = 12,
        x = 500f, facing = 1f, minX = 40f, maxX = 1040f, screenW = 1080f, nearest = nearest,
    )

    /** Une image de social pour les animaux engagés (comme la boucle : chacun avec lui pour animal courant). */
    private fun step(dt: Float = 0.05f) {
        for (p in ctx.world.shown(ArrayList())) {
            p.body.actTime += dt; p.mind.tick(dt)
            if (p.act == Act.SOCIAL) ctx.world.asPet(p) { ctx.acts.handlerFor(p)?.update(p, dt) ?: p.setAct(Act.IDLE) }
        }
        sys.update(dt)
    }

    // --- Perception ---
    @Test fun seul_personneAPercevoir() {
        assertNull(sys.nearest(a))
    }

    @Test fun perception_lePlusProcheDisponible() {
        val b = ctx.addPet("b", x = 800f)
        val c = ctx.addPet("c", x = 650f)
        c.setAct(Act.SIT)
        val n = sys.nearest(a)!!
        assertEquals(c.id, n.id)
        assertEquals(Act.SIT, n.act)
        assertEquals(150f, n.distance)
        ctx.world.station(StationKind.BATH).enter(c.id)            // dans une station : indisponible
        assertEquals(b.id, sys.nearest(a)!!.id)
        b.body.page = 2                                               // sur une autre page
        assertNull(sys.nearest(a))
        b.body.page = 0; ctx.away += b.id                             // fenêtre retirée
        assertNull(sys.nearest(a))
    }

    // --- Cerveau ---
    @Test fun cerveau_seul_jamaisDeSocial_etMemesTirages() {
        for (seed in 0 until 200) {
            val d1 = BunnyBrain(Random(seed)).decide(input(null))
            val d2 = BunnyBrain(Random(seed)).decide(input(null))
            assertEquals(d1, d2)
            assertFalse(d1 is Decision.Socialize)
        }
    }

    @Test fun cerveau_unVoisinTranquille_ilVaLeVoirParfois() {
        val n = Neighbor(PetId("b"), Act.IDLE, 300f)
        val social = (0 until 400).count { BunnyBrain(Random(it)).decide(input(n)) is Decision.Socialize }
        assertTrue("$social", social in 60..160)                     // ≈ 25 % (après les autres tirages)
        // trop loin, ou occupé : jamais
        val far = n.copy(distance = BunnyBrain.SOCIAL_RANGE_DP + 1f)
        val busy = n.copy(act = Act.SLEEP)
        for (seed in 0 until 200) {
            assertFalse(BunnyBrain(Random(seed)).decide(input(far)) is Decision.Socialize)
            assertFalse(BunnyBrain(Random(seed)).decide(input(busy)) is Decision.Socialize)
        }
    }

    // --- Cascade ---
    @Test fun cascade_deterministeAvecUnRandomSeede() {
        fun first(seed: Int): String {
            val c = FakePetCtx(seed = seed)
            val s = SocialSystem(c).also { it.registerActs(c.acts) }
            val b = c.addPet("b", x = 800f, seed = seed + 1)
            assertTrue(s.tryStart(c.first, s.nearest(c.first)!!) || c.first.mind.social == null)
            return c.first.mind.social?.interaction?.javaClass?.simpleName + "/" + b.mind.social?.interaction?.javaClass?.simpleName
        }
        for (seed in 0 until 20) assertEquals(first(seed), first(seed))
        val kinds = (0 until 60).map(::first).toSet()
        assertTrue(kinds.toString(), "HugSocial/HugSocial" in kinds)
        assertTrue(kinds.toString(), "FollowSocial/null" in kinds)
    }

    @Test fun pasDeuxInteractionsALaFois() {
        val b = ctx.addPet("b", x = 800f)
        HugSocial.start(sys, a, b)
        assertFalse(sys.tryStart(a, sys.nearest(a) ?: Neighbor(b.id, Act.IDLE, 300f)))
    }

    // --- Suivre ---
    @Test fun suivre_ilRejointLeDosDuMeneur() {
        val b = ctx.addPet("b", x = 900f)
        b.body.facing = 1f
        FollowSocial.start(sys, a, b)
        assertEquals(Act.SOCIAL, a.act)
        assertNull(b.mind.social)                                    // le meneur n'est pas engagé
        assertEquals(listOf(a.id, b.id), ctx.speakers)
        repeat(100) { step() }
        val behind = b.body.fx - FollowSocial.BEHIND_DP
        assertTrue("${a.body.fx}", abs(a.body.fx - behind) <= 12f)
        repeat(300) { step() }                                       // durée écoulée : il reprend sa vie
        assertEquals(Act.IDLE, a.act)
        assertNull(a.mind.social)
    }

    // --- Câlin ---
    @Test fun calin_lesDeuxSeRejoignent_coeurs_puisFin() {
        val b = ctx.addPet("b", x = 800f)
        a.body.fx = 400f
        HugSocial.start(sys, a, b)
        assertEquals(Act.SOCIAL, a.act); assertEquals(Act.SOCIAL, b.act)
        var hugged = false
        repeat(400) {
            step()
            if (a.mind.social?.phase == 1) {
                hugged = true
                assertTrue(abs(abs(a.body.fx - b.body.fx) - HugSocial.CLOSE_DP) < 8f)
                assertEquals(-a.body.facing, b.body.facing)        // face à face
            }
        }
        assertTrue(hugged)
        assertTrue(PType.HEART in ctx.emitted)
        assertEquals(Act.IDLE, a.act); assertEquals(Act.IDLE, b.act)
        assertNull(a.mind.social); assertNull(b.mind.social)
    }

    @Test fun calin_interrompuQuandOnAttrapeLUn() {
        val b = ctx.addPet("b", x = 800f)
        HugSocial.start(sys, a, b)
        b.setAct(Act.DRAGGED)                                        // l'utilisateur l'attrape
        step()
        assertNull(b.mind.social)                                    // engagement oublié
        step()
        assertEquals(Act.IDLE, a.act)                               // l'autre ne reste pas à attendre
        assertNull(a.mind.social)
    }

    // --- Dispute ---
    @Test fun dispute_tranchéeParLArbitre_miseEnScene() {
        val b = ctx.addPet("b", x = 800f)
        val food = Prop(ctx.world.newPropId(), com.tamacookie.feature.toy.Food.CARROT.spec, 30, 30)
        ctx.director.loseFood(a, food, b)
        sys.update(0.05f)
        assertEquals(Act.SOCIAL, a.act)
        assertSame(CarrotDispute, a.mind.social!!.interaction)
        assertEquals(listOf(a.id, b.id), ctx.speakers)              // le perdant râle, le gagnant se vante
        repeat((CarrotDispute.DURATION_S / 0.05f).toInt() + 2) { step() }
        assertEquals(Act.IDLE, a.act)
    }

    // --- Balle ---
    @Test fun balle_chaqueCoupEstUnePasseVersLAutre() {
        val b = ctx.addPet("b", x = 900f)
        a.body.fx = 300f
        val ball = Prop(ctx.world.newPropId(), BallToy, 42, 42)
        ctx.world.props += ball
        a.setAct(Act.PLAY); b.setAct(Act.PLAY)
        ball.x = 320f; ball.vx = -500f; ball.vy = -600f; ball.onGround = false
        a.mind.kickCooldown = 0.7f                                   // il vient de frapper… vers la gauche
        sys.update(0.016f)
        assertTrue(ball.vx > 0f)                                     // la passe part vers b (à droite)
        assertEquals(500f, ball.vx)
        val v = ball.vx
        sys.update(0.016f)                                           // une seule passe par coup
        assertEquals(v, ball.vx)
        // pas de passe si l'autre ne joue pas
        b.setAct(Act.IDLE); a.mind.kickCooldown = 0.7f; ball.vx = -400f
        sys.update(0.016f)
        assertEquals(-400f, ball.vx)
    }

    // --- Boucle complète ---
    @Test fun deuxAnimaux_laBoucleLesFaitSeRencontrer_deterministe() {
        fun run(seed: Int): Pair<Int, String> {
            val c = FakePetCtx(seed = seed)
            val s = SocialSystem(c).also { it.registerActs(c.acts) }
            val b = c.addPet("b", x = 800f, seed = seed * 31 + 7)
            val beh = PetBehavior(c, object : PetBehavior.Hooks {
                override fun chestVisible(p: Pet) = false
                override val lastSelfServe = 0L
                override fun goToChest(p: Pet, now: Long) {}
                override fun seekAttention(p: Pet) = false
                override fun startPageWander(p: Pet) {}
                override fun hour() = 12
                override fun nearest(p: Pet) = s.nearest(p)
                override fun socialize(p: Pet, other: Neighbor) = s.tryStart(p, other)
            })
            var socialFrames = 0
            val sb = StringBuilder()
            repeat(3000) {
                val pets = listOf(c.first, b)
                for (p in pets) { p.body.actTime += 0.05f; p.mind.tick(0.05f) }
                for (p in pets) c.world.asPet(p) { beh.checkPlatform(p); beh.update(p, 0.05f) }
                s.update(0.05f)
                if (c.first.act == Act.SOCIAL || b.act == Act.SOCIAL) socialFrames++
                if (it % 100 == 0) sb.append(c.first.act).append(b.act)
            }
            return socialFrames to sb.toString()
        }
        val r = run(4)
        assertTrue("${r.first}", r.first > 0)
        assertEquals(r, run(4))
        assertNotNull(r.second)
    }
}
