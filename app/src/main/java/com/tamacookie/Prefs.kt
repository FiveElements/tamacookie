package com.tamacookie

import android.content.Context
import android.content.SharedPreferences

/** Fichier et clés SharedPreferences, centralisés. */
object Prefs {
    const val FILE = "pet"

    /** Le lapin doit tourner (relancé au démarrage du téléphone). */
    const val ENABLED = "enabled"
    /** Niveau de bêtises : 0 sage, 1 taquin, 2 petit monstre. */
    const val MISCHIEF = "mischief"
    /** Nom du lapin (aussi écrit par PetState). */
    const val NAME = "name"
    /** Position du bouton 🏠 en fraction de la largeur / de la hauteur utile. */
    const val DOCK_FX = "dock_fx"
    const val DOCK_FY = "dock_fy"

    /** Largeur apprise d'une page du bureau, par largeur d'écran (portrait / paysage). */
    fun pageWidth(screenW: Int) = "pageW2_$screenW"

    /** Vue où une station est installée. */
    fun stationAnchor(k: StationKind) = "st_${k}_anchor"
    /** Centre d'une station en fraction de la largeur / de groundY. */
    fun stationFx(k: StationKind) = "st_${k}_fx"
    fun stationFy(k: StationKind) = "st_${k}_fy"

    fun of(ctx: Context): SharedPreferences = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
