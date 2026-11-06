package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests de fumée de la physique pure du lapin (densité 1 : 1 dp = 1 px). */
class BunnyPhysicsTest {

    private fun phys(x: Float = 100f, y: Float = 1000f) = BunnyPhysics(1f).apply {
        setBounds(minX = 34f, maxX = 1046f, topY = 0f, groundY = 1000f)
        this.x = x; this.y = y
    }

    /** Fait voler jusqu'à un atterrissage ; retourne le dernier événement et ceux rencontrés. */
    private fun flyUntilLanded(p: BunnyPhysics, platforms: List<Box> = emptyList(), maxSteps: Int = 2000): Pair<AirEvent, List<AirEvent>> {
        val seen = ArrayList<AirEvent>()
        repeat(maxSteps) {
            val ev = p.stepAir(1f / 120f, platforms)
            seen.add(ev)
            if (ev is AirEvent.Landed || ev == AirEvent.ForcedLanded) return ev to seen
        }
        throw AssertionError("jamais atterri")
    }

    @Test
    fun `saut balistique vers un point au sol`() {
        val p = phys()
        p.jumpTo(300f, 1000f, apexRise = 80f)
        assertTrue(p.vy < 0f)
        val (ev, _) = flyUntilLanded(p)
        assertEquals(AirEvent.Landed(-1, false), ev)
        assertEquals(300f, p.x, 6f)
        assertEquals(1000f, p.y, 0.001f)
        assertEquals(0f, p.vx, 0f); assertEquals(0f, p.vy, 0f)
    }

    @Test
    fun `atterrit sur une icone`() {
        val p = phys()
        val icon = Box(250f, 800f, 350f, 860f)
        p.jumpTo(300f, 800f, apexRise = 80f)
        val (ev, _) = flyUntilLanded(p, listOf(icon))
        assertTrue(ev is AirEvent.Landed && ev.onPlatform && ev.platform == 0 && !ev.hard)
        assertEquals(800f, p.y, 0.001f)
    }

    @Test
    fun `chute de tres haut - rebond puis atterrissage brutal`() {
        val p = phys(x = 500f, y = 0f)
        p.setBounds(34f, 1046f, topY = -3000f, groundY = 3000f)
        p.y = -2800f + 100f
        p.jump(0f, 0f)
        val (ev, seen) = flyUntilLanded(p)
        assertTrue(seen.contains(AirEvent.Bounced))
        assertEquals(AirEvent.Landed(-1, true), ev)
        assertTrue(!p.hardLanding)
    }

    @Test
    fun `rebond sur un mur`() {
        val p = phys(x = 40f, y = 500f)
        p.jump(0f, -1000f)
        val ev = p.stepAir(0.02f, emptyList())
        assertEquals(AirEvent.HitWall, ev)
        assertEquals(34f, p.x, 0.001f)
        assertTrue(p.vx > 0f)
    }

    @Test
    fun `atterrissage force a une hauteur donnee`() {
        val p = phys()
        p.jumpTo(500f, 700f, apexRise = 60f)
        p.airLandY = 700f
        val (ev, _) = flyUntilLanded(p)
        assertEquals(AirEvent.ForcedLanded, ev)
        assertEquals(700f, p.y, 0.001f)
        assertNull(p.airLandY)
    }

    @Test
    fun `lancer borne`() {
        val p = phys()
        p.release(99_999f, -99_999f)
        assertEquals(Tuning.THROW_MAX_DP, p.vx, 0f)
        assertEquals(-Tuning.THROW_MAX_DP, p.vy, 0f)
    }

    @Test
    fun `solveJump atteint la cible`() {
        val g = 2600f
        val j = BunnyPhysics.solveJump(0f, 1000f, 400f, 800f, 100f, g)
        val t = j.time
        assertEquals(400f, j.vx * t, 0.01f)
        assertEquals(800f, 1000f + j.vy * t + 0.5f * g * t * t, 0.5f)
    }

    private class TestBody(
        override var x: Float, override var y: Float,
        override var vx: Float = 0f, override var vy: Float = 0f,
        override var onGround: Boolean = false,
    ) : Body

    @Test
    fun `un objet finit par se poser au sol`() {
        val b = TestBody(x = 200f, y = 500f, vx = 300f)
        repeat(2000) {
            BunnyPhysics.stepProp(b, 1f / 60f, 2600f, 1f, 0.62f, 0.8f, 2.2f,
                halfW = 21f, height = 42f, screenW = 1080f, topY = 0f, groundY = 1000f, settleSpeed = 250f)
        }
        assertTrue(b.onGround)
        assertEquals(1000f, b.y, 0f)
        assertTrue(b.x in 21f..1059f)
    }

    @Test
    fun `la nourriture tombe et se pose`() {
        val b = TestBody(x = 200f, y = 100f)
        var landed = false
        repeat(500) { if (BunnyPhysics.stepFalling(b, 1f / 60f, 2600f, 34f, 1046f, 1000f)) landed = true }
        assertTrue(landed && b.onGround)
        assertEquals(1000f, b.y, 0f)
    }
}
