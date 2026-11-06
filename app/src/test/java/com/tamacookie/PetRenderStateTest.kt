package com.tamacookie

import com.tamacookie.feature.toy.Food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Rendu : état affiché tiré du [Pet] ([PetRenderState]) et animation ambiante ([AmbientAnimator]). */
class PetRenderStateTest {
    private val ctx = FakePetCtx()
    private val pet = ctx.pet
    private fun st(f: (PetStats) -> PetStats) { pet.stats = f(pet.stats) }

    @Test fun `l'etat affiche est tire du corps de l'animal`() {
        val b = pet.body
        b.pose = Pose.WALK; b.facing = -1f; b.walkPhase = 0.4f; b.eatProgress = 0.25f
        b.eatingFood = Food.STRAWBERRY
        val s = PetRenderState.of(pet)
        assertEquals(Pose.WALK, s.pose)
        assertEquals(-1f, s.facing)
        assertEquals(0.4f, s.walkPhase)
        assertEquals(0.25f, s.eatProgress)
        assertEquals(Food.STRAWBERRY, s.food)
        assertEquals(b.phys.airVy, s.airVy)
    }

    @Test fun `expression forcee, sinon celle de l'humeur`() {
        assertEquals(Expr.NORMAL, PetRenderState.of(pet).expr)
        st { it.copy(satiety = 10f) }
        assertEquals(Expr.SAD, PetRenderState.of(pet).expr)          // affamé
        pet.express(Expr.SURPRISED, 1f)
        assertEquals(Expr.SURPRISED, PetRenderState.of(pet).expr)
        pet.mind.tick(2f)
        assertEquals(Expr.SAD, PetRenderState.of(pet).expr)
        st { it.copy(sleeping = true) }
        assertEquals(Expr.SLEEPY, PetRenderState.of(pet).expr)
    }

    @Test fun `humeur vers expression`() {
        val m = Mood.entries.associateWith { PetRenderState.moodExpr(it) }
        assertEquals(Expr.NORMAL, m[Mood.HAPPY])
        assertEquals(Expr.NORMAL, m[Mood.OK])
        assertEquals(Expr.NORMAL, m[Mood.DIRTY])
        assertEquals(Expr.SAD, m[Mood.HUNGRY])
        assertEquals(Expr.SAD, m[Mood.SAD])
        assertEquals(Expr.SLEEPY, m[Mood.SLEEPY])
        assertEquals(Expr.SICK, m[Mood.SICK])
    }

    @Test fun `sale et tres sale`() {
        st { it.copy(cleanliness = 50f, poops = 0) }
        assertFalse(PetRenderState.of(pet).dirty)
        st { it.copy(poops = 3) }
        assertTrue(PetRenderState.of(pet).dirty)
        st { it.copy(poops = 0, cleanliness = 40f) }
        val s = PetRenderState.of(pet)
        assertTrue(s.dirty); assertFalse(s.veryDirty)
        st { it.copy(cleanliness = 10f) }
        assertTrue(PetRenderState.of(pet).veryDirty)
    }

    @Test fun `immuable, comparable d'une image a l'autre, apparence copiee`() {
        val a = PetRenderState.of(pet)
        assertEquals(a, PetRenderState.of(pet))         // rien n'a changé : rien à redessiner
        pet.look = pet.look.copy(fur = Fur.CHOCOLATE)      // nouvelle apparence…
        assertEquals(Fur.SNOW, a.look.fur)                 // … l'état déjà affiché ne change pas
        assertNotEquals(a, PetRenderState.of(pet))
    }

    @Test fun `chaque animal a son propre etat`() {
        val other = testPet(BunnyPhysics(1f), Random(1), id = "visiteur")
        other.body.facing = -1f; other.body.pose = Pose.SLEEP
        pet.body.facing = 1f
        ctx.world.add(other)
        val states = ctx.world.pets.values.associate { it.id to PetRenderState.of(it) }
        assertEquals(2, states.size)
        assertEquals(1f, states.getValue(pet.id).facing)
        assertEquals(Pose.SLEEP, states.getValue(other.id).pose)
    }

