package com.tamacookie.persist

import android.content.SharedPreferences
import com.tamacookie.Clock
import com.tamacookie.Identity
import com.tamacookie.Look
import com.tamacookie.Personality
import com.tamacookie.PetId
import com.tamacookie.PetStats
import com.tamacookie.Traits
import com.tamacookie.feature.toy.Toy

/**
 * Migrations du schéma des animaux, jouées dans l'ordre par [PetRepository.open] jusqu'à
 * [PetRepository.SCHEMA_VERSION]. Chacune finit par écrire sa version (`commit` synchrone) : une
 * migration interrompue est rejouée au lancement suivant, elle doit donc être idempotente.
 *
 * Ajouter une version : une entrée à la fin de [ALL] (`from` = version précédente), sans toucher aux anciennes.
 */
object PetMigrations {

    class Env(
        /** Fichier des animaux (schéma courant). */
        val pets: SharedPreferences,
        /** Fichier historique `"pet"` : réglages, et animal du schéma 0. */
        val settings: SharedPreferences,
        val clock: Clock,
        val newId: () -> String,
    )

    class Migration(val from: Int, val description: String, val run: (Env) -> Unit) {
        val to: Int get() = from + 1
    }

    val ALL: List<Migration> = listOf(
        Migration(0, "clés à plat du fichier « pet », apparence par index → clés par PetId, apparence par id", ::v0toV1),
    )

    fun version(pets: SharedPreferences): Int = pets.getInt(PetRepository.KEY_SCHEMA, 0)

    fun run(pets: SharedPreferences, settings: SharedPreferences, clock: Clock, newId: () -> String) {
        val env = Env(pets, settings, clock, newId)
        for (m in ALL) {
            if (version(pets) != m.from) continue
            m.run(env)
            check(version(pets) == m.to) { "la migration ${m.from} n'a pas écrit sa version" }
        }
        // Migration v0→v1 interrompue après l'écriture du schéma 1 : il reste des anciennes clés à effacer
        if (version(pets) >= 1) removeLegacyKeys(settings)
    }

    /** Clés de l'animal au schéma 0 (écrites par l'ancien `PetState.save()` / `saveLook()`, fichier « pet »). */
    val LEGACY_PET_KEYS = listOf(
        "name", "satiety", "happiness", "energy", "cleanliness", "health", "sleeping",
        "poops", "poopAcc", "bornAt", "lastUpdate", "visitedApps", "caged",
        "adopted", "look_fur", "look_ears", "look_eyes", "look_pattern", "look_acc", "look_accColor",
        "personality", "favoriteToy",
    )

    /** Lit l'animal du schéma 0 (mêmes défauts que l'ancien `PetState.load()`), null s'il n'y en a pas. */
    fun readLegacy(p: SharedPreferences, now: Long, id: PetId): PetRecord? {
        if (LEGACY_PET_KEYS.none(p::contains)) return null
        val d = PetStats()
        val bornAt = p.getLong("bornAt", now)
        return PetRecord(
            identity = Identity(id = id, name = p.getString("name", null) ?: Identity.DEFAULT_NAME, bornAt = bornAt),
            traits = Traits(
                personality = PetFields.enumOr(p.getString("personality", null), Personality.CUDDLY),
                favoriteToy = PetFields.enumOr(p.getString("favoriteToy", null), Toy.BALL),
            ),
            look = Look.fromLegacyIndexes(
                p.getInt("look_fur", 0), p.getInt("look_ears", 0), p.getInt("look_eyes", 0),
                p.getInt("look_pattern", 0), p.getInt("look_acc", 0), p.getInt("look_accColor", 0),
            ),
            stats = PetStats(
                satiety = p.getFloat("satiety", d.satiety), happiness = p.getFloat("happiness", d.happiness),
                energy = p.getFloat("energy", d.energy), cleanliness = p.getFloat("cleanliness", d.cleanliness),
                health = p.getFloat("health", d.health), sleeping = p.getBoolean("sleeping", false),
                poops = p.getInt("poops", 0), poopAccumulator = p.getFloat("poopAcc", 0f),
                visitedApps = p.getInt("visitedApps", 0), caged = p.getBoolean("caged", false),
            ),
            adopted = p.getBoolean("adopted", false),
            lastUpdate = p.getLong("lastUpdate", now),
        )
    }

    private fun v0toV1(env: Env) {
        val now = env.clock.wallMs()
        val e = env.pets.edit()
        val legacy = readLegacy(env.settings, now, PetId(env.newId()))
        if (legacy != null) {
            // lastUpdate conservé tel quel : le rattrapage hors-ligne se fera au prochain démarrage du service
            PetFields.write(e, legacy)
            e.putString(PetRepository.KEY_IDS, legacy.id.value)
            e.putString(PetRepository.KEY_RESIDENT, legacy.id.value)
        }
        e.putInt(PetRepository.KEY_SCHEMA, 1)
        // Synchrone : le nouveau schéma est sur disque avant d'effacer l'ancien
        check(e.commit()) { "écriture du schéma 1 refusée" }
        removeLegacyKeys(env.settings)
    }

    private fun removeLegacyKeys(settings: SharedPreferences) {
        if (LEGACY_PET_KEYS.none(settings::contains)) return
        val e = settings.edit()
        LEGACY_PET_KEYS.forEach { e.remove(it) }
        e.commit()
    }
}
