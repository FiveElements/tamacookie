package com.tamacookie

import com.tamacookie.feature.wellbeing.WellbeingCoach
import com.tamacookie.feature.wellbeing.WellbeingTracker
import com.tamacookie.feature.wellbeing.WellbeingTracker.Cue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bien-être numérique (évolution 4c) : suivi du temps d'affilée dans une app, et ce que fait l'animal. */
class WellbeingTest {
    private val clock = FakeClock()
    private val min = 60_000L

    private fun tracker(enabled: Boolean = true) = WellbeingTracker(
        clock,
        exempt = { it == "com.tamacookie" || it == "launcher" },
        transient = { it == "com.android.systemui" },
    ).also { it.enabled = enabled }

    /** Relève [pkg] toutes les 1,5 s pendant [minutes] et retourne les signaux reçus. */
    private fun WellbeingTracker.stay(pkg: String?, minutes: Int): List<Cue> {
        val out = ArrayList<Cue>()
        val steps = minutes * 40
        repeat(steps) { clock.advance(1_500); observe(pkg)?.let(out::add) }
        return out
    }

    @Test
    fun `seuils par defaut - 30 et 45 minutes`() {
        assertEquals(30, GameRules.DEFAULT.breakSuggestAfterMin)
        assertEquals(45, GameRules.DEFAULT.breakSulkAfterMin)
    }

    @Test
    fun `pause a 30 min puis bouderie a 45 min, une seule fois chacune`() {
        val t = tracker()
        assertNull(t.observe("chat"))
        assertEquals(emptyList<Cue>(), t.stay("chat", 29))
        assertEquals(listOf(Cue.BREAK), t.stay("chat", 2))
        assertEquals(emptyList<Cue>(), t.stay("chat", 13))
        assertFalse(t.sulking)
        assertEquals(listOf(Cue.SULK), t.stay("chat", 1))
        assertTrue(t.sulking)
        assertEquals(emptyList<Cue>(), t.stay("chat", 60))   // il boude, sans répéter
        assertTrue(t.sulking)
    }

    @Test
    fun `changer d'app remet a zero et le soulage s'il boudait`() {
        val t = tracker()
        t.observe("chat")
        t.stay("chat", 20)
        assertNull(t.observe("video"))                       // nouvelle séance
        assertEquals(emptyList<Cue>(), t.stay("video", 29))
        assertEquals(listOf(Cue.BREAK, Cue.SULK), t.stay("video", 20))
        assertEquals(Cue.RELIEVED, t.observe("chat"))
        assertFalse(t.sulking)
        assertEquals("chat", t.pkg)
        assertEquals(emptyList<Cue>(), t.stay("chat", 29))
    }

    @Test
    fun `notre app et le lanceur ne comptent pas et remettent a zero`() {
        val t = tracker()
        t.observe("chat"); t.stay("chat", 25)
        assertNull(t.observe("launcher"))
        assertNull(t.pkg)
        assertEquals(emptyList<Cue>(), t.stay("launcher", 120))
        assertEquals(emptyList<Cue>(), t.stay("com.tamacookie", 120))
        t.observe("chat")
        assertEquals(emptyList<Cue>(), t.stay("chat", 25))  // repart de zéro
        t.stay("chat", 25)
        assertTrue(t.sulking)
        assertEquals(Cue.RELIEVED, t.observe("launcher"))
    }

    @Test
    fun `le volet et une reponse vide n'interrompent pas la seance`() {
        val t = tracker()
        t.observe("chat"); t.stay("chat", 20)
        assertEquals(emptyList<Cue>(), t.stay("com.android.systemui", 5))
        assertEquals(listOf(Cue.BREAK), t.stay(null, 6))
        assertEquals("chat", t.pkg)
    }

    @Test
    fun `ecran eteint - reset remet a zero`() {
        val t = tracker()
        t.observe("chat"); t.stay("chat", 29)
        assertNull(t.reset())                                // écran éteint, pas de bouderie : rien à dire
        assertEquals(0L, t.elapsedMs)
        t.observe("chat")
        assertEquals(emptyList<Cue>(), t.stay("chat", 29))
        t.stay("chat", 20)
        assertEquals(Cue.RELIEVED, t.reset())                // il boudait : l'appelant choisit de se taire
        assertFalse(t.sulking)
    }

