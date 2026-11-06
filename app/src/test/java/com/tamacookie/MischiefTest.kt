package com.tamacookie

import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.feature.Travel
import com.tamacookie.feature.mischief.HideSeekMischief
import com.tamacookie.feature.mischief.KnockMischief
import com.tamacookie.feature.mischief.Mischief
import com.tamacookie.feature.mischief.MischiefCtx
import com.tamacookie.feature.mischief.Mischiefs
import com.tamacookie.feature.mischief.ScrollDizzy
import com.tamacookie.feature.mischief.SquatMischief
import com.tamacookie.feature.mischief.UserEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/** Bêtises en plugins (`feature/mischief/`) : choix, registre, et chaque bêtise. */
class MischiefTest {

    private lateinit var ctx: FakePetCtx
    private lateinit var mis: MischiefController
    private val pet get() = ctx.pet

    @Before fun setUp() {
        ctx = FakePetCtx()
        mis = MischiefController(ctx)
        mis.registerActs(ctx.acts)
    }

    /** Bêtise factice pour le choix : probabilité fixe, possible ou non, démarre ou non. */
    private class Fake(val c: Float, val ok: Boolean = true, val starts: Boolean = true) : Mischief {
        override fun chance(level: Int) = c
        override fun canStart(ctx: MischiefCtx, pet: Pet) = ok
        override fun start(ctx: MischiefCtx, pet: Pet) = starts
    }

    private fun seek(list: List<Mischief>, rnd: Random) =
        Mischiefs.seek(list, 1, rnd, { (it as Fake).ok }, { (it as Fake).starts })

    // ---------------------------------------------------------------------
    //  Choix (cascade)
    // ---------------------------------------------------------------------
    @Test
    fun `meme graine, meme choix`() {
        val list = listOf(Fake(0.35f), Fake(0.35f), Fake(1f))
        val a = Random(7); val b = Random(7)
        repeat(200) { assertSame(seek(list, a), seek(list, b)) }
    }

