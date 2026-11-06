package com.tamacookie.feature

import com.tamacookie.Act
import com.tamacookie.Pet
import java.util.EnumMap

/** Ce que fait l'animal quand l'utilisateur change d'application pendant une activité. */
enum class Travel {
    /** Il le suit (activité tranquille). */
    FOLLOWS,
    /** Occupé : il ne le suit pas, mais commente parfois l'application. */
    BUSY,
    /** Il ne le suit pas et ne dit rien (enfermé, chez le docteur). */
    IGNORES,
}

/**
 * Une fonctionnalité qui prend en charge l'animal pendant une activité ([Act]) : la boucle lui
 * délègue l'update, et les propriétés de l'activité (cadence d'animation, voyage…) sont lues ici
 * plutôt que dans des ensembles d'[Act].
 *
 * Le contexte est lié à la création du gestionnaire (chaque famille a le sien : `StationCtx`…).
 */
interface ActHandler {
    /** Animation très économe (~12 i/s). */
    val slowFrames: Boolean get() = false
    val travel: Travel get() = Travel.FOLLOWS
    /** Il ne suit pas l'utilisateur dans une autre application. */
    val blocksTravel: Boolean get() = travel != Travel.FOLLOWS

    fun update(pet: Pet, dt: Float)

    /** Caresse (tap sur l'animal) pendant l'activité ; true = traitée. */
    fun onPet(pet: Pet): Boolean = false
}

/**
 * Registre `Act → fonctionnalité` : la boucle, le toucher et les règles globales (voyage, cadence)
 * le consultent au lieu de connaître chaque fonctionnalité.
 *
 * Étape 3a : seules les stations y sont ([Act.IN_STATION]) ; les jouets, bêtises… y viendront (3b).
 */
class ActRegistry {
    private val resolvers = EnumMap<Act, (Pet) -> ActHandler?>(Act::class.java)

    /** [resolve] renvoie le gestionnaire de l'animal pour cette activité (null = aucun). */
    fun register(act: Act, resolve: (Pet) -> ActHandler?) {
        check(act !in resolvers) { "$act déjà pris en charge" }
        resolvers[act] = resolve
    }

    /** Le gestionnaire de l'activité courante de [pet] (null = géré ailleurs). */
    fun handlerFor(pet: Pet): ActHandler? = resolvers[pet.act]?.invoke(pet)
}
