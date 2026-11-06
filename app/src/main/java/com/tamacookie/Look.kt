package com.tamacookie

import android.graphics.Color

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
}

enum class Fur(override val id: String, override val label: String, override val hex: String) : ColorOption {
    SNOW("snow", "Neige", "#FFFBF5"),
    CREAM("cream", "Crème", "#F5E2C4"),
    CARAMEL("caramel", "Caramel", "#DDA463"),
    CHOCOLATE("chocolate", "Chocolat", "#8C5F3F"),
    GREY("grey", "Gris", "#B8B4BE"),
    LICORICE("licorice", "Réglisse", "#3E363B"),
    PINK("pink", "Rose", "#F8D2DC"),
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
         * (comme `Options.furColor`), les autres options hors liste retombaient sur la première.
         */
        fun fromLegacyIndexes(fur: Int, ears: Int, eyes: Int, pattern: Int, accessory: Int, accColor: Int) = Look(
            fur = Fur.entries[fur.coerceIn(0, Fur.entries.lastIndex)],
            ears = Ears.entries.getOrElse(ears) { Ears.UP },
            eyes = EyeColor.entries[eyes.coerceIn(0, EyeColor.entries.lastIndex)],
            pattern = Pattern.entries.getOrElse(pattern) { Pattern.PLAIN },
            accessory = Accessory.entries.getOrElse(accessory) { Accessory.NONE },
            accColor = AccColor.entries[accColor.coerceIn(0, AccColor.entries.lastIndex)],
        )
    }
}

object Options {
    fun color(o: ColorOption) = Color.parseColor(o.hex)

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
