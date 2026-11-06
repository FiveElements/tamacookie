package com.tamacookie

import com.tamacookie.feature.station.StationKind
import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.Toy
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

    @Test
    fun `nouveau lapin conserve les reglages et efface les cles du lapin`() {
        val prefs = FakePrefs()
        prefs.edit()
            .putInt(Prefs.MISCHIEF, 2)
            .putFloat(Prefs.DOCK_FX, 0.3f).putFloat(Prefs.DOCK_FY, 0.7f)
            .putString(Prefs.stationAnchor(StationKind.CAGE), "home#p1")
            .putFloat(Prefs.stationFx(StationKind.CAGE), 0.4f).putFloat(Prefs.stationFy(StationKind.CAGE), 0.9f)
            .putFloat(Prefs.pageWidth(1080), 1234f)
            // clés de l'ancien lapin
            .putFloat("satiety", 3f).putInt("poops", 4).putBoolean("caged", true).putFloat("poopAcc", 0.9f)
            .putInt("visitedApps", 12).putLong("bornAt", 1L).putLong("lastUpdate", 1L)
            .apply()
        newPet(prefs).load().reset()

        // Réglages conservés
        assertEquals(2, prefs.map[Prefs.MISCHIEF])
        assertEquals(0.3f, prefs.map[Prefs.DOCK_FX])
        assertEquals(0.7f, prefs.map[Prefs.DOCK_FY])
        assertEquals("home#p1", prefs.map[Prefs.stationAnchor(StationKind.CAGE)])
        assertEquals(0.4f, prefs.map[Prefs.stationFx(StationKind.CAGE)])
        assertEquals(0.9f, prefs.map[Prefs.stationFy(StationKind.CAGE)])
        assertEquals(1234f, prefs.map[Prefs.pageWidth(1080)])
        // Clés du lapin remises à neuf
        assertEquals(80f, prefs.map["satiety"])
        assertEquals(0, prefs.map["poops"])
        assertEquals(false, prefs.map["caged"])
        assertEquals(0f, prefs.map["poopAcc"])
        assertEquals(0, prefs.map["visitedApps"])
        assertTrue((prefs.map["bornAt"] as Long) > 1L)
    }

    @Test
    fun `reset efface exactement les cles ecrites par le lapin`() {
        // Les clés supprimées par reset() = toutes celles que save()/saveLook() écrivent, et elles seules
        val prefs = FakePrefs()
        newPet(prefs).apply { save(); saveLook() }
        assertEquals(prefs.map.keys, PetState.PET_KEYS.toSet())
        prefs.edit().putInt(Prefs.MISCHIEF, 0).apply()
        // Un reset sur un lapin "sans état" ne laisse que les réglages + les clés du lapin réécrites
        newPet(prefs).reset()
        assertEquals(PetState.PET_KEYS.toSet() + Prefs.MISCHIEF, prefs.map.keys)
    }

    @Test
    fun `l'ancien lapin ne peut plus sauvegarder apres une adoption`() {
        val prefs = FakePrefs()
        val old = newPet(prefs).load()
        old.satiety = 5f
        old.save()
        // Nouvelle adoption (autre instance, comme AdoptActivity) pendant que l'ancien service tourne
        val adopted = newPet(prefs).load()
        adopted.reset()
        val newBorn = prefs.map["bornAt"]
        // L'ancien service sauvegarde en partant (onDestroy) : ignoré
        old.satiety = 1f
        old.save()
        assertEquals(newBorn, prefs.map["bornAt"])
        assertEquals(80f, prefs.map["satiety"])
        // Le nouveau lapin, lui, sauvegarde normalement
        val reloaded = newPet(prefs).load()
        reloaded.satiety = 50f
        reloaded.save()
        assertEquals(50f, prefs.map["satiety"])
    }
}
