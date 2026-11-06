package com.tamacookie

/**
 * Port **`ScreenSensor`** (Kotlin pur) : ce que le cœur sait de l'écran des autres applications.
 *
 * - [snapshot] : état courant, **immuable** (plateformes, clavier, vue, pages et défilement du bureau) ;
 * - [takeSignals] : ce que l'utilisateur a fait depuis le dernier appel, **sans perte** (deux clics entre
 *   deux images = deux [UserSignal.Click]) — consommé une fois par image par les bêtises.
 *
 * Fil principal uniquement (adaptateur : [ScreenSensorHub], alimenté par `ScreenSensorService` ;
 * test : `FakeScreenSensor`).
 */
interface ScreenSensor {
    val snapshot: ScreenSnapshot

    /** Appelé (fil principal) à chaque nouvel état ; renvoie de quoi se désabonner. */
    fun observe(listener: (ScreenSnapshot) -> Unit): () -> Unit

    /** Signaux reçus depuis le dernier appel, dans l'ordre (la file est vidée). */
    fun takeSignals(): List<UserSignal>

    /** Largeur d'une page du bureau connue par ailleurs (mémorisée), 0 = l'oublier (rotation). */
    fun setPageWidth(w: Float)
}

/** Élément détecté à l'écran (icône, bouton…) ; [label] = nom court, jamais un texte saisi. */
data class Platform(val box: Box, val label: String?)

/** Plateformes d'un scan. Une nouvelle liste = une nouvelle [version] (l'animal vérifie s'il a perdu la sienne). */
class PlatformSet(val items: List<Platform>, val version: Int) {
    /** Rectangles seuls (pour la physique), calculés une fois par liste. */
    val boxes: List<Box> = items.map { it.box }
    fun isEmpty() = items.isEmpty()
    fun isNotEmpty() = items.isNotEmpty()

    companion object {
        val NONE = PlatformSet(emptyList(), 0)
    }
}

/** Deux derniers événements de défilement horizontal du bureau : x (px) à t (uptime ms). `t1 == 0` : aucun. */
data class ScrollSamples(val x0: Int = 0, val t0: Long = 0L, val x1: Int = 0, val t1: Long = 0L) {
    /** Nouvel échantillon : le dernier devient l'avant-dernier. */
    fun push(x: Int, t: Long) = ScrollSamples(x1, t1, x, t)
}

/** État de l'écran (immuable). Valeurs par défaut = rien de connu (service d'accessibilité absent). */
data class ScreenSnapshot(
    /** Service d'accessibilité connecté. */
    val connected: Boolean = false,
    /** Écran allumé (vrai par défaut). */
    val screenOn: Boolean = true,
    val platforms: PlatformSet = PlatformSet.NONE,
    /** Clavier affiché (position), null = pas de clavier. */
    val ime: Box? = null,
    /** Application au premier plan vue par l'accessibilité. */
    val foregroundPkg: String? = null,
    /** "Vue" courante : l'application, et pour le bureau la page ("pkg#p2"). */
    val viewKey: String? = null,
    /** Package du bureau, une fois vu avec des pages. */
    val launcherPkg: String? = null,
    /** Nombre de pages du bureau (0 = inconnu). */
    val pageCount: Int = 0,
    /** Largeur de défilement d'une page du bureau (apprise), 0 = inconnue. */
    val pageW: Float = 0f,
    val scroll: ScrollSamples = ScrollSamples(),
    /**
     * Bouton « Ignorer l'annonce » d'une publicité à l'écran (chasseur de pubs : liste blanche stricte de
     * [ScreenScan.isAdSkip]), null = aucun, ou réglage coupé (il n'est alors même pas cherché).
     */
    val adSkip: Box? = null,
)

/** Ce que fait l'utilisateur (jamais le contenu tapé : seulement « il tape »). */
sealed interface UserSignal {
    /** Un bouton touché (sa position à l'écran). */
    data class Click(val box: Box) : UserSignal
    /** Une frappe au clavier. */
    data object Typing : UserSignal
    /** Un défilement (n'importe quelle application). */
    data object Scroll : UserSignal
}

/**
 * Observable maison : une valeur courante + des écouteurs prévenus à chaque changement (`==`).
 * Pas de coroutines / Flow : tout se passe sur le fil principal, un appel direct suffit.
 */
class Observable<T>(initial: T) {
    var value: T = initial
        private set
    private val listeners = ArrayList<(T) -> Unit>()

    fun set(v: T) {
        if (v == value) return
        value = v
        if (listeners.isEmpty()) return
        for (l in listeners.toTypedArray()) l(v)
    }

    fun observe(listener: (T) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }
}
