package com.tamacookie

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.tamacookie.feature.station.StationKind

/**
 * Lecteur d'écran sur la fenêtre d'un animal (étape 8d) : chaque geste a son équivalent en action.
 *
 * - **Double toucher** (clic) = caresser ; **appui long** ou action « Ouvrir le menu » = le menu de soins
 *   (au doigt : double toucher ou appui long).
 * - « **Mettre dans <station>** » (chaque station affichée qui l'accepte, avec une place) : l'équivalent du
 *   glisser-déposer — il est attrapé ([Gestures.grab], comme au doigt) puis déposé ; « **Sortir de <station>** ».
 * - La description (« Cookie, lapin, content ») est calculée quand le lecteur d'écran la demande
 *   ([A11yTexts.pet]), jamais à chaque image.
 *
 * L'écouteur tactile de [TouchHandler] consomme tous les vrais touchers : le clic et l'appui long posés ici ne
 * viennent donc que du lecteur d'écran (ou d'un clavier / contacteur). Chaque action agit sur l'animal de la
 * fenêtre (animal courant le temps de l'action), comme réponse à l'utilisateur (bulle annoncée).
 */
class PetAccessibility(
    private val context: Context,
    private val windows: OverlayWindows,
    private val ctx: PetCtx,
    private val stations: StationController,
    private val gestures: Gestures,
) {
    /** Ce que font les gestes au doigt, appelé avec l'animal concerné pour animal courant. */
    interface Gestures {
        fun tap()
        fun menu()
        /** Il est attrapé (début d'un glisser). */
        fun grab()
    }

    private val pet: Pet get() = ctx.pet

    /** Rend la fenêtre [view] de l'animal [petOf] utilisable au lecteur d'écran. */
    fun attach(view: View, petOf: () -> Pet?) {
        fun run(block: () -> Unit) { petOf()?.let { asUser(it, block) } }
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        view.setOnClickListener { run { gestures.tap() } }
        view.setOnLongClickListener { run { gestures.menu() }; true }
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                run {
                    val here = ctx.world.stationOf(pet)
                    info.contentDescription = A11yTexts.pet(pet, here)
                    for (a in actions(here)) info.addAction(a)
                }
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                val k = A11yActionIds.kindOf(action)
                when {
                    action == R.id.access_action_menu -> run { gestures.menu() }
                    action == R.id.access_action_leave -> run { leave() }
                    k != null -> run { putIn(k) }
                    else -> return super.performAccessibilityAction(host, action, args)
                }
                return true
            }
        }
    }

    /** Caresse [p] (station occupée « touchée » au lecteur d'écran : la caresse va à l'occupant). */
    fun caress(p: Pet) = asUser(p) { gestures.tap() }

    private fun asUser(p: Pet, block: () -> Unit) {
        ctx.speech.at(SpeechPriority.HIGH) { ctx.world.asPet(p, block) }
    }

    /** Actions de l'animal courant ([here] : la station qu'il occupe). */
    private fun actions(here: StationKind?): List<AccessibilityAction> {
        val menu = context.getString(R.string.access_action_menu)
        val out = arrayListOf(
            AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.access_action_pet)),
            AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, menu),
            AccessibilityAction(R.id.access_action_menu, menu),
        )
        if (here != null) {
            val name = SpokenText.plain(here.behavior.name)
            out += AccessibilityAction(R.id.access_action_leave, context.getString(R.string.access_action_leave, name))
        }
        for (k in StationKind.entries) {
            if (k == here || !canPutIn(k)) continue
            val name = SpokenText.plain(k.behavior.name)
            out += AccessibilityAction(A11yActionIds.station(k), context.getString(R.string.access_action_put_in, name))
        }
        return out
    }

    /** On peut déposer l'animal courant dans [k] : station affichée, qui l'accepte, avec une place. */
    private fun canPutIn(k: StationKind): Boolean =
        windows.overlay.isAttached(WindowId.Station(k)) && pet.species.accepts(k) &&
            ctx.world.station(k).canEnter(pet.id)

    /** Glisser-déposer dans [k] : attrapé comme au doigt, puis déposé dans la station. */
    private fun putIn(k: StationKind) {
        if (!canPutIn(k)) return
        ctx.pages.rebaseToActivePage()
        gestures.grab()
        stations.enterStation(k)
    }

    private fun leave() {
        ctx.noteInteraction()
        stations.leaveStation(pet, hopOut = true)
    }
}
