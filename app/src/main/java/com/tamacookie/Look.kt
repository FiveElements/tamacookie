package com.tamacookie

import android.graphics.Color

/** Apparence du lapin, choisie à l'adoption. */
data class Look(
    var fur: Int = 0,          // index dans Options.furs
    var ears: Int = 0,         // 0 dressées, 1 tombantes, 2 une pliée
    var eyes: Int = 0,
    var pattern: Int = 0,      // 0 uni, 1 tache sur l'œil, 2 hollandais
    var accessory: Int = 0,    // voir Options.accessories
    var accColor: Int = 0,
)

enum class Personality(val label: String, val emoji: String, val desc: String) {
    PLAYFUL("Joueur", "🎾", "Adore jouer, s'ennuie vite"),
    GLUTTON("Gourmand", "🥕", "A tout le temps faim, adore manger"),
    LAZY("Paresseux", "😴", "Fait de longues siestes, marche lentement"),
    CURIOUS("Curieux", "🔍", "Explore les icônes et commente tes apps"),
    CUDDLY("Câlin", "💕", "Réclame des caresses, très affectueux"),
}

object Options {
    val furs = listOf(
        "Neige" to "#FFFBF5", "Crème" to "#F5E2C4", "Caramel" to "#DDA463",
        "Chocolat" to "#8C5F3F", "Gris" to "#B8B4BE", "Réglisse" to "#3E363B", "Rose" to "#F8D2DC",
    )
    val ears = listOf("Dressées", "Tombantes", "Une pliée")
    val eyes = listOf("Noisette" to "#3A2E39", "Bleu" to "#3D7DD8", "Vert" to "#3E9B5A", "Rubis" to "#C2334D")
    val patterns = listOf("Uni", "Tache sur l'œil", "Hollandais")
    val accessories = listOf("Aucun", "Nœud", "Chapeau", "Lunettes", "Écharpe", "Fleurs")
    val accColors = listOf(
        "Rose" to "#FF6F9C", "Bleu" to "#4F8DF5", "Jaune" to "#F6C343",
        "Violet" to "#9B6CF0", "Rouge" to "#E5484D", "Vert" to "#45B86F",
    )

    fun furColor(i: Int) = Color.parseColor(furs[i.coerceIn(0, furs.lastIndex)].second)
    fun eyeColor(i: Int) = Color.parseColor(eyes[i.coerceIn(0, eyes.lastIndex)].second)
    fun accColor(i: Int) = Color.parseColor(accColors[i.coerceIn(0, accColors.lastIndex)].second)

    fun isDark(color: Int): Boolean {
        val l = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return l < 110
    }

    fun shade(color: Int, f: Float): Int = Color.rgb(
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255),
    )
}
