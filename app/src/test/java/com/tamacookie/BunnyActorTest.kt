package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BunnyActorTest {

    private val arena = Arena().apply {
        d = 1f; screenW = 1000f; topY = 50f; groundY = 1800f; minX = 40f; maxX = 960f
    }
    private val actor = BunnyActor(arena)

    private fun pet(x: Float = 500f): Pet = testPet(BunnyPhysics(), Random(3)).also {
        it.body.phys.setBounds(arena.minX, arena.maxX, arena.topY, arena.groundY)
        it.body.fx = x; it.body.fy = arena.groundY
    }

    @Test
    fun `il marche vers la cible a la vitesse donnee`() {
        val p = pet(500f)
        assertFalse(actor.walkToward(p, 800f, 100f, 0.5f))
        assertEquals(550f, p.body.fx, 1e-3f)
        assertEquals(1f, p.body.facing, 0f)
        assertEquals(Pose.WALK, p.body.pose)
        assertTrue(p.body.walkPhase > 0f)
    }

    @Test
    fun `il se tourne vers la gauche pour une cible a gauche`() {
        val p = pet(500f)
        actor.walkToward(p, 100f, 100f, 0.1f)
        assertEquals(-1f, p.body.facing, 0f)
        assertTrue(p.body.fx < 500f)
    }

    @Test
    fun `arrive il s'arrete et remet le pas a zero`() {
        val p = pet(500f)
        p.body.walkPhase = 0.4f
        assertTrue(actor.walkToward(p, 502f, 100f, 0.1f))
        assertEquals(500f, p.body.fx, 0f)
        assertEquals(0f, p.body.walkPhase, 0f)
    }

    @Test
    fun `il ne depasse jamais la cible`() {
        val p = pet(500f)
        actor.walkToward(p, 510f, 1000f, 1f)
        assertEquals(510f, p.body.fx, 1e-3f)
    }

    @Test
    fun `la cible est bornee a l'arene sauf si clamp est faux`() {
        val p = pet(950f)
        actor.walkToward(p, 5000f, 1000f, 1f)
        assertEquals(arena.maxX, p.body.fx, 1e-3f)
        val q = pet(950f)
        actor.walkToward(q, 1200f, 1000f, 1f, clamp = false)
        assertEquals(1200f, q.body.fx, 1e-3f)
    }

    @Test
    fun `marcher le pose au sol`() {
        val p = pet(500f)
        p.body.fy = 900f
        actor.walkToward(p, 600f, 100f, 0.1f)
        assertEquals(arena.groundY, p.body.fy, 0f)
    }

    @Test
    fun `perche sur une icone il saute d'abord au sol et reprend son activite`() {
        val p = pet(500f)
        p.setAct(Act.WALK)
        p.body.platform = Box(450f, 900f, 550f, 950f)
        p.body.fy = 900f
        assertFalse(actor.walkToward(p, 800f, 100f, 0.1f))
        assertEquals(Act.AIR, p.act)
        assertEquals(Act.WALK, p.body.afterAir)
        assertNull(p.body.platform)
        assertTrue(p.body.phys.vx > 0f)   // il saute du côté de sa cible
    }

    @Test
    fun `jump quitte la plateforme et programme la suite`() {
        val p = pet()
        p.body.platform = Box(0f, 0f, 1f, 1f)
        actor.jump(p, -400f, 30f, then = Act.PLAY)
        assertEquals(Act.AIR, p.act)
        assertEquals(Act.PLAY, p.body.afterAir)
        assertNull(p.body.platform)
        assertEquals(-400f, p.body.phys.vy, 0f)
        assertEquals(30f, p.body.phys.vx, 0f)
    }

    @Test
    fun `jumpTo vise le point d'atterrissage`() {
        val p = pet(500f)
        actor.jumpTo(p, 700f, 1200f, Act.SQUAT)
        assertEquals(Act.AIR, p.act)
        assertEquals(Act.SQUAT, p.body.afterAir)
        assertTrue(p.body.phys.vx > 0f)
        assertTrue(p.body.phys.vy < 0f)
    }

    @Test
    fun `faceToward ignore une cible quasiment sous lui`() {
        val p = pet(500f)
        p.body.facing = 1f
        actor.faceToward(p, 499f)
        assertEquals(1f, p.body.facing, 0f)
        actor.faceToward(p, 300f)
        assertEquals(-1f, p.body.facing, 0f)
    }

    @Test
    fun `surfaceY est la plateforme ou le sol`() {
        val p = pet()
        assertEquals(arena.groundY, actor.surfaceY(p), 0f)
        p.body.platform = Box(0f, 700f, 10f, 720f)
        assertEquals(700f, actor.surfaceY(p), 0f)
    }
}
