package com.tamacookie

import com.tamacookie.feature.station.Occupancy
import com.tamacookie.feature.station.StationKind

/**
 * Géométrie de l'écran partagée par tous les animaux (pixels), recalculée par le service
 * à chaque changement de taille / d'orientation.
 */
class Arena {
    /** Densité (px par dp). */
    var d = 1f
    /** Taille de la fenêtre d'un lapin. */
    var bw = 0
    var bh = 0
    /** Marge sous les pieds dans la fenêtre du lapin. */
    var feetPad = 0f
    var screenW = 0f
    var groundY = 0f
    var topY = 0f
    var minX = 0f
    var maxX = 0f
}

/**
 * Le monde : les animaux et ce qu'ils partagent (objets posés, occupation des stations).
 * Un seul animal pour l'instant (le résident), mais rien ne suppose qu'il est unique.
 */
class World {
    val pets = LinkedHashMap<PetId, Pet>()

    /** Nourriture, crottes et jouets posés à l'écran (gérés par [ToyController]). */
    val props = ArrayList<Prop>()
    private var lastPropId = 0

    /** Identifiant d'un nouvel objet (clé de sa fenêtre : [WindowId.Prop]). */
    fun newPropId(): Int = ++lastPropId

    /** Qui occupe chaque station, dans l'ordre de [StationKind] (leur état affiché est dans `StationController`). */
    private val stations: Array<Occupancy> = Array(StationKind.entries.size) { Occupancy(StationKind.entries[it]) }

    fun add(pet: Pet) { pets[pet.id] = pet }

    fun station(k: StationKind): Occupancy = stations[k.ordinal]

    /** Station où se trouve [pet] (null = dehors). Sans allocation : appelé à chaque image. */
    fun stationOf(pet: Pet): StationKind? {
        val id = pet.id
        for (s in stations) if (s.has(id)) return s.kind
        return null
    }

    /** Occupé (cache-cache ou dans une station) : on ne peut ni le nourrir ni jouer avec lui. */
    fun busy(pet: Pet): Boolean = pet.isHiding || stationOf(pet) != null

    /** L'animal résident (le premier ajouté). */
    val resident: Pet get() = pets.values.first()

    fun currentToy(): Prop? = props.firstOrNull { it.toy != null }
    fun hasToy(): Boolean = props.any { it.toy != null }
    fun hasFood(): Boolean = props.any { it.food != null }
    fun hasToyOrFood(): Boolean = props.any { it.toy != null || it.food != null }

    /** L'animal est attrapé par l'utilisateur : il lâche son jouet. */
    fun dropHeldToy() {
        currentToy()?.let { if (it.held) { it.held = false; it.onGround = false } }
    }
}
