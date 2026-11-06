package com.tamacookie.tile

/**
 * État de la tuile Réglages rapides (évolution 4c, Kotlin pur) : active = l'animal est affiché.
 */
object TileTexts {
    data class State(
        /** Tuile allumée : le service tourne et l'animal est montré. */
        val active: Boolean,
        /** Sous-titre (sous le nom). */
        val subtitle: String,
        /** Ce que fera un toucher (lu par l'accessibilité). */
        val action: String,
    )

    /** [running] : le service tourne ; [visible] : l'animal est montré (pas « Caché »). */
    fun state(running: Boolean, visible: Boolean): State = when {
        !running -> State(active = false, subtitle = "Rangé", action = "Le faire venir")
        visible -> State(active = true, subtitle = "Sur l'écran", action = "Cacher")
        else -> State(active = false, subtitle = "Caché", action = "Montrer")
    }

    /** Libellé : le nom du résident, sinon celui de la tuile. */
    fun label(name: String?, fallback: String): String = name?.takeIf { it.isNotBlank() } ?: fallback
}
