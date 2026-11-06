package com.tamacookie.feature.station

import com.tamacookie.PetId

/**
 * "Widgets" flottants dans lesquels on dépose l'animal : baignoire (le laver), vétérinaire (voir ses
 * stats / soigner), cage (l'isoler) ; et le coffre (nourriture / jouets), qui n'accueille personne.
 *
 * L'enum est l'**identité** de la station : son nom sert aux clés de préférences (`st_<NOM>_…`) et
 * son ordre au placement par défaut — ne pas renommer ni réordonner. Tout le reste est porté par
 * son [behavior].
 */
enum class StationKind(val label: String, val wDp: Int, val hDp: Int, val behavior: StationBehavior) {
    BATH("Bain", 130, 84, BathStation),
    VET("Véto", 130, 130, VetStation),
    CAGE("Cage", 130, 136, CageStation),
    CHEST("Coffre", 110, 92, ChestStation),
    // Évolution 4a (à la fin : l'ordre fixe le placement par défaut, le nom les clés `st_<NOM>_*`)
    BED("Lit", 170, 90, BedStation),
    BOWL("Gamelle", 110, 70, BowlStation),
    WHEEL("Roue", 130, 140, WheelStation),
    GARDEN("Jardin", 150, 80, GardenStation),
}

/**
 * Qui occupe une station (pur, sans Android) : ses occupants ([PetId]) et sa capacité.
 * Une seule place partout pour l'instant ; le lit en aura deux (multi-animal, étape 7).
 */
class Occupancy(val kind: StationKind, val capacity: Int = kind.behavior.capacity) {
    private val ids = ArrayList<PetId>(capacity)

    val occupants: List<PetId> get() = ids
    val isEmpty: Boolean get() = ids.isEmpty()
    val isFull: Boolean get() = ids.size >= capacity

    /** Peut-on y déposer [id] ? (station qui accueille, place libre, pas déjà dedans) */
    fun canEnter(id: PetId): Boolean = kind.behavior.acceptsPet && !isFull && !has(id)

    /** Sans allocation (appelé à chaque image). */
    fun has(id: PetId): Boolean {
        for (i in ids.indices) if (ids[i].value == id.value) return true
        return false
    }

    fun enter(id: PetId): Boolean {
        if (!canEnter(id)) return false
        ids.add(id)
        return true
    }

    fun leave(id: PetId): Boolean {
        for (i in ids.indices) if (ids[i].value == id.value) { ids.removeAt(i); return true }
        return false
    }
}
