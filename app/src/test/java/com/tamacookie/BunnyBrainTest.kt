package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.EnumSet
import kotlin.math.abs
import kotlin.random.Random

/** Tests des décisions pures du lapin (BunnyBrain, Decision, Act). */
class BunnyBrainTest {

    /** Random qui renvoie toujours 0 (nextFloat() == 0f, nextInt(n) == 0). */
    private object Zero : Random() {
        override fun nextBits(bitCount: Int) = 0
    }

    /**
     * Random scripté : chaque appel consomme la valeur suivante de sa file.
     * Un appel non prévu (file vide, ou méthode non scriptée) fait échouer le test,
     * ce qui vérifie aussi quels tirages le cerveau effectue.
     */
    private class Scripted(
        floats: List<Float> = emptyList(),
        ints: List<Int> = emptyList(),
        bools: List<Boolean> = emptyList(),
    ) : Random() {
        private val f = ArrayDeque(floats)
        private val i = ArrayDeque(ints)
        private val b = ArrayDeque(bools)
        val remaining get() = f.size + i.size + b.size

        override fun nextBits(bitCount: Int): Int = throw AssertionError("tirage aléatoire non scripté")
        override fun nextFloat(): Float = f.removeFirstOrNull() ?: throw AssertionError("nextFloat() non scripté")
        override fun nextBoolean(): Boolean = b.removeFirstOrNull() ?: throw AssertionError("nextBoolean() non scripté")
        override fun nextInt(until: Int): Int {
            val v = i.removeFirstOrNull() ?: throw AssertionError("nextInt($until) non scripté")
            require(v in 0 until until) { "valeur scriptée $v hors de [0, $until)" }
            return v
        }
    }

    private fun scripted(vararg floats: Float, ints: List<Int> = emptyList()) = Scripted(floats.toList(), ints)

    private fun input(
        energy: Float = 80f, hour: Int = 14, personality: Personality = Personality.PLAYFUL,
        x: Float = 500f, facing: Float = 1f, density: Float = 1f,
        platform: Box? = null, platforms: List<Box> = emptyList(), elsewhere: Boolean = false,
        canPageWander: Boolean = false, chestVisible: Boolean = false, hasToyOrFood: Boolean = false,
        satiety: Float = 80f, happiness: Float = 80f, nowMs: Long = 0L, lastSelfServeMs: Long = 0L,
    ) = BrainInput(
        energy = energy, satiety = satiety, happiness = happiness, personality = personality, hour = hour,
        x = x, facing = facing, minX = MIN_X, maxX = MAX_X, screenW = W, density = density,
        platform = platform, platforms = platforms, elsewhere = elsewhere, canPageWander = canPageWander,
        chestVisible = chestVisible, hasToyOrFood = hasToyOrFood, nowMs = nowMs, lastSelfServeMs = lastSelfServeMs,
    )

    /** Icône de 60 px de large centrée en [cx]. */
    private fun icon(cx: Float, top: Float = 400f) = Box(cx - 30f, top, cx + 30f, top + 60f)

    private inline fun seeds(n: Int = 2000, block: (Random) -> Unit) = repeat(n) { block(Random(it)) }

    private companion object {
        const val MIN_X = 34f
        const val MAX_X = 1046f
        const val W = 1080f
        const val EPS = 1e-3f
    }

