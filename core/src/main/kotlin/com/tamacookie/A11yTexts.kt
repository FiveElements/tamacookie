package com.tamacookie

import com.tamacookie.feature.station.StationKind
import java.util.EnumMap

/**
 * Textes lus par le lecteur d'écran (étape 8d, Kotlin pur) : description des fenêtres overlay (animal, station,
 * objet), jauges en pourcentage, aperçus de l'app, et ce qui mérite d'être **annoncé** dans une bulle.
 *
 * Ils sont calculés à la demande (quand TalkBack interroge la vue), jamais à chaque image. Les libellés fixes de
 * l'app (« Fermer », noms des actions TalkBack, rôles) sont dans `strings.xml` (préfixe `access_`).
 */
object A11yTexts {
    // --- Jauges : « Satiété 80 % » ---
    const val SATIETY = "Satiété"
    const val HAPPINESS = "Bonheur"
    const val ENERGY = "Énergie"
    const val CLEANLINESS = "Propreté"
    const val HEALTH = "Santé"

    /**
     * « Satiété 80 % » : valeur affichée (comme le widget et la fiche du véto : partie entière, bornée à 0..100),
     * espace insécable avant le signe, lu « 80 pour cent ».
     */
    fun gauge(label: String, value: Float): String = "$label ${value.toInt().coerceIn(0, 100)} %"

    /** Les jauges, dans l'ordre de la notification (santé en dernier si [health]). */
    fun gauges(s: PetStats, health: Boolean = true): String {
        val list = mutableListOf(
            gauge(SATIETY, s.satiety), gauge(HAPPINESS, s.happiness),
            gauge(ENERGY, s.energy), gauge(CLEANLINESS, s.cleanliness),
        )
        if (health) list += gauge(HEALTH, s.health)
        return list.joinToString(", ")
    }

    // --- L'animal : « Cookie, lapin, content » ---

    /** Son humeur, en un mot ou deux (« content », « a faim »…). */
    fun mood(mood: Mood, sleeping: Boolean): String = when (mood) {
        Mood.HAPPY -> "aux anges"
        Mood.OK -> "content"
        Mood.HUNGRY -> "a faim"
        Mood.SLEEPY -> if (sleeping) "dort" else "a sommeil"
        Mood.DIRTY -> "a besoin d'un bain"
        Mood.SAD -> "se sent seul"
        Mood.SICK -> "malade"
    }

    /** Ce qu'il fait, quand c'est plus parlant que son humeur ; absent = au repos, en balade ou dans une station. */
    private val ACTIVITIES: Map<Act, String> = EnumMap<Act, String>(Act::class.java).apply {
        put(Act.SLEEP, "dort")
        put(Act.EAT, "mange")
        put(Act.GOTO_FOOD, "court vers à manger")
        put(Act.GOTO_CHEST, "va au coffre")
        put(Act.GOTO_BOWL, "va à la gamelle")
        put(Act.PLAY, "joue")
        put(Act.AIR, "en l'air")
        put(Act.DRAGGED, "tenu")
        for (a in listOf(Act.TRAVEL_OUT, Act.TRAVEL_IN, Act.PAGE_OUT)) put(a, "se déplace")
        put(Act.DIZZY, "a la tête qui tourne")
        put(Act.DANCE, "danse")
        for (a in listOf(Act.SQUAT, Act.KNOCK, Act.TEASE, Act.CHEW_EDGE)) put(a, "fait une bêtise")
        for (a in Act.HIDING) put(a, "se cache")
        put(Act.SOCIAL, "avec un autre animal")
        put(Act.SKIP_AD, "passe la pub")
    }

    /** Ce qu'il fait ([ACTIVITIES]) ; null = au repos, en balade ou dans une station (on dit son humeur). */
    fun activity(act: Act): String? = ACTIVITIES[act]

    /** Humeurs qui méritent d'être dites même quand il est occupé. */
    private val NEEDS = setOf(Mood.HUNGRY, Mood.DIRTY, Mood.SAD, Mood.SICK)

    /**
     * Description de l'animal : nom, espèce, station éventuelle, puis ce qu'il fait ou son humeur
     * (« Cookie, lapin, content » ; « Cookie, lapin, dans Baignoire, a besoin d'un bain » ; « Cookie, lapin, joue,
     * a faim »).
     */
    fun pet(name: String, species: String, act: Act, mood: Mood, sleeping: Boolean, station: String? = null): String {
        val parts = mutableListOf(name, species.lowercase())
        if (station != null) parts += "dans ${SpokenText.plain(station)}"
        val doing = activity(act) ?: if (sleeping) activity(Act.SLEEP) else null
        if (doing != null) parts += doing
        if (doing == null || mood in NEEDS) {
            val m = mood(mood, sleeping)
            if (m != doing) parts += m
        }
        return parts.joinToString(", ")
    }

