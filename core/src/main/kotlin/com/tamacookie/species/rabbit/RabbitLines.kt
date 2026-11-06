package com.tamacookie.species.rabbit

import com.tamacookie.Mood
import com.tamacookie.Season
import com.tamacookie.species.SpeciesLines

/**
 * Répliques du lapin dites par la boucle et le toucher (bavardage, accueil, voyage, toucher…) :
 * [SpeciesLines] de [Rabbit]. Les répliques des fonctionnalités (stations, jouets, bêtises, pages) sont
 * dans leur fonctionnalité. Kotlin pur ; chaque liste est un tirage possible pour `Speech.pickFrom`.
 * Les textes restent en français, en dur, jusqu'à leur extraction dans `strings.xml` (étape 8).
 */
object RabbitLines : SpeciesLines {

    // --- Accueil, réveil, apparition ---
    override fun greeting(name: String, hungry: Boolean, hour: Int): String = when {
        hungry -> "Coucou… j'ai faim 🥺"
        hour < 11 -> "Bonjour ! ☀️ C'est moi, $name !"
        hour < 18 -> "Coucou ! C'est $name 🐰"
        else -> "Bonsoir ! 🌙 $name est là"
    }
    override fun newLook(name: String) = arrayOf("Je m'appelle $name ! ✨", "Tu aimes mon nouveau look ? ✨")
    override val WELCOME_BACK = arrayOf("Te revoilà ! 💕", "Coucou toi !", "Tu m'as manqué !")
    override val SHOWN = arrayOf("Coucou, me revoilà !", "Je suis là ! 🐰", "Hop !")

    // --- Bavardage selon l'humeur ---
    override fun chatter(mood: Mood): Array<String> = when (mood) {
        Mood.HUNGRY -> arrayOf("J'ai faim… 🥕", "Mon ventre gargouille…", "Une petite carotte ?")
        Mood.SLEEPY -> arrayOf("*bâille*", "Je suis fatigué…", "Une sieste me ferait du bien")
        Mood.DIRTY -> arrayOf("Je suis tout sale…", "Un bain ? 🫧", "Ça sent pas très bon ici…")
        Mood.SAD -> arrayOf("Je m'ennuie…", "Tu joues avec moi ?", "Un câlin ? 🥺")
        Mood.SICK -> arrayOf("Je ne me sens pas bien… 🤒", "Atchoum !", "J'ai besoin d'un médicament")
        Mood.HAPPY -> arrayOf("La vie est belle ! ✨", "♪ La la la ♫", "Je t'aime bien 💕", "Tu fais quoi ?", "Il fait beau sur ton écran !")
        Mood.OK -> arrayOf("Hmm hmm…", "*renifle*", "Tu fais quoi ?", "Je me promène 🐾")
    }

    override fun seasonal(season: Season): Array<String> = when (season) {
        Season.CHRISTMAS -> arrayOf("Joyeux Noël ! 🎄", "Il neige sur ton écran ! ❄️", "Le Père Noël aime les carottes ? 🎅")
        Season.HALLOWEEN -> arrayOf("Bouh ! 🎃", "Des bonbons ou une carotte ! 👻")
        Season.SPRING -> arrayOf("Ça sent le printemps ! 🌸", "Les fleurs sont de sortie 🌷")
        Season.NONE -> emptyArray()
    }

    // --- Vie quotidienne ---
    override val POOPED = arrayOf("Oups… 💩", "C'est pas moi !", "Hihi… pardon")
    override val WOKE_UP = arrayOf("Bien dormi ! ☀️", "*s'étire* Bonjour !", "Quelle bonne sieste !")
    override val ATE = arrayOf("Miam ! 🥕", "Trop bon !", "Merci ! *rot*", "Délicieux !")
    override val GOING_TO_SLEEP = arrayOf("Bonne nuit… 💤", "*bâille* Je fais une petite sieste", "Zzz…")

    // --- Plateformes et chutes ---
    override val PLATFORM_LOST = arrayOf("Oups !", "Whoaaa !", "Ça bouge !")
    override val HARD_LANDING_PLATFORM = arrayOf("Ouille ! Ça tourne…", "Aïe aïe aïe…")
    override val HARD_LANDING_GROUND = arrayOf("Ouille ! Ça tourne…", "Aïe aïe aïe…", "Doucement !")
    override fun onPlatform(label: String) =
        arrayOf("Je suis sur $label !", "Coucou depuis $label 👋", "La vue est belle d'ici !", "Hop, sur $label !")

    // --- Toucher, glisser, lancer ---
    override val FOUND_WHILE_DRAGGED = "Trouvé !! 🎉"
    override val DRAGGED = arrayOf("Waaah !", "Hé ! Pose-moi !", "Je voleeee !", "Hiii !")
    override val THROWN = arrayOf("Wiiiiii !", "Je vole !!", "Aaaaah !")
    override val WOKEN_BY_TAP = arrayOf("Hmpf… je dormais !", "Encore 5 minutes…", "Grrr… 😾")
    override val TICKLED = arrayOf("Ça chatouille !! 🤭", "Arrête, hihi !")
    override val PETTED = arrayOf("Hihi ! 💕", "Encore !", "J'adore les câlins", "Mmmh…", "Tu es gentil·le !", "*ronronne* (oui, les lapins aussi)")

    // --- Menu ---
    override val GETTING_UP = "*bâille* D'accord, je me lève…"
    override val NOT_TIRED = arrayOf("Je ne suis pas fatigué !", "Dodo ? Déjà ?!")

    // --- Voyage d'application en application ---
    override val FOLLOWING = arrayOf("J'arrive !", "Attends-moi !", "Hop, on change d'app !", "Je te suis ! 🐾")

    /**
     * Commentaires possibles à l'arrivée dans une app.
     * [category] : `ApplicationInfo.CATEGORY_*` (constantes de compilation, utilisables en JVM).
     */
    override fun appComments(label: String, pkg: String, category: Int, isHome: Boolean): Array<String> {
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
