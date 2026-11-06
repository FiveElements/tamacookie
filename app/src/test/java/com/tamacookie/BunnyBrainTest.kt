package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Tests de fumée des décisions pures du lapin. */
class BunnyBrainTest {

    /** Random qui renvoie toujours 0 (nextFloat() == 0f, nextInt(n) == 0). */
    private object Zero : Random() {
        override fun nextBits(bitCount: Int) = 0
    }

    private fun input(
        energy: Float = 80f, hour: Int = 14, personality: Personality = Personality.PLAYFUL,
        platform: Box? = null, platforms: List<Box> = emptyList(), elsewhere: Boolean = false,
    ) = BrainInput(
        energy = energy, satiety = 80f, happiness = 80f, personality = personality, hour = hour,
        x = 500f, facing = 1f, minX = 34f, maxX = 1046f, screenW = 1080f, density = 1f,
        platform = platform, platforms = platforms, elsewhere = elsewhere,
    )

    @Test
    fun `epuise - il dort`() {
        assertEquals(Decision.Sleep, BunnyBrain(Random(1)).decide(input(energy = 5f)))
    }

    @Test
    fun `ignore depuis longtemps - la betise passe avant le reste`() {
        var asked = false
        val d = BunnyBrain(Random(1)).decide(input()) { asked = true; true }
        assertTrue(asked)
        assertEquals(Decision.AttentionSought, d)
    }

    @Test
    fun `les decisions restent dans l'ecran`() {
        repeat(500) { seed ->
            when (val d = BunnyBrain(Random(seed)).decide(input())) {
                is Decision.Walk -> assertTrue(d.targetX in 34f..1046f)
                is Decision.JumpDown -> assertTrue(d.targetX in 34f..1046f)
                Decision.Sleep, Decision.AttentionSought, Decision.GoToChest -> throw AssertionError("inattendu : $d")
                else -> {}
            }
        }
    }

    @Test
    fun `saute sur une icone a portee mais pas sur la sienne`() {
        val cur = Box(480f, 600f, 520f, 640f)
        val other = Box(700f, 500f, 760f, 560f)
        val far = Box(5000f, 500f, 5060f, 560f)
        val brain = BunnyBrain(Zero)
        val pick = brain.pickPlatform(input(platform = cur, platforms = listOf(cur, far, other)))
        assertEquals(Decision.JumpToPlatform(2, other), pick)
        assertEquals(null, brain.pickPlatform(input(platform = cur, platforms = listOf(cur, far))))
    }

    @Test
    fun `sur une autre page il ne saute pas sur les icones d'ici`() {
        val icon = Box(700f, 500f, 760f, 560f)
        repeat(300) { seed ->
            val d = BunnyBrain(Random(seed)).decide(input(platforms = listOf(icon), elsewhere = true))
            assertFalse(d is Decision.JumpToPlatform)
        }
    }

    @Test
    fun `envie du coffre`() {
        val b = BunnyBrain(Zero)
        assertTrue(b.wantsChest(1_000_000L, 0L, false, true, satiety = 30f, happiness = 90f, energy = 50f))
        // trop tôt depuis la dernière fois
        assertFalse(b.wantsChest(1_000L, 0L, false, true, 30f, 90f, 50f))
        // déjà un objet sorti, ou coffre invisible
        assertFalse(b.wantsChest(1_000_000L, 0L, true, true, 30f, 90f, 50f))
        assertFalse(b.wantsChest(1_000_000L, 0L, false, false, 30f, 90f, 50f))
        // ni faim ni ennui
        assertFalse(b.wantsChest(1_000_000L, 0L, false, true, 90f, 90f, 50f))
    }

    @Test
    fun `seuils pour chercher l'attention`() {
        assertEquals(12 * 60_000L, BunnyBrain.attentionThresholdMs(0, Personality.PLAYFUL))
        assertEquals(216_000L, BunnyBrain.attentionThresholdMs(1, Personality.CUDDLY))
        assertEquals(3 * 60_000L, BunnyBrain.attentionThresholdMs(2, Personality.LAZY))
        assertTrue(BunnyBrain.canSeekAttention(1_000_000L, 0L, 0L, 360_000L))
        assertFalse(BunnyBrain.canSeekAttention(100_000L, 0L, 0L, 360_000L))
        assertFalse(BunnyBrain.canSeekAttention(1_000_000L, 0L, 2_000_000L, 360_000L))
    }

    @Test
    fun `retour sur la page affichee`() {
        val now = 100_000L
        fun r(lastScroll: Long = 0L, urgent: Boolean = false, stuff: Boolean = false, at: Long = 0L,
              canCome: Boolean = true, sleeping: Boolean = false) =
            BunnyBrain.pageReturn(now, lastScroll, urgent, stuff, at, canCome, sleeping)
        assertEquals(BunnyBrain.PageReturn.SCROLLING, r(lastScroll = now - 100))
        assertEquals(BunnyBrain.PageReturn.RUSH, r(urgent = true))
        assertEquals(BunnyBrain.PageReturn.SCHEDULE, r())
        assertEquals(BunnyBrain.PageReturn.WAIT, r(at = now + 1000))
        assertEquals(BunnyBrain.PageReturn.COME_BACK, r(at = now - 1))
        assertEquals(BunnyBrain.PageReturn.COME_BACK, r(stuff = true))
        assertEquals(BunnyBrain.PageReturn.WAIT, r(at = now - 1, sleeping = true))
        assertEquals(BunnyBrain.PageReturn.WAIT, r(at = now - 1, canCome = false))
    }

    @Test
    fun `la nuit un lapin paresseux fatigue dort`() {
        // Zero : toutes les probabilités passent
        assertTrue(BunnyBrain(Zero).wantsSleep(input(energy = 70f, hour = 23, personality = Personality.LAZY)))
        assertFalse(BunnyBrain(Zero).wantsSleep(input(energy = 70f, hour = 14, personality = Personality.PLAYFUL)))
    }
}
