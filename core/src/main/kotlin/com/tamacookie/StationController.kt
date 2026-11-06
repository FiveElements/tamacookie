package com.tamacookie

import com.tamacookie.feature.ActHandler
import com.tamacookie.feature.ActRegistry
import com.tamacookie.feature.station.ChestStation
import com.tamacookie.feature.station.StationBehavior
import com.tamacookie.feature.station.StationCtx
import com.tamacookie.feature.station.StationKind
import com.tamacookie.feature.station.StationPanels
import com.tamacookie.feature.station.VetStation
import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.Toy
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * "Widgets" flottants : baignoire, véto, cage et coffre (nourriture / jouets).
 * Chaque station est installée sur une vue (app ou page du bureau) ; on y dépose le lapin.
 *
 * Ce contrôleur gère l'état des stations ([Station] : placement, ancrage, surlignage, couvercle), leurs
 * fenêtres via [PetCtx.overlay], les panneaux via [panels], et l'entrée / la sortie ; ce qui est propre à
 * chaque station est dans son [StationBehavior] (`feature/station/`). Qui occupe quelle station est dans
 * le monde ([World.station], [World.stationOf]).
 *
 * Kotlin pur (testé en JVM) : les gestes sur les fenêtres des stations arrivent par [touchGoesToPet],
 * [dragStation], [dropStation], [tapStation] (adaptateur `EntityWindows`).
 */
