package com.tamacookie.feature.menu

import com.tamacookie.Act
import com.tamacookie.BunnyView.Expr
import com.tamacookie.Lines
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.PetEvent
import com.tamacookie.PetStats

/** Ce que les entrées du menu peuvent demander, en plus de [PetCtx] (panneaux et jeux pilotés par le toucher). */
interface MenuCtx : PetCtx {
    /** Panneau 🏠 (installer / ranger les objets sur la vue courante). */
    fun toggleStationsPanel()
    /** Cache-cache lancé par l'utilisateur : il explique pourquoi s'il ne peut pas. */
    fun startHideSeek(): Boolean
}

/**
 * Une entrée du menu rapide (double toucher / appui long sur l'animal) : un bouton « emoji + libellé »
 * et son action. [refresh] donne le texte du bouton selon l'état de l'animal (relu chaque seconde) ;
 * null = texte fixe. [run] est appelé une fois le menu fermé.
 */
class MenuEntry(
    val emoji: String,
    val label: String,
    private val refresh: ((PetStats) -> String)? = null,
    val run: (ctx: MenuCtx, pet: Pet) -> Unit,
) {
    private val fixed = "$emoji\n$label"

    /** Le texte change selon l'état de l'animal. */
    val dynamic: Boolean get() = refresh != null

    /** Texte du bouton (emoji, retour à la ligne, libellé). */
    fun text(st: PetStats): String = refresh?.invoke(st) ?: fixed
}

/** Les entrées du menu, dans l'ordre d'affichage (deux par rangée). */
object MenuEntries {
    val SLEEP = MenuEntry("💤", "Dodo", refresh = { if (it.sleeping) "☀️\nRéveil" else "💤\nDodo" }) { ctx, pet ->
        val st = pet.stats
        if (st.sleeping) {
            ctx.on(pet, PetEvent.Woken(grumpy = false))
            pet.express(Expr.SLEEPY, 2f)
            ctx.say(Lines.GETTING_UP)
            pet.setActOrAfterAir(Act.IDLE, 3f)
        } else if (st.energy > 92f) {
            ctx.speech.sayOneOf(Lines.NOT_TIRED)
        } else ctx.startSleep(pet)
    }

    val STATIONS = MenuEntry("🏠", "Objets") { ctx, _ -> ctx.toggleStationsPanel() }

    val HIDE_SEEK = MenuEntry("🫣", "Cache-cache") { ctx, _ -> ctx.startHideSeek() }

    val HIDE = MenuEntry("🙈", "Ranger") { ctx, _ -> ctx.hide() }

    val ALL: List<MenuEntry> = listOf(SLEEP, STATIONS, HIDE_SEEK, HIDE)

    /** Boutons par rangée. */
    const val PER_ROW = 2
}
