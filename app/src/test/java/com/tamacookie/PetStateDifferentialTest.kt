package com.tamacookie

import com.tamacookie.feature.toy.Toy
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** Transitoire : l'ancien PetState et PetRules donnent exactement les mêmes jauges (même suite d'événements). */
class PetStateDifferentialTest {
    @Test
    fun `PetRules reproduit PetState au bit pres`() {
        val rules = PetRulesTest()
        val accField = PetState::class.java.getDeclaredField("poopAccumulator").apply { isAccessible = true }
        for (p in Personality.entries) {
            val rnd = Random(p.ordinal + 7)
            val old = newPet().apply { personality = p; favoriteToy = Toy.FRISBEE }
            var t = 1_700_000_000_000L
            var lastPet = 0L
            var h = 0L
            repeat(400) {
                t += rnd.nextLong(0, 10_000)
                when (val ev = rules.randomEvent(rnd)) {
                    is PetEvent.Elapsed -> old.tick(minOf(ev.hours, 48f))
                    is PetEvent.Fed -> old.feed(ev.food)
                    PetEvent.Petted -> {
                        old.lastPetAt = System.currentTimeMillis() - (t - lastPet)
                        old.pet(); lastPet = t
                    }
                    PetEvent.PlayStarted -> old.startPlay()
                    is PetEvent.PlayHit -> old.playHit(ev.toy, ev.amount)
                    PetEvent.Bathed -> old.clean()
                    PetEvent.Healed -> old.heal()
                    PetEvent.PoopRemoved -> old.removePoop()
                    PetEvent.AppVisited -> old.visitApp()
                    PetEvent.FellAsleep -> old.sleeping = true
                    else -> error(ev.toString())
                }
                val acc = accField.getFloat(old)
                h = PetRulesTest.mix(h, old.satiety, old.happiness, old.energy, old.cleanliness, old.health, old.sleeping,
                    old.poops, acc, old.visitedApps)
            }
            val oldTrace = "$p $h"
            println("GOLDEN $oldTrace")
            assertEquals(oldTrace, rules.trace(p))
        }
    }
}
