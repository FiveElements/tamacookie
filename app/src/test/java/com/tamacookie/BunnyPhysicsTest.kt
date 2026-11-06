package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random
import kotlin.math.abs

/**
 * Tests de la physique pure du lapin et des objets (densité 1 : 1 dp = 1 px, sauf mention).
 * Écran de référence : minX 34, maxX 1046, topY 0 (plafond à 100), sol à 1000.
 */
class BunnyPhysicsTest {

    // ------------------------------------------------------------------ helpers

    private val g = Tuning.GRAVITY_DP
    private val ceiling = Tuning.CEILING_DP

    private fun phys(x: Float = 100f, y: Float = 1000f, density: Float = 1f) = BunnyPhysics(density).apply {
        setBounds(minX = 34f * density, maxX = 1046f * density, topY = 0f, groundY = 1000f * density)
        this.x = x; this.y = y
    }

    /** Fait voler jusqu'à un atterrissage ; retourne le dernier événement et ceux rencontrés. */
    private fun flyUntilLanded(
        p: BunnyPhysics, platforms: List<Box> = emptyList(), dt: Float = 1f / 120f, maxSteps: Int = 5000,
        onStep: (AirEvent) -> Unit = {},
    ): Pair<AirEvent, List<AirEvent>> {
        val seen = ArrayList<AirEvent>()
        repeat(maxSteps) {
            val ev = p.stepAir(dt, platforms)
            seen.add(ev)
            onStep(ev)
            if (ev is AirEvent.Landed || ev == AirEvent.ForcedLanded) return ev to seen
        }
        throw AssertionError("jamais atterri")
    }

    /** Un seul pas très court (1 ms) : permet de contrôler précisément la vitesse d'impact. */
    private fun oneStep(p: BunnyPhysics, platforms: List<Box> = emptyList()) = p.stepAir(0.001f, platforms)

    /**
     * Intègre une trajectoire balistique pendant [time] s (même schéma que stepAir : Euler semi-implicite),
     * à petit pas, sans sol ni murs. Retourne (x, y, yMin).
     */
    private fun simulate(x0: Float, y0: Float, vx: Float, vy0: Float, time: Float, gravity: Float, dt: Float = 1e-4f): Triple<Float, Float, Float> {
        var x = x0.toDouble(); var y = y0.toDouble(); var vy = vy0.toDouble(); var yMin = y
        var t = 0.0
        while (t < time - 1e-9) {
            val h = minOf(dt.toDouble(), time - t)
            vy += gravity * h
            x += vx * h
            y += vy * h
            if (y < yMin) yMin = y
            t += h
        }
        return Triple(x.toFloat(), y.toFloat(), yMin.toFloat())
    }

    private fun assertFinite(vararg v: Float) = v.forEach { assertTrue("valeur non finie : $it", it.isFinite()) }

    private class TestBody(
        override var x: Float, override var y: Float,
        override var vx: Float = 0f, override var vy: Float = 0f,
        override var onGround: Boolean = false,
    ) : Body

    /** Balle de référence (paramètres de ToyController) sur l'écran 1080 x 1000. */
    private fun stepBall(b: Body, dt: Float = 1f / 60f, gFactor: Float = 1f, bounce: Float = 0.62f,
                         wallBounce: Float = 0.8f, friction: Float = 2.2f) =
        BunnyPhysics.stepProp(b, dt, g, gFactor, bounce, wallBounce, friction,
            halfW = 21f, height = 42f, screenW = 1080f, topY = 0f, groundY = 1000f, settleSpeed = Tuning.PROP_SETTLE_DP)

    // ------------------------------------------------------------------ solveJump / impulseForHeight

    @Test
    fun `solveJump atteint la cible`() {
        val j = BunnyPhysics.solveJump(0f, 1000f, 400f, 800f, 100f, g)
        val t = j.time
        assertEquals(400f, j.vx * t, 0.01f)
        assertEquals(800f, 1000f + j.vy * t + 0.5f * g * t * t, 0.5f)
    }

