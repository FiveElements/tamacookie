package com.tamacookie

import com.tamacookie.persist.PetRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PetTest {

    private fun newResident() = testPet(BunnyPhysics(), Random(1))

    @Test
    fun `setAct remet le chrono a zero et retient la duree`() {
        val b = Body()
        b.actTime = 5f
        b.setAct(Act.WALK, 7f)
        assertEquals(Act.WALK, b.act)
        assertEquals(0f, b.actTime, 0f)
        assertEquals(7f, b.actDuration, 0f)
    }

    @Test
    fun `en plein vol l'activite est programmee pour l'atterrissage`() {
        val b = Body()
        b.setAct(Act.AIR)
        b.actTime = 0.4f
        b.setActOrAfterAir(Act.PLAY)
        assertEquals(Act.AIR, b.act)
        assertEquals(Act.PLAY, b.afterAir)
        assertEquals(0.4f, b.actTime, 0f)
    }

    @Test
    fun `au sol setActOrAfterAir change l'activite tout de suite`() {
        val b = Body()
        b.setActOrAfterAir(Act.SLEEP, 9f)
        assertEquals(Act.SLEEP, b.act)
        assertEquals(9f, b.actDuration, 0f)
    }

    @Test
    fun `la position du corps est celle de sa physique`() {
        val b = Body()
        b.fx = 12f; b.fy = 34f
        assertEquals(12f, b.phys.x, 0f)
        assertEquals(34f, b.phys.y, 0f)
    }

    @Test
    fun `une expression forcee expire`() {
        val m = Mind(Random(0))
        m.express(Expr.HAPPY, 1f)
        m.tick(0.6f)
        assertEquals(Expr.HAPPY, m.exprOverride)
        m.tick(0.6f)
        assertNull(m.exprOverride)
    }

    @Test
    fun `le delai de coup de patte diminue`() {
        val m = Mind(Random(0))
        m.kickCooldown = 0.5f
        m.tick(0.2f)
        assertEquals(0.3f, m.kickCooldown, 1e-5f)
    }

    @Test
    fun `l'identifiant est stable, renommer garde l'identifiant`() {
        val rec = PetRecord(Identity(PetId("abc"), "Cookie", bornAt = 5L))
        val a = Pet.fromRecord(rec, BunnyPhysics(), Random(1))
        val b = Pet.fromRecord(rec, BunnyPhysics(), Random(2))
        assertEquals(a.id, b.id)
        assertEquals(Origin.Resident, a.identity.origin)
        a.rename("Noisette")
        assertEquals("Noisette", a.name)
        assertEquals(PetId("abc"), a.id)
        assertEquals(PetId("abc"), a.identity.id)
        assertEquals(rec.copy(identity = rec.identity.copy(name = "Noisette")), a.toRecord().copy(lastUpdate = rec.lastUpdate))
    }

    @Test
    fun `les jauges ne changent que par les evenements`() {
        val p = newResident()
        val r = p.on(PetEvent.Fed(), 0L)
        assertSame(r.stats, p.stats)
        assertTrue(p.wouldRefuse(PetEvent.PlayStarted, 0L).not())
        p.stats = p.stats.copy(energy = 5f)
        assertTrue(p.wouldRefuse(PetEvent.PlayStarted, 0L))
        assertEquals(5f, p.stats.energy)
    }

    @Test
    fun `cache-cache se deduit de l'activite`() {
        val p = newResident()
        assertFalse(p.isHiding)
        p.setAct(Act.PEEK)
        assertTrue(p.isHiding)
    }

    @Test
    fun `le monde retrouve son resident`() {
        val w = World()
        val p = newResident()
        w.add(p)
        assertSame(p, w.resident)
        assertSame(p, w.pets[p.id])
        assertFalse(w.hasToyOrFood())
    }
}