    @Test
    fun `la cascade respecte les probabilites`() {
        val first = Fake(0.35f); val second = Fake(0.35f); val last = Fake(1f)
        val list = listOf(first, second, last)
        val rnd = Random(1234)
        val n = 20_000
        val counts = HashMap<Mischief, Int>()
        repeat(n) { seek(list, rnd)?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        assertEquals(n, counts.values.sum())                         // la dernière (1) ne rate jamais
        assertEquals(0.35, counts[first]!! / n.toDouble(), 0.02)
        assertEquals(0.65 * 0.35, counts[second]!! / n.toDouble(), 0.02)
        assertEquals(0.65 * 0.65, counts[last]!! / n.toDouble(), 0.02)
    }

    @Test
    fun `sans tirage pour une probabilite de 1 ni pour une betise impossible`() {
        val rnd = Random(99); val witness = Random(99)
        assertNotNull(seek(listOf(Fake(0.9f, ok = false), Fake(1f)), rnd))
        // aucun nombre consommé : les deux générateurs sont toujours alignés
        assertEquals(witness.nextFloat(), rnd.nextFloat(), 0f)
    }

    @Test
    fun `une betise qui ne demarre pas passe la main`() {
        val wont = Fake(1f, starts = false); val will = Fake(1f)
        assertSame(will, seek(listOf(wont, will), Random(0)))
        assertNull(seek(listOf(Fake(0f), wont), Random(0)))
    }

    @Test
    fun `la frappe compte jusqu'a une rafale`() {
        val ev = UserEvents()
        ev.typing = UserEvents.TYPING_BURST - 1
        assertFalse(ev.takeTypingBurst())
        ev.typing++
        assertTrue(ev.takeTypingBurst())
        assertEquals(0, ev.typing)
    }

    // ---------------------------------------------------------------------
    //  Registre et propriétés
    // ---------------------------------------------------------------------
    @Test
    fun `chaque activite de betise a son gestionnaire, une seule fois`() {
        val acts = Mischiefs.ALL.flatMap { it.acts }
        assertEquals("une activité revendiquée deux fois", acts.size, acts.toSet().size)
        assertTrue(acts.containsAll(setOf(Act.SQUAT, Act.KNOCK, Act.HIDE_RUN, Act.HIDDEN, Act.PEEK)))
        assertTrue(acts.containsAll(Act.HIDING))
        for (a in acts) {
            pet.setAct(a)
            assertNotNull("$a", ctx.acts.handlerFor(pet))
        }
        // Le registre refuse qu'on enregistre deux fois les bêtises
        try { mis.registerActs(ctx.acts); throw AssertionError("double enregistrement accepté") }
        catch (_: IllegalStateException) {}
        // La réaction au défilement ne pilote aucune activité (le tournis est une activité commune)
        assertTrue(ScrollDizzy.acts.isEmpty())
    }

    @Test
    fun `voyage - occupe pendant toc-toc et bouton, muet a cache-cache`() {
        assertEquals(Travel.BUSY, SquatMischief.travel)
        assertEquals(Travel.BUSY, KnockMischief.travel)
        assertEquals(Travel.IGNORES, HideSeekMischief.travel)
        // cohérent avec l'ensemble d'Act toujours lu par onAppChanged
        assertTrue(Act.SQUAT in Act.BUSY_FOR_TRAVEL && Act.KNOCK in Act.BUSY_FOR_TRAVEL)
        for (m in Mischiefs.ALL) assertFalse(m.slowFrames)
    }

    @Test
    fun `ordre de la cascade - cache-cache, bouton, toc-toc en dernier recours`() {
        for (level in 0..2) {
            val cascade = Mischiefs.ALL.filter { it.chance(level) > 0f }
            assertTrue(cascade.indexOf(HideSeekMischief) < cascade.indexOf(SquatMischief))
            // une bêtise sans tirage (probabilité 1) rend les suivantes inaccessibles : elle ferme la cascade
            assertSame(KnockMischief, cascade.last())
            assertEquals(listOf(KnockMischief), cascade.filter { it.chance(level) >= 1f })
        }
        assertEquals(0.35f, HideSeekMischief.chance(1), 0f)
        assertEquals(0.35f, SquatMischief.chance(1), 0f)
        assertEquals(0.6f, SquatMischief.chance(2), 0f)
    }

    // ---------------------------------------------------------------------
    //  Chaque bêtise
    // ---------------------------------------------------------------------
    @Test
    fun `bouton - sans icone pas de squat, une caresse le fait descendre`() {
        assertFalse(SquatMischief.canStart(fakeMctx(), pet))
        pet.setAct(Act.SQUAT)
        val happy = pet.stats.happiness
        assertTrue(ctx.acts.handlerFor(pet)!!.onPet(pet))
        assertEquals(Act.AIR, pet.act)                              // il saute en bas
        assertEquals(Act.IDLE, pet.body.afterAir)
        assertEquals(3, ctx.emitted.count { it == PType.HEART })
        assertEquals(Expr.HAPPY, pet.mind.exprOverride)
        assertTrue(pet.stats.happiness >= happy)
    }

    @Test
    fun `bouton - il s'installe sur une icone du capteur et tombe quand elle disparait`() {
        val icon = Box(400f, 1000f, 500f, 1100f)
        ctx.screen.update { copy(connected = true) }
        ctx.screen.platforms(icon)
        val m = fakeMctx()
        assertTrue(SquatMischief.canStart(m, pet))
        assertTrue(SquatMischief.start(m, pet))
        assertEquals(icon, m.state.squatRect)
        assertEquals(Act.AIR, pet.act)                              // il saute dessus
        assertEquals(Act.SQUAT, pet.body.afterAir)
        pet.setAct(Act.SQUAT)
        SquatMischief.update(m, pet, 0.1f)
        assertEquals(Act.SQUAT, pet.act)
        ctx.screen.platforms(Box(420f, 1000f, 520f, 1100f))          // nouvelle liste, toujours sous lui
        SquatMischief.update(m, pet, 0.1f)
        assertEquals(Act.SQUAT, pet.act)
        ctx.screen.platforms(Box(800f, 300f, 900f, 400f))            // l'icône a disparu
        SquatMischief.update(m, pet, 0.1f)
        assertEquals(Act.AIR, pet.act)
    }

    @Test
    fun `bouton - petit monstre, il s'assoit sur le clavier et tombe quand il se ferme`() {
        val kb = Box(0f, 1500f, 1080f, 2000f)
        ctx.screen.update { copy(ime = kb) }
        val m = object : MischiefCtx, PetCtx by ctx {
            override val level = 2
            override var cooldownUntil = 0L
            override val state = com.tamacookie.feature.mischief.MischiefState()
        }
        val ev = UserEvents().apply { typing = UserEvents.TYPING_BURST }
        var tries = 0
        while (!SquatMischief.react(m, pet, ev)) { ev.typing = UserEvents.TYPING_BURST; assertTrue(++tries < 100) }
        assertTrue(m.state.squatKbd)
        assertEquals(kb.top, m.state.squatRect.top, 0f)
        assertTrue(m.state.squatRect.left in 270f..810f)
        pet.setAct(Act.SQUAT)
        SquatMischief.update(m, pet, 0.1f)
        assertEquals(Act.SQUAT, pet.act)
        ctx.screen.update { copy(ime = null) }
        SquatMischief.update(m, pet, 0.1f)
        assertEquals(Act.AIR, pet.act)
    }

    @Test
    fun `bouton - il se lasse et descend`() {
        pet.setAct(Act.SQUAT)
        pet.body.actTime = 61f                                      // au-delà de toute limite
        ctx.acts.handlerFor(pet)!!.update(pet, 0.1f)
        assertEquals(Act.AIR, pet.act)
        assertEquals(1, ctx.said.size)
    }

    @Test
    fun `toc-toc - ni tirage ni condition`() {
        assertTrue(KnockMischief.canStart(fakeMctx(), pet))
        assertTrue(KnockMischief.start(fakeMctx(), pet))
        assertEquals(Act.KNOCK, pet.act)
        assertFalse("la caresse est ordinaire", ctx.acts.handlerFor(pet)!!.onPet(pet))
    }

    @Test
    fun `cache-cache - on ne l'arrete pas pendant qu'il court, caresse ordinaire une fois cache`() {
        pet.setAct(Act.HIDE_RUN)
        assertTrue(ctx.acts.handlerFor(pet)!!.onPet(pet))
        assertEquals(Act.HIDE_RUN, pet.act)
        assertTrue(ctx.said.isEmpty())
        pet.setAct(Act.HIDDEN)
        assertFalse(ctx.acts.handlerFor(pet)!!.onPet(pet))
    }

    @Test
    fun `defilement - parfois le tournis, puis une pause`() {
        mis.level = 1
        mis.checkUserEvents()                                       // première lecture des compteurs
        var steps = 0
        while (pet.act != Act.DIZZY) {
            ctx.screen.emit(UserSignal.Scroll)
            mis.checkUserEvents()
            assertTrue("jamais étourdi", ++steps < 5_000)
        }
        assertTrue(ctx.said.last().contains("tournis"))
        // pendant la pause, plus de réaction
        pet.setAct(Act.IDLE)
        val said = ctx.said.size
        repeat(500) { ctx.screen.emit(UserSignal.Scroll); mis.checkUserEvents() }
        assertEquals(said, ctx.said.size)
        assertEquals(Act.IDLE, pet.act)
    }

    @Test
    fun `frappe - taquin il commente le clavier sans s'y asseoir`() {
        mis.level = 1
        ctx.screen.update { copy(ime = Box(0f, 1500f, 1080f, 2000f)) }
        mis.checkUserEvents()
        var steps = 0
        while (ctx.said.isEmpty()) {
            repeat(UserEvents.TYPING_BURST) { ctx.screen.emit(UserSignal.Typing); mis.checkUserEvents() }
            assertTrue("il ne dit jamais rien", ++steps < 200)
        }
        assertTrue(ctx.said.single().let { "écris" in it || "tapes" in it || "coucou" in it })
        assertEquals(Act.IDLE, pet.act)                             // pas de squat au niveau 1
    }

    /** Contexte de bêtise minimal (pour appeler une bêtise directement). */
    private fun fakeMctx(): MischiefCtx = object : MischiefCtx, PetCtx by ctx {
        override val level = 1
        override var cooldownUntil = 0L
        override val state = com.tamacookie.feature.mischief.MischiefState()
    }
}
