package com.tamacookie.widget

import com.tamacookie.A11yTexts
import com.tamacookie.Mood
import com.tamacookie.NotifierTexts
import com.tamacookie.PetStats
import com.tamacookie.species.Species

/**
 * Textes du widget d'écran d'accueil (évolution 4c, Kotlin pur) : ce qu'affiche [PetWidget], calculé à partir
 * de ce que le repository a sauvegardé. Reprend les textes de la notification ([NotifierTexts.moodText],
 * [NotifierTexts.statusLine]).
 */
object WidgetTexts {
    /** Une jauge du widget : emoji, nom (lu par l'accessibilité), valeur 0..100. */
    data class Gauge(val emoji: String, val label: String, val value: Int) {
        /** Lu par le lecteur d'écran : « Satiété 80 % » (étape 8d). */
        val description: String get() = A11yTexts.gauge(label, value.toFloat())
    }

    data class Content(
        /** « 😊 Cookie ». */
        val title: String,
        /** « Se promène » (humeur, comme la notification). */
        val subtitle: String,
        val gauges: List<Gauge>,
        /** Description compacte des jauges (= ligne de la notification). */
        val statusLine: String,
        /** Ligne d'état : vide quand il est là, sinon comment le faire venir. */
        val status: String,
    )

    const val STOPPED = "Rangé · « Appeler » pour le faire venir"

    /** Emoji d'humeur (le titre du widget). */
    fun moodEmoji(mood: Mood, sleeping: Boolean): String = when (mood) {
        Mood.HAPPY -> "😄"
        Mood.OK -> "🙂"
        Mood.HUNGRY -> "😋"
        Mood.SLEEPY -> if (sleeping) "😴" else "🥱"
        Mood.DIRTY -> "🫧"
        Mood.SAD -> "🥺"
        Mood.SICK -> "🤒"
    }

    /** Les quatre jauges de la notification, dans le même ordre, arrondies et bornées à 0..100. */
    fun gauges(s: PetStats): List<Gauge> = listOf(
        Gauge("🥕", A11yTexts.SATIETY, pct(s.satiety)),
        Gauge("💖", A11yTexts.HAPPINESS, pct(s.happiness)),
        Gauge("⚡", A11yTexts.ENERGY, pct(s.energy)),
        Gauge("🛁", A11yTexts.CLEANLINESS, pct(s.cleanliness)),
    )

    private fun pct(v: Float): Int = v.toInt().coerceIn(0, 100)

    /** Contenu pour l'animal [name] (jauges [s]) ; [running] : le service tourne (sinon il est « rangé »). */
    fun content(name: String, s: PetStats, running: Boolean): Content {
        val mood = NotifierTexts.moodText(s.mood, s.sleeping, hiding = false)
            .replaceFirstChar { it.uppercaseChar() } + if (s.caged) " (en cage 🔒)" else ""
        return Content(
            title = "${moodEmoji(s.mood, s.sleeping)} $name",
            subtitle = mood,
            gauges = gauges(s),
            statusLine = NotifierTexts.statusLine(s.satiety, s.happiness, s.energy, s.cleanliness),
            status = if (running) "" else STOPPED,
        )
    }

    /** Aucun animal enregistré (jamais lancé) : on invite à adopter un animal de l'espèce par défaut. */
    val NO_PET = Content(
        title = "${Species.DEFAULT.emoji} TamaCookie",
        subtitle = Species.DEFAULT.lines.ADOPT_PROMPT,
        gauges = emptyList(),
        statusLine = "",
        status = "",
    )
}

/**
 * Au plus une mise à jour par [intervalMs] (évolution 4c : le widget après une sauvegarde). Une demande trop
 * tôt n'est pas perdue : elle est **reportée** à la fin de l'intervalle (une seule en attente).
 */
class UpdateThrottle(private val intervalMs: Long = 60_000L) {
    private var last: Long? = null
    /** Une mise à jour reportée est programmée. */
    var pending = false
        private set

    /**
     * Demande une mise à jour à [now] : 0 = maintenant (elle est comptée), > 0 = à programmer dans ce délai
     * (puis [fired]), -1 = déjà programmée (rien à faire).
     */
    fun request(now: Long): Long {
        if (pending) return -1
        val l = last
        if (l == null || now - l >= intervalMs) { last = now; return 0 }
        pending = true
        return l + intervalMs - now
    }

    /** La mise à jour reportée vient d'être faite à [now]. */
    fun fired(now: Long) { pending = false; last = now }

    /** La mise à jour reportée est annulée (service arrêté). */
    fun cancel() { pending = false }
}
