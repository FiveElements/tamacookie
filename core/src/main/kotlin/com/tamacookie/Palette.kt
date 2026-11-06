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
    /** Taches du pelage tacheté crème (évolution 4b). */
    const val FUR_SPOT = 0xFFC9935F.toInt()
    /** Couronne : or et reflet. */
    const val CROWN_GOLD = 0xFFF2C14E.toInt()
    const val CROWN_SHINE = 0xFFFFE9A8.toInt()

    // --- Particules ---
    const val HEART = 0xFFFF5C8A.toInt()
    const val ZZZ = 0xFF7A8CD6.toInt()
    const val NOTE = 0xFFB05CE0.toInt()
    const val SPARKLE = 0xFF7FD3FF.toInt()
    const val DROP = 0xFF6EC6FF.toInt()
    const val STAR = 0xFFFFC83D.toInt()
    const val DIRT_BUBBLE = 0xFF8DA05A.toInt()
    /** Saisons (évolution 4b) : flocon, pétale. */
    const val SNOW = 0xFFB9DCF7.toInt()
    const val PETAL = 0xFFFFB3C9.toInt()

    // --- Décor de saison (évolution 4b) ---
    const val SANTA_RED = 0xFFD93A3A.toInt()
    const val SANTA_WHITE = 0xFFFFFFFF.toInt()
    const val PUMPKIN = 0xFFF28C28.toInt()
    const val PUMPKIN_DARK = 0xFFC2621A.toInt()
    const val PUMPKIN_STEM = 0xFF6B8E3A.toInt()
    const val PUMPKIN_FACE = 0xFF4A2A12.toInt()
    const val SPRING_FLOWER = 0xFFFF8FB1.toInt()
    const val SPRING_FLOWER_2 = 0xFFB59CFF.toInt()

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
    const val APPLE = 0xFFD94A3D.toInt()
    const val APPLE_STEM = 0xFF6B4A2B.toInt()
    const val HAY = 0xFFE3C46A.toInt()
    const val HAY_DARK = 0xFFB8963F.toInt()
    const val TREAT = 0xFFC68642.toInt()
    const val TREAT_CHIP = 0xFF5A3A22.toInt()
    const val ROPE = 0xFFE9D3A8.toInt()
    const val ROPE_STRIPE = 0xFFD9534F.toInt()
    const val YARN = 0xFFB07CE0.toInt()
    const val TUNNEL = 0xFF4FA3E0.toInt()
    const val TUNNEL_RING = 0xFF2F7DB8.toInt()
    const val TUNNEL_HOLE = 0xFF1C3B57.toInt()

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
    const val BED_WOOD = 0xFFA9744F.toInt()
    const val BED_PILLOW = 0xFFFFFFFF.toInt()
    const val BED_BLANKET = 0xFF8FB8F2.toInt()
    const val BED_BLANKET_DOT = 0xFFFFF3B0.toInt()
    const val BOWL = 0xFFE86F5A.toInt()
    const val BOWL_RIM = 0xFFF6B7A8.toInt()
    const val WHEEL = 0xFF5BBF8A.toInt()
    const val WHEEL_SPOKE = 0xFF9AA0A8.toInt()
    const val WHEEL_STAND = 0xFF8A8F98.toInt()
    const val SOIL = 0xFF8B5E3C.toInt()
    const val GRASS = 0xFF5CB85C.toInt()
    const val FLOWER_A = 0xFFFF8FB1.toInt()
    const val FLOWER_B = 0xFFB9A2FF.toInt()

    // --- Jauges ---
    const val BAR_BG = 0xFFEFE7E1.toInt()
    const val BAR_LOW = 0xFFE5484D.toInt()

    // --- Interface de l'app : écrans, panneaux, widget (étape 8d) ---
    // Chaque paire texte / fond utilisée est vérifiée par ContrastTest (≥ 4,5:1, WCAG AA texte normal).
    /** Fond crème des écrans et du widget. */
    const val UI_BG = 0xFFFFF4E6.toInt()
    /** Fond des panneaux flottants, du dock. */
    const val UI_PANEL = 0xFFFFFDF9.toInt()
    /** Texte secondaire (descriptions, astuces, valeurs). */
    const val UI_MUTED = 0xFF7A6B75.toInt()
    /** Petits boutons de l'écran principal. */
    const val UI_CHIP = 0xFFFBE3D6.toInt()
    /** Choix sélectionné (fond) et son contour (non textuel). */
    const val UI_SELECTED = 0xFFFFE0E8.toInt()
    const val UI_SELECTED_STROKE = 0xFFF07893.toInt()
    /** Contour d'un choix non sélectionné. */
    const val UI_STROKE = 0xFFE3D6CC.toInt()
    /** Boutons des panneaux (menu, coffre, véto). */
    const val UI_BUTTON = 0xFFFBEFE6.toInt()
    /** Autorisation accordée / manquante. */
    const val UI_OK = 0xFFE3F4E8.toInt()
    const val UI_WARN = 0xFFFFE3E3.toInt()
    /**
     * Bouton d'action (texte blanc : « Faire apparaître », « Adopter ! », boutons du widget). Était `#F07893`,
     * 2,7:1 avec le blanc : assombri en framboise (4,8:1) — même teinte, plus foncée.
     */
    const val UI_ACTION = 0xFFC2456A.toInt()
    /**
     * Bouton principal sans la permission de surimpression (il ouvre les réglages : il reste actif). Était
     * `#BDB3B8` avec un texte blanc (2,0:1) : fond plus clair et texte [INK] (8,6:1).
     */
    const val UI_ACTION_OFF = 0xFFD9D1D5.toInt()
    /** Bouton principal quand l'animal est affiché (« Ranger »), texte blanc. */
    const val UI_ACTION_STOP = 0xFF7A6B75.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
}

/** Contraste entre deux couleurs (WCAG 2.x, Kotlin pur) : de 1 (identiques) à 21 (noir sur blanc). */
object Contrast {
    /** Luminance relative d'une couleur opaque `0xAARRGGBB` (l'alpha est ignoré). */
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Seuil WCAG AA pour un texte de taille normale. */
    const val AA = 4.5
}