    /** Description d'un animal du monde ([station] : celle qu'il occupe). */
    fun pet(p: Pet, station: StationKind? = null): String =
        pet(p.name, p.species.label, p.body.act, p.stats.mood, p.stats.sleeping, station?.behavior?.name)

    /** Aperçu de l'app (écran principal, adoption) : « Cookie, lapin, content ». */
    fun preview(name: String, species: String, mood: Mood = Mood.OK, sleeping: Boolean = false): String =
        listOf(name, species.lowercase(), mood(mood, sleeping)).joinToString(", ")

    // --- Stations et objets ---

    /** « Baignoire, vide » ; « Baignoire, Cookie dedans » ; « Coffre » (n'accueille personne). */
    fun station(name: String, occupants: List<String>, acceptsPet: Boolean): String {
        val n = SpokenText.plain(name)
        if (!acceptsPet) return n
        return if (occupants.isEmpty()) "$n, vide" else "$n, ${occupants.joinToString(" et ")} dedans"
    }

    const val POOP = "Crotte"

    /** « Balle, jouet » ; « Carotte, à manger » ; « Crotte de Cookie » ([owner] : son auteur). */
    fun prop(p: Prop, owner: String? = null): String {
        val toy = p.toy
        val food = p.food
        return when {
            toy != null -> "${SpokenText.plain(toy.label)}, jouet"
            food != null -> "${SpokenText.plain(food.label)}, à manger"
            p.isPoop && owner != null -> "$POOP de $owner"
            else -> POOP
        }
    }

    // --- Bulles ---

    /**
     * La réplique est annoncée par le lecteur d'écran : seulement les réponses à l'utilisateur (toucher, menu,
     * commande : [SpeechPriority.HIGH]). Le bavardage et ce qu'il fait tout seul restent à l'écran sans être lus.
     */
    fun announces(priority: SpeechPriority): Boolean = priority == SpeechPriority.HIGH
}

/** Un texte affiché, tel que le lecteur d'écran doit le dire (étape 8d, Kotlin pur). */
object SpokenText {
    /**
     * [text] sans ses emojis ni ses symboles décoratifs (« 🛁 Baignoire » → « Baignoire »,
     * « 💤\nDodo » → « Dodo »), espaces et retours à la ligne réduits à un espace. Lettres, chiffres et
     * ponctuation sont gardés.
     */
    fun plain(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (isDecoration(cp)) { sb.append(' '); continue }
            sb.appendCodePoint(cp)
        }
        return sb.toString().replace(SPACES, " ").trim()
    }

    /** Comme [plain], ligne par ligne : une liste à puces d'emojis (« Comment jouer ») garde ses lignes. */
    fun plainLines(text: String): String =
        text.lines().map(::plain).filter { it.isNotEmpty() }.joinToString("\n")

    private val SPACES = Regex("\\s+")

    /** Emoji, symbole, ou ce qui les assemble (jointure, sélecteur de variante, touche, couleur de peau). */
    private fun isDecoration(cp: Int): Boolean {
        if (cp in JOINERS || cp in VARIATION_SELECTORS || cp in EMOJI_BLOCKS) return true
        val t = Character.getType(cp)
        return t == Character.OTHER_SYMBOL.toInt() || t == Character.SURROGATE.toInt()
    }

    /** Jointure sans chasse (❤️‍🩹) et touche (1️⃣). */
    private val JOINERS = setOf(0x200D, 0x20E3)
    private val VARIATION_SELECTORS = 0xFE00..0xFE0F
    /**
     * Blocs d'emojis du plan supplémentaire (dont les couleurs de peau) : par plage et non par catégorie Unicode,
     * car un JDK ancien ne connaît pas les emojis récents (🫣, Unicode 14, est « non attribué » pour le JDK 17).
     */
    private val EMOJI_BLOCKS = 0x1F000..0x1FAFF
}

/**
 * Réduction des animations (étape 8d, Kotlin pur) : décidée par l'app d'après les réglages d'Android
 * (`Settings.Global.ANIMATOR_DURATION_SCALE`, `TRANSITION_ANIMATION_SCALE`, `WINDOW_ANIMATION_SCALE`), appliquée
 * par [AmbientAnimator.reducedMotion]. L'animal continue de vivre (marche, respiration, clignement, Zzz) : seules
 * les particules et frémissements **purement décoratifs** disparaissent.
 */
object ReducedMotion {
    /**
     * Vrai si l'utilisateur a coupé les animations : durée des animations à 0 (« Supprimer les animations »
     * d'Android met les trois échelles à 0), ou transitions **et** fenêtres à 0.
     */
    fun isReduced(animatorScale: Float, transitionScale: Float, windowScale: Float): Boolean =
        animatorScale <= 0f || (transitionScale <= 0f && windowScale <= 0f)
}