    // ------------------------------------------------------------------ Tests de fumée (existants)

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
                is Decision.Walk -> assertTrue(d.targetX in MIN_X..MAX_X)
                is Decision.JumpDown -> assertTrue(d.targetX in MIN_X..MAX_X)
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
        assertFalse(b.wantsChest(1_000L, 0L, false, true, 30f, 90f, 50f))
        assertFalse(b.wantsChest(1_000_000L, 0L, true, true, 30f, 90f, 50f))
        assertFalse(b.wantsChest(1_000_000L, 0L, false, false, 30f, 90f, 50f))
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
        assertTrue(BunnyBrain(Zero).wantsSleep(input(energy = 70f, hour = 23, personality = Personality.LAZY)))
        assertFalse(BunnyBrain(Zero).wantsSleep(input(energy = 70f, hour = 14, personality = Personality.PLAYFUL)))
    }

    // ------------------------------------------------------------------ Sommeil

    @Test
    fun `isNight - de 22h inclus a 7h exclu`() {
        val night = (0..23).filter { BunnyBrain.isNight(it) }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 22, 23), night)
        assertTrue(BunnyBrain.isNight(22))
        assertFalse(BunnyBrain.isNight(21))
        assertTrue(BunnyBrain.isNight(6))
        assertFalse(BunnyBrain.isNight(7))
    }

    @Test
    fun `energie tres basse - il dort toujours, sans tirage, a toute heure`() {
        for (p in Personality.values()) for (h in 0..23) {
            // Scripted vide : aucun tirage ne doit être nécessaire
            assertTrue("$p $h h", BunnyBrain(Scripted()).wantsSleep(input(energy = 11.9f, hour = h, personality = p)))
            assertEquals("$p $h h", Decision.Sleep, BunnyBrain(Scripted()).decide(input(energy = 0f, hour = h, personality = p)))
        }
        // Paresseux : seuil plus haut
        assertTrue(BunnyBrain(Scripted()).wantsSleep(input(energy = 29.9f, personality = Personality.LAZY)))
    }

    @Test
    fun `seuil d'epuisement - 12 n'est plus epuise le jour`() {
        assertFalse(BunnyBrain(Scripted()).wantsSleep(input(energy = 12f, hour = 14)))
        assertFalse(BunnyBrain(Scripted()).wantsSleep(input(energy = 60f, hour = 14, personality = Personality.LAZY)))
        // paresseux à 30 : plus épuisé, seule la sieste (tirage) peut l'endormir
        assertFalse(BunnyBrain(scripted(0.5f)).wantsSleep(input(energy = 30f, hour = 14, personality = Personality.LAZY)))
    }

    @Test
    fun `nuit et energie moyenne - il dort une fois sur deux`() {
        assertTrue(BunnyBrain(scripted(0.49f)).wantsSleep(input(energy = 40f, hour = 23)))
        assertFalse(BunnyBrain(scripted(0.5f)).wantsSleep(input(energy = 40f, hour = 23)))
        assertTrue(BunnyBrain(scripted(0f)).wantsSleep(input(energy = 54.9f, hour = 22)))
        assertTrue(BunnyBrain(scripted(0f)).wantsSleep(input(energy = 54.9f, hour = 6)))
        // 7h : ce n'est plus la nuit, aucun tirage
        assertFalse(BunnyBrain(Scripted()).wantsSleep(input(energy = 40f, hour = 7)))
        // énergie suffisante : pas de tirage la nuit non plus
        assertFalse(BunnyBrain(Scripted()).wantsSleep(input(energy = 55f, hour = 23)))

        var slept = 0
        seeds { if (BunnyBrain(it).wantsSleep(input(energy = 40f, hour = 2))) slept++ }
        assertTrue("dormi $slept / 2000", slept in 850..1150)
    }

    @Test
    fun `paresseux - seuils de sommeil plus hauts`() {
        val lazy = Personality.LAZY
        // nuit : jusqu'à 75 (au lieu de 55)
        assertTrue(BunnyBrain(scripted(0.4f)).wantsSleep(input(energy = 70f, hour = 23, personality = lazy)))
        assertFalse(BunnyBrain(Scripted()).wantsSleep(input(energy = 70f, hour = 23, personality = Personality.PLAYFUL)))
        assertFalse(BunnyBrain(Scripted()).wantsSleep(input(energy = 75f, hour = 23, personality = lazy)))
        // jour, énergie < 60 : sieste 12 % du temps
        assertTrue(BunnyBrain(scripted(0.11f)).wantsSleep(input(energy = 59f, hour = 14, personality = lazy)))
        assertFalse(BunnyBrain(scripted(0.12f)).wantsSleep(input(energy = 59f, hour = 14, personality = lazy)))
        assertFalse(BunnyBrain(Scripted()).wantsSleep(input(energy = 60f, hour = 14, personality = lazy)))
        // nuit : si le tirage de nuit échoue, la sieste paresseuse a encore sa chance
        assertTrue(BunnyBrain(scripted(0.9f, 0.05f)).wantsSleep(input(energy = 50f, hour = 1, personality = lazy)))
        assertFalse(BunnyBrain(scripted(0.9f, 0.5f)).wantsSleep(input(energy = 50f, hour = 1, personality = lazy)))

        var lazySlept = 0
        var playfulSlept = 0
        seeds {
            if (BunnyBrain(it).wantsSleep(input(energy = 50f, hour = 14, personality = lazy))) lazySlept++
        }
        seeds {
            if (BunnyBrain(it).wantsSleep(input(energy = 50f, hour = 14, personality = Personality.PLAYFUL))) playfulSlept++
        }
        assertTrue("paresseux $lazySlept / 2000", lazySlept in 160..340)
        assertEquals(0, playfulSlept)
    }

    @Test
    fun `jour et energie haute - jamais de sommeil`() {
        for (p in Personality.values()) for (h in 7..21) for (e in listOf(60f, 75f, 100f)) {
            repeat(50) { seed ->
                val b = BunnyBrain(Random(seed * 31 + h))
                assertFalse("$p $h h $e", b.wantsSleep(input(energy = e, hour = h, personality = p)))
            }
        }
    }

    // ------------------------------------------------------------------ Priorités de decide

    @Test
    fun `le sommeil passe avant l'attention - seekAttention n'est pas appele`() {
        var asked = false
        val d = BunnyBrain(Scripted()).decide(input(energy = 5f)) { asked = true; true }
        assertEquals(Decision.Sleep, d)
        assertFalse(asked)
    }

    @Test
    fun `l'attention passe avant le coffre et l'exploration`() {
        val brain = BunnyBrain(Scripted()) // aucun tirage après la bêtise
        val d = brain.decide(input(satiety = 10f, chestVisible = true, nowMs = 1_000_000L, canPageWander = true)) { true }
        assertEquals(Decision.AttentionSought, d)
    }

    @Test
    fun `seekAttention est appele a chaque decision eveillee`() {
        var calls = 0
        seeds(200) { BunnyBrain(it).decide(input()) { calls++; false } }
        assertEquals(200, calls)
    }

    @Test
    fun `coffre - choisi quand il en a envie, avant l'exploration`() {
        val s = input(satiety = 10f, chestVisible = true, nowMs = 1_000_000L, canPageWander = true)
        val rnd = scripted(0.2f)
        assertEquals(Decision.GoToChest, BunnyBrain(rnd).decide(s))
        assertEquals(0, rnd.remaining)
    }

    @Test
    fun `coffre - jamais sans envie`() {
        // pas d'envie : coffre invisible, rassasié et heureux, ou en pause
        val cases = listOf(
            input(satiety = 10f, chestVisible = false, nowMs = 1_000_000L),
            input(satiety = 90f, happiness = 90f, chestVisible = true, nowMs = 1_000_000L),
            input(satiety = 10f, chestVisible = true, nowMs = 100_000L, lastSelfServeMs = 0L),
            input(satiety = 10f, chestVisible = true, nowMs = 1_000_000L, hasToyOrFood = true),
        )
        for (s in cases) seeds(300) { assertFalse(BunnyBrain(it).decide(s) == Decision.GoToChest) }
    }

    @Test
    fun `coffre - pas depuis une autre page`() {
        val s = input(satiety = 10f, chestVisible = true, nowMs = 1_000_000L, elsewhere = true)
        // sur une autre page : ni tirage du coffre ni de l'exploration, seulement r puis la cible
        assertEquals(Decision.Walk(500f), BunnyBrain(scripted(0.1f, 0.5f)).decide(s))
        seeds(500) { assertFalse(BunnyBrain(it).decide(s) == Decision.GoToChest) }
    }

    @Test
    fun `exploration de page - seuil 6 pourcents, 12 pour le curieux`() {
        assertEquals(Decision.PageWander, BunnyBrain(scripted(0.059f)).decide(input(canPageWander = true)))
        assertFalse(BunnyBrain(scripted(0.06f, 0.9f, 0.5f)).decide(input(canPageWander = true)) == Decision.PageWander)
        assertEquals(Decision.PageWander,
            BunnyBrain(scripted(0.119f)).decide(input(canPageWander = true, personality = Personality.CURIOUS)))
        assertFalse(BunnyBrain(scripted(0.12f, 0.9f, 0.5f))
            .decide(input(canPageWander = true, personality = Personality.CURIOUS)) == Decision.PageWander)
    }

    @Test
    fun `exploration de page - seulement si possible, ici et au sol`() {
        // le tirage gagnant ne suffit pas si l'exploration est impossible
        assertEquals(Decision.Idle(5.5f), BunnyBrain(scripted(0f, 0.9f, 0.5f)).decide(input(canPageWander = false)))
        val forbidden = listOf(
            input(canPageWander = false),
            input(canPageWander = true, elsewhere = true),
            input(canPageWander = true, platform = icon(500f)),
        )
        for (s in forbidden) seeds(500) { assertFalse(BunnyBrain(it).decide(s) == Decision.PageWander) }
        var wandered = 0
        seeds { if (BunnyBrain(it).decide(input(canPageWander = true)) == Decision.PageWander) wandered++ }
        assertTrue("explorations $wandered / 2000", wandered in 70..180)
    }

    // ------------------------------------------------------------------ Sur une icône

    @Test
    fun `sur une icone - redescend a 160 px au plus, dans l'ecran`() {
        val cur = icon(500f)
        // r < 0.30 → JumpDown, puis décalage (f * 2 - 1) * 160
        assertEquals(Decision.JumpDown(580f), BunnyBrain(scripted(0.1f, 0.75f)).decide(input(platform = cur)))
        assertEquals(Decision.JumpDown(340f), BunnyBrain(scripted(0.29f, 0f)).decide(input(platform = cur)))
        // près du bord : ramené dans [minX, maxX]
        assertEquals(Decision.JumpDown(MAX_X), BunnyBrain(scripted(0f, 0.99f)).decide(input(platform = icon(1000f), x = 1000f)))
        assertEquals(Decision.JumpDown(MIN_X), BunnyBrain(scripted(0f, 0f)).decide(input(platform = icon(60f), x = 60f)))
        // densité 2 : 320 px
        assertEquals(Decision.JumpDown(660f), BunnyBrain(scripted(0f, 0.75f)).decide(input(platform = cur, density = 2f)))

        for (x in listOf(40f, 300f, 500f, 1040f)) seeds(300) {
            val d = BunnyBrain(it).decide(input(platform = icon(x), x = x))
            if (d is Decision.JumpDown) {
                assertTrue(d.targetX in MIN_X..MAX_X)
                assertTrue(abs(d.targetX - x) <= 160f + EPS)
            }
        }
    }

    @Test
    fun `sur une icone - saute sur une autre si en forme`() {
        val cur = icon(500f)
        val other = icon(700f)
        val s = input(platform = cur, platforms = listOf(cur, other), energy = 50f)
        assertEquals(Decision.JumpToPlatform(1, other), BunnyBrain(Scripted(listOf(0.45f), listOf(0))).decide(s))
        // trop fatigué (énergie ≤ 20) : il se retourne à la place
        assertEquals(Decision.TurnAround(3.5f), BunnyBrain(scripted(0.45f, 0.5f)).decide(s.copy(energy = 20f)))
        // aucune icône à portée : idem
        assertEquals(Decision.TurnAround(3.5f), BunnyBrain(scripted(0.45f, 0.5f)).decide(s.copy(platforms = listOf(cur))))
    }

    @Test
    fun `sur une icone - se retourne ou reste perche`() {
        val s = input(platform = icon(500f))
        assertEquals(Decision.TurnAround(2f), BunnyBrain(scripted(0.6f, 0f)).decide(s))
        assertEquals(Decision.TurnAround(3.5f), BunnyBrain(scripted(0.74f, 0.5f)).decide(s))
        assertEquals(Decision.Idle(3f), BunnyBrain(scripted(0.75f, 0f)).decide(s))
        assertEquals(Decision.Idle(5.5f), BunnyBrain(scripted(0.99f, 0.5f)).decide(s))
    }

    @Test
    fun `sur une icone - seulement JumpDown, JumpToPlatform, TurnAround ou Idle`() {
        val cur = icon(500f)
        val s = input(platform = cur, platforms = listOf(cur, icon(300f), icon(750f)), canPageWander = true)
        val seen = mutableSetOf<String>()
        seeds { r ->
            val d = BunnyBrain(r).decide(s)
            seen += d::class.simpleName!!
            when (d) {
                is Decision.JumpDown, is Decision.TurnAround, is Decision.Idle -> {}
                is Decision.JumpToPlatform -> assertFalse(d.target == cur)
                else -> throw AssertionError("inattendu sur une icône : $d")
            }
        }
        assertEquals(setOf("JumpDown", "JumpToPlatform", "TurnAround", "Idle"), seen)
    }

    @Test
    fun `sur une icone d'une autre page - pas de saut vers les icones d'ici`() {
        // Régression : la branche "sur une icône" ignorait `elsewhere`.
        val cur = icon(500f)
        val s = input(platform = cur, platforms = listOf(cur, icon(700f)), elsewhere = true)
        assertEquals(Decision.TurnAround(3.5f), BunnyBrain(scripted(0.45f, 0.5f)).decide(s))
        seeds { assertFalse(BunnyBrain(it).decide(s) is Decision.JumpToPlatform) }
    }

    // ------------------------------------------------------------------ Au sol

    @Test
    fun `au sol - saute sur une icone selon le caractere`() {
        val other = icon(700f)
        val s = input(platforms = listOf(other), energy = 50f)
        // [tirage exploration, r] puis nextInt pour l'icône
        assertEquals(Decision.JumpToPlatform(0, other), BunnyBrain(Scripted(listOf(0.9f, 0.34f), listOf(0))).decide(s))
        // r = 0.5 : au-delà de 0.35 pour le joueur → marche ; sous 0.6 pour le curieux → saut
        assertEquals(Decision.Walk(500f), BunnyBrain(scripted(0.9f, 0.5f, 0.5f)).decide(s))
        assertEquals(Decision.JumpToPlatform(0, other),
            BunnyBrain(Scripted(listOf(0.9f, 0.5f), listOf(0))).decide(s.copy(personality = Personality.CURIOUS)))
        // énergie ≤ 25 : pas de saut, la même valeur de r donne une marche
        assertEquals(Decision.Walk(500f), BunnyBrain(scripted(0.9f, 0.1f, 0.5f)).decide(s.copy(energy = 25f)))
    }

    @Test
    fun `le curieux saute plus souvent sur les icones`() {
        val s = input(platforms = listOf(icon(300f), icon(700f)))
        fun jumps(p: Personality): Int {
            var n = 0
            seeds { if (BunnyBrain(it).decide(s.copy(personality = p)) is Decision.JumpToPlatform) n++ }
            return n
        }
        val curious = jumps(Personality.CURIOUS)
        val playful = jumps(Personality.PLAYFUL)
        assertTrue("curieux $curious, joueur $playful", curious > playful * 1.4)
        assertTrue("curieux $curious", curious in 1100..1300)   // ≈ 0.6 * 2000
        assertTrue("joueur $playful", playful in 600..800)      // ≈ 0.35 * 2000
    }

    @Test
    fun `marche - cible dans l'ecran et a portee`() {
        assertEquals(500f + 0.5f * W * 0.45f,
            (BunnyBrain(scripted(0.9f, 0.2f, 0.75f)).decide(input()) as Decision.Walk).targetX, EPS)
        assertEquals(MIN_X, (BunnyBrain(scripted(0.9f, 0.2f, 0f)).decide(input(x = 100f)) as Decision.Walk).targetX, EPS)
        assertEquals(MAX_X, (BunnyBrain(scripted(0.9f, 0.2f, 0.999f)).decide(input(x = 1000f)) as Decision.Walk).targetX, EPS)

        for (x in listOf(MIN_X, 200f, 540f, 900f, MAX_X)) seeds(500) {
            val d = BunnyBrain(it).decide(input(x = x))
            if (d is Decision.Walk) {
                assertTrue(d.targetX in MIN_X..MAX_X)
                assertTrue(abs(d.targetX - x) <= W * 0.45f + EPS)
            }
        }
    }

    @Test
    fun `petit saut - seulement si energie au-dessus de 25`() {
        assertEquals(Decision.Hop(-650f, 90f), BunnyBrain(scripted(0.9f, 0.6f)).decide(input(energy = 26f)))
        assertEquals(Decision.Hop(-1300f, -180f), BunnyBrain(scripted(0.9f, 0.6f)).decide(input(facing = -1f, density = 2f)))
        assertEquals(Decision.TurnAround(2.5f), BunnyBrain(scripted(0.9f, 0.6f, 0.5f)).decide(input(energy = 25f)))
        seeds(500) { assertFalse(BunnyBrain(it).decide(input(energy = 25f)) is Decision.Hop) }
    }

    @Test
    fun `au sol - demi-tour puis repos`() {
        assertEquals(Decision.TurnAround(1.5f), BunnyBrain(scripted(0.9f, 0.68f, 0f)).decide(input()))
        assertEquals(Decision.TurnAround(3f), BunnyBrain(scripted(0.9f, 0.79f, 0.75f)).decide(input()))
        assertEquals(Decision.Idle(3f), BunnyBrain(scripted(0.9f, 0.8f, 0f)).decide(input()))
        assertEquals(Decision.Idle(7.375f), BunnyBrain(scripted(0.9f, 0.99f, 0.875f)).decide(input()))
    }

    @Test
    fun `au sol - toutes les activites tranquilles sont possibles`() {
        val seen = mutableSetOf<String>()
        seeds { seen += BunnyBrain(it).decide(input(platforms = listOf(icon(700f))))::class.simpleName!! }
        assertEquals(setOf("Walk", "Hop", "TurnAround", "Idle", "JumpToPlatform"), seen)
    }

    @Test
    fun `durees de repos positives et bornees`() {
        val cases = listOf(input(), input(platform = icon(500f)), input(energy = 20f), input(elsewhere = true))
        for (s in cases) seeds(1000) {
            when (val d = BunnyBrain(it).decide(s)) {
                is Decision.Idle -> assertTrue("$d", d.seconds >= 3f && d.seconds < 8f)
                is Decision.TurnAround -> assertTrue("$d", d.idleSeconds >= 1.5f && d.idleSeconds < 5f)
                else -> {}
            }
        }
    }

    // ------------------------------------------------------------------ pickPlatform

    @Test
    fun `pickPlatform - null sans candidat, sans tirage`() {
        val brain = BunnyBrain(Scripted())
        assertNull(brain.pickPlatform(input()))
        assertNull(brain.pickPlatform(input(platforms = listOf(icon(500f), icon(2000f)))))
    }

    @Test
    fun `pickPlatform - distance minimale de 30 dp`() {
        assertNull(BunnyBrain(Zero).pickPlatform(input(platforms = listOf(icon(530f), icon(470f)))))
        assertEquals(Decision.JumpToPlatform(0, icon(531f)), BunnyBrain(Zero).pickPlatform(input(platforms = listOf(icon(531f)))))
        // densité 2 : 60 px
        assertNull(BunnyBrain(Zero).pickPlatform(input(density = 2f, platforms = listOf(icon(560f)))))
        assertEquals(Decision.JumpToPlatform(0, icon(561f)),
            BunnyBrain(Zero).pickPlatform(input(density = 2f, platforms = listOf(icon(561f)))))
    }

    @Test
    fun `pickPlatform - portee de moins d'un demi-ecran`() {
        // x = 500, demi-écran = 540 : centre à 1039 OK, 1040 trop loin (et 1040 reste dans [minX, maxX])
        assertEquals(Decision.JumpToPlatform(0, icon(1039f)), BunnyBrain(Zero).pickPlatform(input(platforms = listOf(icon(1039f)))))
        assertNull(BunnyBrain(Zero).pickPlatform(input(platforms = listOf(icon(1040f)))))
    }

    @Test
    fun `pickPlatform - centre dans les limites de marche`() {
        assertNull(BunnyBrain(Zero).pickPlatform(input(x = 300f, platforms = listOf(icon(33f)))))
        assertEquals(Decision.JumpToPlatform(0, icon(MIN_X)),
            BunnyBrain(Zero).pickPlatform(input(x = 300f, platforms = listOf(icon(MIN_X)))))
        assertNull(BunnyBrain(Zero).pickPlatform(input(x = 900f, platforms = listOf(icon(1047f)))))
    }

    @Test
    fun `pickPlatform - jamais l'icone actuelle`() {
        val cur = icon(700f)
        // x = 500 : l'icône actuelle serait à portée si ce n'était pas la sienne
        assertNull(BunnyBrain(Zero).pickPlatform(input(platform = cur, platforms = listOf(cur))))
        seeds(300) {
            val p = BunnyBrain(it).pickPlatform(input(platform = cur, platforms = listOf(icon(300f), cur, icon(800f))))
            assertTrue(p != null && p.target != cur)
        }
    }

    @Test
    fun `pickPlatform - le k-ieme candidat avec son index d'origine`() {
        val list = listOf(icon(2000f), icon(300f), icon(505f), icon(800f))
        val s = input(platforms = list)
        assertEquals(Decision.JumpToPlatform(1, list[1]), BunnyBrain(Scripted(ints = listOf(0))).pickPlatform(s))
        assertEquals(Decision.JumpToPlatform(3, list[3]), BunnyBrain(Scripted(ints = listOf(1))).pickPlatform(s))
        // tirage uniforme entre les 2 candidats
        val counts = IntArray(list.size)
        seeds { counts[BunnyBrain(it).pickPlatform(s)!!.index]++ }
        assertEquals(0, counts[0] + counts[2])
        assertTrue(counts.contentToString(), counts[1] in 850..1150 && counts[3] in 850..1150)
    }

    // ------------------------------------------------------------------ wantsChest

    @Test
    fun `coffre - pause de 2 minutes`() {
        val cd = Tuning.SELF_SERVE_COOLDOWN_MS
        assertEquals(120_000L, cd)
        assertFalse(BunnyBrain(Scripted()).wantsChest(cd - 1, 0L, false, true, 10f, 10f, 80f))
        assertTrue(BunnyBrain(scripted(0f)).wantsChest(cd, 0L, false, true, 10f, 10f, 80f))
        assertTrue(BunnyBrain(scripted(0f)).wantsChest(500_000L + cd, 500_000L, false, true, 10f, 10f, 80f))
    }

    @Test
    fun `coffre - rien si objet deja sorti ou coffre invisible, sans tirage`() {
        assertFalse(BunnyBrain(Scripted()).wantsChest(1_000_000L, 0L, true, true, 10f, 10f, 80f))
        assertFalse(BunnyBrain(Scripted()).wantsChest(1_000_000L, 0L, false, false, 10f, 10f, 80f))
    }

    @Test
    fun `coffre - faim ou ennui, une fois sur deux`() {
        fun w(rnd: Random, satiety: Float, happiness: Float, energy: Float) =
            BunnyBrain(rnd).wantsChest(1_000_000L, 0L, false, true, satiety, happiness, energy)
        // faim
        assertTrue(w(scripted(0.49f), 44.9f, 90f, 10f))
        assertFalse(w(scripted(0.5f), 44.9f, 90f, 10f))
        // ennui : bonheur < 55 et énergie > 35
        assertTrue(w(scripted(0f), 90f, 54.9f, 35.1f))
        // ni faim ni ennui : aucun tirage
        assertFalse(w(Scripted(), 45f, 90f, 80f))
        assertFalse(w(Scripted(), 90f, 55f, 80f))
        assertFalse(w(Scripted(), 90f, 10f, 35f))

        var yes = 0
        seeds { if (w(it, 20f, 90f, 50f)) yes++ }
        assertTrue("coffre $yes / 2000", yes in 850..1150)
        seeds(500) { assertFalse(w(it, 60f, 70f, 90f)) }
    }

    // ------------------------------------------------------------------ Attention

    @Test
    fun `seuil d'attention selon les betises et le calin`() {
        for (p in Personality.values()) {
            val cuddly = p == Personality.CUDDLY
            assertEquals("$p", if (cuddly) 432_000L else 720_000L, BunnyBrain.attentionThresholdMs(0, p))
            assertEquals("$p", if (cuddly) 216_000L else 360_000L, BunnyBrain.attentionThresholdMs(1, p))
            assertEquals("$p", if (cuddly) 108_000L else 180_000L, BunnyBrain.attentionThresholdMs(2, p))
            // au-delà de 2 : traité comme le niveau maximal
            assertEquals("$p", BunnyBrain.attentionThresholdMs(2, p), BunnyBrain.attentionThresholdMs(3, p))
        }
        // plus de bêtises = seuil plus court ; le câlin réclame plus tôt
        assertTrue(BunnyBrain.attentionThresholdMs(1, Personality.PLAYFUL) < BunnyBrain.attentionThresholdMs(0, Personality.PLAYFUL))
        assertEquals(432_000L, BunnyBrain.attentionThresholdMs(0, Personality.CUDDLY))
        assertEquals(108_000L, BunnyBrain.attentionThresholdMs(2, Personality.CUDDLY))
    }

    @Test
    fun `canSeekAttention - bornes du seuil et de la pause`() {
        val t = 360_000L
        assertTrue(BunnyBrain.canSeekAttention(t, 0L, 0L, t))
        assertFalse(BunnyBrain.canSeekAttention(t - 1, 0L, 0L, t))
        assertTrue(BunnyBrain.canSeekAttention(1_000_000L, 0L, 1_000_000L, t))
        assertFalse(BunnyBrain.canSeekAttention(1_000_000L, 0L, 1_000_001L, t))
        // interaction récente : remet le compteur à zéro
        assertFalse(BunnyBrain.canSeekAttention(1_000_000L, 900_000L, 0L, t))
    }

    @Test
    fun `pause entre deux betises`() {
        assertEquals(100_000L, BunnyBrain.attentionCooldownMs(0))
        assertEquals(100_000L, BunnyBrain.attentionCooldownMs(1))
        assertEquals(50_000L, BunnyBrain.attentionCooldownMs(2))
        // la pause reste plus courte que le seuil le plus court
        assertTrue(BunnyBrain.attentionCooldownMs(2) < BunnyBrain.attentionThresholdMs(2, Personality.CUDDLY))
    }

    @Test
    fun `rollAttention - une chance sur deux`() {
        assertTrue(BunnyBrain(scripted(0f)).rollAttention())
        assertTrue(BunnyBrain(scripted(0.5f)).rollAttention())
        assertFalse(BunnyBrain(scripted(0.51f)).rollAttention())
        var yes = 0
        seeds { if (BunnyBrain(it).rollAttention()) yes++ }
        assertTrue("attention $yes / 2000", yes in 850..1150)
    }

    // ------------------------------------------------------------------ Retour de page

    @Test
    fun `pageReturn - defilement recent puis priorites`() {
        val now = 100_000L
        val quiet = Tuning.PAGE_RETURN_SCROLL_QUIET_MS
        fun r(lastScroll: Long = 0L, urgent: Boolean = false, stuff: Boolean = false, at: Long = 0L,
              canCome: Boolean = true, sleeping: Boolean = false) =
            BunnyBrain.pageReturn(now, lastScroll, urgent, stuff, at, canCome, sleeping)
        assertEquals(500L, quiet)
        assertEquals(BunnyBrain.PageReturn.SCROLLING, r(lastScroll = now - quiet + 1))
        assertEquals(BunnyBrain.PageReturn.SCHEDULE, r(lastScroll = now - quiet))
        // le défilement passe même avant l'urgence
        assertEquals(BunnyBrain.PageReturn.SCROLLING, r(lastScroll = now, urgent = true, stuff = true))
        // l'urgence passe avant tout le reste, même endormi
        assertEquals(BunnyBrain.PageReturn.RUSH, r(urgent = true, at = now + 10_000, canCome = false, sleeping = true))
        // échéance pile : il revient
        assertEquals(BunnyBrain.PageReturn.COME_BACK, r(at = now))
        assertEquals(BunnyBrain.PageReturn.WAIT, r(at = now + 1))
        // jouet / nourriture : retour immédiat, même si programmé plus tard…
        assertEquals(BunnyBrain.PageReturn.COME_BACK, r(stuff = true, at = now + 60_000))
        // … mais pas s'il dort ou est occupé
        assertEquals(BunnyBrain.PageReturn.WAIT, r(stuff = true, sleeping = true))
        assertEquals(BunnyBrain.PageReturn.WAIT, r(stuff = true, canCome = false))
        // pas encore programmé : on programme même s'il dort
        assertEquals(BunnyBrain.PageReturn.SCHEDULE, r(sleeping = true, canCome = false))
    }

    @Test
    fun `delai de retour et duree d'exploration dans leurs bornes`() {
        val retMin = Tuning.PAGE_RETURN_MIN_MS
        val retMax = retMin + Tuning.PAGE_RETURN_RAND_MS
        val wMin = Tuning.PAGE_WANDER_MIN_MS
        val wMax = wMin + Tuning.PAGE_WANDER_RAND_MS
        assertEquals(retMin, BunnyBrain(Scripted(ints = listOf(0))).pageReturnDelayMs())
        assertEquals(retMax - 1, BunnyBrain(Scripted(ints = listOf(Tuning.PAGE_RETURN_RAND_MS - 1))).pageReturnDelayMs())
        assertEquals(wMin, BunnyBrain(Scripted(ints = listOf(0))).pageWanderDurationMs())
        assertEquals(wMax - 1, BunnyBrain(Scripted(ints = listOf(Tuning.PAGE_WANDER_RAND_MS - 1))).pageWanderDurationMs())
        seeds {
            val b = BunnyBrain(it)
            assertTrue(b.pageReturnDelayMs() in retMin until retMax)
            assertTrue(b.pageWanderDurationMs() in wMin until wMax)
        }
        assertEquals(2_500, retMin)
        assertEquals(6_000, retMax)
        assertEquals(15_000, wMin)
        assertEquals(30_000, wMax)
    }

    // ------------------------------------------------------------------ Ensembles d'Act

    @Test
    fun `ensembles d'Act - EnumSet non vides`() {
        val sets = listOf(Act.MOVING, Act.SLOW, Act.NO_PAGE_FOLLOW, Act.CAN_COME_BACK, Act.CAN_COME_BACK_OR_PAGE_OUT,
            Act.RUSH_BACK, Act.BUSY_FOR_TRAVEL, Act.HIDING, Act.FREE)
        for (s in sets) {
            assertTrue(s is EnumSet<*>)
            assertTrue(s.isNotEmpty())
        }
    }

    @Test
    fun `ensembles d'Act - mouvement et repos disjoints`() {
        assertTrue(Act.MOVING.intersect(Act.SLOW).isEmpty())
        // chaque Act a exactement une cadence d'animation : rapide, lente ou intermédiaire
        for (a in Act.values()) assertFalse("$a", a in Act.MOVING && a in Act.SLOW)
        assertTrue(Act.CAN_COME_BACK.intersect(Act.SLOW).isEmpty())
    }

    @Test
    fun `ensembles d'Act - retour sur la page coherent`() {
        assertEquals(Act.CAN_COME_BACK + Act.PAGE_OUT, Act.CAN_COME_BACK_OR_PAGE_OUT)
        assertFalse(Act.PAGE_OUT in Act.CAN_COME_BACK)
        // accourir et revenir tranquillement s'excluent
        assertTrue(Act.CAN_COME_BACK.intersect(Act.RUSH_BACK).isEmpty())
        // tenu ou caché : ni retour ni accourir
        assertTrue(Act.NO_PAGE_FOLLOW.intersect(Act.CAN_COME_BACK_OR_PAGE_OUT).isEmpty())
        assertTrue(Act.NO_PAGE_FOLLOW.intersect(Act.RUSH_BACK).isEmpty())
        // tranquille = pas occupé pour un voyage
        assertTrue(Act.CAN_COME_BACK.intersect(Act.BUSY_FOR_TRAVEL).isEmpty())
    }

    @Test
    fun `ensembles d'Act - cachettes et liberte`() {
        assertTrue(Act.NO_PAGE_FOLLOW.containsAll(Act.HIDING))
        assertEquals(setOf(Act.DRAGGED), Act.NO_PAGE_FOLLOW - Act.HIDING)
        assertTrue(Act.CAN_COME_BACK.containsAll(Act.FREE))
        assertTrue(Act.FREE.intersect(Act.HIDING).isEmpty())
        assertTrue(Act.FREE.intersect(Act.BUSY_FOR_TRAVEL).isEmpty())
        assertTrue(Act.FREE.intersect(Act.SLOW).isEmpty())
        // les déplacements vers un but sont animés en mouvement
        for (a in listOf(Act.WALK, Act.AIR, Act.DRAGGED, Act.GOTO_FOOD, Act.GOTO_CHEST, Act.PAGE_OUT,
            Act.TRAVEL_IN, Act.TRAVEL_OUT, Act.HIDE_RUN)) assertTrue("$a", a in Act.MOVING)
        assertTrue(Act.SLEEP in Act.BUSY_FOR_TRAVEL && Act.SLEEP in Act.SLOW)
    }
}
