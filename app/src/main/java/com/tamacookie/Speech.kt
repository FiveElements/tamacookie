package com.tamacookie

import com.tamacookie.species.Species
import com.tamacookie.species.SpeciesLines
import kotlin.random.Random

/**
 * Parole du lapin : tirage d'une réplique ([pickFrom], [pick]) et affichage dans la bulle ([say]).
 *
 * Kotlin pur : l'affichage est délégué à [show] (bulle de [OverlayWindows] + reprise de la boucle),
 * ce qui permet de tester les choix de répliques en JVM.
 */
class Speech(
    private val rnd: Random,
    private val show: (text: String, seconds: Float) -> Unit,
) {
    fun say(text: String, seconds: Float = DEFAULT_SECONDS) = show(text, seconds)

    /** Dit une réplique tirée au hasard dans [lines]. */
    fun sayOneOf(lines: Array<String>, seconds: Float = DEFAULT_SECONDS) = say(pickFrom(lines), seconds)

    fun pickFrom(lines: Array<String>): String = lines[rnd.nextInt(lines.size)]

    fun pick(vararg s: String): String = s[rnd.nextInt(s.size)]

    fun greeting(name: String, mood: Mood, hour: Int, lines: SpeciesLines = Species.DEFAULT.lines): String =
        lines.greeting(name, hungry = mood == Mood.HUNGRY, hour = hour)

    fun chatterLine(mood: Mood, lines: SpeciesLines = Species.DEFAULT.lines): String = pickFrom(lines.chatter(mood))

    fun appComment(
        label: String, pkg: String, category: Int, isHome: Boolean, lines: SpeciesLines = Species.DEFAULT.lines,
    ): String = pickFrom(lines.appComments(label, pkg, category, isHome))

    companion object {
        const val DEFAULT_SECONDS = 3.2f
    }
}
