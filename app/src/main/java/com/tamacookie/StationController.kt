package com.tamacookie

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.BunnyView.Pose
import java.util.EnumMap
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * "Widgets" flottants : baignoire, véto, cage et coffre (nourriture / jouets).
 * Chaque station est installée sur une vue (app ou page du bureau) ; on y dépose le lapin.
 */
class StationController(private val ctx: PetCtx) {
    private val stations = EnumMap<StationKind, Station>(StationKind::class.java)
    /** Toutes les stations, dans l'ordre de [StationKind] (parcours sans allocation). */
    private lateinit var all: Array<Station>

    /** Station où se trouve le lapin (null = dehors). */
    var inStation: StationKind? = null
        private set
    private var hoverStation: StationKind? = null

    private var vetTimer = 0f
    private var chestOf: Station? = null

    /** Fiche du véto, à côté de la station (à droite si la place le permet). */
    private val vetPanel = FloatingPanel<VetPanel>(ctx.windows, Tuning.VET_PANEL_W_DP, watchOutside = false) { lp, w, h ->
        val s = get(StationKind.VET)
        val win = ctx.windows
        val scx = s.lp.x + s.lp.width / 2f
        val scy = s.lp.y + s.lp.height / 2f
        val right = scx + s.lp.width / 2f + 12 * d
        lp.x = win.clampPanelX(if (right + w < arena.screenW) right else scx - s.lp.width / 2f - 12 * d - w, w)
        lp.y = win.clampPanelY(scy - h / 2f, h)
    }

    /** Coffre ouvert, au-dessus de la station (dessous s'il n'y a pas la place) ; elle suit le défilement. */
    private val chestPanel = FloatingPanel<ChestPanel>(ctx.windows, Tuning.CHEST_PANEL_W_DP, watchOutside = true) { lp, w, h ->
        val s = chestOf ?: return@FloatingPanel
        val win = ctx.windows
        val scx = s.lp.x + s.lp.width / 2f
        val scy = s.lp.y + s.lp.height / 2f
        lp.x = win.clampPanelX(scx - w / 2f, w)
        val above = scy - s.lp.height / 2f - h - 8 * d
        lp.y = win.clampPanelY(if (above > arena.topY) above else scy + s.lp.height / 2f + 8 * d, h)
    }

    /** Panneau 🏠, à côté du bouton (du côté où il y a de la place). */
    private val stationsPanel = FloatingPanel<StationsPanel>(ctx.windows, Tuning.STATIONS_PANEL_W_DP, watchOutside = true) { lp, w, h ->
        val win = ctx.windows
        val dockLp = win.dockLp
        val dockCx = dockLp.x + dockLp.width / 2f
        lp.x = win.clampPanelX(if (dockCx > arena.screenW / 2) dockLp.x - w - 8 * d else dockLp.x + dockLp.width + 8 * d, w)
        lp.y = win.clampPanelY(dockLp.y + dockLp.height / 2f - h / 2f, h)
    }

    // Le lapin percute le coffre : il s'ouvre et un objet en jaillit
    private var chestBumpCd = 0f
    private var chestLid = 0f
    private var chestPop: Station? = null
    /** Dernière fois qu'il s'est servi seul dans le coffre. */
    var selfServeAt = 0L

    private val arena get() = ctx.arena
    private val d get() = arena.d
    private val pet: Pet get() = ctx.pet

    operator fun get(k: StationKind): Station = stations[k]!!

    // =====================================================================
    //  Création, placement, positions sauvegardées
    // =====================================================================
    fun create() {
        val prefs = ctx.prefs
        val list = ArrayList<Station>()
        StationKind.values().forEachIndexed { i, k ->
            val v = StationView(ctx.context, k)
            val lp = ctx.windows.params((k.wDp * d).toInt(), (k.hDp * d).toInt(), touchable = true)
            val s = Station(k, v, lp)
            s.anchor = prefs.getString(Prefs.stationAnchor(k), null)
            placeStation(s, savedCx(prefs, k, i), savedCy(prefs, k))
            setupStationTouch(s)
            stations[k] = s
            list.add(s)
        }
        all = list.toTypedArray()
    }

