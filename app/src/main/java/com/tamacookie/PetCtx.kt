package com.tamacookie

import android.content.SharedPreferences
import com.tamacookie.BunnyView.PType
import com.tamacookie.feature.ActRegistry
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

    // --- Jauges et sauvegarde (l'hôte est la seule autorité sur l'état des animaux) ---
    /**
     * Applique [event] aux jauges de [pet] ([PetRules]) ; s'il compte ([PetEvent.savesNow]) et n'est pas
     * refusé, l'animal est sauvegardé tout de suite. Seul chemin par lequel une fonctionnalité change les jauges.
     */
    fun on(pet: Pet, event: PetEvent): PetRules.Result {
        val r = pet.on(event, clock.wallMs(), rules)
        if (event.savesNow && !r.refused) save(pet)
        return r
    }
    /** Règles du jeu (taux, seuils) : [GameRules.DEFAULT] en jeu. */
    val rules: GameRules get() = GameRules.DEFAULT
    /** Sauvegarde [pet] (port `PetStore`). */
    fun save(pet: Pet)

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

    // --- Effets ponctuels (particules, retour haptique) : envoyés au rendu de l'animal ---
    fun emit(pet: Pet, type: PType, n: Int = 1)
    fun haptic(pet: Pet, feedback: Int)
    fun vibrate(pattern: LongArray)

    // --- Interactions entre fonctionnalités (questions au monde plutôt qu'appels de contrôleur) ---
    /** Dernière interaction de l'utilisateur (heure murale, ms) : les bêtises attendent qu'il l'oublie. */
    var lastInteraction: Long
    fun noteInteraction() { lastInteraction = clock.wallMs() }
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
    fun startSleep(pet: Pet)

    // --- Boucle, notification, menu ---
    fun requestFrames()
    fun updateNotification(force: Boolean)
    fun closeMenu()
    fun hide()

    // --- Fenêtres : toutes passent par [overlay] (ids par entité) ---
    /** La seule autorité sur les fenêtres overlay (`OverlayManager` ; faux hôte en test). */
    val overlay: OverlayHost
    fun applyView()
    fun layoutAnchored()
    /** Fenêtre de l'animal retirée (en cage sur une autre page, caché). */
    val bunnyAway: Boolean
    fun setBunnyAway(away: Boolean, fadeIn: Boolean = false)
    fun placeBunny()
    fun setDockHiding(hiding: Boolean)
}
