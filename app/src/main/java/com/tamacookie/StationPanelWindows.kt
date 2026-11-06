package com.tamacookie

import android.content.Context
import com.tamacookie.feature.station.StationKind
import com.tamacookie.feature.station.StationPanels
import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.Toy

/** Adaptateur Android des panneaux des stations ([StationPanels]) : vues et placement à l'écran. */
class StationPanelWindows(
    private val context: Context,
    private val windows: OverlayWindows,
    private val arena: Arena,
) : StationPanels {
    private val d get() = arena.d
    private var vetAt: Station? = null
    private var chestAt: Station? = null

    /** Fiche du véto, à côté de la station (à droite si la place le permet). */
    private val vetPanel = FloatingPanel<VetPanel>(windows, WindowId.Panel("vet"), Tuning.VET_PANEL_W_DP, watchOutside = false) { lp, w, h ->
        val s = vetAt ?: return@FloatingPanel
        val scx = s.x + s.w / 2f
        val scy = s.y + s.h / 2f
        val right = scx + s.w / 2f + 12 * d
        lp.x = windows.clampPanelX(if (right + w < arena.screenW) right else scx - s.w / 2f - 12 * d - w, w)
        lp.y = windows.clampPanelY(scy - h / 2f, h)
    }

    /** Coffre ouvert, au-dessus de la station (dessous s'il n'y a pas la place) ; elle suit le défilement. */
    private val chestPanel = FloatingPanel<ChestPanel>(windows, WindowId.Panel("chest"), Tuning.CHEST_PANEL_W_DP, watchOutside = true) { lp, w, h ->
        val s = chestAt ?: return@FloatingPanel
        val scx = s.x + s.w / 2f
        val scy = s.y + s.h / 2f
        lp.x = windows.clampPanelX(scx - w / 2f, w)
        val above = scy - s.h / 2f - h - 8 * d
        lp.y = windows.clampPanelY(if (above > arena.topY) above else scy + s.h / 2f + 8 * d, h)
    }

    /** Panneau 🏠, à côté du bouton (du côté où il y a de la place). */
    private val stationsPanel = FloatingPanel<StationsPanel>(windows, WindowId.Panel("stations"), Tuning.STATIONS_PANEL_W_DP, watchOutside = true) { lp, w, h ->
        val size = windows.dockSize
        val dockX = windows.dockX; val dockY = windows.dockY
        val dockCx = dockX + size / 2f
        lp.x = windows.clampPanelX(if (dockCx > arena.screenW / 2) dockX - w - 8 * d else dockX + size + 8 * d, w)
        lp.y = windows.clampPanelY(dockY + size / 2f - h / 2f, h)
    }

    override val vetOpen get() = vetPanel.isOpen
    override val chestOpen get() = chestPanel.isOpen
    override val stationsOpen get() = stationsPanel.isOpen

    override fun openVet(pet: Pet, vet: Station, onHeal: () -> Unit, onClose: () -> Unit) {
        vetAt = vet
        vetPanel.open(VetPanel(context, pet, onHeal, onClose))
    }

    override fun refreshVet() { vetPanel.view?.refresh() }

    override fun closeVet() = vetPanel.close()

    override fun openChest(pet: Pet, chest: Station, onFood: (Food) -> Unit, onToy: (Toy) -> Unit, onClose: () -> Unit) {
        chestAt = chest
        chestPanel.open(ChestPanel(context, pet, onFood, onToy, onClose), onClose)
    }

    override fun closeChest() = chestPanel.close()

    override fun openStations(status: (StationKind) -> Pair<Int, String>, onToggle: (StationKind) -> Unit, onClose: () -> Unit) {
        stationsPanel.open(StationsPanel(context, status, onToggle, onClose), onClose)
    }

    override fun refreshStations() { stationsPanel.view?.refresh() }

    override fun closeStations() = stationsPanel.close()

    override fun reposition() {
        vetPanel.reposition()
        chestPanel.reposition()
        stationsPanel.reposition()
    }
}
