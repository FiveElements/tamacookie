package com.tamacookie.species

import com.tamacookie.PetRenderer
import com.tamacookie.species.rabbit.Rabbit
import com.tamacookie.species.rabbit.RabbitRenderer

/**
 * Dessin (Android) de chaque espèce : l'espèce ([Species]) est dans le cœur, son renderer ici (un par vue).
 * Une nouvelle espèce doit y être ajoutée (vérifié par `DrawersTest` pour toutes les [Species.ALL]).
 */
object SpeciesRenderers {
    private val byId: Map<String, (density: Float) -> PetRenderer> = mapOf(
        Rabbit.ID to ::RabbitRenderer,
    )

    fun has(species: Species): Boolean = species.id in byId

    /** Renderer d'un animal de [species] ; [density] = px par dp. */
    fun of(species: Species, density: Float): PetRenderer =
        checkNotNull(byId[species.id]) { "espèce sans dessin : ${species.id}" }(density)
}
