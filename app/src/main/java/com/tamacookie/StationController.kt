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
class StationController(private val svc: BunnyService) {
    private val stations = EnumMap<StationKind, Station>(StationKind::class.java)
    /** Toutes les stations, dans l'ordre de [StationKind] (parcours sans allocation). */
    private lateinit var all: Array<Station>

    /** Station où se trouve le lapin (null = dehors). */
    var inStation: StationKind? = null
        private set
    private var hoverStation: StationKind? = null

    private var vetPanel: VetPanel? = null
    private var vetLp: WindowManager.LayoutParams? = null
    private var vetTimer = 0f
    private var stationsPanel: StationsPanel? = null
    private var stationsLp: WindowManager.LayoutParams? = null
    private var chestPanel: ChestPanel? = null
    private var chestLp: WindowManager.LayoutParams? = null
    private var chestOf: Station? = null

    // Le lapin percute le coffre : il s'ouvre et un objet en jaillit
    private var chestBumpCd = 0f
    private var chestLid = 0f
    private var chestPop: Station? = null
    /** Dernière fois qu'il s'est servi seul dans le coffre. */
    var selfServeAt = 0L

    private val d get() = svc.d

    operator fun get(k: StationKind): Station = stations[k]!!

    // =====================================================================
    //  Création, placement, positions sauvegardées
    // =====================================================================
    fun create() {
        val prefs = Prefs.of(svc)
        val list = ArrayList<Station>()
        StationKind.values().forEachIndexed { i, k ->
            val v = StationView(svc, k)
            val lp = svc.windows.params((k.wDp * d).toInt(), (k.hDp * d).toInt(), touchable = true)
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
        prefs.getFloat(Prefs.stationFx(k), -1f).let { if (it < 0) svc.screenW * (0.2f + 0.2f * i) else it * svc.screenW }

    private fun savedCy(prefs: SharedPreferences, k: StationKind): Float =
        prefs.getFloat(Prefs.stationFy(k), -1f).let { if (it < 0) svc.topY + (svc.groundY - svc.topY) * 0.32f else it * svc.groundY }

    /** Nouvelle taille d'écran : positions recalculées depuis les fractions sauvegardées. */
    fun reloadPositions() {
        val prefs = Prefs.of(svc)
        all.forEachIndexed { i, s -> placeStation(s, savedCx(prefs, s.kind, i), savedCy(prefs, s.kind)) }
    }

    private fun placeStation(s: Station, cx: Float, cy: Float) {
        val hw = s.lp.width / 2f; val hh = s.lp.height / 2f
        s.cx = cx.coerceIn(hw, max(hw, svc.screenW - hw))
        s.cy = cy.coerceIn(svc.topY + hh, max(svc.topY + hh, svc.groundY - hh))
        s.lp.x = (s.cx - hw).roundToInt(); s.lp.y = (s.cy - hh).roundToInt()
        if (s.shown) svc.windows.update(s.view, s.lp)
    }

    private fun saveAnchor(s: Station) {
        Prefs.of(svc).edit().putString(Prefs.stationAnchor(s.kind), s.anchor).apply()
    }

    private fun saveStationPos(s: Station) {
        Prefs.of(svc).edit()
            .putFloat(Prefs.stationFx(s.kind), s.cx / svc.screenW)
            .putFloat(Prefs.stationFy(s.kind), s.cy / svc.groundY).apply()
    }

    fun anchoredHere(k: StationKind): Boolean {
        val a = get(k).anchor
        return a != null && a == svc.pages.currentView
    }

    /** Place, montre ou cache chaque station selon la vue et le défilement. */
    fun layout() {
        val pages = svc.pages
        for (s in all) {
            val off = pages.visibleOffset(s.anchor)
            if (!off.isNaN()) {
                val x = (s.cx - s.lp.width / 2f + off).roundToInt()
                if (!s.shown) {
                    s.lp.x = x
                    svc.windows.add(s.view, s.lp); s.shown = true
                    if (off == 0f) { s.view.alpha = 0f; s.view.animate().alpha(1f).setDuration(160).start() } else s.view.alpha = 1f
                } else if (s.lp.x != x) {
                    s.lp.x = x; svc.windows.update(s.view, s.lp)
                }
            } else if (s.shown) {
                svc.windows.safeRemove(s.view); s.shown = false; s.view.highlight = false
            }
        }
    }

    /** Lapin dans la cage : il suit la cage (et disparaît avec elle). */
    fun followCage() {
        val cage = get(StationKind.CAGE)
        val pages = svc.pages
        val off = pages.visibleOffset(cage.anchor)
        val vis = !off.isNaN()
        val raw = pages.anchorOffset(cage.anchor)
        pages.bunnyShift = if (raw.isNaN()) 0f else raw
        if (!vis && !svc.bunnyAway) {
            svc.setBunnyAway(true)
        } else if (vis && svc.bunnyAway) {
            positionInStation(StationKind.CAGE)
            // le lapin doit rester SOUS la cage
            if (cage.shown) { svc.windows.safeRemove(cage.view); cage.shown = false }
            svc.setBunnyAway(false)
            svc.windows.add(cage.view, cage.lp); cage.shown = true
            if (off == 0f) svc.say(svc.pick("Tu es revenu ! 🥺", "Je suis toujours là… dans ma cage"), 2.5f)
        }
        pages.split(cage.anchor ?: "").second?.let { pages.bunnyPage = it }
    }

    fun detachAll() {
        for (s in all) if (s.shown) { svc.windows.safeRemove(s.view); s.shown = false }
    }

    /** Animation de la baignoire et rafraîchissement du panneau véto. */
    fun update(dt: Float) {
        val bath = get(StationKind.BATH)
        if (bath.shown) { bath.view.t += dt; bath.view.invalidate() }
        vetTimer += dt
        if (vetTimer > 1f) { vetTimer = 0f; vetPanel?.refresh() }
    }

    /** Une station animée est visible (baignoire, couvercle du coffre). */
    fun animating(): Boolean = get(StationKind.BATH).shown || chestLid > 0f || chestPop != null

    // =====================================================================
    //  Toucher : appui long pour déplacer, tap pour le coffre / une explication
    // =====================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun setupStationTouch(s: Station) {
        val slop = ViewConfiguration.get(svc).scaledTouchSlop.toFloat()
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
            if (forwarding) return@setOnTouchListener svc.bunnyTouch?.onTouch(svc.bunny, ev) ?: false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startCx = s.cx; startCy = s.cy
                    moving = false
                    svc.handler.postDelayed(longPress, 350)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (moving) placeStation(s, startCx + ev.rawX - downX, startCy + ev.rawY - downY)
                    else if (hypot(ev.rawX - downX, ev.rawY - downY) > slop) svc.handler.removeCallbacks(longPress)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    svc.handler.removeCallbacks(longPress)
                    val tap = ev.actionMasked == MotionEvent.ACTION_UP && hypot(ev.rawX - downX, ev.rawY - downY) < slop
                    if (moving) {
                        s.view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        saveStationPos(s)
                    } else if (tap && s.kind == StationKind.CHEST) {
                        openChest(s)
                    } else if (tap) {
                        svc.say(when (s.kind) {
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
        val hx = svc.fx; val hy = svc.fy - 45 * d
        var found: StationKind? = null
        for (s in all) if (s.shown && s.kind != StationKind.CHEST && s.contains(hx, hy, 12 * d)) { found = s.kind; break }
        if (found != hoverStation) {
            hoverStation?.let { get(it).view.highlight = false }
            hoverStation = found
            found?.let {
                get(it).view.highlight = true
                svc.bunny.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
            StationKind.BATH -> { svc.fx = s.cx; svc.fy = s.bottom - 14 * d }
            StationKind.VET -> { svc.fx = s.cx + 12 * d; svc.fy = s.bottom - 26 * d }
            StationKind.CAGE -> { svc.fx = s.cx; svc.fy = s.bottom - 16 * d }
            StationKind.CHEST -> {}
        }
        svc.placeBunny()
    }

    // =====================================================================
    //  Entrée / sortie
    // =====================================================================
    fun enterStation(k: StationKind, announce: Boolean = true) {
        if (k == StationKind.CHEST) return
        val s = get(k)
        svc.mischief.noteInteraction()
        // Une station utilisée est installée sur la vue courante
        val here = svc.pages.currentView
        if (here.isNotEmpty() && s.anchor != here) { s.anchor = here; saveAnchor(s) }
        svc.layoutAnchored()
        inStation = k
        s.view.occupied = true
        svc.platform = null; svc.phys.airLandY = null; svc.phys.stop()
        svc.toys.currentToy()?.held = false
        positionInStation(k)
        when (k) {
            StationKind.CHEST -> {}
            StationKind.BATH -> {
                svc.pet.sleeping = false
                svc.setAct(Act.BATH)
                if (announce) svc.say(svc.pick("Un bain ! 🫧", "Splash ! 💦", "Brrr, elle est froide !"))
            }
            StationKind.VET -> {
                svc.setAct(Act.VET)
                openVetPanel()
                if (announce) svc.say(svc.pick("Bonjour docteur… 😳", "J'ai peur des piqûres…", "Ça va être long ?"))
            }
            StationKind.CAGE -> {
                svc.pet.caged = true; svc.pet.save()
                svc.chatterIn = 20f
                svc.setAct(Act.CAGED)
                if (announce) {
                    svc.express(Expr.SAD, 2f)
                    svc.say(svc.pick("Hé ! Pourquoi la cage ?? 🥺", "Laisse-moi sortir ! 😢", "J'ai rien fait !"))
                }
                svc.updateNotification(force = true)
            }
        }
        svc.layoutAnchored()
    }

    fun leaveStation(hopOut: Boolean) {
        val k = inStation ?: return
        get(k).view.occupied = false
        inStation = null
        if (k == StationKind.VET) closeVetPanel()
        if (k == StationKind.CAGE) {
            svc.pet.caged = false; svc.pet.save()
            svc.express(Expr.HAPPY, 2f)
            svc.bunny.emit(PType.HEART, 3)
            svc.say(svc.pick("Libre !! 🎉", "Enfin dehors ! Merci !", "Je serai sage, promis 😇"))
            svc.updateNotification(force = true)
        }
        svc.layoutAnchored()
        if (hopOut) svc.jumpTo((svc.fx + (if (svc.fx < svc.screenW / 2) 90 else -90) * d).coerceIn(svc.minX, svc.maxX), svc.groundY, Act.IDLE)
        else svc.setAct(Act.IDLE, 2f)
    }

    /** Vrai (et il le dit) s'il est dans la baignoire / chez le véto / en cage, ou en train de se cacher. */
    fun busyInStation(): Boolean {
        if (svc.mischief.isHiding()) { svc.say("Je joue à cache-cache ! 🫣"); return true }
        val s = inStation ?: return false
        svc.say(when (s) {
            StationKind.CAGE -> svc.pick("Je ne peux pas sortir… 🥺", "Ouvre la cage d'abord !")
            StationKind.BATH -> "Attends, je prends mon bain ! 🫧"
            StationKind.VET -> "Je suis chez le docteur !"
            StationKind.CHEST -> ""
        })
        return true
    }

    /** Act.BATH */
    fun updateBath(dt: Float) {
        val bunny = svc.bunny
        bunny.pose = Pose.IDLE
        svc.express(Expr.HAPPY, 0.2f)
        if (svc.rnd.nextFloat() < dt * 14f) bunny.emit(PType.SPARKLE)
        if (svc.rnd.nextFloat() < dt * 6f) bunny.emit(PType.BUBBLE)
        if (svc.actTime > 4f) {
            svc.pet.clean()
            svc.say(svc.pick("Tout propre ! ✨", "Ça sent bon la fraise !", "Brrr… mais je brille !"))
            leaveStation(hopOut = true)
        }
    }

    /** Act.CAGED */
    fun updateCaged(dt: Float) {
        val pet = svc.pet
        svc.bunny.pose = if (pet.sleeping) Pose.SLEEP else Pose.SIT
        if (!pet.sleeping) {
            svc.express(Expr.SAD, 0.3f)
            svc.chatterIn -= dt
            if (svc.chatterIn <= 0f) {
                svc.chatterIn = 25f + svc.rnd.nextFloat() * 20f
                svc.say(svc.pick("Laisse-moi sortir… 🥺", "J'ai été sage, promis !", "C'est tout petit ici…",
                    "Snif… 😢", "Je m'ennuie dans ma cage"))
            }
        }
    }

    // =====================================================================
    //  Panneaux : stations (🏠), coffre, véto
    // =====================================================================
    /** Bouton 🏠 : panneau pour installer / ranger chaque objet sur la vue courante. */
    fun toggleStationsPanel() {
        svc.mischief.noteInteraction()
        if (stationsPanel != null) { closeStationsPanel(); return }
        svc.closeMenu()
        val pages = svc.pages
        val panel = StationsPanel(svc,
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
                    if (inStation == k) svc.say(if (k == StationKind.CAGE) "Je suis dedans ! 🥺" else "Attends, je suis dedans !")
                    else { s.anchor = null; saveAnchor(s) }
                } else {
                    if (inStation == k && k == StationKind.CAGE) {
                        svc.say("La cage reste où elle est… avec moi dedans 🥺")
                    } else {
                        if (inStation == k) leaveStation(hopOut = false)
                        s.anchor = pages.currentView; saveAnchor(s)
                    }
                }
                svc.layoutAnchored()
            },
            onClose = { closeStationsPanel() })
        val w = svc.windows.panelWidth(Tuning.STATIONS_PANEL_W_DP)
        val lp = svc.windows.panelParams(w, watchOutside = true)
        panel.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { closeStationsPanel(); true } else false
        }
        stationsPanel = panel; stationsLp = lp
        layoutStationsPanel()
        svc.windows.add(panel, lp)
    }

    private fun layoutStationsPanel() {
        val panel = stationsPanel ?: return
        val lp = stationsLp ?: return
        val win = svc.windows
        val w = win.panelWidth(Tuning.STATIONS_PANEL_W_DP)
        lp.width = w
        val h = win.measurePanel(panel, w)
        val dockLp = win.dockLp
        val dockCx = dockLp.x + dockLp.width / 2f
        lp.x = win.clampPanelX(if (dockCx > svc.screenW / 2) dockLp.x - w - 8 * d else dockLp.x + dockLp.width + 8 * d, w)
        lp.y = win.clampPanelY(dockLp.y + dockLp.height / 2f - h / 2f, h)
    }

    fun closeStationsPanel() {
        stationsPanel?.let { svc.windows.safeRemove(it) }
        stationsPanel = null; stationsLp = null
    }

    fun refreshStationsPanel() { stationsPanel?.refresh() }

    // --- Coffre : nourriture et jouets ---
    private fun openChest(s: Station) {
        svc.mischief.noteInteraction()
        closeChest()
        svc.closeMenu()
        s.view.open = true
        val panel = ChestPanel(svc, svc.pet,
            onFood = { f -> closeChest(); svc.toys.feed(f, from = s) },
            onToy = { t -> closeChest(); svc.toys.startPlay(t, from = s) },
            onClose = { closeChest() })
        val lp = svc.windows.panelParams(svc.windows.panelWidth(Tuning.CHEST_PANEL_W_DP), watchOutside = true)
        panel.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { closeChest(); true } else false
        }
        chestPanel = panel; chestLp = lp; chestOf = s
        layoutChestPanel()
        svc.windows.add(panel, lp)
    }

    private fun layoutChestPanel() {
        val panel = chestPanel ?: return
        val lp = chestLp ?: return
        val s = chestOf ?: return
        val win = svc.windows
        val w = win.panelWidth(Tuning.CHEST_PANEL_W_DP)
        lp.width = w
        val h = win.measurePanel(panel, w)
        // position de la station à l'écran (elle suit le défilement du bureau)
        val scx = s.lp.x + s.lp.width / 2f
        val scy = s.lp.y + s.lp.height / 2f
        lp.x = win.clampPanelX(scx - w / 2f, w)
        val above = scy - s.lp.height / 2f - h - 8 * d
        lp.y = win.clampPanelY(if (above > svc.topY) above else scy + s.lp.height / 2f + 8 * d, h)
    }

    fun closeChest() {
        chestPanel?.let { svc.windows.safeRemove(it) }
        chestPanel = null; chestLp = null
        chestOf?.view?.open = false
        chestOf = null
    }

    /** Le coffre s'il est visible sur la page du lapin (et pas déjà ouvert par l'utilisateur). */
    fun visibleChest(): Station? {
        val s = get(StationKind.CHEST)
        if (!s.shown || chestPanel != null || svc.pages.bunnyElsewhere()) return null
        if (s.lp.x + s.lp.width < 0 || s.lp.x > svc.screenW) return null
        return s
    }

    /** Act.GOTO_CHEST : il se place à côté du coffre et saute dedans pour se servir. */
    fun updateGoToChest(dt: Float) {
        val s = visibleChest()
        if (s == null || svc.toys.hasToyOrFood()) { svc.setAct(Act.IDLE, 2f); return }
        val cx = s.lp.x + s.lp.width / 2f - svc.pages.bunnyShift
        val side = if (svc.fx < cx) -1f else 1f
        if (svc.walkToward(cx + side * 110 * d, 140 * d, dt)) {
            svc.bunny.facing = -side
            svc.say(svc.pick("Je vais me servir ! 😋", "Hop, au coffre !", "Il y a sûrement des carottes là-dedans…",
                "Je sais où sont les jouets ! 🎁"), 1.8f)
            svc.jumpTo(cx, s.lp.y + s.lp.height * 0.6f, Act.IDLE)
        }
    }

    /** Collision du lapin (en vol) avec le coffre. */
    fun checkChestBump(dt: Float) {
        if (chestBumpCd > 0f) chestBumpCd -= dt
        if (chestLid > 0f) {
            chestLid -= dt
            if (chestLid <= 0f && chestPanel == null) get(StationKind.CHEST).view.open = false
        }
        val act = svc.act
        // L'objet sort une fois le lapin retombé (pas en plein vol)
        val pop = chestPop
        if (pop != null) {
            if (act != Act.AIR && act != Act.DRAGGED) { chestPop = null; popFromChest(pop) }
            return
        }
        if (act != Act.AIR || chestBumpCd > 0f) return
        val s = visibleChest() ?: return
        val bx = svc.fx + svc.pages.bunnyShift
        val fy = svc.fy
        val hw = 26 * d
        val l = s.lp.x.toFloat(); val t = s.lp.y.toFloat()
        val r = l + s.lp.width; val b = t + s.lp.height
        if (bx + hw < l || bx - hw > r || fy < t + 10 * d || fy - 95 * d > b) return
        // Boum !
        chestBumpCd = 3f
        s.view.open = true; chestLid = 1.6f
        val phys = svc.phys
        phys.vx = -phys.vx * 0.35f; phys.vy = max(phys.vy, 150 * d)
        phys.airLandY = null
        svc.bunny.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        svc.express(Expr.SURPRISED, 1f)
        if (svc.toys.hasToyOrFood()) {
            svc.say(svc.pick("Bonk ! 😵", "Aïe, le coffre !"), 1.5f)
        } else {
            chestPop = s
            svc.say(svc.pick("Bonk ! Oh, le coffre s'ouvre ! 🎁", "Oups, j'ai cogné le coffre !", "Sésame, ouvre-toi ! ✨"), 2f)
        }
    }

    /** Sort de quoi manger (s'il a faim) ou un jouet du coffre. */
    private fun popFromChest(s: Station) {
        if (!s.shown) return
        s.view.open = true; chestLid = 0.9f
        val pet = svc.pet
        val rnd = svc.rnd
        val keep = svc.mischief.lastInteraction            // ce n'est pas l'utilisateur qui s'est occupé de lui
        val glutton = pet.personality == Personality.GLUTTON && rnd.nextFloat() < 0.6f
        if (pet.satiety < 60f || glutton || pet.energy < 25f) {
            val f = when {
                pet.health < 60f -> Food.LETTUCE
                pet.happiness < 40f -> Food.STRAWBERRY
                rnd.nextFloat() < 0.7f -> Food.CARROT
                else -> Food.STRAWBERRY
            }
            svc.toys.feed(f, from = s)
        } else {
            val t = if (rnd.nextFloat() < 0.5f) pet.favoriteToy else Toy.values()[rnd.nextInt(Toy.values().size)]
            svc.toys.startPlay(t, from = s)
        }
        svc.mischief.lastInteraction = keep
    }

    // --- Véto ---
    private fun openVetPanel() {
        closeVetPanel()
        val pet = svc.pet
        val p = VetPanel(svc, pet,
            onHeal = {
                if (pet.health >= 90f) svc.say("Il va très bien ! ✅")
                else {
                    pet.heal()
                    svc.bunny.emit(PType.SPARKLE, 6)
                    svc.express(Expr.SAD, 1.5f)
                    svc.say(svc.pick("Aïe ! … merci docteur 💉", "Même pas mal ! (un peu)"))
                }
                vetPanel?.refresh()
            },
            onClose = { leaveStation(hopOut = true) })
        val lp = svc.windows.panelParams(svc.windows.panelWidth(Tuning.VET_PANEL_W_DP), watchOutside = false)
        vetPanel = p; vetLp = lp
        layoutVetPanel()
        svc.windows.add(p, lp)
    }

    private fun layoutVetPanel() {
        val p = vetPanel ?: return
        val lp = vetLp ?: return
        val s = get(StationKind.VET)
        val win = svc.windows
        val w = win.panelWidth(Tuning.VET_PANEL_W_DP)
        lp.width = w
        val h = win.measurePanel(p, w)
        val scx = s.lp.x + s.lp.width / 2f
        val scy = s.lp.y + s.lp.height / 2f
        val right = scx + s.lp.width / 2f + 12 * d
        lp.x = win.clampPanelX(if (right + w < svc.screenW) right else scx - s.lp.width / 2f - 12 * d - w, w)
        lp.y = win.clampPanelY(scy - h / 2f, h)
    }

    private fun closeVetPanel() {
        vetPanel?.let { svc.windows.safeRemove(it) }
        vetPanel = null; vetLp = null
    }

    /** Après "Montrer" : s'il est toujours chez le véto, le panneau revient (sinon il y resterait bloqué). */
    fun reopenVetPanelIfNeeded() {
        if (inStation == StationKind.VET && vetPanel == null) openVetPanel()
    }

    /** Nouvelle taille d'écran : les panneaux ouverts sont replacés dans l'écran. */
    fun repositionPanels() {
        val win = svc.windows
        layoutVetPanel(); vetPanel?.let { v -> vetLp?.let { win.update(v, it) } }
        layoutChestPanel(); chestPanel?.let { v -> chestLp?.let { win.update(v, it) } }
        layoutStationsPanel(); stationsPanel?.let { v -> stationsLp?.let { win.update(v, it) } }
    }

    fun closePanels() {
        closeVetPanel()
        closeStationsPanel()
        closeChest()
    }
}
