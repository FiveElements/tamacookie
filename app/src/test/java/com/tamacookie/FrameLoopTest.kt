package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Boucle d'images ([FrameLoop]) pilotée par un faux Choreographer / Handler à horloge virtuelle, branchée sur
 * l'animation ambiante ([AmbientAnimator.frame]) comme dans le service : un animal **visible** doit recevoir
 * des images, voir son dessin changer et demander des redessins, quelle que soit sa pose.
 */
class FrameLoopTest {

    /** Faux Choreographer (vsync toutes les [vsyncMs] ms) + Handler, sur une horloge virtuelle. */
    private class FakeScheduler(val vsyncMs: Long = 16L) : FrameScheduler {
        var now = 0L
        var frameAt: Long? = null
        var tickAt: Long? = null
        override fun postFrame(delayMs: Long) {
            // comme Choreographer : la prochaine vsync après l'échéance
            val due = now + delayMs
            frameAt = (due / vsyncMs + 1) * vsyncMs
        }
        override fun cancelFrames() { frameAt = null }
        override fun postTick(delayMs: Long) { tickAt = now + delayMs }
        override fun cancelTicks() { tickAt = null }

        /** Fait avancer le temps de [ms] en livrant images et vérifications à leur échéance. */
        fun run(loop: FrameLoop, ms: Long) {
            val end = now + ms
            while (true) {
                val f = frameAt; val t = tickAt
                val next = listOfNotNull(f, t).minOrNull() ?: break
                if (next > end) break
                now = next
                if (next == f) { frameAt = null; loop.onFrame(now * 1_000_000L) }
                else { tickAt = null; loop.onTick() }
            }
            now = end
        }
    }

    private class Harness(var state: PetRenderState, var invisible: Boolean = false, var fps: Int = Tuning.FPS_IDLE) {
        val sched = FakeScheduler()
        val ambient = AmbientAnimator(Random(42))
        var shown = state
        var steps = 0
        var simulated = 0f
        var redraws = 0
        val loop = FrameLoop(sched, uptimeMs = { sched.now }, targetFps = { fps }, canSuspend = { invisible }) { dt ->
            steps++; simulated += dt
            // ce que fait PetView.render
            val old = shown
            shown = state
            if (ambient.frame(dt, old, state, 260f, 280f, 2f)) redraws++
        }
    }

    @Test fun `le lapin visible qui dort recoit des images, respire, fait des Zzz et se redessine`() {
        val h = Harness(PetRenderState(pose = Pose.SLEEP, expr = Expr.SLEEPY), fps = Tuning.FPS_SLEEP)
        h.loop.start()
        h.sched.run(h.loop, 500)                      // il vient de s'endormir
        val t0 = h.ambient.t; val steps0 = h.steps; val redraws0 = h.redraws
        h.sched.run(h.loop, 2_500)                    // 2,5 s (comme les captures sur la tablette)
        assertFalse(h.loop.suspended)
        val frames = h.steps - steps0
        assertTrue("$frames images en 2,5 s", frames in 25..35)          // ≈ 12 i/s
        assertEquals("$frames", frames, h.redraws - redraws0)              // chaque image redessine
        assertEquals(2.5f, h.ambient.t - t0, 0.1f)                         // le temps (respiration) avance
        // Zzz : ≈ 0,9 par seconde
        var zzz = 0
        repeat(10) { h.sched.run(h.loop, 1_000); zzz += h.ambient.particles.count { it.type == PType.ZZZ } }
        assertTrue("$zzz", zzz > 0)
    }

    @Test fun `les poses immobiles restent animees`() {
        for (pose in listOf(Pose.IDLE, Pose.SIT, Pose.SLEEP, Pose.EAT, Pose.DRAGGED)) {
            for (fps in listOf(Tuning.FPS_IDLE, Tuning.FPS_SLEEP)) {
                val h = Harness(PetRenderState(pose = pose), fps = fps)
                h.loop.start()
                h.sched.run(h.loop, 3_000)
                assertFalse("$pose", h.loop.suspended)
                assertTrue("$pose à $fps i/s : ${h.redraws} redessins / ${h.steps}", h.redraws == h.steps && h.steps > 3 * fps * 3 / 4)
                assertEquals("$pose", 3f, h.simulated, 0.1f)
            }
        }
    }

    @Test fun `la cadence suit l'activite`() {
        assertEquals(0L, FrameLoop.frameDelayMs(Tuning.FPS_MOVING))
        assertEquals(17L, FrameLoop.frameDelayMs(Tuning.FPS_IDLE))
        assertEquals(67L, FrameLoop.frameDelayMs(Tuning.FPS_SLEEP))
        val h = Harness(PetRenderState(pose = Pose.WALK), fps = Tuning.FPS_MOVING)
        h.loop.start()
        h.sched.run(h.loop, 1_000)
        assertTrue("${h.steps}", h.steps in 55..65)
        assertEquals(1f, h.simulated, 0.05f)
    }

    @Test fun `invisible et rien a animer, la boucle se suspend mais la simulation avance, puis reprend`() {
        val h = Harness(PetRenderState(pose = Pose.SLEEP), invisible = true, fps = Tuning.FPS_SLEEP)
        h.loop.start()
        h.sched.run(h.loop, 100)
        assertTrue(h.loop.suspended)
        assertEquals(null, h.sched.frameAt)
        val sim0 = h.simulated
        h.sched.run(h.loop, 3_000)
        assertEquals(3f, h.simulated - sim0, 0.35f)                         // par pas de 0,1 s
        h.invisible = false                                                 // il redevient visible
        h.sched.run(h.loop, Tuning.SUSPENDED_TICK_MS + 50)
        assertFalse(h.loop.suspended)
        val steps0 = h.steps
        h.sched.run(h.loop, 1_000)
        assertTrue("${h.steps - steps0}", h.steps - steps0 >= 10)
    }

    @Test fun `reprise demandee, arret et redemarrage`() {
        var resumed = 0
        val sched = FakeScheduler()
        var invisible = true
        val loop = FrameLoop(sched, { sched.now }, { 30 }, { invisible }, onResume = { resumed++ }) {}
        assertFalse(loop.resume())                                          // ne tourne pas
        loop.start()
        sched.run(loop, 50)
        assertTrue(loop.suspended)
        invisible = false
        assertTrue(loop.resume())
        assertEquals(1, resumed)
        assertFalse(loop.resume())                                          // déjà reprise
        loop.stop()
        assertEquals(null, sched.frameAt); assertEquals(null, sched.tickAt)
        assertFalse(loop.looping)
        loop.onFrame(1_000_000_000L)                                        // image en retard : ignorée
        loop.start(); loop.start()
        assertTrue(loop.looping)
    }

    @Test fun `une erreur dans une image n'arrete pas la boucle`() {
        val sched = FakeScheduler()
        var n = 0
        val errors = ArrayList<String>()
        val loop = FrameLoop(sched, { sched.now }, { 30 }, { false }, onError = { w, _ -> errors += w }) {
            n++; if (n == 2) throw IllegalStateException("boum")
        }
        loop.start()
        sched.run(loop, 500)
        assertEquals(listOf("frame error"), errors)
        assertTrue("$n", n > 10)
    }
}
