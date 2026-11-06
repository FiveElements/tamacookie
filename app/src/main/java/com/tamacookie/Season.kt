package com.tamacookie

import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId

/**
 * Thème saisonnier (évolution 4b) : un **ajout du rendu** et de quelques répliques, jamais une modification
 * de l'apparence sauvegardée ([Look]). Calculé depuis l'heure murale ([Clock]) et le fuseau injectés.
 *
 * Périodes (bornes incluses, chaque année) : [CHRISTMAS] du 1er décembre au 6 janvier, [HALLOWEEN] du
 * 24 octobre au 2 novembre, [SPRING] du 20 mars au 30 avril ; [NONE] le reste du temps.
 */
enum class Season(
    /** Particule qu'il fait tomber autour de lui pendant la saison (null = aucune). */
    val particle: PType?,
    /** Premier et dernier jour (inclus). La période peut enjamber le 1er janvier. */
    private val from: MonthDay?,
    private val to: MonthDay?,
) {
    NONE(null, null, null),
    SPRING(PType.PETAL, MonthDay.of(3, 20), MonthDay.of(4, 30)),
    HALLOWEEN(null, MonthDay.of(10, 24), MonthDay.of(11, 2)),
    CHRISTMAS(PType.SNOW, MonthDay.of(12, 1), MonthDay.of(1, 6)),
    ;

    private fun contains(d: MonthDay): Boolean {
        val a = from ?: return false
        val b = to ?: return false
        return if (a <= b) d in a..b else d >= a || d <= b
    }

    companion object {
        /** La saison du jour [date] (Kotlin pur). */
        fun of(date: LocalDate): Season {
            val d = MonthDay.from(date)
            return entries.firstOrNull { it.contains(d) } ?: NONE
        }

        /** La saison à l'heure murale [wallMs] dans le fuseau [zone]. */
        fun at(wallMs: Long, zone: ZoneId): Season = of(Instant.ofEpochMilli(wallMs).atZone(zone).toLocalDate())
    }
}

/**
 * La saison affichée, recalculée au plus une fois par [RECHECK_MS] (la boucle la lit à chaque image) ;
 * [enabled] = réglage `Prefs.SEASONAL` (faux : toujours [Season.NONE]).
 */
class SeasonalTheme(
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    var enabled = true

    private var cached = Season.NONE
    private var checkedAt = Long.MIN_VALUE

    fun current(): Season {
        if (!enabled) return Season.NONE
        val now = clock.wallMs()
        // l'heure murale peut reculer (réglage de l'horloge) : on recalcule aussi dans ce cas
        if (checkedAt == Long.MIN_VALUE || now - checkedAt >= RECHECK_MS || now < checkedAt) {
            cached = Season.at(now, zone())
            checkedAt = now
        }
        return cached
    }

    companion object {
        const val RECHECK_MS = 60_000L
    }
}