    @Test
    fun `solveJump - la simulation pas a pas arrive sur la cible plus haute, plus basse ou a meme hauteur`() {
        val cases = listOf(
            floatArrayOf(100f, 1000f, 500f, 700f),   // plus haute, à droite
            floatArrayOf(800f, 600f, 300f, 1000f),   // plus basse, à gauche
            floatArrayOf(200f, 1000f, 650f, 1000f),  // même hauteur
            floatArrayOf(500f, 900f, 500f, 400f),    // à la verticale
        )
        for ((fx, fy, tx, ty) in cases.map { it.toList() }) {
            val j = BunnyPhysics.solveJump(fx, fy, tx, ty, 80f, g)
            assertTrue(j.time > 0f)
            val (x, y, _) = simulate(fx, fy, j.vx, j.vy, j.time, g)
            assertEquals("x pour ($fx,$fy)->($tx,$ty)", tx, x, 0.5f)
            assertEquals("y pour ($fx,$fy)->($tx,$ty)", ty, y, 0.5f)
        }
    }

    @Test
    fun `solveJump - le sommet est apexRise au-dessus du plus haut des deux points`() {
        for ((fy, ty) in listOf(1000f to 700f, 600f to 1000f, 900f to 900f)) {
            val apexRise = 90f
            val j = BunnyPhysics.solveJump(0f, fy, 300f, ty, apexRise, g)
            val (_, _, yMin) = simulate(0f, fy, j.vx, j.vy, j.time, g)
            val expected = minOf(fy, ty) - apexRise
            assertEquals(expected, yMin, 0.5f)
            assertTrue(yMin < fy && yMin < ty)
        }
    }

    @Test
    fun `solveJump - vx du signe de la cible, vy vers le haut, arrivee en descendant`() {
        val right = BunnyPhysics.solveJump(500f, 1000f, 800f, 700f, 70f, g)
        val left = BunnyPhysics.solveJump(500f, 1000f, 200f, 700f, 70f, g)
        val vertical = BunnyPhysics.solveJump(500f, 1000f, 500f, 700f, 70f, g)
        assertTrue(right.vx > 0f); assertTrue(left.vx < 0f); assertEquals(0f, vertical.vx, 0f)
        assertEquals(-right.vx, left.vx, 0.001f)
        for (j in listOf(right, left, vertical)) {
            assertTrue(j.vy < 0f)
            assertTrue("doit arriver en descendant", j.vy + g * j.time > 0f)
        }
    }

    @Test
    fun `solveJump - densite 2 - meme duree, vitesses doublees`() {
        val j1 = BunnyPhysics.solveJump(100f, 1000f, 400f, 800f, 80f, g)
        val j2 = BunnyPhysics.solveJump(200f, 2000f, 800f, 1600f, 160f, g * 2f)
        assertEquals(j1.time, j2.time, 1e-4f)
        assertEquals(2f * j1.vx, j2.vx, 0.01f)
        assertEquals(2f * j1.vy, j2.vy, 0.01f)
    }

    @Test
    fun `impulseForHeight fait monter exactement de h`() {
        for (h in listOf(10f, 120f, 400f)) {
            val vy0 = BunnyPhysics.impulseForHeight(g, h)
            assertTrue(vy0 < 0f)
            val (_, _, yMin) = simulate(0f, 0f, 0f, vy0, -vy0 / g, g)
            assertEquals(-h, yMin, 0.2f)
        }
        assertEquals(0f, BunnyPhysics.impulseForHeight(g, 0f), 0f)
    }

    @Test
    fun `impulseForHeight - le lapin atteint la hauteur voulue via stepAir`() {
        val p = phys(x = 500f)
        p.jump(BunnyPhysics.impulseForHeight(p.gravity, 300f), 0f)
        var top = p.y
        flyUntilLanded(p, dt = 1f / 2000f) { if (p.y < top) top = p.y }
        assertEquals(700f, top, 1f)
    }

