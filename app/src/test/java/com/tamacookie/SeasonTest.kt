package com.tamacookie

import com.tamacookie.species.Species
import com.tamacookie.species.rabbit.RabbitLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.random.Random

/** Thèmes saisonniers (évolution 4b) : dates limites, cache, rendu ajouté sans toucher au [Look], répliques. */
class SeasonTest {

    private fun on(m: Int, d: Int, y: Int = 2026) = Season.of(LocalDate.of(y, m, d))

    @Test fun datesLimites() {
        // printemps : 20 mars – 30 avril
        assertEquals(Season.NONE, on(3, 19)); assertEquals(Season.SPRING, on(3, 20))
        assertEquals(Season.SPRING, on(4, 30)); assertEquals(Season.NONE, on(5, 1))
        // Halloween : 24 octobre – 2 novembre
        assertEquals(Season.NONE, on(10, 23)); assertEquals(Season.HALLOWEEN, on(10, 24))
        assertEquals(Season.HALLOWEEN, on(10, 31)); assertEquals(Season.HALLOWEEN, on(11, 2))
        assertEquals(Season.NONE, on(11, 3))
        // Noël : 1er décembre – 6 janvier (à cheval sur deux années)
        assertEquals(Season.NONE, on(11, 30)); assertEquals(Season.CHRISTMAS, on(12, 1))
        assertEquals(Season.CHRISTMAS, on(12, 31)); assertEquals(Season.CHRISTMAS, on(1, 1, 2027))
        assertEquals(Season.CHRISTMAS, on(1, 6, 2027)); assertEquals(Season.NONE, on(1, 7, 2027))
        // reste de l'année, 29 février compris
        assertEquals(Season.NONE, on(2, 29, 2028)); assertEquals(Season.NONE, on(7, 14))
        assertEquals(Season.NONE, on(11, 6))
    }

    @Test fun chaqueJourDeLAnnee_uneSeuleSaison() {
        var d = LocalDate.of(2028, 1, 1)
        val count = HashMap<Season, Int>()
        while (d.year == 2028) { count.merge(Season.of(d), 1, Int::plus); d = d.plusDays(1) }
        assertEquals(6 + 31, count[Season.CHRISTMAS])
        assertEquals(10, count[Season.HALLOWEEN])
        assertEquals(12 + 30, count[Season.SPRING])
        assertEquals(366 - 37 - 10 - 42, count[Season.NONE])
    }

