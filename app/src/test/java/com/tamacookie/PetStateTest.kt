package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetStateTest {

    private val eps = 0.001f

    @Test
    fun `tick sur zero heure ne change rien`() {
        val pet = newPet()
        val before = pet.satiety
        assertEquals(0, pet.tick(0f))
        assertEquals(before, pet.satiety, eps)
    }

    @Test
    fun `la faim augmente avec le temps`() {
        val pet = newPet()
        pet.tick(2f)
        assertEquals(80f - 14f, pet.satiety, eps)
    }

    @Test
    fun `un gourmand a faim plus vite`() {
        val normal = newPet().apply { personality = Personality.CUDDLY }
        val glutton = newPet().apply { personality = Personality.GLUTTON }
        normal.tick(2f); glutton.tick(2f)
        assertTrue(glutton.satiety < normal.satiety)
    }

    @Test
    fun `en cage le bonheur baisse trois fois plus vite`() {
        val free = newPet().apply { personality = Personality.LAZY }
        val caged = newPet().apply { personality = Personality.LAZY; caged = true }
        free.tick(1f); caged.tick(1f)
        val lossFree = 80f - free.happiness
        val lossCaged = 80f - caged.happiness
        assertEquals(lossFree * 3f, lossCaged, eps)
    }

    @Test
    fun `les jauges restent entre 0 et 100`() {
        val pet = newPet()
        pet.tick(48f)
        for (v in listOf(pet.satiety, pet.happiness, pet.energy, pet.cleanliness, pet.health)) {
            assertTrue("valeur $v", v in 0f..100f)
        }
        pet.heal(); pet.heal(); pet.heal()
        assertTrue(pet.health <= 100f)
    }

    @Test
    fun `le sommeil recharge l'energie et le reveille a 100`() {
        val pet = newPet().apply { energy = 20f; sleeping = true }
        pet.tick(1f)
        assertEquals(45f, pet.energy, eps)
        assertTrue(pet.sleeping)
        pet.tick(4f)
        assertEquals(100f, pet.energy, eps)
        assertFalse(pet.sleeping)
    }

    @Test
    fun `environ une crotte toutes les trois heures quand il a mange`() {
        val pet = newPet().apply { satiety = 100f }
        val poops = pet.tick(3.01f)
        assertEquals(1, poops)
        assertEquals(1, pet.poops)
    }

    @Test
    fun `pas de crotte s'il a faim ou s'il dort`() {
        val hungry = newPet().apply { satiety = 10f }
        assertEquals(0, hungry.tick(6f))
        val sleeping = newPet().apply { sleeping = true; energy = 0f }
        assertEquals(0, sleeping.tick(3.5f))
    }

    @Test
    fun `au maximum cinq crottes`() {
        val pet = newPet()
        repeat(20) { pet.satiety = 100f; pet.tick(3.1f) }
        assertEquals(5, pet.poops)
    }

    @Test
    fun `manger rassasie`() {
        val pet = newPet().apply { satiety = 30f }
        assertTrue(pet.feed(Food.CARROT))
        assertEquals(30f + Food.CARROT.satiety, pet.satiety, eps)
    }

    @Test
    fun `repu il refuse sauf une fraise`() {
        val pet = newPet().apply { satiety = 98f }
        assertFalse(pet.feed(Food.CARROT))
        assertTrue(pet.feed(Food.STRAWBERRY))
    }

    @Test
    fun `trop fatigue pour jouer`() {
        val pet = newPet().apply { energy = 10f }
        assertFalse(pet.startPlay())
        pet.energy = 50f
        assertTrue(pet.startPlay())
    }

    @Test
    fun `le jouet prefere rapporte deux fois plus`() {
        val a = newPet().apply { personality = Personality.LAZY; favoriteToy = Toy.BALL; happiness = 50f }
        val b = newPet().apply { personality = Personality.LAZY; favoriteToy = Toy.PLUSH; happiness = 50f }
        a.playHit(Toy.BALL, 2f); b.playHit(Toy.BALL, 2f)
        assertEquals(54f, a.happiness, eps)
        assertEquals(52f, b.happiness, eps)
    }

    @Test
    fun `le bain rend tout propre`() {
        val pet = newPet().apply { cleanliness = 5f }
        pet.clean()
        assertEquals(100f, pet.cleanliness, eps)
    }

    @Test
    fun `ramasser une crotte`() {
        val pet = newPet().apply { poops = 2; cleanliness = 50f }
        pet.removePoop()
        assertEquals(1, pet.poops)
        assertEquals(58f, pet.cleanliness, eps)
    }

    @Test
    fun `humeur`() {
        assertEquals(PetState.Mood.SLEEPY, newPet().apply { sleeping = true }.mood)
        assertEquals(PetState.Mood.SICK, newPet().apply { health = 10f }.mood)
        assertEquals(PetState.Mood.HUNGRY, newPet().apply { satiety = 5f }.mood)
        assertEquals(PetState.Mood.DIRTY, newPet().apply { poops = 3 }.mood)
        assertEquals(PetState.Mood.SAD, newPet().apply { happiness = 10f }.mood)
        assertEquals(PetState.Mood.HAPPY, newPet().apply { happiness = 90f; satiety = 90f }.mood)
        assertEquals(PetState.Mood.OK, newPet().apply { happiness = 60f }.mood)
    }

    @Test
    fun `sauvegarde puis rechargement`() {
        val prefs = FakePrefs()
        newPet(prefs).apply {
            name = "Noisette"; satiety = 42f; poops = 2; caged = true
            look = Look(1, 2, 3, 1, 4, 2); personality = Personality.CURIOUS; favoriteToy = Toy.FRISBEE
            adopted = true
            save(); saveLook()
        }
        val loaded = newPet(prefs).load()
        assertEquals("Noisette", loaded.name)
        assertEquals(42f, loaded.satiety, 0.1f)
        assertEquals(2, loaded.poops)
        assertTrue(loaded.caged)
        assertTrue(loaded.adopted)
        assertEquals(Look(1, 2, 3, 1, 4, 2), loaded.look)
        assertEquals(Personality.CURIOUS, loaded.personality)
        assertEquals(Toy.FRISBEE, loaded.favoriteToy)
    }

    @Test
    fun `nouveau lapin conserve l'activation`() {
        val prefs = FakePrefs()
        prefs.edit().putBoolean("enabled", true).putFloat("satiety", 3f).apply()
        val pet = newPet(prefs).apply { satiety = 3f }
        pet.reset()
        assertEquals(true, prefs.map["enabled"])
        assertEquals(80f, pet.satiety, eps)
    }
}