    @Test
    fun `une caresse met fin a la bouderie et relance le compte`() {
        val t = tracker()
        assertNull(t.onPetted())
        t.observe("chat"); t.stay("chat", 46)
        assertTrue(t.sulking)
        assertEquals(Cue.MADE_UP, t.onPetted())
        assertFalse(t.sulking)
        assertNull(t.onPetted())
        assertEquals(emptyList<Cue>(), t.stay("chat", 29))
        assertEquals(listOf(Cue.BREAK), t.stay("chat", 2))
    }

    @Test
    fun `reglage coupe - rien`() {
        val t = tracker(enabled = false)
        assertNull(t.observe("chat"))
        assertEquals(emptyList<Cue>(), t.stay("chat", 120))
        assertNull(t.check())
        assertNull(t.onPetted())
        assertNull(t.pkg)
        // Couper en pleine bouderie : remis à zéro
        val u = tracker()
        u.observe("chat"); u.stay("chat", 50)
        assertTrue(u.sulking)
        u.enabled = false
        assertFalse(u.sulking)
        assertNull(u.pkg)
    }

    @Test
    fun `seuils pris dans les regles`() {
        val t = WellbeingTracker(clock, GameRules.DEFAULT.copy(breakSuggestAfterMin = 1, breakSulkAfterMin = 2))
        t.enabled = true
        t.observe("chat")
        clock.advance(min); assertEquals(Cue.BREAK, t.check())
        clock.advance(min); assertEquals(Cue.SULK, t.check())
    }

    // --- Ce que fait l'animal ---

    @Test
    fun `pause - il baille, s'assoit et le propose, sans toucher aux jauges`() {
        val ctx = FakePetCtx()
        val p = ctx.first
        val before = p.stats
        WellbeingCoach.react(ctx, p, Cue.BREAK)
        assertEquals(Expr.SLEEPY, p.mind.exprOverride)
        assertEquals(Act.SIT, p.act)
        assertTrue(ctx.said.single() in WellbeingCoach.BREAK)
        assertEquals(before, p.stats)
        assertTrue(ctx.saved.isEmpty())
    }

    @Test
    fun `bouderie - visage maintenu jusqu'au pardon`() {
        val ctx = FakePetCtx()
        val p = ctx.first
        val before = p.stats
        WellbeingCoach.react(ctx, p, Cue.SULK)
        assertEquals(Expr.ANGRY, p.mind.exprOverride)
        assertTrue(ctx.said.single() in WellbeingCoach.SULK)
        p.mind.tick(3f)
        assertNull(p.mind.exprOverride)
        WellbeingCoach.maintain(ctx, p, sulking = true)
        assertEquals(Expr.ANGRY, p.mind.exprOverride)
        // Une autre expression en cours n'est pas écrasée
        p.express(Expr.SURPRISED, 1f)
        WellbeingCoach.maintain(ctx, p, sulking = true)
        assertEquals(Expr.SURPRISED, p.mind.exprOverride)
        WellbeingCoach.react(ctx, p, Cue.MADE_UP)
        assertEquals(Expr.HAPPY, p.mind.exprOverride)
        assertTrue(PType.HEART in ctx.emitted)
        assertTrue(ctx.said.last() in WellbeingCoach.MADE_UP)
        assertEquals(before, p.stats)
    }

    @Test
    fun `pas de reaction endormi, cache ou hors ecran`() {
        val ctx = FakePetCtx()
        val p = ctx.first
        p.setAct(Act.SLEEP)
        WellbeingCoach.react(ctx, p, Cue.BREAK)
        WellbeingCoach.maintain(ctx, p, sulking = true)
        p.setAct(Act.IDLE)
        ctx.hidden = true
        WellbeingCoach.react(ctx, p, Cue.SULK)
        ctx.hidden = false
        ctx.away += p.id
        WellbeingCoach.react(ctx, p, Cue.RELIEVED)
        assertTrue(ctx.said.isEmpty())
        assertNull(p.mind.exprOverride)
    }
}
