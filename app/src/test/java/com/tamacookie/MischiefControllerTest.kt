package com.tamacookie

import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.feature.station.StationKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Rendu possible par la façade [PetCtx] : le contrôleur tourne sans service ni fenêtre. */
class MischiefControllerTest {

    private lateinit var ctx: FakePetCtx
    private lateinit var mis: MischiefController
    private val pet get() = ctx.pet

    @Before fun setUp() {
        resetPlatforms()
        ctx = FakePetCtx()
        mis = MischiefController(ctx)
        mis.registerActs(ctx.acts)
    }

    /** Une image de l'activité en cours, comme la boucle du service (via le registre). */
    private fun step(dt: Float) = ctx.acts.handlerFor(pet)!!.update(pet, dt)

    /** Tap sur l'animal, comme `TouchHandler.onPetTap` (via le registre). */
    private fun tap(): Boolean = ctx.acts.handlerFor(pet)?.onPet(pet) ?: false

    @After fun tearDown() = resetPlatforms()

    /** Ignoré longtemps : appelle [MischiefController.maybeSeekAttention] jusqu'à ce que le tirage réussisse. */
    private fun seekUntilSomethingHappens(): Boolean {
        repeat(50) {
            ctx.lastInteraction = ctx.clock.wallMs() - 3_600_000L
            val said = ctx.said.size
            val r = mis.maybeSeekAttention()
            if (r || ctx.said.size > said) return r
            ctx.clock.advance(200_000L)          // au-delà de toute pause entre deux tentatives
        }
        throw AssertionError("le tirage n'a jamais réussi")
    }

    @Test
    fun `pas de betise dans une station`() {
        ctx.world.station(StationKind.BATH).enter(pet.id)
        ctx.lastInteraction = ctx.clock.wallMs() - 3_600_000L
        assertFalse(mis.maybeSeekAttention())
        assertTrue(ctx.said.isEmpty())
    }

    @Test
    fun `pas de betise s'il vient d'etre occupe`() {
        ctx.noteInteraction()
        assertFalse(mis.maybeSeekAttention())
        assertEquals(Act.IDLE, pet.act)
    }

    @Test
    fun `sage il se contente de reclamer`() {
        mis.level = 0
        assertFalse(seekUntilSomethingHappens())
        assertEquals(1, ctx.said.size)
        assertEquals(Act.IDLE, pet.act)
    }

    @Test
    fun `taquin sans icones ni cache-cache il vient toquer`() {
        mis.level = 1
        assertTrue(seekUntilSomethingHappens())
        assertEquals(Act.KNOCK, pet.act)
    }

    @Test
    fun `toc-toc au milieu de l'ecran puis il se calme`() {
        pet.body.fx = 100f
        pet.setAct(Act.KNOCK)
        var steps = 0
        while (ctx.vibrations.isEmpty()) {
            step(0.1f)
            assertTrue("il n'arrive jamais", ++steps < 200)
        }
        assertEquals(ctx.arena.screenW / 2, pet.body.fx, 3f)
        assertEquals(Expr.SURPRISED, pet.mind.exprOverride)
        assertEquals(1, ctx.said.size)
        // Il reste un moment, puis retourne à ses occupations
        pet.body.actTime = 3.6f
        step(0.1f)
        assertEquals(Act.IDLE, pet.act)
        assertEquals(1, ctx.vibrations.size)
    }

    @Test
    fun `cache-cache impossible sans l'accessibilite`() {
        assertFalse(mis.startHideSeek(fromMenu = true))
        assertTrue(ctx.said.single().contains("accessibilité"))
        assertNull(mis.hidingView)
    }

    @Test
    fun `cache-cache complet - il se cache, depasse, et on le trouve`() {
        Platforms.connected = true
        Platforms.pageCount = 3
        Platforms.launcherPkg = "launcher"
        ctx.pages.currentView = "launcher#p1"
        val happy = pet.stats.happiness

        assertTrue(mis.startHideSeek(fromMenu = true))
        assertEquals(Act.HIDE_RUN, pet.act)
        assertTrue(pet.isHiding)
        val where = mis.hidingView
        assertNotNull(where)
        assertTrue(where == "launcher#p2" || where == "launcher#p3")
        assertEquals(ctx.arena.screenW + ctx.arena.bw, pet.body.targetX, 0f)
        assertEquals(1, ctx.menuClosed)

        // Il court hors de l'écran, puis disparaît
        var steps = 0
        while (pet.act == Act.HIDE_RUN) {
            step(0.1f)
            assertTrue(++steps < 200)
        }
        assertEquals(Act.HIDDEN, pet.act)
        assertTrue(ctx.dockShowsHiding)
        assertEquals(1, ctx.notifications)
        assertEquals(1, ctx.appliedViews)

        // L'utilisateur arrive sur la bonne page : la tête dépasse
        ctx.pages.currentView = where!!
        mis.onViewChanged()
        assertEquals(Act.PEEK, pet.act)

        // Trouvé !
        assertTrue(tap())
        assertNull(mis.hidingView)
        assertFalse(ctx.dockShowsHiding)
        assertEquals(Act.AIR, pet.act)
        assertEquals((happy + 15f).coerceAtMost(100f), pet.stats.happiness, 1e-3f)
        assertEquals(5, ctx.emitted.count { it == PType.HEART })
    }

    @Test
    fun `personne ne le cherche - il abandonne et retombe`() {
        pet.setAct(Act.HIDE_RUN)
        pet.body.targetX = pet.body.fx                   // déjà au bord : il se cache tout de suite
        step(0.1f)
        assertEquals(Act.HIDDEN, pet.act)
        val happy = pet.stats.happiness
        step(0.1f)
        assertEquals(Act.HIDDEN, pet.act)                // trop tôt pour abandonner
        ctx.clock.advance(Tuning.HIDE_GIVE_UP_MS + 1)
        step(0.1f)
        assertEquals(Act.AIR, pet.act)
        assertEquals(Act.IDLE, pet.body.afterAir)
        assertEquals(happy - 3f, pet.stats.happiness, 1e-3f)
        assertEquals(Expr.SAD, pet.mind.exprOverride)
        assertEquals(2, ctx.appliedViews)                // caché, puis réapparu
    }

    @Test
    fun `sage il ignore clics et frappe`() {
        mis.level = 0
        mis.checkUserEvents()                            // première lecture des compteurs
        Platforms.clickVersion++; Platforms.typingVersion++; Platforms.scrollVersion++
        repeat(20) { mis.checkUserEvents() }
        assertTrue(ctx.said.isEmpty())
        assertEquals(Act.IDLE, pet.act)
    }
}
