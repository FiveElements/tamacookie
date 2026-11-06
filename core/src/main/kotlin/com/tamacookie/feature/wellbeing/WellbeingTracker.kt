package com.tamacookie.feature.wellbeing

import com.tamacookie.Clock
import com.tamacookie.GameRules

/**
 * Bien-être numérique (évolution 4c, Kotlin pur) : temps passé **d'affilée** dans la même application.
 *
 * Alimenté par l'app au premier plan déjà connue du service ([observe], à chaque relevé), il annonce des
 * [Cue] : une pause proposée à [GameRules.breakSuggestAfterMin], une bouderie à [GameRules.breakSulkAfterMin]
 * (qui dure jusqu'à un changement d'app — [Cue.RELIEVED] — ou une caresse — [Cue.MADE_UP]).
 * Jamais de blocage, jamais de jauge : il ne fait que proposer.
 *
 * - [exempt] : notre app, le lanceur — n'y compte pas, et y aller **remet à zéro** (on a quitté l'app) ;
 * - [transient] : le volet de notifications — ignoré (le compte continue derrière) ; une réponse nulle aussi ;
 * - écran éteint, lapin caché, réglage coupé : [reset].
 *
 * Temps : [Clock.uptimeMs] (monotone ; l'écran éteint remet de toute façon à zéro).
 */
class WellbeingTracker(
    private val clock: Clock,
    private val rules: GameRules = GameRules.DEFAULT,
    private val exempt: (String) -> Boolean = { false },
    private val transient: (String) -> Boolean = { false },
) {
    enum class Cue {
        /** Seuil de pause atteint : il bâille, s'étire, propose une pause. */
        BREAK,
        /** Seuil de bouderie atteint : il boude. */
        SULK,
        /** Caressé pendant qu'il boudait : il pardonne (le compte repart de zéro). */
        MADE_UP,
        /** On a changé d'app pendant qu'il boudait : il est content. */
        RELIEVED,
    }

    /** Réglage (`Prefs.WELLBEING`) ; le couper remet à zéro et rend le suivi muet. */
    var enabled: Boolean = false
        set(v) { if (field != v) { field = v; reset() } }

    /** App suivie (null : aucune, ou une app qui ne compte pas). */
    var pkg: String? = null
        private set
    private var since = 0L
    private var suggested = false

    /** Il boude (maintenir son expression) jusqu'à [Cue.MADE_UP] ou [Cue.RELIEVED]. */
    var sulking = false
        private set

    private val breakMs get() = rules.breakSuggestAfterMin * 60_000L
    private val sulkMs get() = rules.breakSulkAfterMin * 60_000L

    /** Temps passé d'affilée dans [pkg] (0 sans app suivie). */
    val elapsedMs: Long get() = if (pkg == null) 0L else clock.uptimeMs() - since

    /**
     * Oublie l'app suivie (écran éteint, caché, réglage changé). Retourne [Cue.RELIEVED] s'il boudait
     * (l'appelant choisit d'en parler ou non).
     */
    fun reset(): Cue? {
        val was = sulking
        pkg = null; suggested = false; sulking = false
        return if (was) Cue.RELIEVED else null
    }

    /** App au premier plan à ce relevé ([foreground] null : pas de réponse). Retourne le signal du moment. */
    fun observe(foreground: String?): Cue? {
        if (!enabled) return null
        if (foreground == null || transient(foreground)) return check()
        if (exempt(foreground)) return reset()
        if (foreground != pkg) {
            val r = reset()
            pkg = foreground
            since = clock.uptimeMs()
            return r
        }
        return check()
    }

    /** Seuils franchis depuis le dernier appel (chaque signal n'est annoncé qu'une fois par séance). */
    fun check(): Cue? {
        if (!enabled || pkg == null) return null
        val t = clock.uptimeMs() - since
        if (!sulking && t >= sulkMs) { sulking = true; suggested = true; return Cue.SULK }
        if (!suggested && t >= breakMs) { suggested = true; return Cue.BREAK }
        return null
    }

    /** Caressé : s'il boudait, il pardonne et le compte repart de zéro (dans la même app). */
    fun onPetted(): Cue? {
        if (!enabled || !sulking) return null
        sulking = false; suggested = false
        since = clock.uptimeMs()
        return Cue.MADE_UP
    }
}
