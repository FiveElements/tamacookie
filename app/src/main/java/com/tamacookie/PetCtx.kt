package com.tamacookie

import android.content.Context
import android.content.SharedPreferences
import android.view.MotionEvent
import com.tamacookie.BunnyView.PType
import com.tamacookie.feature.ActRegistry
import com.tamacookie.feature.toy.Food
import kotlin.random.Random

/**
 * Façade étroite que reçoivent les fonctionnalités (contrôleurs) au lieu du service entier.
 * Implémentée par [BunnyService] ; `FakePetCtx` en test.
 *
 * Une fonctionnalité agit sur l'animal qu'on lui passe (ou [pet], l'animal courant) et interroge le
 * monde ; elle n'appelle pas directement un autre contrôleur (sauf exceptions documentées dans CLAUDE.md).
 */
interface PetCtx {
    // --- Monde ---
    val world: World
    /** L'animal courant (le résident tant qu'il n'y en a qu'un). */
    val pet: Pet get() = world.resident
    val arena: Arena
    val actor: BunnyActor
    val clock: Clock
    /** Aléatoire partagé (répliques, objets). Le cerveau de chaque animal a le sien ([Mind.rnd]). */
    val rnd: Random
    val prefs: SharedPreferences
    /** Vues / pages du bureau et défilement (où se trouve chaque chose). */
    val pages: PageTracker
    /** Quelle fonctionnalité prend en charge l'activité courante d'un animal (stations…). */
    val acts: ActRegistry

    // --- État global ---
    /** Tout est masqué (« Cacher ») : aucune fenêtre. */
    val hidden: Boolean
    fun foregroundPkg(): String?
    /** Nom lisible d'une application (null si inconnue). */
    fun appLabel(pkg: String): String?

    // --- Paroles ---
    val speech: Speech
    fun say(text: String, seconds: Float = Speech.DEFAULT_SECONDS)
    fun pick(vararg s: String): String

    // --- Effets visibles (passent par la vue tant qu'il n'y a pas de RenderState : étape 4) ---
    fun emit(pet: Pet, type: PType, n: Int = 1)
    fun haptic(pet: Pet, feedback: Int)
    fun vibrate(pattern: LongArray)
    /** Ce qu'il a dans la bouche pendant qu'il mange. */
    fun showFood(pet: Pet, food: Food)

    // --- Interactions entre fonctionnalités (questions au monde plutôt qu'appels de contrôleur) ---
    /** Dernière interaction de l'utilisateur (heure murale, ms). */
    var lastInteraction: Long
    fun noteInteraction()
    /**
     * Vrai (et il le dit) s'il est occupé : à cache-cache ou dans une station ([World.busy]).
     * La réplique vient du comportement de la station ([com.tamacookie.feature.station.StationBehavior.busyLine]).
     */
    fun sayIfBusy(pet: Pet = this.pet): Boolean {
        if (pet.isHiding) { say("Je joue à cache-cache ! 🫣"); return true }
        val k = world.stationOf(pet) ?: return false
        say(k.behavior.busyLine(this))
        return true
    }
    fun endHiding()
    fun startSleep(pet: Pet)

    // --- Boucle, notification, menu ---
    fun requestFrames()
    fun updateNotification(force: Boolean)
    fun closeMenu()
    fun hide()

    // --- Fenêtres et vues (→ OverlayManager à l'étape 4) ---
    fun applyView()
    fun layoutAnchored()
    /** Fenêtre de l'animal retirée (en cage sur une autre page, caché). */
    val bunnyAway: Boolean
    fun setBunnyAway(away: Boolean, fadeIn: Boolean = false)
    fun placeBunny()
    /** Transmet un geste à la fenêtre de l'animal (la cage lui passe les touchers). */
    fun forwardTouch(ev: MotionEvent): Boolean
    fun setDockHiding(hiding: Boolean)
    /** Contexte Android (vues, panneaux) : couplage restant des contrôleurs qui créent des fenêtres. */
    val context: Context
    val windows: OverlayWindows
}
