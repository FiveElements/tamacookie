package com.tamacookie

/**
 * Calculs purs (sans Android) liés aux "vues" et au défilement des pages du bureau.
 * Isolés ici pour être testés unitairement.
 */
object ViewKey {
    /** "pkg#p2" -> (pkg, 2) ; "pkg" -> (pkg, null). */
    fun split(v: String): Pair<String, Int?> {
        val i = v.lastIndexOf("#p")
        return if (i < 0) v to null else v.substring(0, i) to v.substring(i + 2).toIntOrNull()
    }

    fun of(pkg: String, page: Int?): String = if (page == null) pkg else "$pkg#p$page"
}

object ScrollMath {
    /** Durée maximale d'anticipation entre deux événements de défilement (~10 par seconde). */
    const val LEAD_MS = 110f
    /** Temps pour revenir sur la dernière position connue quand les événements s'arrêtent. */
    const val DECAY_MS = 100f

    /**
     * Position de défilement estimée à [now], extrapolée à partir des deux derniers événements
     * (x0 à t0, x1 à t1). Si aucun nouvel événement n'arrive (doigt immobile), l'estimation
     * revient sur x1 au lieu de rester en avance.
     */
    fun estimate(x0: Int, t0: Long, x1: Int, t1: Long, now: Long): Float {
        val dt = t1 - t0
        val vel = if (dt in 30..250) (x1 - x0).toFloat() / dt else 0f
        val ahead = (now - t1).toFloat().coerceAtLeast(0f)
        val lead = if (ahead <= LEAD_MS) ahead else LEAD_MS * (1f - (ahead - LEAD_MS) / DECAY_MS).coerceAtLeast(0f)
        return x1 + vel * lead
    }

    /** Une largeur de page apprise est plausible si elle est entre 60 % et ~100 % de l'écran. */
    fun plausiblePageWidth(w: Float, screenW: Float): Boolean = w in screenW * 0.6f..screenW * 1.02f

    /** Page (1-based) affichée pour une position de défilement donnée. */
    fun pageAt(scrollX: Float, pageW: Float, pageCount: Int): Int =
        (kotlin.math.round(scrollX / pageW).toInt() + 1).coerceIn(1, maxOf(1, pageCount))

    /** Décalage horizontal à l'écran d'un objet posé sur [page] (0 = page affichée, au repos). */
    fun offset(page: Int, pageW: Float, scrollX: Float): Float = (page - 1) * pageW - scrollX

    /**
     * Le défilement est "terminé" (et non un doigt immobile en plein glissé) :
     * les derniers pas de l'animation sont de quelques pixels seulement.
     */
    fun ended(x0: Int, x1: Int): Boolean = kotlin.math.abs(x1 - x0) <= 3
}
