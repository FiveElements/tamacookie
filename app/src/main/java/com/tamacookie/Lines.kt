package com.tamacookie

/**
 * Répliques du lapin dites par le service (bavardage, accueil, voyage, toucher…).
 *
 * Kotlin pur (testable en JVM). Chaque liste est un tirage possible pour [Speech.pick].
 * Les textes restent en français, en dur, jusqu'à leur extraction dans `strings.xml` (étape 8).
 * Les répliques propres aux contrôleurs (stations, jouets, bêtises, pages) les rejoindront
 * avec leur fonctionnalité (étape 3).
 */
object Lines {

    // --- Accueil, réveil, apparition ---
    fun greeting(name: String, hungry: Boolean, hour: Int): String = when {
        hungry -> "Coucou… j'ai faim 🥺"
        hour < 11 -> "Bonjour ! ☀️ C'est moi, $name !"
        hour < 18 -> "Coucou ! C'est $name 🐰"
        else -> "Bonsoir ! 🌙 $name est là"
    }
    fun newLook(name: String) = arrayOf("Je m'appelle $name ! ✨", "Tu aimes mon nouveau look ? ✨")
    val WELCOME_BACK = arrayOf("Te revoilà ! 💕", "Coucou toi !", "Tu m'as manqué !")
    val SHOWN = arrayOf("Coucou, me revoilà !", "Je suis là ! 🐰", "Hop !")

    // --- Bavardage selon l'humeur ---
    fun chatter(mood: PetState.Mood): Array<String> = when (mood) {
        PetState.Mood.HUNGRY -> arrayOf("J'ai faim… 🥕", "Mon ventre gargouille…", "Une petite carotte ?")
        PetState.Mood.SLEEPY -> arrayOf("*bâille*", "Je suis fatigué…", "Une sieste me ferait du bien")
        PetState.Mood.DIRTY -> arrayOf("Je suis tout sale…", "Un bain ? 🫧", "Ça sent pas très bon ici…")
        PetState.Mood.SAD -> arrayOf("Je m'ennuie…", "Tu joues avec moi ?", "Un câlin ? 🥺")
        PetState.Mood.SICK -> arrayOf("Je ne me sens pas bien… 🤒", "Atchoum !", "J'ai besoin d'un médicament")
        PetState.Mood.HAPPY -> arrayOf("La vie est belle ! ✨", "♪ La la la ♫", "Je t'aime bien 💕", "Tu fais quoi ?", "Il fait beau sur ton écran !")
        PetState.Mood.OK -> arrayOf("Hmm hmm…", "*renifle*", "Tu fais quoi ?", "Je me promène 🐾")
    }

    // --- Vie quotidienne ---
    val POOPED = arrayOf("Oups… 💩", "C'est pas moi !", "Hihi… pardon")
    val WOKE_UP = arrayOf("Bien dormi ! ☀️", "*s'étire* Bonjour !", "Quelle bonne sieste !")
    val ATE = arrayOf("Miam ! 🥕", "Trop bon !", "Merci ! *rot*", "Délicieux !")
    val GOING_TO_SLEEP = arrayOf("Bonne nuit… 💤", "*bâille* Je fais une petite sieste", "Zzz…")

    // --- Plateformes et chutes ---
    val PLATFORM_LOST = arrayOf("Oups !", "Whoaaa !", "Ça bouge !")
    val HARD_LANDING_PLATFORM = arrayOf("Ouille ! Ça tourne…", "Aïe aïe aïe…")
    val HARD_LANDING_GROUND = arrayOf("Ouille ! Ça tourne…", "Aïe aïe aïe…", "Doucement !")
    fun onPlatform(label: String) =
        arrayOf("Je suis sur $label !", "Coucou depuis $label 👋", "La vue est belle d'ici !", "Hop, sur $label !")

    // --- Toucher, glisser, lancer ---
    const val FOUND_WHILE_DRAGGED = "Trouvé !! 🎉"
    val DRAGGED = arrayOf("Waaah !", "Hé ! Pose-moi !", "Je voleeee !", "Hiii !")
    val THROWN = arrayOf("Wiiiiii !", "Je vole !!", "Aaaaah !")
    val WOKEN_BY_TAP = arrayOf("Hmpf… je dormais !", "Encore 5 minutes…", "Grrr… 😾")
    val TICKLED = arrayOf("Ça chatouille !! 🤭", "Arrête, hihi !")
    val PETTED = arrayOf("Hihi ! 💕", "Encore !", "J'adore les câlins", "Mmmh…", "Tu es gentil·le !", "*ronronne* (oui, les lapins aussi)")

    // --- Menu ---
    const val GETTING_UP = "*bâille* D'accord, je me lève…"
    val NOT_TIRED = arrayOf("Je ne suis pas fatigué !", "Dodo ? Déjà ?!")

    // --- Voyage d'application en application ---
    val FOLLOWING = arrayOf("J'arrive !", "Attends-moi !", "Hop, on change d'app !", "Je te suis ! 🐾")

    /**
     * Commentaires possibles à l'arrivée dans une app.
     * [category] : `ApplicationInfo.CATEGORY_*` (constantes de compilation, utilisables en JVM).
     */
    fun appComments(label: String, pkg: String, category: Int, isHome: Boolean): Array<String> {
        val p = pkg.lowercase()
        return when {
            isHome -> arrayOf("De retour à la maison 🏠", "Ah, l'écran d'accueil !", "On fait quoi maintenant ?")
            "camera" in p -> arrayOf("Prends-moi en photo ! 📸", "Je fais mon plus beau sourire")
            "clock" in p || "alarm" in p -> arrayOf("Il est l'heure de… manger ?", "Tic tac 🕰️")
            "store" in p || "vending" in p -> arrayOf("On fait les boutiques ? 🛍️", "Tu m'achètes une carotte ?")
            category == CATEGORY_GAME -> arrayOf("Un jeu ! Je peux jouer ? 🎮", "Vas-y, bats le record !")
            category == CATEGORY_VIDEO -> arrayOf("Pop-corn ? 🍿 Je regarde avec toi", "Chut, ça commence !")
            category == CATEGORY_AUDIO -> arrayOf("J'adore cette musique ♪", "On danse ? ♫")
            category == CATEGORY_SOCIAL -> arrayOf("Dis bonjour de ma part sur $label !", "Qui t'écrit ? 👀")
            category == CATEGORY_MAPS -> arrayOf("On part en voyage ? 🗺️", "Je peux venir ?")
            category == CATEGORY_NEWS -> arrayOf("Quoi de neuf dans le monde ?", "Des nouvelles des lapins ?")
            category == CATEGORY_IMAGE -> arrayOf("Montre-moi tes photos !", "Il y a des photos de moi ?")
            category == CATEGORY_PRODUCTIVITY -> arrayOf("Courage pour le travail 💪", "Je ne dérange pas… promis")
            else -> arrayOf("Coucou $label !", "Oh, $label ! On fait quoi ici ?", "Je visite $label 🐾", "C'est joli chez $label")
        }
    }

    // Valeurs de android.content.pm.ApplicationInfo.CATEGORY_* (copiées pour rester en Kotlin pur)
    private const val CATEGORY_GAME = 0
    private const val CATEGORY_AUDIO = 1
    private const val CATEGORY_VIDEO = 2
    private const val CATEGORY_IMAGE = 3
    private const val CATEGORY_SOCIAL = 4
    private const val CATEGORY_NEWS = 5
    private const val CATEGORY_MAPS = 6
    private const val CATEGORY_PRODUCTIVITY = 7
}