    private fun savedCx(prefs: SharedPreferences, k: StationKind, i: Int): Float =
        prefs.getFloat(Prefs.stationFx(k), -1f).let { if (it < 0) arena.screenW * (0.2f + 0.2f * i) else it * arena.screenW }

    private fun savedCy(prefs: SharedPreferences, k: StationKind): Float =
        prefs.getFloat(Prefs.stationFy(k), -1f).let { if (it < 0) arena.topY + (arena.groundY - arena.topY) * 0.32f else it * arena.groundY }

    /** Nouvelle taille d'écran : positions recalculées depuis les fractions sauvegardées. */
    fun reloadPositions() {
        val prefs = ctx.prefs
        all.forEachIndexed { i, s -> placeStation(s, savedCx(prefs, s.kind, i), savedCy(prefs, s.kind)) }
    }

    private fun placeStation(s: Station, cx: Float, cy: Float) {
        val hw = s.lp.width / 2f; val hh = s.lp.height / 2f
        s.cx = cx.coerceIn(hw, max(hw, arena.screenW - hw))
        s.cy = cy.coerceIn(arena.topY + hh, max(arena.topY + hh, arena.groundY - hh))
        s.lp.x = (s.cx - hw).roundToInt(); s.lp.y = (s.cy - hh).roundToInt()
        if (s.shown) ctx.windows.update(s.view, s.lp)
    }

    private fun saveAnchor(s: Station) {
        ctx.prefs.edit().putString(Prefs.stationAnchor(s.kind), s.anchor).apply()
    }

    private fun saveStationPos(s: Station) {
        ctx.prefs.edit()
            .putFloat(Prefs.stationFx(s.kind), s.cx / arena.screenW)
            .putFloat(Prefs.stationFy(s.kind), s.cy / arena.groundY).apply()
    }

    fun anchoredHere(k: StationKind): Boolean {
        val a = get(k).anchor
        return a != null && a == ctx.pages.currentView
    }

    /** Place, montre ou cache chaque station selon la vue et le défilement. */
    fun layout() {
        val pages = ctx.pages
        for (s in all) {
            val off = pages.visibleOffset(s.anchor)
            if (!off.isNaN()) {
                val x = (s.cx - s.lp.width / 2f + off).roundToInt()
                if (!s.shown) {
                    s.lp.x = x
                    ctx.windows.add(s.view, s.lp); s.shown = true
                    if (off == 0f) { s.view.alpha = 0f; s.view.animate().alpha(1f).setDuration(160).start() } else s.view.alpha = 1f
                } else if (s.lp.x != x) {
                    s.lp.x = x; ctx.windows.update(s.view, s.lp)
                }
            } else if (s.shown) {
                ctx.windows.safeRemove(s.view); s.shown = false; s.view.highlight = false
            }
        }
    }

    /** Lapin dans la cage : il suit la cage (et disparaît avec elle). */
    fun followCage() {
        val cage = get(StationKind.CAGE)
        val pages = ctx.pages
        val off = pages.visibleOffset(cage.anchor)
        val vis = !off.isNaN()
        val raw = pages.anchorOffset(cage.anchor)
        pages.bunnyShift = if (raw.isNaN()) 0f else raw
        if (!vis && !ctx.bunnyAway) {
            ctx.setBunnyAway(true)
        } else if (vis && ctx.bunnyAway) {
            positionInStation(StationKind.CAGE)
            // le lapin doit rester SOUS la cage
            if (cage.shown) { ctx.windows.safeRemove(cage.view); cage.shown = false }
            ctx.setBunnyAway(false)
            ctx.windows.add(cage.view, cage.lp); cage.shown = true
            if (off == 0f) ctx.say(ctx.pick("Tu es revenu ! 🥺", "Je suis toujours là… dans ma cage"), 2.5f)
        }
        pages.split(cage.anchor ?: "").second?.let { pages.bunnyPage = it }
    }

    fun detachAll() {
        for (s in all) if (s.shown) { ctx.windows.safeRemove(s.view); s.shown = false }
    }

