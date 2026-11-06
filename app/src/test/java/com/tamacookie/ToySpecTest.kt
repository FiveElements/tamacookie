package com.tamacookie

import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.feature.toy.BalloonToy
import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.FrisbeeToy
import com.tamacookie.feature.toy.Launch
import com.tamacookie.feature.toy.Poop
import com.tamacookie.feature.toy.Toy
import com.tamacookie.feature.toy.ToyCtx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jouets et nourriture en plugins (`feature/toy/`) : exhaustivité, identité sauvegardée, propriétés. */
class ToySpecTest {

    @Test
    fun `chaque jouet a un spec qui le designe`() {
        for (t in Toy.entries) {
            val s = t.spec
            assertSame(t, s.toy)
            assertNull("$t", s.food)
            assertTrue("$t", s.wDp > 0 && s.hDp > 0)
            assertTrue("$t : on le lance au doigt", s.touchable)
            assertTrue("$t", s.startLines.isNotEmpty())
        }
        // Un spec par jouet (pas de copier-coller d'un objet sur deux entrées)
        assertEquals(Toy.entries.size, Toy.entries.map { it.spec }.toSet().size)
    }

    @Test
    fun `chaque nourriture a un spec qui la designe`() {
        for (f in Food.entries) {
            assertSame(f, f.spec.food)
            assertNull("$f", f.spec.toy)
            assertFalse("$f : elle tombe, on ne la touche pas", f.spec.touchable)
            assertTrue("$f", f.spec.lines.isNotEmpty())
            assertEquals(38, f.spec.wDp); assertEquals(38, f.spec.hDp)
        }
        assertEquals(Food.entries.size, Food.entries.map { it.spec }.toSet().size)
        // La carotte se couche une fois au sol
        assertEquals(90f, Food.CARROT.spec.restSpin, 0f)
        assertEquals(0f, Food.LETTUCE.spec.restSpin, 0f)
    }

    @Test
    fun `la crotte n'est ni un jouet ni de la nourriture`() {
        assertNull(Poop.toy); assertNull(Poop.food)
        assertTrue(Poop.touchable)
    }

    @Test
    fun `noms sauvegardes inchanges`() {
        // favoriteToy est sauvegardé par nom : ne jamais renommer (on ajoute à la fin)
        assertEquals(listOf("BALL", "BALLOON", "FRISBEE", "PLUSH"), Toy.entries.take(4).map { it.name })
        assertEquals(listOf("CARROT", "LETTUCE", "STRAWBERRY"), Food.entries.take(3).map { it.name })
    }

    @Test
    fun `proprietes reprises de l'ancien code`() {
        val old = Toy.entries.take(4)                    // les jouets d'avant l'étape 3b
        // tailles des fenêtres (dp)
        assertEquals(listOf(42 to 42, 52 to 86, 52 to 30, 40 to 44), old.map { it.spec.wDp to it.spec.hDp })
        // teinte au hasard : ballon et frisbee
        assertEquals(setOf(Toy.BALLOON, Toy.FRISBEE), old.filter { it.spec.randomTint }.toSet())
        // il proteste quand on les lui prend : doudou et frisbee
        assertEquals(setOf(Toy.PLUSH, Toy.FRISBEE), old.filter { it.spec.complainsWhenTaken }.toSet())
        // lancers
        val def = Toy.BALL.spec.launch
        assertSame(Launch.DEFAULT, def)
        assertSame(Launch.DEFAULT, Toy.PLUSH.spec.launch)
        assertEquals(listOf(120f, 250f, -500f, -950f), listOf(def.aboveDp, def.vxDp, def.vyDp, def.chestVyDp))
        val b = BalloonToy.launch
        assertEquals(listOf(220f, 60f, -120f, -250f), listOf(b.aboveDp, b.vxDp, b.vyDp, b.chestVyDp))
        val f = FrisbeeToy.launch
        assertEquals(listOf(120f, 650f, -380f, -950f), listOf(f.aboveDp, f.vxDp, f.vyDp, f.chestVyDp))
    }

    /** Faux contexte des jouets : la façade de test + l'état de la partie. */
    private class FakeToyCtx(val base: FakePetCtx = FakePetCtx()) : ToyCtx, PetCtx by base {
        override val userHolds = false
        override var holdTime = 0f
        override var catchCooldown = 0f
        override var groundSaidAt = 0L
    }

    @Test
    fun `toucher le jouet fait plaisir, deux fois plus avec le prefere`() {
        val ctx = FakeToyCtx()
        val pet = ctx.pet
        pet.traits = pet.traits.copy(favoriteToy = Toy.PLUSH)
        pet.stats = pet.stats.copy(happiness = 50f)
        ctx.hit(pet, Toy.BALL)
        val once = pet.stats.happiness - 50f
        assertTrue(once > 0f)
        assertEquals(1, ctx.base.emitted.size)
        assertTrue(ctx.base.emitted[0] == PType.HEART || ctx.base.emitted[0] == PType.NOTE)
        assertEquals(Expr.HAPPY, pet.mind.exprOverride)
        pet.stats = pet.stats.copy(happiness = 50f)
        ctx.hit(pet, Toy.PLUSH)
        assertTrue(pet.stats.happiness - 50f > once)
    }
}
