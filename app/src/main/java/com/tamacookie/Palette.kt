package com.tamacookie

/**
 * Couleurs du rendu, précalculées en entiers couleur ARGB (0xAARRGGBB, sémantique `@ColorInt` ;
 * l'annotation n'est pas utilisée faute de dépendance androidx).
 *
 * Remplace les `Color.parseColor(...)` qui étaient exécutés à chaque image (particules, objets, stations).
 * Littéraux Int (et non `Color.parseColor`) : valeurs utilisables et testables en JVM pur.
 */
object Palette {
    // --- Traits et texte ---
    const val INK = 0xFF3A2E39.toInt()
    const val OUTLINE = 0xFF5B4A55.toInt()
    const val OUTLINE_DARK = 0xFF1E181C.toInt()
    const val CREAM = 0xFFF3E9DD.toInt()

    // --- Lapin ---
    const val FUR = 0xFFFFFBF5.toInt()
    const val FUR_SHADE = 0xFFF1E6DC.toInt()
    const val PINK = 0xFFF6AFC0.toInt()
    const val CHEEK = 0x66F07893
    const val SICK_CHEEK = 0x669BC48A
    const val NOSE = 0xFFF07893.toInt()
    const val DIRT = 0xFF9C7A5B.toInt()
    /** `Color.argb(45, 0, 0, 0)` */
    const val SHADOW = 0x2D000000
    const val PATCH_LIGHT = 0xFF7A5A48.toInt()
    const val HAT = 0xFF4A3B45.toInt()
    const val YELLOW = 0xFFFFD54A.toInt()
    /** `Color.argb(60, 255, 255, 255)` */
    const val LENS = 0x3CFFFFFF

    // --- Particules ---
    const val HEART = 0xFFFF5C8A.toInt()
    const val ZZZ = 0xFF7A8CD6.toInt()
    const val NOTE = 0xFFB05CE0.toInt()
    const val SPARKLE = 0xFF7FD3FF.toInt()
    const val DROP = 0xFF6EC6FF.toInt()
    const val STAR = 0xFFFFC83D.toInt()
    const val DIRT_BUBBLE = 0xFF8DA05A.toInt()

    // --- Nourriture et objets ---
    const val CARROT = 0xFFF28C28.toInt()
    const val LEAF = 0xFF4CAF50.toInt()
    const val LETTUCE_DARK = 0xFF5FAE4E.toInt()
    const val LETTUCE = 0xFF8FD67E.toInt()
    const val LETTUCE_VEIN = 0xFFC9EFC0.toInt()
    const val STRAWBERRY = 0xFFE5484D.toInt()
    const val SEED = 0xFFFFE08A.toInt()
    const val POOP = 0xFF8B5E3C.toInt()
    const val BALL = 0xFFFF6F61.toInt()
    const val BALL_STRIPE = 0xFFFFD166.toInt()
    /** `Color.argb(110, 255, 255, 255)` */
    const val BALLOON_SHINE = 0x6EFFFFFF
    /** `Color.argb(90, 255, 255, 255)` */
    const val FRISBEE_SHINE = 0x5AFFFFFF
    const val WOOD = 0xFFB07A4F.toInt()
    const val PLUSH_LIGHT = 0xFFE8C39E.toInt()

    // --- Stations ---
    const val WOOD_DARK = 0xFF7A5236.toInt()
    const val GOLD = 0xFFE9B949.toInt()
    const val CHEST_GLOW = 0xFFFFE9A8.toInt()
    const val BATH_FEET = 0xFFC9A227.toInt()
    const val BATH_RIM = 0xFFE8F4FB.toInt()
    const val BATH_BUBBLE = 0xFF8FD3F4.toInt()
    const val METAL = 0xFFB8B4BE.toInt()
    const val VET_GREEN = 0xFF3DBE7A.toInt()
    const val VET_TABLE = 0xFF9FD8F2.toInt()
    const val STETHOSCOPE = 0xFF6E7B8B.toInt()
    const val CAGE_BASE = 0xFF8C5F3F.toInt()
    const val CAGE_BARS = 0xFF7A7480.toInt()

    // --- Jauges ---
    const val BAR_BG = 0xFFEFE7E1.toInt()
    const val BAR_LOW = 0xFFE5484D.toInt()
}
