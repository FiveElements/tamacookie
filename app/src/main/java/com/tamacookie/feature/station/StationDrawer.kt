package com.tamacookie.feature.station

import android.graphics.Canvas
import com.tamacookie.StationView

/**
 * Dessin d'une station (Android) : le comportement ([StationBehavior]) est dans le cœur, le dessin ici,
 * un `object …StationDrawer` par station. Il lit l'état affiché via la vue (`v.t`, `v.open`, `v.occupied`)
 * et ses pinceaux dans `v.pen`.
 */
interface StationDrawer {
    fun draw(c: Canvas, v: StationView, w: Float, h: Float)
}

/** Le dessin de chaque [StationKind] (`when` exhaustif : une nouvelle station ne compile pas sans son dessin). */
object StationDrawers {
    fun of(k: StationKind): StationDrawer = when (k) {
        StationKind.BATH -> BathStationDrawer
        StationKind.VET -> VetStationDrawer
        StationKind.CAGE -> CageStationDrawer
        StationKind.CHEST -> ChestStationDrawer
        StationKind.BED -> BedStationDrawer
        StationKind.BOWL -> BowlStationDrawer
        StationKind.WHEEL -> WheelStationDrawer
        StationKind.GARDEN -> GardenStationDrawer
    }
}
