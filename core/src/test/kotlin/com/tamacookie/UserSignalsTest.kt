package com.tamacookie

import com.tamacookie.feature.mischief.Mischief
import com.tamacookie.feature.mischief.MischiefCtx
import com.tamacookie.feature.mischief.UserEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Les bêtises reçoivent les signaux du port [ScreenSensor] sans en perdre. */
class UserSignalsTest {

    /** Bêtise factice : enregistre ce qu'on lui propose, réagit au clic n° [reactAt] (0 = jamais). */
    private class Recorder(var reactAt: Int = 0) : Mischief {
        val clicks = ArrayList<Box?>()
        val scrolls = ArrayList<Boolean>()
        var typing = 0
        override fun react(ctx: MischiefCtx, pet: Pet, ev: UserEvents): Boolean {
            clicks += ev.click; scrolls += ev.scroll; typing = ev.typing
            return ev.click != null && clicks.count { it != null } == reactAt
        }
    }

    private lateinit var ctx: FakePetCtx
    private val rec = Recorder()
    private lateinit var mis: MischiefController
    private val a = Box(0f, 500f, 100f, 600f)
    private val b = Box(200f, 500f, 300f, 600f)

    @Before fun setUp() {
        ctx = FakePetCtx()
        mis = MischiefController(ctx, listOf(rec))
        mis.level = 1
    }

    @Test
    fun `les signaux d'avant la premiere image sont ignores`() {
        ctx.screen.emit(UserSignal.Click(a), UserSignal.Typing)
        mis.checkUserEvents()
        assertEquals(listOf<Box?>(null), rec.clicks)
        assertEquals(0, rec.typing)
        assertTrue(ctx.screen.takeSignals().isEmpty())               // consommés quand même
    }

    @Test
    fun `deux clics entre deux images - deux propositions, dans l'ordre`() {
        mis.checkUserEvents()
        rec.clicks.clear()
        ctx.screen.emit(UserSignal.Click(a), UserSignal.Click(b))
        mis.checkUserEvents()
        assertEquals(listOf<Box?>(a, b), rec.clicks)
        mis.checkUserEvents()                                       // plus rien : un passage sans clic
        assertEquals(listOf(a, b, null), rec.clicks)
    }

    @Test
    fun `la premiere betise qui reagit arrete la rafale`() {
        rec.reactAt = 1
        mis.checkUserEvents()
        rec.clicks.clear()
        ctx.screen.emit(UserSignal.Click(a), UserSignal.Click(b))
        mis.checkUserEvents()
        assertEquals(listOf<Box?>(a), rec.clicks)
    }

    @Test
    fun `chaque frappe compte, le defilement une fois par image`() {
        mis.checkUserEvents()
        rec.clicks.clear(); rec.scrolls.clear()
        repeat(3) { ctx.screen.emit(UserSignal.Typing) }
        ctx.screen.emit(UserSignal.Scroll, UserSignal.Click(a), UserSignal.Scroll, UserSignal.Click(b))
        mis.checkUserEvents()
        assertEquals(3, rec.typing)
        assertEquals(listOf(true, false), rec.scrolls)
    }

    @Test
    fun `endormi ou sage - les signaux sont consommes sans reaction`() {
        mis.checkUserEvents()
        rec.clicks.clear()
        mis.level = 0
        ctx.screen.emit(UserSignal.Click(a), UserSignal.Typing)
        mis.checkUserEvents()
        mis.level = 1
        mis.checkUserEvents()
        assertEquals(listOf<Box?>(null), rec.clicks)                 // le clic d'avant n'est pas rejoué
        assertEquals(0, rec.typing)
    }

    @Test
    fun `occupe (pas libre) - les frappes comptent, les clics sont perdus`() {
        mis.checkUserEvents()
        rec.clicks.clear()
        pet.setAct(Act.EAT)
        ctx.screen.emit(UserSignal.Click(a), UserSignal.Typing)
        mis.checkUserEvents()
        pet.setAct(Act.IDLE)
        mis.checkUserEvents()
        assertEquals(listOf<Box?>(null), rec.clicks)
        assertEquals(1, rec.typing)
    }

    private val pet get() = ctx.pet
}
