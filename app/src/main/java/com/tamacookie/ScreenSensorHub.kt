package com.tamacookie

import kotlin.math.abs

/**
 * Implémentation du port [ScreenSensor] (Kotlin pur, fil principal) : `ScreenSensorService` lui
 * **publie** ce qu'il voit (événements d'accessibilité, résultats de scan), le cœur lit [snapshot] et
 * consomme [takeSignals]. Tient l'état que se partageaient le service et `BunnyService` dans l'ancien
 * objet global `Platforms` (calage du défilement, largeur de page apprise, version des plateformes).
 *
 * Unique par processus ([AppGraph]).
 */
class ScreenSensorHub : ScreenSensor {
    private val state = Observable(ScreenSnapshot())
    override val snapshot: ScreenSnapshot get() = state.value

    private val signals = ArrayList<UserSignal>()
    private var platformVersion = 0

    /**
     * Incrémenté à chaque vidage des plateformes venu d'ailleurs que d'un scan (défilement, écran éteint) :
     * un scan lancé avant ne doit pas republier des plateformes qui ont bougé depuis.
     */
    var epoch = 0
        private set

    override fun observe(listener: (ScreenSnapshot) -> Unit) = state.observe(listener)

    override fun takeSignals(): List<UserSignal> {
        if (signals.isEmpty()) return emptyList()
        val out = ArrayList(signals)
        signals.clear()
        return out
    }

    override fun setPageWidth(w: Float) = update { copy(pageW = w) }

    private inline fun update(f: ScreenSnapshot.() -> ScreenSnapshot) = state.set(state.value.f())

    private fun signal(s: UserSignal) {
        if (signals.size >= MAX_SIGNALS) signals.removeAt(0)   // personne ne lit (boucle arrêtée) : on garde les plus récents
        signals += s
    }

    private fun ScreenSnapshot.withPlatforms(l: List<Platform>): ScreenSnapshot {
        if (l == platforms.items) return this
        return copy(platforms = PlatformSet(l, ++platformVersion))
    }

    // --- Ce que publie le service d'accessibilité ---

    fun onConnected(screenOn: Boolean) = update { copy(connected = true, screenOn = screenOn) }

    fun onDisconnected() {
        epoch++
        update { copy(connected = false, screenOn = true).withPlatforms(emptyList()) }
    }

    fun onScreenOff() {
        epoch++
        update { copy(screenOn = false).withPlatforms(emptyList()) }
    }

    fun onScreenOn() = update { copy(screenOn = true) }

    /**
     * Défilement (`TYPE_VIEW_SCROLLED`) : celui du bureau est suivi en direct ; tout défilement est un
     * signal et vide les plateformes (tout bouge, un scan suivra).
     */
    fun onViewScrolled(pkg: String?, scrollX: Int, scrollY: Int, deltaX: Int, now: Long) {
        epoch++
        update {
            val s = if (pkg != null && pkg == launcherPkg && scrollY == 0 && scrollX >= 0 &&
                (deltaX != 0 || scrollX != scroll.x1)
            ) copy(scroll = scroll.push(scrollX, now)) else this
            s.withPlatforms(emptyList())
        }
        signal(UserSignal.Scroll)
    }

    /** Un bouton touché ([box] = ses bornes, null ou vide : ignoré). */
    fun onClick(box: Box?) {
        if (box != null && !box.isEmpty) signal(UserSignal.Click(box))
    }

    fun onTyping() = signal(UserSignal.Typing)

    /**
     * Résultat d'un scan de l'arbre ([ScanResult]), lancé quand [epoch] valait [startedAt] ; [now] en
     * uptime ms. Ignoré écran éteint. Si les plateformes ont été vidées entre-temps, la vue et les pages
     * sont mises à jour mais pas les plateformes (le scan suivant les republiera).
     */
    fun onScan(r: ScanResult, startedAt: Int, now: Long) {
        if (!snapshot.screenOn) return
        val fresh = startedAt == epoch
        update {
            var s = copy(ime = r.ime)
            val pkg = r.pkg
            if (pkg == null) return@update s.withPlatforms(emptyList())
            s = s.copy(foregroundPkg = pkg)
            val pg = r.page
            if (pg != null) s = s.copy(pageCount = pg.second, launcherPkg = pkg)
            val page = pg?.first
            s = s.copy(viewKey = if (page != null) "$pkg#p$page" else pkg)
            if (page != null && pkg == s.launcherPkg) s = s.settle(page, r.screenW, now)
            if (fresh) s.withPlatforms(r.platforms) else s
        }
    }

    /** Bureau au repos sur [page] : apprendre la largeur d'une page et recaler le défilement. */
    private fun ScreenSnapshot.settle(page: Int, screenW: Int, now: Long): ScreenSnapshot {
        val sc = scroll
        // Au repos = l'animation s'est terminée (derniers pas de 0-3 px), pas un doigt
        // immobile au milieu d'un glissé (plus d'événements mais position intermédiaire).
        val ended = sc.t1 == 0L || ScrollMath.ended(sc.x0, sc.x1)
        val settled = ended && (sc.t1 == 0L || now - sc.t1 > 700)
        if (!settled) return this
        var pw = pageW
        if (page > 1 && sc.t1 != 0L && sc.x1 > 0) {
            val w = sc.x1.toFloat() / (page - 1)
            if (ScrollMath.plausiblePageWidth(w, screenW.toFloat()) && (pw <= 0f || abs(w - pw) > 4f)) pw = w
        }
        val expected = if (pw > 0f) ((page - 1) * pw).toInt() else if (page == 1) 0 else -1
        // ex. bouton Accueil qui ramène à la page 1 sans animation
        val sc2 = if (expected >= 0 && (sc.t1 == 0L || abs(sc.x1 - expected) > 8))
            sc.push(expected, now - 1000).push(expected, now - 1000) else sc
        return if (pw == pageW && sc2 == sc) this else copy(pageW = pw, scroll = sc2)
    }

    companion object {
        /** Signaux gardés au plus en attendant d'être lus. */
        const val MAX_SIGNALS = 64
    }
}

/**
 * Ce qu'a vu un scan (immuable, fabriqué sur le fil du scan) : [pkg] null = pas de fenêtre active lisible,
 * ou une fenêtre ignorée ([ScreenScan.ignoredPkg]) ; [screenW] = largeur de l'écran au moment du scan.
 */
data class ScanResult(
    val ime: Box?,
    val pkg: String?,
    val platforms: List<Platform> = emptyList(),
    val page: Pair<Int, Int>? = null,
    val screenW: Int = 0,
)
