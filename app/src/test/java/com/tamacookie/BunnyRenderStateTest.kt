package com.tamacookie

import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.BunnyView.Pose
import com.tamacookie.feature.toy.Food
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Rendu : état affiché tiré du [Pet] ([BunnyRenderState]) et animation ambiante ([AmbientAnimator]). */
class BunnyRenderStateTest {
    private val ctx = FakePetCtx()
    private val pet = ctx.pet
    private val st = pet.stats

    @Test fun `l'etat affiche est tire du corps de l'animal`() {
        val b = pet.body
        b.pose = Pose.WALK; b.facing = -1f; b.walkPhase = 0.4f; b.eatProgress = 0.25f
        b.eatingFood = Food.STRAWBERRY
        val s = BunnyRenderState.of(pet)
        assertEquals(Pose.WALK, s.pose)
        assertEquals(-1f, s.facing)
        assertEquals(0.4f, s.walkPhase)
        assertEquals(0.25f, s.eatProgress)
        assertEquals(Food.STRAWBERRY, s.food)
        assertEquals(b.phys.airVy, s.airVy)
    }

    @Test fun `expression forcee, sinon celle de l'humeur`() {
        assertEquals(Expr.NORMAL, BunnyRenderState.of(pet).expr)
        st.satiety = 10f
        assertEquals(Expr.SAD, BunnyRenderState.of(pet).expr)          // affamé
        pet.express(Expr.SURPRISED, 1f)
        assertEquals(Expr.SURPRISED, BunnyRenderState.of(pet).expr)
        pet.mind.tick(2f)
        assertEquals(Expr.SAD, BunnyRenderState.of(pet).expr)
        st.sleeping = true
        assertEquals(Expr.SLEEPY, BunnyRenderState.of(pet).expr)
    }

    @Test fun `humeur vers expression`() {
        val m = PetState.Mood.entries.associateWith { BunnyRenderState.moodExpr(it) }
        assertEquals(Expr.NORMAL, m[PetState.Mood.HAPPY])
        assertEquals(Expr.NORMAL, m[PetState.Mood.OK])
        assertEquals(Expr.NORMAL, m[PetState.Mood.DIRTY])
        assertEquals(Expr.SAD, m[PetState.Mood.HUNGRY])
        assertEquals(Expr.SAD, m[PetState.Mood.SAD])
        assertEquals(Expr.SLEEPY, m[PetState.Mood.SLEEPY])
        assertEquals(Expr.SICK, m[PetState.Mood.SICK])
    }

    @Test fun `sale et tres sale`() {
        st.cleanliness = 50f; st.poops = 0
        assertFalse(BunnyRenderState.of(pet).dirty)
        st.poops = 3
        assertTrue(BunnyRenderState.of(pet).dirty)
        st.poops = 0; st.cleanliness = 40f
        val s = BunnyRenderState.of(pet)
        assertTrue(s.dirty); assertFalse(s.veryDirty)
        st.cleanliness = 10f
        assertTrue(BunnyRenderState.of(pet).veryDirty)
    }

    @Test fun `immuable, comparable d'une image a l'autre, apparence copiee`() {
        val a = BunnyRenderState.of(pet)
        assertEquals(a, BunnyRenderState.of(pet))         // rien n'a changé : rien à redessiner
        st.look.fur = 3                                    // l'apparence du jeu est mutable…
        assertEquals(0, a.look.fur)                        // … l'état affiché, non
        assertNotEquals(a, BunnyRenderState.of(pet))
    }

    @Test fun `chaque animal a son propre etat`() {
        val other = Pet(Identity(PetId("visiteur")) { "Visiteur" }, newPet(), Body(BunnyPhysics(1f)), Mind(Random(1)))
        other.body.facing = -1f; other.body.pose = Pose.SLEEP
        pet.body.facing = 1f
        ctx.world.add(other)
        val states = ctx.world.pets.values.associate { it.id to BunnyRenderState.of(it) }
        assertEquals(2, states.size)
        assertEquals(1f, states.getValue(pet.id).facing)
        assertEquals(Pose.SLEEP, states.getValue(other.id).pose)
    }

    // ---------------------------------------------------------------------
    //  Animation ambiante
    // ---------------------------------------------------------------------
    private fun run(s: BunnyRenderState, seconds: Float, a: AmbientAnimator = AmbientAnimator(Random(7))): AmbientAnimator {
        var t = 0f
        while (t < seconds) { a.update(1f / 30f, s, 120f, 140f, 1f); t += 1f / 30f }
        return a
    }

    @Test fun `les particules suivent la pose et l'humeur`() {
        /** Types de particules vus pendant 10 s dans l'état [s]. */
        fun types(s: BunnyRenderState): Set<PType> {
            val a = AmbientAnimator(Random(7))
            val seen = HashSet<PType>()
            repeat(300) { a.update(1f / 30f, s, 120f, 140f, 1f); a.particles.forEach { seen += it.type } }
            return seen
        }
        assertTrue(types(BunnyRenderState()).isEmpty())
        assertEquals(setOf(PType.ZZZ), types(BunnyRenderState(pose = Pose.SLEEP)))
        assertEquals(setOf(PType.CRUMB), types(BunnyRenderState(pose = Pose.EAT)))
        assertEquals(setOf(PType.STAR), types(BunnyRenderState(expr = Expr.DIZZY)))
        assertEquals(setOf(PType.DROP), types(BunnyRenderState(expr = Expr.SICK)))
        assertEquals(setOf(PType.BUBBLE), types(BunnyRenderState(veryDirty = true)))
    }

    @Test fun `aleatoire injecte, animation deterministe`() {
        val s = BunnyRenderState(pose = Pose.SLEEP, expr = Expr.SAD)
        val a = run(s, 6f, AmbientAnimator(Random(3)))
        val b = run(s, 6f, AmbientAnimator(Random(3)))
        assertEquals(a.particles.map { it.type to it.x }, b.particles.map { it.type to it.x })
        assertEquals(a.blinking, b.blinking)
    }

    @Test fun `il cligne des yeux de temps en temps`() {
        val a = AmbientAnimator(Random(1))
        val s = BunnyRenderState()
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
        run(BunnyRenderState(pose = Pose.WALK), 2f, a)
        assertTrue(a.particles.isEmpty())
    }

    @Test fun `rien a redessiner quand rien ne bouge`() {
        val a = AmbientAnimator(Random(1))
        val still = BunnyRenderState(pose = Pose.WALK)
        // ni clignement (2,5 s), ni oreille (4 s), ni particule, et la marche ne dépend que de walkPhase
        assertFalse(a.update(1f / 30f, still, 120f, 140f, 1f))
        assertFalse(a.update(1f / 30f, BunnyRenderState(pose = Pose.AIR), 120f, 140f, 1f))
        // la respiration au repos dépend du temps
        assertTrue(a.update(1f / 30f, BunnyRenderState(pose = Pose.IDLE), 120f, 140f, 1f))
        assertTrue(a.update(1f / 30f, still.copy(expr = Expr.HAPPY), 120f, 140f, 1f))
        assertTrue(a.update(1f / 30f, still.copy(look = Look(accessory = 4)), 120f, 140f, 1f))
        a.emit(PType.NOTE, 1, 120f, 140f, 1f, 1f)
        assertTrue(a.update(1f / 30f, still, 120f, 140f, 1f))
    }
}
