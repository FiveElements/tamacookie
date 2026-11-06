package com.tamacookie.feature.station

import com.tamacookie.Pet
import com.tamacookie.Station
import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.Toy

/**
 * Port des panneaux des stations (fiche du véto, coffre ouvert, panneau 🏠) : leurs vues sont Android
 * (`StationPanelWindows`), leurs décisions restent dans `StationController`. Faux en test.
 */
interface StationPanels {
    val vetOpen: Boolean
    val chestOpen: Boolean
    val stationsOpen: Boolean

    /** Fiche du véto pour [pet], à côté de la station [vet]. */
    fun openVet(pet: Pet, vet: Station, onHeal: () -> Unit, onClose: () -> Unit)
    fun refreshVet()
    fun closeVet()

    /** Coffre [chest] ouvert : choisir à manger ou un jouet. */
    fun openChest(pet: Pet, chest: Station, onFood: (Food) -> Unit, onToy: (Toy) -> Unit, onClose: () -> Unit)
    fun closeChest()

    /**
     * Panneau 🏠 : [status] (0 = sur cette vue, 1 = ailleurs, 2 = rangée ; + libellé), [onToggle] (installer /
     * ranger). Un toucher à l'extérieur ou ✕ appelle [onClose].
     */
    fun openStations(status: (StationKind) -> Pair<Int, String>, onToggle: (StationKind) -> Unit, onClose: () -> Unit)
    fun refreshStations()
    fun closeStations()

    /** Nouvelle taille d'écran (ou station déplacée) : panneaux ouverts replacés. */
    fun reposition()
}