    /** Animation de la baignoire et rafraîchissement du panneau véto. */
    fun update(dt: Float) {
        val bath = get(StationKind.BATH)
        if (bath.shown) { bath.view.t += dt; bath.view.invalidate() }
        vetTimer += dt
        if (vetTimer > 1f) { vetTimer = 0f; vetPanel.view?.refresh() }
    }

    /** Une station animée est visible (baignoire, couvercle du coffre). */
    fun animating(): Boolean = get(StationKind.BATH).shown || chestLid > 0f || chestPop != null

    // =====================================================================
    //  Toucher : appui long pour déplacer, tap pour le coffre / une explication
    // =====================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun setupStationTouch(s: Station) {
        val slop = ViewConfiguration.get(ctx.context).scaledTouchSlop.toFloat()
        var downX = 0f; var downY = 0f; var startCx = 0f; var startCy = 0f
        var moving = false
        var forwarding = false
        val longPress = Runnable {
            moving = true
            s.view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            s.view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(100).start()
        }
        s.view.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) forwarding = s.kind == inStation
            // Lapin à l'intérieur : les touchers vont au lapin (pour le sortir / le caresser)
            if (forwarding) return@setOnTouchListener ctx.forwardTouch(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startCx = s.cx; startCy = s.cy
                    moving = false
                    s.view.postDelayed(longPress, 350)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (moving) placeStation(s, startCx + ev.rawX - downX, startCy + ev.rawY - downY)
                    else if (hypot(ev.rawX - downX, ev.rawY - downY) > slop) s.view.removeCallbacks(longPress)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    s.view.removeCallbacks(longPress)
                    val tap = ev.actionMasked == MotionEvent.ACTION_UP && hypot(ev.rawX - downX, ev.rawY - downY) < slop
                    if (moving) {
                        s.view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        saveStationPos(s)
                    } else if (tap && s.kind == StationKind.CHEST) {
                        openChest(s)
                    } else if (tap) {
                        ctx.say(when (s.kind) {
                            StationKind.BATH -> "Glisse-moi dans la baignoire pour me laver 🛁"
                            StationKind.VET -> "Dépose-moi chez le véto pour voir ma santé 🩺"
                            StationKind.CAGE -> "La cage… tu ne vas pas m'y mettre, hein ? 🥺"
                            StationKind.CHEST -> ""
                        } + "\n(appui long pour déplacer)", 3f)
                    }
                    moving = false
                }
            }
            true
        }
    }

    /** Pendant qu'on le glisse : surligne la station sous le lapin. */
    fun updateHover() {
        val hx = pet.body.fx; val hy = pet.body.fy - 45 * d
        var found: StationKind? = null
        for (s in all) if (s.shown && s.kind != StationKind.CHEST && s.contains(hx, hy, 12 * d)) { found = s.kind; break }
        if (found != hoverStation) {
            hoverStation?.let { get(it).view.highlight = false }
            hoverStation = found
            found?.let {
                get(it).view.highlight = true
                ctx.haptic(pet, HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    /** Fin du glisser : retourne la station visée (et enlève le surlignage). */
    fun takeHover(): StationKind? {
        val h = hoverStation
        h?.let { get(it).view.highlight = false }
        hoverStation = null
        return h
    }

    fun positionInStation(k: StationKind) {
        val s = get(k)
        when (k) {
            StationKind.BATH -> { pet.body.fx = s.cx; pet.body.fy = s.bottom - 14 * d }
            StationKind.VET -> { pet.body.fx = s.cx + 12 * d; pet.body.fy = s.bottom - 26 * d }
            StationKind.CAGE -> { pet.body.fx = s.cx; pet.body.fy = s.bottom - 16 * d }
            StationKind.CHEST -> {}
        }
        ctx.placeBunny()
    }

    // =====================================================================
    //  Entrée / sortie
    // =====================================================================
    fun enterStation(k: StationKind, announce: Boolean = true) {
        if (k == StationKind.CHEST) return
        val s = get(k)
        ctx.noteInteraction()
        // Une station utilisée est installée sur la vue courante
        val here = ctx.pages.currentView
        if (here.isNotEmpty() && s.anchor != here) { s.anchor = here; saveAnchor(s) }
        ctx.layoutAnchored()
        inStation = k
        s.view.occupied = true
        pet.body.platform = null; pet.body.phys.airLandY = null; pet.body.phys.stop()
        ctx.world.currentToy()?.held = false
        positionInStation(k)
        when (k) {
            StationKind.CHEST -> {}
            StationKind.BATH -> {
                pet.stats.sleeping = false
                pet.setAct(Act.BATH)
                if (announce) ctx.say(ctx.pick("Un bain ! 🫧", "Splash ! 💦", "Brrr, elle est froide !"))
            }
            StationKind.VET -> {
                pet.setAct(Act.VET)
                openVetPanel()
                if (announce) ctx.say(ctx.pick("Bonjour docteur… 😳", "J'ai peur des piqûres…", "Ça va être long ?"))
            }
            StationKind.CAGE -> {
                pet.stats.caged = true; pet.stats.save()
                pet.mind.chatterIn = 20f
                pet.setAct(Act.CAGED)
                if (announce) {
                    pet.express(Expr.SAD, 2f)
                    ctx.say(ctx.pick("Hé ! Pourquoi la cage ?? 🥺", "Laisse-moi sortir ! 😢", "J'ai rien fait !"))
                }
                ctx.updateNotification(force = true)
            }
        }
        ctx.layoutAnchored()
    }

    fun leaveStation(hopOut: Boolean) {
        val k = inStation ?: return
        get(k).view.occupied = false
        inStation = null
        if (k == StationKind.VET) closeVetPanel()
        if (k == StationKind.CAGE) {
            pet.stats.caged = false; pet.stats.save()
            pet.express(Expr.HAPPY, 2f)
            ctx.emit(pet, PType.HEART, 3)
            ctx.say(ctx.pick("Libre !! 🎉", "Enfin dehors ! Merci !", "Je serai sage, promis 😇"))
            ctx.updateNotification(force = true)
        }
        ctx.layoutAnchored()
        if (hopOut) ctx.actor.jumpTo(pet, (pet.body.fx + (if (pet.body.fx < arena.screenW / 2) 90 else -90) * d).coerceIn(arena.minX, arena.maxX), arena.groundY, Act.IDLE)
        else pet.setAct(Act.IDLE, 2f)
    }

    /** Vrai (et il le dit) s'il est dans la baignoire / chez le véto / en cage, ou en train de se cacher. */
    fun busyInStation(): Boolean {
        if (pet.isHiding) { ctx.say("Je joue à cache-cache ! 🫣"); return true }
        val s = inStation ?: return false
        ctx.say(when (s) {
            StationKind.CAGE -> ctx.pick("Je ne peux pas sortir… 🥺", "Ouvre la cage d'abord !")
            StationKind.BATH -> "Attends, je prends mon bain ! 🫧"
            StationKind.VET -> "Je suis chez le docteur !"
            StationKind.CHEST -> ""
        })
        return true
    }

    /** Act.BATH */
    fun updateBath(pet: Pet, dt: Float) {
        pet.body.pose = Pose.IDLE
        pet.express(Expr.HAPPY, 0.2f)
        if (ctx.rnd.nextFloat() < dt * 14f) ctx.emit(pet, PType.SPARKLE)
        if (ctx.rnd.nextFloat() < dt * 6f) ctx.emit(pet, PType.BUBBLE)
        if (pet.body.actTime > 4f) {
            pet.stats.clean()
            ctx.say(ctx.pick("Tout propre ! ✨", "Ça sent bon la fraise !", "Brrr… mais je brille !"))
            leaveStation(hopOut = true)
        }
    }

    /** Act.CAGED */
    fun updateCaged(pet: Pet, dt: Float) {
        val st = pet.stats
        pet.body.pose = if (st.sleeping) Pose.SLEEP else Pose.SIT
        if (!st.sleeping) {
            pet.express(Expr.SAD, 0.3f)
            pet.mind.chatterIn -= dt
            if (pet.mind.chatterIn <= 0f) {
                pet.mind.chatterIn = 25f + ctx.rnd.nextFloat() * 20f
                ctx.say(ctx.pick("Laisse-moi sortir… 🥺", "J'ai été sage, promis !", "C'est tout petit ici…",
                    "Snif… 😢", "Je m'ennuie dans ma cage"))
            }
        }
    }

    // =====================================================================
    //  Panneaux : stations (🏠), coffre, véto
    // =====================================================================
    /** Bouton 🏠 : panneau pour installer / ranger chaque objet sur la vue courante. */
    fun toggleStationsPanel() {
        ctx.noteInteraction()
        if (stationsPanel.isOpen) { closeStationsPanel(); return }
        ctx.closeMenu()
        val pages = ctx.pages
        val panel = StationsPanel(ctx.context,
            status = { k ->
                val s = get(k)
                when {
                    s.anchor == null -> 2 to ""
                    s.anchor == pages.currentView -> 0 to ""
                    else -> 1 to pages.viewLabel(s.anchor)
                }
            },
            onToggle = { k ->
                val s = get(k)
                if (s.anchor == pages.currentView) {
                    if (inStation == k) ctx.say(if (k == StationKind.CAGE) "Je suis dedans ! 🥺" else "Attends, je suis dedans !")
                    else { s.anchor = null; saveAnchor(s) }
                } else {
                    if (inStation == k && k == StationKind.CAGE) {
                        ctx.say("La cage reste où elle est… avec moi dedans 🥺")
                    } else {
                        if (inStation == k) leaveStation(hopOut = false)
                        s.anchor = pages.currentView; saveAnchor(s)
                    }
                }
                ctx.layoutAnchored()
            },
            onClose = { closeStationsPanel() })
        stationsPanel.open(panel) { closeStationsPanel() }
    }

    fun closeStationsPanel() = stationsPanel.close()

    fun refreshStationsPanel() { stationsPanel.view?.refresh() }

    // --- Coffre : nourriture et jouets ---
    private fun openChest(s: Station) {
        ctx.noteInteraction()
        closeChest()
        ctx.closeMenu()
        s.view.open = true
        val panel = ChestPanel(ctx.context, pet.stats,
            onFood = { f -> closeChest(); ctx.feed(f, from = s) },
            onToy = { t -> closeChest(); ctx.startPlay(t, from = s) },
            onClose = { closeChest() })
        chestOf = s
        chestPanel.open(panel) { closeChest() }
    }

    fun closeChest() {
        chestPanel.close()
        chestOf?.view?.open = false
        chestOf = null
    }

    /** Le coffre s'il est visible sur la page du lapin (et pas déjà ouvert par l'utilisateur). */
    fun visibleChest(): Station? {
        val s = get(StationKind.CHEST)
        if (!s.shown || chestPanel.isOpen || ctx.pages.bunnyElsewhere()) return null
        if (s.lp.x + s.lp.width < 0 || s.lp.x > arena.screenW) return null
        return s
    }

    /** Act.GOTO_CHEST : il se place à côté du coffre et saute dedans pour se servir. */
    fun updateGoToChest(pet: Pet, dt: Float) {
        val s = visibleChest()
        if (s == null || ctx.world.hasToyOrFood()) { pet.setAct(Act.IDLE, 2f); return }
        val cx = s.lp.x + s.lp.width / 2f - ctx.pages.bunnyShift
        val side = if (pet.body.fx < cx) -1f else 1f
        if (ctx.actor.walkToward(pet, cx + side * 110 * d, 140 * d, dt)) {
            pet.body.facing = -side
            ctx.say(ctx.pick("Je vais me servir ! 😋", "Hop, au coffre !", "Il y a sûrement des carottes là-dedans…",
                "Je sais où sont les jouets ! 🎁"), 1.8f)
            ctx.actor.jumpTo(pet, cx, s.lp.y + s.lp.height * 0.6f, Act.IDLE)
        }
    }

    /** Collision du lapin (en vol) avec le coffre. */
    fun checkChestBump(dt: Float) {
        if (chestBumpCd > 0f) chestBumpCd -= dt
        if (chestLid > 0f) {
            chestLid -= dt
            if (chestLid <= 0f && !chestPanel.isOpen) get(StationKind.CHEST).view.open = false
        }
        val act = pet.body.act
        // L'objet sort une fois le lapin retombé (pas en plein vol)
        val pop = chestPop
        if (pop != null) {
            if (act != Act.AIR && act != Act.DRAGGED) { chestPop = null; popFromChest(pop) }
            return
        }
        if (act != Act.AIR || chestBumpCd > 0f) return
        val s = visibleChest() ?: return
        val bx = pet.body.fx + ctx.pages.bunnyShift
        val fy = pet.body.fy
        val hw = 26 * d
        val l = s.lp.x.toFloat(); val t = s.lp.y.toFloat()
        val r = l + s.lp.width; val b = t + s.lp.height
        if (bx + hw < l || bx - hw > r || fy < t + 10 * d || fy - 95 * d > b) return
        // Boum !
        chestBumpCd = 3f
        s.view.open = true; chestLid = 1.6f
        val phys = pet.body.phys
        phys.vx = -phys.vx * 0.35f; phys.vy = max(phys.vy, 150 * d)
        phys.airLandY = null
        ctx.haptic(pet, HapticFeedbackConstants.KEYBOARD_TAP)
        pet.express(Expr.SURPRISED, 1f)
        if (ctx.world.hasToyOrFood()) {
            ctx.say(ctx.pick("Bonk ! 😵", "Aïe, le coffre !"), 1.5f)
        } else {
            chestPop = s
            ctx.say(ctx.pick("Bonk ! Oh, le coffre s'ouvre ! 🎁", "Oups, j'ai cogné le coffre !", "Sésame, ouvre-toi ! ✨"), 2f)
        }
    }

    /** Sort de quoi manger (s'il a faim) ou un jouet du coffre. */
    private fun popFromChest(s: Station) {
        if (!s.shown) return
        s.view.open = true; chestLid = 0.9f
        val st = pet.stats
        val rnd = ctx.rnd
        val keep = ctx.lastInteraction            // ce n'est pas l'utilisateur qui s'est occupé de lui
        val glutton = st.personality == Personality.GLUTTON && rnd.nextFloat() < 0.6f
        if (st.satiety < 60f || glutton || st.energy < 25f) {
            val f = when {
                st.health < 60f -> Food.LETTUCE
                st.happiness < 40f -> Food.STRAWBERRY
                rnd.nextFloat() < 0.7f -> Food.CARROT
                else -> Food.STRAWBERRY
            }
            ctx.feed(f, from = s)
        } else {
            val t = if (rnd.nextFloat() < 0.5f) st.favoriteToy else Toy.values()[rnd.nextInt(Toy.values().size)]
            ctx.startPlay(t, from = s)
        }
        ctx.lastInteraction = keep
    }

    // --- Véto ---
    private fun openVetPanel() {
        closeVetPanel()
        val st = pet.stats
        val p = VetPanel(ctx.context, st,
            onHeal = {
                if (st.health >= 90f) ctx.say("Il va très bien ! ✅")
                else {
                    st.heal()
                    ctx.emit(pet, PType.SPARKLE, 6)
                    pet.express(Expr.SAD, 1.5f)
                    ctx.say(ctx.pick("Aïe ! … merci docteur 💉", "Même pas mal ! (un peu)"))
                }
                vetPanel.view?.refresh()
            },
            onClose = { leaveStation(hopOut = true) })
        vetPanel.open(p)
    }

    private fun closeVetPanel() = vetPanel.close()

    /** Après "Montrer" : s'il est toujours chez le véto, le panneau revient (sinon il y resterait bloqué). */
    fun reopenVetPanelIfNeeded() {
        if (inStation == StationKind.VET && !vetPanel.isOpen) openVetPanel()
    }

    /** Nouvelle taille d'écran : les panneaux ouverts sont replacés dans l'écran. */
    fun repositionPanels() {
        vetPanel.reposition()
        chestPanel.reposition()
        stationsPanel.reposition()
    }

    fun closePanels() {
        closeVetPanel()
        closeStationsPanel()
        closeChest()
    }
}
