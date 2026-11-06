package com.tamacookie

import com.tamacookie.feature.station.StationKind

/**
 * Fichiers et clés SharedPreferences des **réglages**, centralisés. Les animaux sont dans un fichier à part
 * ([PETS_FILE]), lu et écrit seulement par `PetRepository`. Lus par le cœur via le port [SettingsStore] ; côté Android : `Prefs.of(context)`.
 */
object Prefs {
    /** Réglages (nom historique : il contenait aussi l'animal jusqu'au schéma 0). */
    const val FILE = "pet"
    /** Animaux (schéma versionné, clés par PetId). */
    const val PETS_FILE = "pets"

    /** Le lapin doit tourner (relancé au démarrage du téléphone). */
    const val ENABLED = "enabled"
    /** Niveau de bêtises : 0 sage, 1 taquin, 2 petit monstre. */
    const val MISCHIEF = "mischief"
    /** Position du bouton 🏠 en fraction de la largeur / de la hauteur utile. */
    const val DOCK_FX = "dock_fx"
    const val DOCK_FY = "dock_fy"
    /** Thèmes de saison (Noël, Halloween, printemps) : activés par défaut ([SEASONAL_DEFAULT]). */
    const val SEASONAL = "seasonal"
    const val SEASONAL_DEFAULT = true
    /** Bien-être numérique (pause proposée après un long moment dans la même app) : coupé par défaut. */
    const val WELLBEING = "wellbeing"
    const val WELLBEING_DEFAULT = false

    /** Largeur apprise d'une page du bureau, par largeur d'écran (portrait / paysage). */
    fun pageWidth(screenW: Int) = "pageW2_$screenW"

    /** Vue où une station est installée. */
    fun stationAnchor(k: StationKind) = "st_${k}_anchor"
    /** Centre d'une station en fraction de la largeur / de groundY. */
    fun stationFx(k: StationKind) = "st_${k}_fx"
    fun stationFy(k: StationKind) = "st_${k}_fy"
}
