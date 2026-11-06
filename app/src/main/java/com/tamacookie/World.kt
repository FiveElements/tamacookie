package com.tamacookie

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
 * Le monde : les animaux et ce qu'ils partagent (objets posés ; stations à l'étape 3).
 * Un seul animal pour l'instant (le résident), mais rien ne suppose qu'il est unique.
 */
class World {
    val pets = LinkedHashMap<PetId, Pet>()

    /** Nourriture, crottes et jouets posés à l'écran (gérés par [ToyController]). */
    val props = ArrayList<Prop>()

    fun add(pet: Pet) { pets[pet.id] = pet }

    /** L'animal résident (le premier ajouté). */
    val resident: Pet get() = pets.values.first()

    fun currentToy(): Prop? = props.firstOrNull { it.toy != null }
    fun hasToy(): Boolean = props.any { it.toy != null }
    fun hasFood(): Boolean = props.any { it.view.kind.isFood }
    fun hasToyOrFood(): Boolean = props.any { it.toy != null || it.view.kind.isFood }

    /** L'animal est attrapé par l'utilisateur : il lâche son jouet. */
    fun dropHeldToy() {
        currentToy()?.let { if (it.held) { it.held = false; it.onGround = false } }
    }
}
