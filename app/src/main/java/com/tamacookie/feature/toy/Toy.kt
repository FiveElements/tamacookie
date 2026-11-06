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
    ROPE("Corde", "🪢", RopeToy),
    YARN("Pelote", "🧶", YarnToy),
    TUNNEL("Tunnel", "🕳️", TunnelToy),
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
    APPLE("Pomme", "🍎", 22f, 6f, 3f, "#D94A3D", AppleFood),
    HAY("Foin", "🌾", 25f, 1f, 4f, "#E3C46A", HayFood),
    /** Friandise : beaucoup de joie, presque pas de satiété (toujours acceptée, comme la fraise). */
    TREAT("Friandise", "🍪", 4f, 18f, 0f, "#C68642", TreatFood),
}
