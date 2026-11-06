package com.tamacookie

/**
 * Textes de la notification (Kotlin pur) : ligne des jauges, humeur du titre, alertes. Affichés par
 * `PetNotifier` (Android), repris par le widget ([com.tamacookie.widget.WidgetTexts]) et l'écran principal.
 */
object NotifierTexts {
    fun statusLine(satiety: Float, happiness: Float, energy: Float, cleanliness: Float): String =
        "🥕${satiety.toInt()}  💖${happiness.toInt()}  ⚡${energy.toInt()}  🛁${cleanliness.toInt()}"

    /** Fin du titre de la notification permanente (« Cookie <moodText> »). */
    fun moodText(mood: Mood, sleeping: Boolean, hiding: Boolean): String =
        if (hiding) "se cache… trouve-le sur une autre page ! 🫣" else when (mood) {
            Mood.HAPPY -> "est aux anges"
            Mood.OK -> "se promène"
            Mood.HUNGRY -> "a faim"
            Mood.SLEEPY -> if (sleeping) "dort" else "a sommeil"
            Mood.DIRTY -> "a besoin d'un bain"
            Mood.SAD -> "se sent seul"
            Mood.SICK -> "est malade"
        }

    /** Texte d'alerte pour une humeur, ou null si cette humeur ne mérite pas d'alerte. */
    fun alertText(name: String, mood: Mood): String? = when (mood) {
        Mood.HUNGRY -> "$name a faim ! 🥕"
        Mood.DIRTY -> "$name a besoin d'un bain 🫧"
        Mood.SICK -> "$name est malade 🤒"
        Mood.SAD -> "$name s'ennuie de toi 🥺"
        else -> null
    }
}