    // ---------------------------------------------------------------------
    //  Animation ambiante
    // ---------------------------------------------------------------------
    private fun run(s: PetRenderState, seconds: Float, a: AmbientAnimator = AmbientAnimator(Random(7))): AmbientAnimator {
        var t = 0f
        while (t < seconds) { a.update(1f / 30f, s, 120f, 140f, 1f); t += 1f / 30f }
        return a
    }

    @Test fun `les particules suivent la pose et l'humeur`() {
        /** Types de particules vus pendant 10 s dans l'état [s]. */
        fun types(s: PetRenderState): Set<PType> {
            val a = AmbientAnimator(Random(7))
            val seen = HashSet<PType>()
            repeat(300) { a.update(1f / 30f, s, 120f, 140f, 1f); a.particles.forEach { seen += it.type } }
            return seen
        }
        assertTrue(types(PetRenderState()).isEmpty())
        assertEquals(setOf(PType.ZZZ), types(PetRenderState(pose = Pose.SLEEP)))
        assertEquals(setOf(PType.CRUMB), types(PetRenderState(pose = Pose.EAT)))
        assertEquals(setOf(PType.STAR), types(PetRenderState(expr = Expr.DIZZY)))
        assertEquals(setOf(PType.DROP), types(PetRenderState(expr = Expr.SICK)))
        assertEquals(setOf(PType.BUBBLE), types(PetRenderState(veryDirty = true)))
    }

    @Test fun `aleatoire injecte, animation deterministe`() {
        val s = PetRenderState(pose = Pose.SLEEP, expr = Expr.SAD)
        val a = run(s, 6f, AmbientAnimator(Random(3)))
        val b = run(s, 6f, AmbientAnimator(Random(3)))
        assertEquals(a.particles.map { it.type to it.x }, b.particles.map { it.type to it.x })
        assertEquals(a.blinking, b.blinking)
    }

    @Test fun `il cligne des yeux de temps en temps`() {
        val a = AmbientAnimator(Random(1))
        val s = PetRenderState()
        var blinks = 0
        var was = false
        repeat(30 * 20) {
            a.update(1f / 30f, s, 120f, 140f, 1f)
            if (a.blinking && !was) blinks++
            was = a.blinking
        }
        assertTrue("$blinks", blinks in 3..10)          // toutes les 2 à 6 s
    }

    @Test fun `emettre ajoute des particules qui finissent par disparaitre`() {
        val a = AmbientAnimator(Random(1))
        a.emit(PType.HEART, 3, 120f, 140f, 1f, 1f)
        assertEquals(3, a.particles.size)
        run(PetRenderState(pose = Pose.WALK), 2f, a)
        assertTrue(a.particles.isEmpty())
    }

    @Test fun `rien a redessiner quand rien ne bouge`() {
        val a = AmbientAnimator(Random(1))
        val still = PetRenderState(pose = Pose.WALK)
        // ni clignement (2,5 s), ni oreille (4 s), ni particule, et la marche ne dépend que de walkPhase
        assertFalse(a.update(1f / 30f, still, 120f, 140f, 1f))
        assertFalse(a.update(1f / 30f, PetRenderState(pose = Pose.AIR), 120f, 140f, 1f))
        // la respiration au repos dépend du temps
        assertTrue(a.update(1f / 30f, PetRenderState(pose = Pose.IDLE), 120f, 140f, 1f))
        assertTrue(a.update(1f / 30f, still.copy(expr = Expr.HAPPY), 120f, 140f, 1f))
        assertTrue(a.update(1f / 30f, still.copy(look = Look(accessory = Accessory.SCARF)), 120f, 140f, 1f))
        a.emit(PType.NOTE, 1, 120f, 140f, 1f, 1f)
        assertTrue(a.update(1f / 30f, still, 120f, 140f, 1f))
    }
}