    @Test fun fuseauInjecte() {
        // 23 octobre 23 h 30 UTC = 24 octobre 1 h 30 à Paris (heure d'été)
        val ms = ZonedDateTime.of(2026, 10, 23, 23, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(Season.NONE, Season.at(ms, ZoneOffset.UTC))
        assertEquals(Season.HALLOWEEN, Season.at(ms, ZoneId.of("Europe/Paris")))
    }

    @Test fun particulesDeSaison() {
        assertEquals(PType.SNOW, Season.CHRISTMAS.particle)
        assertEquals(PType.PETAL, Season.SPRING.particle)
        assertNull(Season.HALLOWEEN.particle)
        assertNull(Season.NONE.particle)
    }

    @Test fun theme_reglageEtCache() {
        val clock = FakeClock(wall = ZonedDateTime.of(2026, 11, 30, 23, 59, 30, 0, ZoneOffset.UTC).toInstant().toEpochMilli())
        val theme = SeasonalTheme(clock) { ZoneOffset.UTC }
        assertEquals(Season.NONE, theme.current())
        clock.advance(40_000)                                    // 1er décembre 0 h 00 10, mais lu il y a < 1 min
        assertEquals(Season.NONE, theme.current())
        clock.advance(20_000)
        assertEquals(Season.CHRISTMAS, theme.current())
        theme.enabled = false                                    // réglage coupé : aucun thème
        assertEquals(Season.NONE, theme.current())
        theme.enabled = true
        assertEquals(Season.CHRISTMAS, theme.current())
        clock.wall -= 86_400_000L                                // l'horloge recule : recalcul immédiat
        assertEquals(Season.NONE, theme.current())
    }

    @Test fun rendu_ajoutSansToucherALApparence() {
        val pet = testPet()
        val plain = PetRenderState.of(pet)
        assertEquals(Season.NONE, plain.season)
        assertFalse(plain.seasonHat || plain.seasonPumpkin || plain.seasonFlowers)

        val xmas = PetRenderState.of(pet, season = Season.CHRISTMAS)
        assertTrue(xmas.seasonHat)                               // aucun accessoire : bonnet
        assertSame(pet.look, xmas.look)                          // l'apparence reste celle sauvegardée
        assertEquals(Accessory.NONE, pet.look.accessory)
        val crowned = xmas.copy(look = Look(accessory = Accessory.CROWN))
        assertFalse(crowned.seasonHat)                           // il garde son accessoire

        val hw = PetRenderState(season = Season.HALLOWEEN)
        assertTrue(hw.seasonPumpkin); assertFalse(hw.seasonHat)
        assertFalse(hw.copy(pose = Pose.AIR).seasonPumpkin)      // en vol / tenu : pas de décor au sol
        assertFalse(hw.copy(pose = Pose.DRAGGED).seasonPumpkin)
        assertTrue(hw.copy(pose = Pose.SLEEP).seasonPumpkin)
        assertTrue(PetRenderState(season = Season.SPRING).seasonFlowers)
        assertFalse(PetRenderState(season = Season.SPRING).seasonPumpkin)
    }

    @Test fun particules_seulementEnSaison_etTiragesInchangesHorsSaison() {
        fun run(season: Season, seed: Int): List<PType> {
            val a = AmbientAnimator(Random(seed))
            val types = ArrayList<PType>()
            val s = PetRenderState(season = season)
            repeat(600) { a.update(0.05f, s, 300f, 300f, 1f); a.particles.forEach { types += it.type } }
            return types
        }
        val none = run(Season.NONE, 3)
        assertFalse(PType.SNOW in none || PType.PETAL in none)
        assertTrue(PType.SNOW in run(Season.CHRISTMAS, 3))
        assertTrue(PType.PETAL in run(Season.SPRING, 3))
        assertFalse(PType.SNOW in run(Season.HALLOWEEN, 3))
        // hors saison, la suite des tirages (clignements, particules d'humeur) est celle d'avant 4b :
        // même graine, même état sans saison → mêmes particules, image après image
        val a = AmbientAnimator(Random(9)); val b = AmbientAnimator(Random(9))
        val sad = PetRenderState(expr = Expr.SAD, pose = Pose.SLEEP)
        repeat(400) {
            a.update(0.05f, sad, 300f, 300f, 1f); b.update(0.05f, sad.copy(season = Season.HALLOWEEN), 300f, 300f, 1f)
            assertEquals(a.particles.map { it.type to it.x }, b.particles.map { it.type to it.x })
            assertEquals(a.blinking, b.blinking)
        }
    }

    @Test fun repliquesDeSaison() {
        for (s in Species.ALL) for (season in Season.entries) {
            val lines = s.lines.seasonal(season)
            if (season == Season.NONE) assertEquals(0, lines.size) else assertTrue("${s.id} $season", lines.isNotEmpty())
        }
        assertTrue(RabbitLines.seasonal(Season.CHRISTMAS).any { "Noël" in it })
    }

    @Test fun bavardage_repliquesDeSaisonSeulementEnSaison() {
        fun chatter(season: Season): List<String> {
            val ctx = FakePetCtx()
            ctx.season = season
            val beh = PetBehavior(ctx, NoHooks())
            val p = ctx.first
            repeat(200) {
                p.setAct(Act.IDLE); p.mind.chatterIn = 0f
                ctx.world.asPet(p) { beh.update(p, 0.001f) }
            }
            return ctx.said
        }
        val xmas = RabbitLines.seasonal(Season.CHRISTMAS).toSet()
        val inSeason = chatter(Season.CHRISTMAS)
        val seasonal = inSeason.count { it in xmas }
        assertTrue("$seasonal / ${inSeason.size}", seasonal in 30..100)  // ≈ 30 % du bavardage
        assertTrue(chatter(Season.NONE).none { it in xmas })
    }

    /** Hooks neutres du comportement. */
    private class NoHooks : PetBehavior.Hooks {
        override fun chestVisible(p: Pet) = false
        override val lastSelfServe = 0L
        override fun goToChest(p: Pet, now: Long) {}
        override fun seekAttention(p: Pet) = false
        override fun startPageWander(p: Pet) {}
        override fun hour() = 14
    }
}
