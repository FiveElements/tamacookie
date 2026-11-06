package com.tamacookie

/**
 * Une option d'apparence. [id] est **sauvegardé** et échangé ([com.tamacookie.persist.PetSnapshot]) :
 * ne jamais le changer (le nom d'enum et le libellé peuvent changer, l'ordre aussi).
 */
interface LookOption {
    val id: String
    val label: String
}

/** Une option d'apparence qui est une couleur (`#RRGGBB`). */
interface ColorOption : LookOption {
    val hex: String
    /** La couleur en ARGB opaque (`0xFFRRGGBB`, comme `Color.parseColor(hex)`), calculée en Kotlin pur. */
    val argb: Int get() = (0xFF000000L or hex.removePrefix("#").toLong(16)).toInt()
}

enum class Fur(override val id: String, override val label: String, override val hex: String) : ColorOption {
    SNOW("snow", "Neige", "#FFFBF5"),
    CREAM("cream", "Crème", "#F5E2C4"),
    CARAMEL("caramel", "Caramel", "#DDA463"),
    CHOCOLATE("chocolate", "Chocolat", "#8C5F3F"),
    GREY("grey", "Gris", "#B8B4BE"),
    LICORICE("licorice", "Réglisse", "#3E363B"),
    PINK("pink", "Rose", "#F8D2DC"),
    // --- Évolution 4b (à la fin : l'ordre des sept premiers est celui des index du schéma 0) ---
    GINGER("ginger", "Roux", "#E08A4B"),
    /** Crème avec des taches caramel (dessinées par le renderer, voir [Fur.spotted]). */
    SPOTTED_CREAM("spotted_cream", "Tacheté crème", "#F7EAD3"),
    BLUE_GREY("blue_grey", "Bleu-gris", "#8D9AB0"),
    ;

    /** Pelage tacheté : le renderer ajoute des taches ([Palette.FUR_SPOT]). */
    val spotted: Boolean get() = this == SPOTTED_CREAM

    companion object {
        /** Pelages du schéma 0 (index 0..6), pour la migration. */
        const val LEGACY_COUNT = 7
    }
}

enum class Ears(override val id: String, override val label: String) : LookOption {
    UP("up", "Dressées"),
    /** Tombantes (bélier). */
    LOP("lop", "Tombantes"),
    ONE_FOLDED("one_folded", "Une pliée"),
}

enum class EyeColor(override val id: String, override val label: String, override val hex: String) : ColorOption {
    HAZEL("hazel", "Noisette", "#3A2E39"),
    BLUE("blue", "Bleu", "#3D7DD8"),
    GREEN("green", "Vert", "#3E9B5A"),
    RUBY("ruby", "Rubis", "#C2334D"),
}

enum class Pattern(override val id: String, override val label: String) : LookOption {
    PLAIN("plain", "Uni"),
    EYE_PATCH("eye_patch", "Tache sur l'œil"),
    DUTCH("dutch", "Hollandais"),
}

enum class Accessory(override val id: String, override val label: String) : LookOption {
    NONE("none", "Aucun"),
    BOW("bow", "Nœud"),
    HAT("hat", "Chapeau"),
    GLASSES("glasses", "Lunettes"),
    SCARF("scarf", "Écharpe"),
    FLOWERS("flowers", "Fleurs"),
    // --- Évolution 4b (à la fin) ---
    CROWN("crown", "Couronne"),
    BANDANA("bandana", "Bandana"),
    BOW_TIE("bow_tie", "Nœud papillon"),
    ;

    companion object {
        /** Accessoires du schéma 0 (index 0..5), pour la migration. */
        const val LEGACY_COUNT = 6
    }
}

enum class AccColor(override val id: String, override val label: String, override val hex: String) : ColorOption {
    PINK("pink", "Rose", "#FF6F9C"),
    BLUE("blue", "Bleu", "#4F8DF5"),
    YELLOW("yellow", "Jaune", "#F6C343"),
    VIOLET("violet", "Violet", "#9B6CF0"),
    RED("red", "Rouge", "#E5484D"),
    GREEN("green", "Vert", "#45B86F"),
}

/** Apparence de l'animal, choisie à l'adoption (immuable ; identifiants stables, plus d'index). */
data class Look(
    val fur: Fur = Fur.SNOW,
    val ears: Ears = Ears.UP,
    val eyes: EyeColor = EyeColor.HAZEL,
    val pattern: Pattern = Pattern.PLAIN,
    val accessory: Accessory = Accessory.NONE,
    val accColor: AccColor = AccColor.PINK,
) {
    companion object {
        /** L'option d'identifiant [id], ou [default] si inconnu (données d'une autre version, d'un pair…). */
        fun <E : LookOption> byId(options: List<E>, id: String?, default: E): E = options.firstOrNull { it.id == id } ?: default

        /**
         * Ancien format (index entiers, schéma 0) : les couleurs étaient bornées à la liste
         * (comme `Options.furColor`), les autres options hors liste retombaient sur la première. Les listes
         * sont celles du schéma 0 ([Fur.LEGACY_COUNT], [Accessory.LEGACY_COUNT]) : les options ajoutées depuis
         * ne changent pas ce que donne un vieil index (même hors bornes).
         */
        fun fromLegacyIndexes(fur: Int, ears: Int, eyes: Int, pattern: Int, accessory: Int, accColor: Int) = Look(
            fur = Fur.entries[fur.coerceIn(0, Fur.LEGACY_COUNT - 1)],
            ears = Ears.entries.getOrElse(ears) { Ears.UP },
            eyes = EyeColor.entries[eyes.coerceIn(0, EyeColor.entries.lastIndex)],
            pattern = Pattern.entries.getOrElse(pattern) { Pattern.PLAIN },
            accessory = if (accessory in 0 until Accessory.LEGACY_COUNT) Accessory.entries[accessory] else Accessory.NONE,
            accColor = AccColor.entries[accColor.coerceIn(0, AccColor.entries.lastIndex)],
        )
    }
}