    // ------------------------------------------------------------------ jumpTo + stepAir

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
    fun `saut balistique vers la gauche au pas plafonne de 50 ms`() {
        val p = phys(x = 900f)
        p.jumpTo(400f, 1000f, apexRise = 70f)
        val vx = p.vx
        assertTrue(vx < 0f)
        val (ev, _) = flyUntilLanded(p, dt = Tuning.DT_CAP_S)
        assertEquals(AirEvent.Landed(-1, false), ev)
        assertEquals(400f, p.x, abs(vx) * Tuning.DT_CAP_S + 1f)
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
    fun `atterrit sur l icone visee parmi plusieurs avec le bon indice`() {
        val icons = listOf(
            Box(100f, 700f, 200f, 760f),
            Box(600f, 500f, 700f, 560f),
            Box(850f, 800f, 950f, 860f),
        )
        for ((i, icon) in icons.withIndex()) {
            val p = phys(x = 400f)
            p.jumpTo(icon.centerX, icon.top, apexRise = 80f)
            val (ev, _) = flyUntilLanded(p, icons)
            assertEquals("icône $i", AirEvent.Landed(i, false), ev)
            assertEquals(icon.top, p.y, 0f)
            assertEquals(icon.centerX, p.x, 8f)
        }
    }

    @Test
    fun `ne se pose pas en montant a travers une icone, seulement en redescendant`() {
        val p = phys(x = 300f)
        val icon = Box(250f, 800f, 350f, 860f)
        p.jump(BunnyPhysics.impulseForHeight(p.gravity, 350f), 0f)   // sommet à 650, au-dessus de l'icône
        var wentAbove = false
        val (ev, _) = flyUntilLanded(p, listOf(icon)) { e ->
            if (p.vy < 0f) assertFalse("posé en montant", e is AirEvent.Landed)
            if (p.y < icon.top) wentAbove = true
        }
        assertTrue(wentAbove)
        assertEquals(AirEvent.Landed(0, false), ev)
        assertEquals(800f, p.y, 0f)
    }

    @Test
    fun `saut trop court sous une icone - retombe au sol`() {
        val p = phys(x = 300f)
        val icon = Box(250f, 800f, 350f, 860f)
        p.jump(BunnyPhysics.impulseForHeight(p.gravity, 150f), 0f)   // sommet à 850 < haut de l'icône
        val (ev, _) = flyUntilLanded(p, listOf(icon))
        assertEquals(AirEvent.Landed(-1, false), ev)
    }

    @Test
    fun `marge horizontale - pas d atterrissage trop pres du bord de l icone`() {
        val icon = Box(250f, 800f, 350f, 860f)
        val margin = Tuning.PLATFORM_MARGIN_DP
        // (x, attendu : indice ou -1 pour le sol)
        val cases = listOf(
            icon.left + margin - 1f to -1, icon.left + margin to 0, icon.left + margin + 1f to 0,
            icon.right - margin - 1f to 0, icon.right - margin to 0, icon.right - margin + 1f to -1,
            icon.left - 10f to -1,
        )
        for ((x, expected) in cases) {
            val p = phys(x = x, y = 600f)
            p.jump(0f, 0f)
            val (ev, _) = flyUntilLanded(p, listOf(icon))
            assertEquals("x = $x", expected, (ev as AirEvent.Landed).platform)
        }
    }

    @Test
    fun `tolerance sur le haut de l icone`() {
        val icon = Box(250f, 800f, 350f, 860f)
        val tol = Tuning.PLATFORM_TOP_TOLERANCE_PX
        val p = phys(x = 300f, y = 800f + tol - 0.5f)
        p.vy = 100f
        assertEquals(AirEvent.Landed(0, false), oneStep(p, listOf(icon)))
        assertEquals(800f, p.y, 0f)

        val q = phys(x = 300f, y = 800f + tol + 1f)        // déjà sous le haut : il traverse
        q.vy = 100f
        assertEquals(AirEvent.Flying, oneStep(q, listOf(icon)))
    }

    @Test
    fun `atterrissage brutal sur icone au-dela du seuil, pas en dessous`() {
        val icon = Box(250f, 800f, 350f, 860f)
        val hard = Tuning.HARD_LANDING_DP
        for ((vy, expected) in listOf(hard - 50f to false, hard + 50f to true)) {
            val p = phys(x = 300f, y = 799f)
            p.vy = vy
            assertEquals("vy = $vy", AirEvent.Landed(0, expected), oneStep(p, listOf(icon)))
            assertEquals(0f, p.vx, 0f); assertEquals(0f, p.vy, 0f); assertEquals(0f, p.airVy, 0f)
        }
    }

    @Test
    fun `sol - atterrissage doux sous le seuil de rebond`() {
        val p = phys(x = 500f, y = 999f)
        p.vy = Tuning.GROUND_BOUNCE_DP - 100f; p.vx = 200f
        assertEquals(AirEvent.Landed(-1, false), oneStep(p))
        assertEquals(1000f, p.y, 0f)
        assertEquals(0f, p.vx, 0f); assertEquals(0f, p.vy, 0f); assertEquals(0f, p.airVy, 0f)
        assertFalse(p.hardLanding)
    }

    @Test
    fun `sol - rebond amorti au-dela du seuil, sans etre brutal`() {
        val p = phys(x = 500f, y = 999f)
        p.vy = Tuning.GROUND_BOUNCE_DP + 100f; p.vx = 200f
        assertEquals(AirEvent.Bounced, oneStep(p))
        val impact = Tuning.GROUND_BOUNCE_DP + 100f + g * 0.001f
        assertEquals(1000f, p.y, 0f)
        assertEquals(-impact * Tuning.GROUND_BOUNCE_DAMP, p.vy, 0.01f)
        assertEquals(200f * Tuning.GROUND_BOUNCE_FRICTION, p.vx, 0.01f)
        assertFalse(p.hardLanding)
        val (ev, _) = flyUntilLanded(p)
        assertEquals(AirEvent.Landed(-1, false), ev)
    }

    @Test
    fun `sol - impact brutal - rebondit puis se pose en brutal`() {
        val p = phys(x = 500f, y = 999f)
        p.vy = Tuning.HARD_LANDING_DP + 100f
        assertEquals(AirEvent.Bounced, oneStep(p))
        assertTrue(p.hardLanding)
        val (ev, _) = flyUntilLanded(p)
        assertEquals(AirEvent.Landed(-1, true), ev)
        assertFalse(p.hardLanding)
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
    fun `impact brutal au sol puis pose sur une icone - le vol suivant n herite pas de l impact`() {
        val p = phys(x = 300f, y = 999f)
        p.vy = Tuning.HARD_LANDING_DP + 100f
        assertEquals(AirEvent.Bounced, oneStep(p))      // rebond : remonte d'environ 110 px
        val icon = Box(250f, 950f, 350f, 1000f)
        val (ev, _) = flyUntilLanded(p, listOf(icon))
        assertEquals("l'impact brutal du vol est signalé à l'atterrissage", AirEvent.Landed(0, true), ev)
        assertFalse(p.hardLanding)

        // L'icône disparaît : il tombe (stop(), sans jump) de 50 px seulement → atterrissage doux
        p.stop()
        val (ev2, _) = flyUntilLanded(p)
        assertEquals(AirEvent.Landed(-1, false), ev2)
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
    fun `rebond sur le mur droit avec vitesse inversee et attenuee`() {
        val p = phys(x = 1040f, y = 500f)
        p.jump(0f, 1000f)
        assertEquals(AirEvent.HitWall, p.stepAir(0.02f, emptyList()))
        assertEquals(1046f, p.x, 0f)
        assertEquals(-1000f * Tuning.WALL_BOUNCE, p.vx, 0.001f)
    }

    @Test
    fun `plafond - repousse vers le bas avec attenuation`() {
        val p = phys(x = 500f, y = ceiling + 5f)
        p.jump(-1000f, 0f)
        val ev = oneStep(p.apply { y = ceiling + 0.5f })
        assertEquals(AirEvent.Flying, ev)
        assertEquals(ceiling, p.y, 0f)
        val expected = abs(-1000f + g * 0.001f) * Tuning.CEILING_BOUNCE
        assertEquals(expected, p.vy, 0.01f)
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
    fun `atterrissage force - ignore les icones et ne se pose pas en montant`() {
        val p = phys(x = 300f)
        val icon = Box(250f, 750f, 350f, 800f)
        p.jump(BunnyPhysics.impulseForHeight(p.gravity, 400f), 0f)   // sommet 600
        p.airLandY = 850f
        val (ev, _) = flyUntilLanded(p, listOf(icon)) { e ->
            if (p.vy < 0f) assertEquals(AirEvent.Flying, e)
        }
        assertEquals(AirEvent.ForcedLanded, ev)
        assertEquals(850f, p.y, 0f)
        assertEquals(0f, p.vx, 0f); assertEquals(0f, p.vy, 0f); assertEquals(0f, p.airVy, 0f)
    }

    @Test
    fun `atterrissage force - ignore aussi le sol`() {
        val p = phys(x = 500f, y = 900f)
        p.jump(0f, 0f)
        p.airLandY = 1050f
        val (ev, seen) = flyUntilLanded(p)
        assertEquals(AirEvent.ForcedLanded, ev)
        assertEquals(1050f, p.y, 0f)
        assertTrue(seen.none { it is AirEvent.Landed || it == AirEvent.Bounced })
    }

    @Test
    fun `jump et jumpTo annulent l atterrissage force et l impact brutal`() {
        val p = phys()
        p.airLandY = 500f; p.hardLanding = true
        p.jump(-500f, 10f)
        assertNull(p.airLandY); assertFalse(p.hardLanding)
        p.airLandY = 500f; p.hardLanding = true
        p.jumpTo(400f, 1000f, 80f)
        assertNull(p.airLandY); assertFalse(p.hardLanding)
    }

    @Test
    fun `lancer borne`() {
        val p = phys()
        p.release(99_999f, -99_999f)
        assertEquals(Tuning.THROW_MAX_DP, p.vx, 0f)
        assertEquals(-Tuning.THROW_MAX_DP, p.vy, 0f)
    }

    @Test
    fun `lancer - borne proportionnelle a la densite, vitesses raisonnables inchangees`() {
        val p = phys(density = 2f)
        p.hardLanding = true
        p.release(-99_999f, 99_999f)
        assertEquals(-Tuning.THROW_MAX_DP * 2f, p.vx, 0f)
        assertEquals(Tuning.THROW_MAX_DP * 2f, p.vy, 0f)
        assertFalse(p.hardLanding)
        p.release(1234f, -567f)
        assertEquals(1234f, p.vx, 0f); assertEquals(-567f, p.vy, 0f)
    }

    @Test
    fun `stop remet les vitesses a zero`() {
        val p = phys(x = 500f, y = 500f)
        p.release(800f, -900f)
        p.stop()
        assertEquals(0f, p.vx, 0f); assertEquals(0f, p.vy, 0f)
    }

    @Test
    fun `facing suit vx au-dela du seuil`() {
        val face = Tuning.FACE_VX_DP
        for ((vx, expected) in listOf(face + 40f to 1f, -(face + 40f) to -1f, face - 20f to 0f, 0f to 0f)) {
            val p = phys(x = 500f, y = 500f)
            p.jump(0f, vx)
            assertEquals(AirEvent.Flying, oneStep(p))
            assertEquals("vx = $vx", expected, p.facing, 0f)
            assertEquals(p.vy, p.airVy, 0f)
        }
        // aussi pendant un atterrissage forcé
        val p = phys(x = 500f, y = 500f)
        p.jump(0f, -300f); p.airLandY = 900f
        oneStep(p)
        assertEquals(-1f, p.facing, 0f)
    }

    // ------------------------------------------------------------------ stepProp (jouets)

    @Test
    fun `objet - la gravite accelere la chute`() {
        val b = TestBody(x = 500f, y = 300f)
        stepBall(b, dt = 0.01f)
        assertEquals(g * 0.01f, b.vy, 0.001f)
        assertEquals(300f + g * 0.01f * 0.01f, b.y, 0.001f)
        assertFalse(b.onGround)
    }

    @Test
    fun `objet - rebond au sol attenue, puis arret sous settleSpeed`() {
        val b = TestBody(x = 500f, y = 999f, vy = 1000f)
        stepBall(b, dt = 0.001f)
        assertEquals(1000f, b.y, 0f)
        assertEquals(-(1000f + g * 0.001f) * 0.62f, b.vy, 0.01f)
        assertFalse(b.onGround)

        val slow = TestBody(x = 500f, y = 999.9f, vy = Tuning.PROP_SETTLE_DP - 50f)
        stepBall(slow, dt = 0.001f)
        assertEquals(0f, slow.vy, 0f)
        assertTrue(slow.onGround)
    }

    @Test
    fun `objet - les rebonds successifs diminuent jusqu a l arret`() {
        val b = TestBody(x = 500f, y = 200f)
        val impacts = ArrayList<Float>()
        var prevVy = 0f
        repeat(1200) {   // 5 s
            prevVy = b.vy
            stepBall(b, dt = 1f / 240f)
            if (prevVy > 0f && b.vy <= 0f && b.y == 1000f) impacts.add(prevVy)
        }
        assertTrue(impacts.size >= 2)
        for (i in 1 until impacts.size) assertTrue("rebond $i plus faible", impacts[i] < impacts[i - 1])
        assertTrue(b.onGround)
        assertEquals(0f, b.vy, 0f)
    }

    @Test
    fun `objet - le frottement au sol l arrete`() {
        val b = TestBody(x = 300f, y = 1000f, vx = 400f, onGround = true)
        var prev = b.vx
        repeat(300) {   // 5 s
            stepBall(b)
            assertTrue(b.onGround)
            assertEquals(1000f, b.y, 0f)
            assertTrue(b.vx in 0f..prev)
            prev = b.vx
        }
        assertTrue("vx = ${b.vx}", b.vx < 1f)
    }

    @Test
    fun `objet - frottement au sol jamais inverse meme avec un grand pas`() {
        val b = TestBody(x = 300f, y = 1000f, vx = 400f, onGround = true)
        stepBall(b, dt = 1f)    // 1 - 2.2 * 1 < 0 → borné à 0
        assertEquals(0f, b.vx, 0f)
    }

    @Test
    fun `objet - un objet pose reste immobile au pas plafonne`() {
        val b = TestBody(x = 300f, y = 1000f, onGround = true)
        repeat(100) {
            stepBall(b, dt = Tuning.DT_CAP_S)
            assertTrue(b.onGround); assertEquals(1000f, b.y, 0f); assertEquals(0f, b.vy, 0f)
        }
    }

    @Test
    fun `objet - redecolle - onGround repasse a faux`() {
        val b = TestBody(x = 300f, y = 1000f, vy = -800f, onGround = true)
        stepBall(b)
        assertTrue(b.y < 998f)
        assertFalse(b.onGround)
    }

    @Test
    fun `objet - rebond sur les murs`() {
        val l = TestBody(x = 25f, y = 500f, vx = -1000f)
        stepBall(l, dt = 0.01f)
        assertEquals(21f, l.x, 0f)
        assertEquals(800f, l.vx, 0.001f)

        val r = TestBody(x = 1055f, y = 500f, vx = 1000f)
        stepBall(r, dt = 0.01f)
        assertEquals(1059f, r.x, 0f)
        assertEquals(-800f, r.vx, 0.001f)
    }

    @Test
    fun `objet - rebond au plafond`() {
        val b = TestBody(x = 500f, y = 45f, vy = -1000f)
        stepBall(b, dt = 0.01f)
        assertEquals(42f, b.y, 0f)          // haut de l'objet collé au plafond
        assertEquals((1000f - g * 0.01f) * Tuning.PROP_CEILING_BOUNCE, b.vy, 0.01f)
    }

    @Test
    fun `objet - gFactor faible - le ballon flotte`() {
        val ball = TestBody(x = 300f, y = 100f)
        val balloon = TestBody(x = 700f, y = 100f)
        repeat(30) {    // 0,5 s
            stepBall(ball)
            stepBall(balloon, gFactor = 0.08f, bounce = 0.3f, wallBounce = 0.9f, friction = 3f)
        }
        val fallBall = ball.y - 100f
        val fallBalloon = balloon.y - 100f
        assertTrue(fallBalloon > 0f)
        assertEquals(0.08f, fallBalloon / fallBall, 0.005f)
    }

    @Test
    fun `objet - ne sort jamais de l ecran, meme lance violemment`() {
        val rnd = Random(42)
        repeat(50) {
            val b = TestBody(
                x = 21f + rnd.nextFloat() * 1038f, y = 42f + rnd.nextFloat() * 958f,
                vx = (rnd.nextFloat() * 2f - 1f) * Tuning.TOY_THROW_MAX_DP,
                vy = (rnd.nextFloat() * 2f - 1f) * Tuning.TOY_THROW_MAX_DP,
            )
            val dt = if (rnd.nextBoolean()) Tuning.DT_CAP_S else 1f / 60f
            repeat(400) {
                stepBall(b, dt = dt)
                assertFinite(b.x, b.y, b.vx, b.vy)
                assertTrue("x = ${b.x}", b.x in 21f..1059f)
                assertTrue("y = ${b.y}", b.y in 42f..1000f)
            }
        }
    }

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

    // ------------------------------------------------------------------ stepFalling (nourriture)

    @Test
    fun `la nourriture tombe et se pose`() {
        val b = TestBody(x = 200f, y = 100f)
        var landed = false
        repeat(500) { if (BunnyPhysics.stepFalling(b, 1f / 60f, 2600f, 34f, 1046f, 1000f)) landed = true }
        assertTrue(landed && b.onGround)
        assertEquals(1000f, b.y, 0f)
    }

    @Test
    fun `nourriture - demi-gravite, true une seule fois a l atterrissage`() {
        val b = TestBody(x = 200f, y = 100f, vx = 120f)
        assertFalse(BunnyPhysics.stepFalling(b, 0.01f, g, 34f, 1046f, 1000f))
        assertEquals(g * 0.5f * 0.01f, b.vy, 0.001f)
        var landings = 0
        repeat(500) { if (BunnyPhysics.stepFalling(b, 1f / 60f, g, 34f, 1046f, 1000f)) landings++ }
        assertEquals(1, landings)
        assertTrue(b.onGround)
        assertEquals(1000f, b.y, 0f)
        assertEquals(0f, b.vx, 0f)
        val x = b.x
        assertFalse(BunnyPhysics.stepFalling(b, 1f / 60f, g, 34f, 1046f, 1000f))
        assertEquals(x, b.x, 0f)
    }

    @Test
    fun `nourriture - bornee horizontalement`() {
        val l = TestBody(x = 40f, y = 100f, vx = -5000f)
        val r = TestBody(x = 1000f, y = 100f, vx = 5000f)
        repeat(200) {
            BunnyPhysics.stepFalling(l, Tuning.DT_CAP_S, g, 34f, 1046f, 1000f)
            BunnyPhysics.stepFalling(r, Tuning.DT_CAP_S, g, 34f, 1046f, 1000f)
            assertTrue(l.x in 34f..1046f); assertTrue(r.x in 34f..1046f)
        }
        assertEquals(34f, l.x, 0f); assertEquals(1046f, r.x, 0f)
        assertTrue(l.onGround && r.onGround)
    }

    // ------------------------------------------------------------------ invariants

    @Test
    fun `gravite proportionnelle a la densite`() {
        assertEquals(Tuning.GRAVITY_DP, BunnyPhysics(1f).gravity, 0f)
        assertEquals(Tuning.GRAVITY_DP * 2.75f, BunnyPhysics(2.75f).gravity, 0.01f)
    }

    @Test
    fun `densite 2 - meme saut a l echelle, meme duree`() {
        val p1 = phys(x = 100f)
        val p2 = phys(x = 200f, density = 2f).apply { y = 2000f }
        p1.jumpTo(500f, 1000f, 80f)
        p2.jumpTo(1000f, 2000f, 160f)
        val (_, s1) = flyUntilLanded(p1)
        val (e2, s2) = flyUntilLanded(p2)
        assertEquals(AirEvent.Landed(-1, false), e2)
        assertEquals(s1.size, s2.size)
        assertEquals(p1.x * 2f, p2.x, 1f)
    }

    @Test
    fun `dt nul - rien ne bouge, aucune valeur non finie`() {
        val p = phys(x = 500f, y = 500f)
        p.jump(-300f, 200f)
        assertEquals(AirEvent.Flying, p.stepAir(0f, emptyList()))
        assertEquals(500f, p.x, 0f); assertEquals(500f, p.y, 0f)
        assertEquals(-300f, p.vy, 0f); assertEquals(200f, p.vx, 0f)

        val onGround = phys(x = 500f)
        assertEquals(AirEvent.Landed(-1, false), onGround.stepAir(0f, emptyList()))
        assertFinite(onGround.x, onGround.y, onGround.vx, onGround.vy)

        val b = TestBody(x = 500f, y = 500f, vx = 100f, vy = -100f)
        stepBall(b, dt = 0f)
        assertEquals(500f, b.x, 0f); assertEquals(500f, b.y, 0f); assertEquals(100f, b.vx, 0f)

        val f = TestBody(x = 500f, y = 500f)
        assertFalse(BunnyPhysics.stepFalling(f, 0f, g, 34f, 1046f, 1000f))
        assertEquals(500f, f.y, 0f)
    }

    @Test
    fun `grands pas - le lapin reste dans l ecran et finit par se poser`() {
        val rnd = Random(7)
        for (dt in listOf(Tuning.DT_CAP_S, Tuning.SUSPENDED_STEP_S, 2f / Tuning.FPS_SLEEP)) {
            repeat(30) {
                val p = phys(x = 34f + rnd.nextFloat() * 1012f, y = 150f + rnd.nextFloat() * 850f)
                p.release((rnd.nextFloat() * 2f - 1f) * 9000f, (rnd.nextFloat() * 2f - 1f) * 9000f)
                flyUntilLanded(p, dt = dt) {
                    assertFinite(p.x, p.y, p.vx, p.vy, p.airVy)
                    assertTrue("x = ${p.x}", p.x in 34f..1046f)
                    assertTrue("y = ${p.y}", p.y in ceiling..1000f)
                }
                assertEquals(1000f, p.y, 0f)
                assertFalse(p.hardLanding)
            }
        }
    }

    @Test
    fun `box - centre`() {
        val b = Box(10f, 20f, 50f, 100f)
        assertEquals(30f, b.centerX, 0f)
        assertEquals(60f, b.centerY, 0f)
    }

    @Test
    fun `landed - onPlatform`() {
        assertTrue(AirEvent.Landed(0, false).onPlatform)
        assertFalse(AirEvent.Landed(-1, true).onPlatform)
        if (AirEvent.Landed(2, true) != AirEvent.Landed(2, true)) fail("égalité de données attendue")
    }
}
