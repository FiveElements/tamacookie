package com.tamacookie.feature.toy

/**
 * Les jouets. Le nom est sauvegardé (`favoriteToy`) : ne pas renommer. Tout le reste (taille, lancer,
 * dessin, jeu, répliques) est porté par son [spec] (`BallToy.kt`…).
 */
enum class Toy(val label: String, val emoji: String, val spec: ToySpec) {
    BALL("Balle", "🎾", BallToy),
    BALLOON("Ballon", "🎈", BalloonToy),
    FRISBEE("Frisbee", "🥏", FrisbeeToy),
    PLUSH("Doudou", "🧸", PlushToy),
}

/** La nourriture (le coffre, la notification « Nourrir »). Effets sur les jauges ici, dessin dans [spec] (`Foods.kt`). */
enum class Food(
    val label: String, val emoji: String,
    val satiety: Float, val happiness: Float, val health: Float,
    val color: String,
    val spec: FoodSpec,
) {
    CARROT("Carotte", "🥕", 30f, 4f, 0f, "#F28C28", CarrotFood),
    LETTUCE("Salade", "🥬", 20f, 2f, 6f, "#7BC96F", LettuceFood),
    STRAWBERRY("Fraise", "🍓", 10f, 12f, 0f, "#E5484D", StrawberryFood),
}