class StationController(
    private val ctx: PetCtx,
    private val toys: ToyController,
    private val panels: StationPanels,
) {
    private val stations = EnumMap<StationKind, Station>(StationKind::class.java)
    /** Toutes les stations, dans l'ordre de [StationKind] (parcours sans allocation). */
    private lateinit var all: Array<Station>

    private var hoverStation: StationKind? = null

    private var vetTimer = 0f
    private var chestOf: Station? = null

    // Le lapin percute le coffre : il s'ouvre et un objet en jaillit
    private var chestBumpCd = 0f
    private var chestLid = 0f
    private var chestPop: Station? = null
    /** L'animal qui a percuté le coffre (l'objet sort quand il est retombé). */
    private var chestPopFor: PetId? = null
    /** Dernière fois qu'il s'est servi seul dans le coffre. */
    var selfServeAt = 0L

    private val arena get() = ctx.arena
    private val d get() = arena.d
    private val pet: Pet get() = ctx.pet
    private val overlay get() = ctx.overlay

    operator fun get(k: StationKind): Station = stations[k]!!

    /** Ce que reçoivent les comportements de station : la façade, plus la sortie et les panneaux. */
    private val sctx: StationCtx = object : StationCtx, PetCtx by ctx {
        override fun leaveStation(pet: Pet, hopOut: Boolean) = this@StationController.leaveStation(pet, hopOut)
        override fun openVetPanel(pet: Pet) = this@StationController.openVetPanel(pet)
        override fun closeVetPanel() = this@StationController.closeVetPanel()
        override fun openChest() = this@StationController.openChest(get(StationKind.CHEST))
    }

    /** [Act.IN_STATION] : la boucle et le toucher délèguent au comportement de la station occupée. */
    private inner class InStation(private val b: StationBehavior) : ActHandler {
        override val slowFrames get() = b.slowFrames
        override val travel get() = b.travel
        override fun update(pet: Pet, dt: Float) = b.update(sctx, pet, dt)
        override fun onPet(pet: Pet): Boolean { b.onPet(sctx, pet); return true }
    }

    private val handlers: Array<ActHandler> = Array(StationKind.entries.size) { InStation(StationKind.entries[it].behavior) }

    /** Gestionnaire de [Act.IN_STATION] : le comportement de la station occupée. */
    fun handlerFor(p: Pet): ActHandler? = ctx.world.stationOf(p)?.let { handlers[it.ordinal] }

    /** [Act.GOTO_CHEST] : il va se servir dans le coffre. */
    private val goingToChest = object : ActHandler {
        override fun update(pet: Pet, dt: Float) = updateGoToChest(pet, dt)
    }

    /** Dans une station, aller au coffre : la boucle et le toucher délèguent à ce contrôleur. */
    fun registerActs(acts: ActRegistry) {
        acts.register(Act.IN_STATION, ::handlerFor)
        acts.register(Act.GOTO_CHEST) { goingToChest }
    }

    /** Station qui garde [p] avec elle (la cage) : il la suit d'une vue à l'autre. */
    fun holdingStation(p: Pet): StationKind? = ctx.world.stationOf(p)?.takeIf { it.behavior.holdsPet }

    private fun shown(s: Station) = overlay.isAttached(s.windowId)

    private fun redraw(s: Station) = overlay.redraw(s.windowId)

    // =====================================================================
    //  Création, placement, positions sauvegardées
    // =====================================================================
    fun create() {
        val prefs = ctx.prefs
        val list = ArrayList<Station>()
        StationKind.values().forEachIndexed { i, k ->
            val s = Station(k, (k.wDp * d).toInt(), (k.hDp * d).toInt())
            s.anchor = prefs.getString(Prefs.stationAnchor(k), null)
            placeStation(s, savedCx(prefs, k, i), savedCy(prefs, k, i))
            stations[k] = s
            list.add(s)
        }
        all = list.toTypedArray()
    }

    /** Placement par défaut : quatre par rangée (les quatre premières : la rangée d'origine). */
    private fun savedCx(prefs: SettingsStore, k: StationKind, i: Int): Float =
        prefs.getFloat(Prefs.stationFx(k), -1f).let { if (it < 0) arena.screenW * (0.2f + 0.2f * (i % 4)) else it * arena.screenW }

    private fun savedCy(prefs: SettingsStore, k: StationKind, i: Int): Float =
        prefs.getFloat(Prefs.stationFy(k), -1f).let {
            if (it < 0) arena.topY + (arena.groundY - arena.topY) * (0.32f + 0.3f * (i / 4)) else it * arena.groundY
        }

    /** Nouvelle taille d'écran : positions recalculées depuis les fractions sauvegardées. */
    fun reloadPositions() {
        val prefs = ctx.prefs
        all.forEachIndexed { i, s -> placeStation(s, savedCx(prefs, s.kind, i), savedCy(prefs, s.kind, i)) }
    }

    private fun placeStation(s: Station, cx: Float, cy: Float) {
        val hw = s.w / 2f; val hh = s.h / 2f
        s.cx = cx.coerceIn(hw, max(hw, arena.screenW - hw))
        s.cy = cy.coerceIn(arena.topY + hh, max(arena.topY + hh, arena.groundY - hh))
        s.x = (s.cx - hw).roundToInt(); s.y = (s.cy - hh).roundToInt()
        overlay.move(s.windowId, s.x, s.y)
    }

    private fun saveAnchor(s: Station) {
        ctx.prefs.edit { putString(Prefs.stationAnchor(s.kind), s.anchor) }
    }

    private fun saveStationPos(s: Station) {
        ctx.prefs.edit {
            putFloat(Prefs.stationFx(s.kind), s.cx / arena.screenW)
            putFloat(Prefs.stationFy(s.kind), s.cy / arena.groundY)
        }
    }

    fun anchoredHere(k: StationKind): Boolean {
        val a = get(k).anchor
        return a != null && a == ctx.pages.currentView
    }

    /** Place, montre ou cache chaque station selon la vue et le défilement (fondu si elle apparaît sur place). */
    fun layout() {
        val pages = ctx.pages
        for (s in all) {
            val off = pages.visibleOffset(s.anchor)
            val was = shown(s)
            val x = overlay.placeAnchored(s.windowId, s.cx - s.w / 2f, s.y, off, fadeIn = off == 0f)
            if (x != null) s.x = x
            else if (was && s.highlight) { s.highlight = false; redraw(s) }
        }
    }

    /** [p] dans une station qui le garde ([StationBehavior.holdsPet]) : il la suit (et disparaît avec elle). */
    fun followHolder(k: StationKind, p: Pet = pet) {
        val st = get(k)
        val pages = ctx.pages
        val off = pages.visibleOffset(st.anchor)
        val vis = !off.isNaN()
        val raw = pages.anchorOffset(st.anchor)
        pages.bunnyShift = if (raw.isNaN()) 0f else raw
        if (!vis && !ctx.isAway(p)) {
            ctx.setAway(p, true)
        } else if (vis && ctx.isAway(p)) {
            positionInStation(p, k)
            // le lapin reste SOUS la station : ordre des couches garanti par l'OverlayHost (WindowLayer)
            ctx.setAway(p, false)
            k.behavior.onShownAgain(sctx, p, onScreen = off == 0f)
        }
        pages.split(st.anchor.orEmpty()).second?.let { pages.bunnyPage = it }
    }

    /** Animation des stations au décor animé (baignoire) et rafraîchissement du panneau véto. */
    fun update(dt: Float) {
        for (s in all) if (s.behavior.animated && shown(s)) { s.t += dt; redraw(s) }
        vetTimer += dt
        if (vetTimer > 1f) { vetTimer = 0f; panels.refreshVet() }
    }

    /** Une station animée est visible (baignoire, couvercle du coffre). */
    fun animating(): Boolean {
        for (s in all) if (s.behavior.animated && shown(s)) return true
        return chestLid > 0f || chestPop != null
    }

    // =====================================================================
    //  Gestes sur une station (reçus de sa fenêtre) : appui long pour déplacer, tap
    // =====================================================================
    /** Un animal est dedans : les touchers vont à lui (pour le sortir / le caresser). */
    fun touchGoesToPet(s: Station): Boolean = occupantOf(s) != null

    /** L'animal (du monde) qui occupe [s], null = vide. */
    fun occupantOf(s: Station): Pet? {
        for (q in ctx.world.pets.values) if (ctx.world.station(s.kind).has(q.id)) return q
        return null
    }

    /** Déplacement au doigt (après l'appui long) : centre en ([cx], [cy]). */
    fun dragStation(s: Station, cx: Float, cy: Float) = placeStation(s, cx, cy)

    /** Fin du déplacement : position sauvegardée. */
    fun dropStation(s: Station) = saveStationPos(s)

    /** Toucher bref : le coffre s'ouvre, les autres s'expliquent ([StationBehavior.onTap]). */
    fun tapStation(s: Station) = s.behavior.onTap(sctx)

    /** Pendant qu'on le glisse : surligne la station sous le lapin. */
    fun updateHover() {
        val hx = pet.body.fx; val hy = pet.body.fy - 45 * d
        var found: StationKind? = null
        for (s in all) if (shown(s) && pet.species.accepts(s.kind) && ctx.world.station(s.kind).canEnter(pet.id) && s.contains(hx, hy, 12 * d)) { found = s.kind; break }
        if (found != hoverStation) {
            hoverStation?.let { setHighlight(get(it), false) }
            hoverStation = found
            found?.let {
                setHighlight(get(it), true)
                ctx.haptic(pet, Haptic.KEYBOARD_TAP)
            }
        }
    }

    /** Fin du glisser : retourne la station visée (et enlève le surlignage). */
    fun takeHover(): StationKind? {
        val h = hoverStation
        h?.let { setHighlight(get(it), false) }
        hoverStation = null
        return h
    }

    private fun setHighlight(s: Station, on: Boolean) {
        if (s.highlight == on) return
        s.highlight = on
        redraw(s)
    }

    /** Assoit [p] à sa place dans la station ([StationBehavior.seatFor] : selon son rang d'arrivée). */
    fun positionInStation(p: Pet, k: StationKind) {
        val s = get(k)
        val rank = ctx.world.station(k).occupants.indexOfFirst { it.value == p.id.value }.coerceAtLeast(0)
        val seat = k.behavior.seatFor(rank)
        p.body.fx = s.cx + seat.dxDp * d; p.body.fy = s.bottom - seat.aboveBottomDp * d
        ctx.place(p)
    }

    /** Nouvelle taille d'écran : l'animal reprend sa place dans sa station. */
    fun repositionOccupant(p: Pet = pet) {
        ctx.world.stationOf(p)?.let { positionInStation(p, it) }
    }

    // =====================================================================
    //  Entrée / sortie
    // =====================================================================
    private fun setOccupied(s: Station) {
        s.occupied = !ctx.world.station(s.kind).isEmpty
        redraw(s)
    }

    fun enterStation(k: StationKind, announce: Boolean = true, p: Pet = pet) {
        val occ = ctx.world.station(k)
        if (!p.species.accepts(k) || !occ.canEnter(p.id)) return
        // Une seule station à la fois (ne devrait pas arriver : le glisser le fait sortir avant)
        ctx.world.stationOf(p)?.let { prev ->
            ctx.world.station(prev).leave(p.id); setOccupied(get(prev))
        }
        val s = get(k)
        ctx.noteInteraction()
        // Une station utilisée est installée sur la vue courante
        val here = ctx.pages.currentView
        if (here.isNotEmpty() && s.anchor != here) { s.anchor = here; saveAnchor(s) }
        ctx.layoutAnchored()
        occ.enter(p.id)
        setOccupied(s)
        p.body.platform = null; p.body.phys.airLandY = null; p.body.phys.stop()
        ctx.world.currentToy()?.held = false
        positionInStation(p, k)
        p.setAct(Act.IN_STATION)
        k.behavior.onEnter(sctx, p, announce)
        ctx.layoutAnchored()
    }

    /** Au démarrage : il retourne dans la station où il était (la cage, sauvegardée). */
    fun restore(p: Pet = pet) {
        for (k in StationKind.entries) if (k.behavior.restores(p)) { enterStation(k, announce = false, p = p); return }
    }

    fun leaveStation(p: Pet = pet, hopOut: Boolean) {
        val k = ctx.world.stationOf(p) ?: return
        ctx.world.station(k).leave(p.id)
        setOccupied(get(k))
        k.behavior.onLeave(sctx, p)
        ctx.layoutAnchored()
        if (hopOut) ctx.actor.jumpTo(p, (p.body.fx + (if (p.body.fx < arena.screenW / 2) 90 else -90) * d).coerceIn(arena.minX, arena.maxX), arena.groundY, Act.IDLE)
        else p.setAct(Act.IDLE, 2f)
    }

    // =====================================================================
    //  Panneaux : stations (🏠), coffre, véto
    // =====================================================================
    /** Bouton 🏠 : panneau pour installer / ranger chaque objet sur la vue courante. */
    fun toggleStationsPanel() {
        ctx.noteInteraction()
        if (panels.stationsOpen) { closeStationsPanel(); return }
        ctx.closeMenu()
        val pages = ctx.pages
        panels.openStations(
            status = { k ->
                val s = get(k)
                when {
                    s.anchor == null -> 2 to ""
                    s.anchor == pages.currentView -> 0 to ""
                    else -> 1 to pages.viewLabel(s.anchor)
                }
            },
            onToggle = { k -> toggleStation(k) },
            onClose = { closeStationsPanel() },
        )
    }

    /** Panneau 🏠 : installe [k] sur la vue courante, ou la range (sauf si l'animal ne peut pas en sortir). */
    fun toggleStation(k: StationKind) {
        val pages = ctx.pages
        val s = get(k)
        val b = k.behavior
        val inside = ctx.world.station(k).has(pet.id)
        if (s.anchor == pages.currentView) {
            if (inside) ctx.say(b.occupiedLine)
            else { s.anchor = null; saveAnchor(s) }
        } else {
            if (inside && b.holdsPet) {
                ctx.say(b.stayLine)
            } else {
                if (inside) leaveStation(pet, hopOut = false)
                s.anchor = pages.currentView; saveAnchor(s)
            }
        }
        ctx.layoutAnchored()
    }

    fun closeStationsPanel() = panels.closeStations()

    fun refreshStationsPanel() = panels.refreshStations()

    // --- Coffre : nourriture et jouets ---
    private fun setOpen(s: Station, open: Boolean) {
        s.open = open
        redraw(s)
    }

    private fun openChest(s: Station) {
        ctx.noteInteraction()
        closeChest()
        ctx.closeMenu()
        setOpen(s, true)
        chestOf = s
        panels.openChest(pet, s,
            onFood = { f -> closeChest(); toys.feed(f, from = s) },
            onToy = { t -> closeChest(); toys.startPlay(t, from = s) },
            onClose = { closeChest() })
    }

    fun closeChest() {
        panels.closeChest()
        chestOf?.let { setOpen(it, false) }
        chestOf = null
    }

    /** Le coffre s'il est visible sur la page du lapin (et pas déjà ouvert par l'utilisateur). */
    fun visibleChest(): Station? {
        val s = get(StationKind.CHEST)
        if (!shown(s) || panels.chestOpen || ctx.pages.bunnyElsewhere()) return null
        if (s.x + s.w < 0 || s.x > arena.screenW) return null
        return s
    }

    /** Act.GOTO_CHEST : il se place à côté du coffre et saute dedans pour se servir. */
    private fun updateGoToChest(pet: Pet, dt: Float) {
        val s = visibleChest()
        if (s == null || ctx.world.hasToyOrFood()) { pet.setAct(Act.IDLE, 2f); return }
        val cx = s.x + s.w / 2f - ctx.pages.bunnyShift
        val side = if (pet.body.fx < cx) -1f else 1f
        if (ctx.actor.walkToward(pet, cx + side * 110 * d, 140 * d, dt)) {
            pet.body.facing = -side
            ctx.say(ctx.pick(*pet.species.lines.GOING_TO_CHEST), 1.8f)
            ctx.actor.jumpTo(pet, cx, s.y + s.h * 0.6f, Act.IDLE)
        }
    }

    /** Couvercle et délai entre deux chocs : une fois par image, avant [checkChestBump] de chaque animal. */
    fun tickChest(dt: Float) {
        if (chestBumpCd > 0f) chestBumpCd -= dt
        if (chestLid > 0f) {
            chestLid -= dt
            if (chestLid <= 0f && !panels.chestOpen) setOpen(get(StationKind.CHEST), false)
        }
    }

    /** Collision de l'animal courant (en vol) avec le coffre. */
    fun checkChestBump() {
        val pet = pet
        val act = pet.body.act
        // L'objet sort une fois le lapin retombé (pas en plein vol) ; pendant ce temps, pas d'autre choc
        val pop = chestPop
        if (pop != null) {
            if (chestPopFor != pet.id) return
            if (act != Act.AIR && act != Act.DRAGGED) { chestPop = null; chestPopFor = null; popFromChest(pop) }
            return
        }
        if (act != Act.AIR || chestBumpCd > 0f) return
        val s = visibleChest() ?: return
        val bx = pet.body.fx + ctx.pages.bunnyShift
        val fy = pet.body.fy
        val hw = 26 * d
        val l = s.x.toFloat(); val t = s.y.toFloat()
        val r = l + s.w; val b = t + s.h
        if (bx + hw < l || bx - hw > r || fy < t + 10 * d || fy - 95 * d > b) return
        // Boum !
        chestBumpCd = 3f
        setOpen(s, true); chestLid = 1.6f
        val phys = pet.body.phys
        phys.vx = -phys.vx * 0.35f; phys.vy = max(phys.vy, 150 * d)
        phys.airLandY = null
        ctx.haptic(pet, Haptic.KEYBOARD_TAP)
        pet.express(Expr.SURPRISED, 1f)
        if (ctx.world.hasToyOrFood()) {
            ctx.say(ctx.pick(*ChestStation.BUMPED), 1.5f)
        } else {
            chestPop = s
            chestPopFor = pet.id
            ctx.say(ctx.pick(*ChestStation.BUMPED_OPEN), 2f)
        }
    }

    /** Sort de quoi manger (s'il a faim) ou un jouet du coffre. */
    private fun popFromChest(s: Station) {
        if (!shown(s)) return
        setOpen(s, true); chestLid = 0.9f
        val st = pet.stats
        val rnd = ctx.rnd
        val keep = ctx.lastInteraction            // ce n'est pas l'utilisateur qui s'est occupé de lui
        val greed = pet.species.behavior(pet.personality).chestGreedChance
        val glutton = greed > 0f && rnd.nextFloat() < greed
        val r = ctx.rules
        if (st.satiety < r.chestFoodSatietyBelow || glutton || st.energy < r.chestFoodEnergyBelow) {
            val f = when {
                st.health < r.chestLettuceHealthBelow -> Food.LETTUCE
                st.happiness < r.chestTreatJoyBelow -> Food.STRAWBERRY
                rnd.nextFloat() < 0.7f -> Food.CARROT
                else -> Food.STRAWBERRY
            }
            toys.feed(f, from = s)
        } else {
            val t = if (rnd.nextFloat() < 0.5f) pet.favoriteToy else Toy.values()[rnd.nextInt(Toy.values().size)]
            toys.startPlay(t, from = s)
        }
        ctx.lastInteraction = keep
    }

    // --- Véto ---
    private fun openVetPanel(pet: Pet) {
        closeVetPanel()
        panels.openVet(pet, get(StationKind.VET),
            onHeal = {
                if (pet.stats.health >= ctx.rules.healthyAbove) ctx.say(VetStation.HEALTHY)
                else {
                    ctx.on(pet, PetEvent.Healed)
                    ctx.emit(pet, PType.SPARKLE, 6)
                    pet.express(Expr.SAD, 1.5f)
                    ctx.say(ctx.pick(*VetStation.HEALED))
                }
                panels.refreshVet()
            },
            onClose = { leaveStation(pet, hopOut = true) })
    }

    private fun closeVetPanel() = panels.closeVet()

    /** Après "Montrer" : s'il est toujours chez le véto, le panneau revient (sinon il y resterait bloqué). */
    fun reopenVetPanelIfNeeded() {
        if (ctx.world.stationOf(pet) == StationKind.VET && !panels.vetOpen) openVetPanel(pet)
    }

    /** Nouvelle taille d'écran : les panneaux ouverts sont replacés dans l'écran. */
    fun repositionPanels() = panels.reposition()

    fun closePanels() {
        closeVetPanel()
        closeStationsPanel()
        closeChest()
    }
}
