package com.tamacookie.species

import com.tamacookie.Mood
import com.tamacookie.Season

/**
 * Répliques d'une espèce dites par la boucle (bavardage, accueil, voyage, chutes) et le toucher.
 * Les répliques des fonctionnalités (stations, jouets, bêtises, pages) restent dans leur fonctionnalité.
 * Chaque tableau est un tirage possible (`Speech.pickFrom`).
 */
interface SpeciesLines {
    // --- Accueil, réveil, apparition ---
    fun greeting(name: String, hungry: Boolean, hour: Int): String
    fun newLook(name: String): Array<String>
    val WELCOME_BACK: Array<String>
    val SHOWN: Array<String>

    // --- Bavardage selon l'humeur ---
    fun chatter(mood: Mood): Array<String>

    /** Répliques de saison (évolution 4b), glissées dans le bavardage ; vide = aucune ([Season.NONE]). */
    fun seasonal(season: Season): Array<String>

    // --- Vie quotidienne ---
    val POOPED: Array<String>
    val WOKE_UP: Array<String>
    val ATE: Array<String>
    val GOING_TO_SLEEP: Array<String>

    // --- Plateformes et chutes ---
    val PLATFORM_LOST: Array<String>
    val HARD_LANDING_PLATFORM: Array<String>
    val HARD_LANDING_GROUND: Array<String>
    fun onPlatform(label: String): Array<String>

    // --- Toucher, glisser, lancer ---
    val FOUND_WHILE_DRAGGED: String
    val DRAGGED: Array<String>
    val THROWN: Array<String>
    val WOKEN_BY_TAP: Array<String>
    val TICKLED: Array<String>
    val PETTED: Array<String>

    // --- Menu ---
    val GETTING_UP: String
    val NOT_TIRED: Array<String>

    // --- Voyage d'application en application ---
    val FOLLOWING: Array<String>

    /** Commentaires possibles à l'arrivée dans une app ([category] : `ApplicationInfo.CATEGORY_*`). */
    fun appComments(label: String, pkg: String, category: Int, isHome: Boolean): Array<String>
}
