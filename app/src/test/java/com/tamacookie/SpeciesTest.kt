package com.tamacookie

import com.tamacookie.species.BehaviorMods
import com.tamacookie.species.Species
import com.tamacookie.species.rabbit.Rabbit
import com.tamacookie.species.rabbit.RabbitLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpeciesTest {

    @Test fun registre_lapinParDefaut_idsUniques() {
        assertSame(Rabbit, Species.DEFAULT)
        assertSame(Rabbit, Species.byId("rabbit"))
        assertNull(Species.byId("dragon"))
        assertNull(Species.byId(null))
        assertSame(Species.DEFAULT, Species.of("dragon"))
        assertEquals(Species.ALL.size, Species.ALL.map { it.id }.toSet().size)
    }

    /** L'identifiant du lapin est sauvegardé dans toutes les sauvegardes : il ne change jamais. */
    @Test fun idDuLapinFige() {
        assertEquals("rabbit", Rabbit.ID)
        assertEquals(Rabbit.ID, Identity(PetId("x")).species)
    }

    @Test fun exhaustivite_chaqueEspeceEstComplete() {
        for (s in Species.ALL) {
            val sp = s.spec
            assertTrue(s.id, s.label.isNotBlank() && s.emoji.isNotBlank())
            for (v in listOf(sp.windowWDp, sp.windowHDp, sp.walkSlowDp, sp.walkNormalDp, sp.walkHappyDp,
                sp.travelOutDp, sp.travelInDp, sp.pageOutDp, sp.hideRunDp, sp.headTopDp, sp.headTopSleepingDp)) {
                assertTrue(s.id, v > 0f)
            }
            assertTrue(s.id, sp.walkSlowDp < sp.walkNormalDp && sp.walkNormalDp < sp.walkHappyDp)
            for (p in Personality.entries) s.behavior(p)          // pas d'exception : chaque caractère est couvert
            for (m in Mood.entries) assertTrue("${s.id} $m", s.lines.chatter(m).isNotEmpty())
            assertTrue(s.lines.greeting("A", hungry = false, hour = 12).isNotBlank())
            for (a in listOf(s.lines.ATE, s.lines.PETTED, s.lines.DRAGGED, s.lines.FOLLOWING, s.lines.WOKE_UP,
                s.lines.POOPED, s.lines.GOING_TO_SLEEP, s.lines.NOT_TIRED)) assertTrue(s.id, a.isNotEmpty())
        }
    }

    /** Les valeurs du lapin sont celles d'avant l'étape 7 (littéraux de `Tuning`, du cerveau, des contrôleurs). */
    @Test fun lapin_memesValeursQuAvant() {
        val sp = Rabbit.spec
        assertEquals(130f, sp.windowWDp); assertEquals(140f, sp.windowHDp); assertEquals(6f, sp.feetPadDp)
        assertEquals(45f, sp.walkSlowDp); assertEquals(75f, sp.walkNormalDp); assertEquals(95f, sp.walkHappyDp)
        assertEquals(260f, sp.travelOutDp); assertEquals(200f, sp.travelInDp)
        assertEquals(150f, sp.pageOutDp); assertEquals(300f, sp.hideRunDp)
        assertEquals(112f, sp.headTopDp); assertEquals(70f, sp.headTopSleepingDp)

        val none = BehaviorMods()
        assertEquals(BehaviorMods(playSeconds = 50f), Rabbit.behavior(Personality.PLAYFUL))
        assertEquals(BehaviorMods(chestGreedChance = 0.6f), Rabbit.behavior(Personality.GLUTTON))
        assertEquals(BehaviorMods(walkSpeedFactor = 0.7f, sleepEnergyBelow = 30f, nightSleepEnergyBelow = 75f,
            napEnergyBelow = 60f), Rabbit.behavior(Personality.LAZY))
        assertEquals(BehaviorMods(pageWanderChance = 0.12f, platformChance = 0.6f), Rabbit.behavior(Personality.CURIOUS))
        assertEquals(BehaviorMods(attentionFactor = 0.6f, socialChance = 0.4f), Rabbit.behavior(Personality.CUDDLY))
        assertEquals(BehaviorMods(attentionFactor = 1.3f, socialChance = 0.1f, platformChance = 0.25f,
            hideChanceFactor = 2f, startleChance = 0.35f), Rabbit.behavior(Personality.SHY))
        assertEquals(BehaviorMods(attentionFactor = 0.5f, mischiefChanceFactor = 1.5f), Rabbit.behavior(Personality.PRANKSTER))
        assertEquals(BehaviorMods(pageWanderChance = 0.15f, platformChance = 0.7f, travelCooldownFactor = 0.5f),
            Rabbit.behavior(Personality.EXPLORER))
        // les nouveaux champs de 4b sont neutres par défaut (anciens caractères : comportement d'avant)
        assertEquals(1f, none.mischiefChanceFactor); assertEquals(1f, none.hideChanceFactor)
        assertEquals(1f, none.travelCooldownFactor); assertEquals(0f, none.startleChance)
        for (p in Personality.entries.take(5)) {
            val m = Rabbit.behavior(p)
            assertEquals("$p", 1f, m.mischiefChanceFactor); assertEquals("$p", 1f, m.hideChanceFactor)
            assertEquals("$p", 1f, m.travelCooldownFactor); assertEquals("$p", 0f, m.startleChance)
        }
        assertEquals(35f, none.playSeconds); assertEquals(0.06f, none.pageWanderChance)
        assertSame(RabbitLines, Rabbit.lines)
    }

    @Test fun animal_resoutSonEspece() {
        val p = testPet(BunnyPhysics(1f), Random(1))
        assertSame(Rabbit, p.species)
        val unknown = Pet(Identity(PetId("u"), species = "dragon"), Traits(), Look(), PetStats(),
            Body(BunnyPhysics(1f)), Mind(Random(1)))
        assertSame(Species.DEFAULT, unknown.species)
    }

    @Test fun lapin_accepteToutesLesStationsEtTousLesJouets() {
        for (k in com.tamacookie.feature.station.StationKind.entries) assertTrue(Rabbit.accepts(k))
        for (t in com.tamacookie.feature.toy.Toy.entries) assertTrue(Rabbit.accepts(t))
    }
}
