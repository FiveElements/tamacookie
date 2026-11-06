package com.tamacookie.feature.station

import com.tamacookie.PType
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.feature.Travel

/** Où s'assoit l'animal : décalage horizontal depuis le centre et hauteur des pieds au-dessus du bas (dp). */
class Seat(val dxDp: Float, val aboveBottomDp: Float) {
    companion object {
        val CENTER = Seat(0f, 0f)
    }
}

/**
 * Ce que le contrôleur des stations offre à leurs comportements, en plus de [PetCtx] :
 * sortir l'animal et les panneaux propres à certaines stations.
 */
interface StationCtx : PetCtx {
    /** Sort [pet] de sa station ([hopOut] = il saute dehors, sinon il reste sur place). */
    fun leaveStation(pet: Pet, hopOut: Boolean)
    fun openVetPanel(pet: Pet)
    fun closeVetPanel()
    /** Ouvre le coffre (choix de nourriture / jouet). */
    fun openChest()
}

/**
 * Le comportement d'une station, rattaché à son [StationKind] : propriétés (lues par la boucle, le
 * toucher, les règles de voyage…) et ce qui arrive à l'animal qui y entre, y reste et en sort. Le dessin
 * est côté Android (`StationDrawers.of(kind)`, un `…StationDrawer` par station dans `:app`).
 *
 * Un `object` par station, dans son fichier (`BathStation.kt`…). Il ne connaît que [StationCtx] / [PetCtx]
 * et le [Pet] qu'on lui passe ; il ne garde aucun état d'animal (multi-animal).
 */
interface StationBehavior {
    /** Nom affiché dans le panneau 🏠 (avec son emoji). */
    val name: String

    /** On peut y déposer un animal (faux pour le coffre). */
    val acceptsPet: Boolean get() = true
    /** Nombre d'animaux qu'elle accueille à la fois. */
    val capacity: Int get() = 1
    /** Animal dedans : animation très économe (~12 i/s). */
    val slowFrames: Boolean get() = false
    /** Animal dedans : suit-il l'utilisateur dans une autre application ? */
    val travel: Travel get() = Travel.IGNORES
    val blocksTravel: Boolean get() = travel != Travel.FOLLOWS
    /**
     * Elle garde l'animal : il y dort, il la suit d'une vue à l'autre (et disparaît avec elle),
     * on ne peut pas la ranger avec lui dedans. Sinon, changer de vue le fait sortir.
     */
    val holdsPet: Boolean get() = false
    /** Il peut s'y endormir (« Dodo » le laisse dedans au lieu de le faire sortir). */
    val sleepsInside: Boolean get() = holdsPet
    /**
     * Décor animé quand elle est occupée (bain qui mousse, roue qui tourne) : redessiné à chaque image tant qu'elle
     * est visible **et** occupée. Vide, elle est dessinée immobile et ne demande pas d'images (la boucle peut se
     * suspendre).
     */
    val animated: Boolean get() = false
    val seat: Seat get() = Seat.CENTER

    /** Place du [rank]-ième occupant (0 = le premier entré) : [seat] pour tous, sauf station à places distinctes. */
    fun seatFor(rank: Int): Seat = seat

    /** Tap sur la station vide : explication (voir [onTap]). */
    val tapHint: String
    /** On essaie de la ranger alors qu'il est dedans. */
    val occupiedLine: String get() = "Attends, je suis dedans !"
    /** On essaie de la déplacer sur une autre vue alors qu'il est dedans ([holdsPet] seulement). */
    val stayLine: String get() = occupiedLine

    /** Ce qu'il répond quand on veut le nourrir / jouer alors qu'il est dedans. */
    fun busyLine(ctx: PetCtx): String

    /** Tap sur la station (l'animal n'est pas dedans). */
    fun onTap(ctx: StationCtx) {
        ctx.say("$tapHint\n(appui long pour déplacer)", 3f)
    }

    /** Il y entre ([announce] = il le dit ; faux à la restauration au démarrage). Son activité est déjà [com.tamacookie.Act.IN_STATION]. */
    fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {}

    /** Chaque image tant qu'il est dedans. */
    fun update(ctx: StationCtx, pet: Pet, dt: Float) {}

    /** Il en sort (déjà retiré des occupants). */
    fun onLeave(ctx: StationCtx, pet: Pet) {}

    /** On le caresse pendant qu'il est dedans. */
    fun onPet(ctx: StationCtx, pet: Pet) {
        ctx.emit(pet, PType.HEART, 1)
    }

    /** Il était dedans à la dernière sauvegarde : on l'y remet au démarrage. */
    fun restores(pet: Pet): Boolean = false

    /** La station (avec lui dedans) réapparaît sur la vue affichée ([holdsPet] seulement). */
    fun onShownAgain(ctx: StationCtx, pet: Pet, onScreen: Boolean) {}
}
