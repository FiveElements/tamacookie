package com.tamacookie

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.WindowInsets
import android.view.WindowManager
import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.BunnyView.Pose
import com.tamacookie.feature.ActRegistry
import com.tamacookie.feature.Travel
import com.tamacookie.persist.Adoption
import com.tamacookie.persist.PetRecord
import com.tamacookie.persist.PetRepository
import com.tamacookie.persist.Pets
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

/**
 * Service de premier plan qui fait vivre Cookie en surimpression sur l'écran.
 *
 * Orchestrateur : cycle de vie, notification, boucle d'animation, machine à états [Act]
 * et câblage des domaines. Possède le [World] (l'animal résident et les objets) et implémente
 * [PetCtx], la façade que reçoivent les contrôleurs :
 * [BunnyPhysics] (vol), [BunnyBrain] (décisions), [BunnyActor] (gestes), [PageTracker] (vues / pages du bureau),
 * [OverlayManager] (toutes les fenêtres, ids par entité), [OverlayWindows] (bulles, dock, panneaux),
 * [StationController] (bain, véto, cage, coffre),
 * [ToyController] (jouets, nourriture, crottes), [MischiefController] (bêtises, cache-cache).
 */
class BunnyService : Service(), Choreographer.FrameCallback, PetCtx {

    companion object {
        const val ACTION_FEED = "com.tamacookie.FEED"
        const val ACTION_TOGGLE = "com.tamacookie.TOGGLE"
        const val ACTION_STOP = "com.tamacookie.STOP"
        const val ACTION_RELOAD = "com.tamacookie.RELOAD"
        private const val TAG = "TamaCookie"

        @Volatile var running = false
            private set

        fun start(ctx: Context) {
            Prefs.of(ctx).edit().putBoolean(Prefs.ENABLED, true).apply()
            ctx.startForegroundService(Intent(ctx, BunnyService::class.java))
        }

        fun send(ctx: Context, action: String) {
            if (running) ctx.startService(Intent(ctx, BunnyService::class.java).setAction(action))
        }

        fun stop(ctx: Context) {
            Prefs.of(ctx).edit().putBoolean(Prefs.ENABLED, false).apply()
            ctx.stopService(Intent(ctx, BunnyService::class.java))
        }
    }

    // --- Monde ---
    override val world = World()
    override val arena = Arena()
    override val actor = BunnyActor(arena)
    override val clock: Clock = AndroidClock
    override val rnd = Random(System.nanoTime())
    /** L'animal résident (seul animal du monde pour l'instant). */
    private lateinit var resident: Pet
    /** Seule classe qui lit / écrit les animaux ; le service en est l'autorité tant qu'il tourne. */
    private lateinit var repo: PetRepository
    override val pet: Pet get() = resident

    // --- Fenêtres ---
    private lateinit var wm: WindowManager
    /** La seule autorité sur les fenêtres overlay. */
    private lateinit var overlayManager: OverlayManager
    override val overlay: OverlayHost get() = overlayManager
    private lateinit var windows: OverlayWindows
    /** Fenêtre de chaque animal (vue + paramètres). */
    private class PetWindow(val id: WindowId, val view: BunnyView, val lp: WindowManager.LayoutParams)
    private val petWindows = HashMap<PetId, PetWindow>()
    /** Vue du résident (gestes, aperçu des effets). */
    private val bunny: BunnyView get() = petWindows.getValue(resident.id).view

    // --- Domaines ---
    override lateinit var pages: PageTracker
        private set
    /** Activités prises en charge par une fonctionnalité (stations : [Act.IN_STATION]). */
    override val acts = ActRegistry()
    private lateinit var stations: StationController
    private lateinit var toys: ToyController
    private lateinit var mischief: MischiefController
    private val handler = Handler(Looper.getMainLooper())
    /** Paroles : bulle (si les fenêtres existent) + reprise de la boucle. */
    override val speech = Speech(rnd) { text, seconds ->
        if (::windows.isInitialized) {
            if (!hidden && !bunnyAway) windows.say(resident.id, text, seconds)
            requestFrames()
        }
    }
    private lateinit var notifier: PetNotifier
    private lateinit var touch: TouchHandler
    private lateinit var watcher: ForegroundAppWatcher
    /** Fenêtres créées et domaines câblés : les intents peuvent être traités. */
    private var ready = false

    // --- Plateformes (icônes / boutons) détectées : cache partagé, converti une fois par version ---
    private var platCacheVersion = -1
    private var platList: List<Platforms.P> = emptyList()
    private var platBoxes: List<Box> = emptyList()

    // --- Visibilité ---
    override var hidden = false
        private set
    /** Invisible ici (en cage sur une autre page, ou caché) : sa fenêtre est retirée. */
    override var bunnyAway = false
        private set

    // --- Boucle ---
    private var looping = false
    /** Boucle suspendue (lapin invisible) : simple vérification périodique via [suspendedTick]. */
    private var suspended = false
    private var lastSuspendedTick = 0L
    /** Écran allumé ET déverrouillé. */
    private var awake = true
    private var lastFrameNs = 0L
    private var lastTickWall = 0L
    private var lastSaveWall = 0L
    private var notifTimer = 0f
    private var menuTimer = 0f

    // =====================================================================
    //  Cycle de vie
    // =====================================================================
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier = PetNotifier(
            this,
            pet = { if (::resident.isInitialized) resident else null },
            hidden = { hidden },
            hiding = { ::mischief.isInitialized && mischief.hidingView != null },
        )
        notifier.createChannels()
        if (!Settings.canDrawOverlays(this)) {
            // Lancé par startForegroundService : il faut tout de même passer au premier plan avant de s'arrêter
            tryStartForeground()
            stopSelf(); return
        }
        val d = resources.displayMetrics.density
        arena.d = d
        arena.bw = (Tuning.BUNNY_W_DP * d).toInt()
        arena.bh = (Tuning.BUNNY_H_DP * d).toInt()
        arena.feetPad = Tuning.FEET_PAD_DP * d
        repo = Pets.repository(this)
        resident = loadResident(BunnyPhysics(d))
        world.add(resident)
        watcher = ForegroundAppWatcher(this)
        wm = getSystemService(WindowManager::class.java)
        overlayManager = OverlayManager(wm)
        windows = OverlayWindows(this, arena, { prefs }, overlayManager, bubbleAnchor)
        pages = PageTracker(this)
        toys = ToyController(this)
        toys.registerActs(acts)
        stations = StationController(this, toys, StationPanelWindows(this, windows, arena))
        stations.registerActs(acts)
        mischief = MischiefController(this)
        mischief.registerActs(acts)
        touch = TouchHandler(this, windows, this, stations, mischief)
        val entities = EntityWindows(this, windows, world, toys, stations) { ev ->
            touch.listener?.onTouch(bunny, ev) ?: false
        }
        overlayManager.factory = entities::create

        computeBounds()
        dropIn(resident)
        mischief.loadLevel()

        // La fenêtre du lapin est ajoutée AVANT startForeground : sur Android 15+, une fenêtre
        // en surimpression visible autorise le passage au premier plan (redémarrage START_STICKY).
        try {
            createWindows()
        } catch (e: Exception) {
            Log.e(TAG, "createWindows", e)
            tryStartForeground()
            removeAllWindows(); stopSelf(); return
        }
        if (!tryStartForeground()) {
            removeAllWindows(); stopSelf(); return
        }
        running = true
        ready = true

        toys.restorePoops()
        handler.postDelayed({
            if (!ready) return@postDelayed
            pages.currentView = pages.computeView()
            stations.restore(resident)
            applyView()
        }, 300)
        lastTickWall = clock.wallMs()
        lastSaveWall = lastTickWall

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_ON)
        }, Context.RECEIVER_NOT_EXPORTED)

        startLoop()
        handler.postDelayed(appPoll, 2000)
        handler.postDelayed({
            say(speech.greeting(resident.name, resident.stats.mood, Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
        }, 900)
    }

    /** Charge le résident (créé au premier lancement) et rattrape explicitement le temps passé hors-ligne. */
    private fun loadResident(phys: BunnyPhysics): Pet {
        val now = clock.wallMs()
        val rec = repo.residentOrCreate()
        val p = Pet.fromRecord(rec, phys, rnd)
        p.on(PetEvent.Elapsed((now - rec.lastUpdate) / 3_600_000f), now)
        save(p)
        return p
    }

    /** Il tombe du haut de l'écran en arrivant. */
    private fun dropIn(p: Pet) {
        val b = p.body
        b.fx = arena.screenW * (0.3f + rnd.nextFloat() * 0.4f)
        b.fy = arena.topY + Tuning.SPAWN_DROP_DP * arena.d
        b.phys.vy = 0f
        b.setAct(Act.AIR); b.afterAir = Act.IDLE
    }

    /** Nom, apparence ou réglages changés : petite fête. */
    private fun showNewLook() {
        applyView()
        emit(resident, PType.SPARKLE, 6)
        speech.sayOneOf(Lines.newLook(resident.name))
        requestFrames()
    }

    /** Écran d'adoption validé (commande [PetCommands.ACTION_ADOPT]) : le service est l'autorité. */
    private fun adopt(a: Adoption) {
        val old = resident
        val rec = a.apply(old.toRecord(), repo::newPetId, clock.wallMs())
        if (rec.id != old.id) { replaceResident(rec); return }
        old.rename(rec.identity.name)
        old.look = rec.look
        old.adopted = true
        save(old)
        showNewLook()
    }

    /**
     * Un nouveau lapin remplace le résident, sans redémarrer le service : l'ancien quitte proprement
     * cache-cache, station, menu, panneaux et objets ; ses clés sont effacées ; la fenêtre est réutilisée.
     */
    private fun replaceResident(rec: PetRecord) {
        val old = resident
        if (old.isHiding) mischief.endHiding()
        if (world.stationOf(old) != null) stations.leaveStation(old, hopOut = false)
        closeMenu()
        stations.closePanels()
        toys.clear()
        windows.forgetBubble(old.id)
        val w = petWindows.remove(old.id)
        if (w != null) overlayManager.release(w.id)
        world.pets.remove(old.id)
        repo.adopt(rec, replacing = old.id)

        val p = Pet.fromRecord(rec, BunnyPhysics(arena.d), rnd)
        resident = p
        world.add(p)
        computeBounds()
        pages.resetBunny()
        dropIn(p)
        if (w != null) petWindows[p.id] = PetWindow(WindowId.Pet(p.id), w.view, w.lp)
        bunnyAway = false
        if (!hidden) attachPet(p)
        applyView()
        lastTickWall = clock.wallMs()
        emit(p, PType.SPARKLE, 6)
        say(speech.greeting(p.name, p.stats.mood, Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
        updateNotification(force = true)
        requestFrames()
    }

    /** Passe au premier plan ; false si le système le refuse (ex. redémarrage en arrière-plan sur Android 15+). */
    private fun tryStartForeground(): Boolean = try {
        val n = notifier.build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(PetNotifier.NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(PetNotifier.NOTIF_ID, n)
        true
    } catch (e: Exception) {
        // ForegroundServiceStartNotAllowedException (API 31+) ou autre refus
        Log.w(TAG, "startForeground refusé", e)
        false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ready) {
            // Vues non initialisées (permission manquante, démarrage refusé…) : on ignore
            if (intent?.action == ACTION_STOP) stop(this)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_FEED -> { if (hidden) show(); toys.feed(); requestFrames() }
            ACTION_TOGGLE -> if (hidden) show() else hide()
            ACTION_STOP -> { stop(this); return START_NOT_STICKY }
            // Réglages changés (niveau de bêtises)
            ACTION_RELOAD -> { mischief.loadLevel(); showNewLook() }
            PetCommands.ACTION_SET_NAME -> {
                val n = Adoption.cleanName(intent.getStringExtra(PetCommands.EXTRA_NAME)) ?: return START_STICKY
                resident.rename(n)
                save(resident)
                showNewLook()
            }
            PetCommands.ACTION_ADOPT -> PetCommands.adoptionFrom(intent)?.let(::adopt)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        val wasReady = ready
        ready = false
        handler.removeCallbacksAndMessages(null)
        stopLoop()
        if (wasReady) {
            try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
            save(resident)
            removeAllWindows()
        }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.post {
            if (!ready) return@post
            computeBounds()
            // nouvelle orientation : la largeur des pages du bureau change
            pages.onScreenResized()
            val b = resident.body
            b.fx = b.fx.coerceIn(arena.minX, arena.maxX)
            b.platform = null
            if (b.act != Act.AIR && b.act != Act.DRAGGED) b.fy = arena.groundY
            toys.onScreenResized()
            closeMenu()
            // stations et dock : recalculés depuis les fractions sauvegardées
            stations.reloadPositions()
            windows.reloadDockPosition()
            stations.repositionOccupant(resident)
            stations.repositionPanels()
            requestFrames()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { awake = false; stopLoop(); handler.removeCallbacks(appPoll); save(resident) }
                // Écran allumé mais verrouillé : rien à animer derrière l'écran de verrouillage
                Intent.ACTION_SCREEN_ON -> if (!isLocked()) wakeUp(greet = false)
                Intent.ACTION_USER_PRESENT -> wakeUp(greet = true)
            }
        }
    }

    private fun isLocked(): Boolean =
        try { getSystemService(KeyguardManager::class.java).isKeyguardLocked } catch (_: Exception) { false }

    private fun wakeUp(greet: Boolean) {
        if (awake) return
        awake = true
        // Écran rallumé (et déverrouillé) : on reprend le suivi de l'app au premier plan
        handler.removeCallbacks(appPoll)
        handler.postDelayed(appPoll, 1500)
        tickStats(force = true)
        if (!hidden) startLoop()
        if (greet && !resident.stats.sleeping) speech.sayOneOf(Lines.WELCOME_BACK)
    }

    // =====================================================================
    //  Fenêtres
    // =====================================================================
    private fun createWindows() {
        val v = BunnyView(this)
        v.render(BunnyRenderState.of(resident), 0f)
        petWindows[resident.id] = PetWindow(WindowId.Pet(resident.id), v, windows.params(arena.bw, arena.bh, touchable = true))
        attachPet(resident)
        touch.attach(v)
        stations.create()
        windows.createDock { stations.toggleStationsPanel() }
    }

    /** Affiche la fenêtre de [p] à sa position. */
    private fun attachPet(p: Pet, fadeIn: Boolean = false) {
        val w = petWindows[p.id] ?: return
        w.lp.x = petX(p); w.lp.y = petY(p)
        overlayManager.attach(w.id, w.view, w.lp, fadeIn)
    }

    /** Retire toutes les fenêtres ; menu, panneaux et partie en cours sont fermés proprement. */
    private fun removeAllWindows() {
        if (!::overlayManager.isInitialized) return
        closeMenu()
        if (::stations.isInitialized) stations.closePanels()
        overlayManager.detachAll()
        if (::toys.isInitialized) toys.onDetached()
    }

    override fun hide() {
        if (hidden) return
        hidden = true
        removeAllWindows()
        stopLoop()
        updateNotification(force = true)
    }

    private fun show() {
        if (!hidden) return
        hidden = false
        computeBounds()
        bunnyAway = false
        attachPet(resident)
        toys.reattach()
        windows.addDock()
        layoutAnchored()
        applyView()   // gère aussi le lapin en cage sur une autre vue
        stations.reopenVetPanelIfNeeded()
        startLoop()
        speech.sayOneOf(Lines.SHOWN)
        updateNotification(force = true)
    }

    /**
     * Rend le lapin invisible ici (fenêtre retirée) ou le réaffiche.
     * [fadeIn] : petite apparition en fondu.
     */
    override fun setBunnyAway(away: Boolean, fadeIn: Boolean) {
        if (away == bunnyAway) return
        bunnyAway = away
        val w = petWindows[resident.id] ?: return
        if (away) {
            closeMenu(); windows.hideBubble(resident.id)
            overlayManager.detach(w.id)
        } else {
            attachPet(resident, fadeIn)
            requestFrames()
        }
    }

    @Suppress("DEPRECATION")
    private fun computeBounds() {
        val m = wm.currentWindowMetrics
        val b = m.bounds
        val ins = m.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        val d = arena.d
        arena.screenW = b.width().toFloat()
        arena.groundY = b.height() - ins.bottom - Tuning.GROUND_MARGIN_DP * d
        arena.topY = ins.top.toFloat()
        arena.minX = ins.left + Tuning.SIDE_MARGIN_DP * d
        arena.maxX = arena.screenW - ins.right - Tuning.SIDE_MARGIN_DP * d
        resident.body.phys.setBounds(arena.minX, arena.maxX, arena.topY, arena.groundY)
    }

    /** Coin haut gauche de la fenêtre de [p] (ses pieds en bas au centre, décalage du défilement compris). */
    private fun petX(p: Pet) = (p.body.fx - arena.bw / 2f + pages.bunnyShift).roundToInt()
    private fun petY(p: Pet) = (p.body.fy - (arena.bh - arena.feetPad)).roundToInt()

    override fun placeBunny() {
        // Boucle suspendue : il est hors de l'écran, inutile de déplacer la fenêtre (fait à la reprise)
        if (suspended) return
        val w = petWindows[resident.id] ?: return
        overlay.move(w.id, petX(resident), petY(resident))
    }

    /** Position des bulles : au-dessus de la tête de chaque animal, à l'écran. */
    private val bubbleAnchor = object : OverlayWindows.BubbleAnchor {
        override fun headX(pet: PetId): Float {
            val p = world.pets[pet] ?: return 0f
            return p.body.fx + pages.bunnyShift
        }
        override fun headTop(pet: PetId): Float {
            val p = world.pets[pet] ?: return 0f
            return p.body.fy - (if (p.act == Act.SLEEP) 70 else 112) * arena.d
        }
    }

    // =====================================================================
    //  Vues : chaque objet reste sur l'app / la page du bureau où il est posé
    // =====================================================================
    private fun checkView() {
        if (pages.refresh()) applyView()
        layoutAnchored()
    }

    /** Place, montre ou cache chaque objet ancré selon la vue et le défilement. Appelé à chaque image. */
    override fun layoutAnchored() {
        if (hidden) return
        stations.layout()
        toys.layoutPoops()
        val holder = stations.holdingStation(resident)
        if (holder != null) stations.followHolder(holder, resident) else pages.updateBunnyPage()
    }

    /** Changement de vue : bain/véto interrompus, cache-cache. */
    override fun applyView() {
        if (hidden) return
        val st = world.stationOf(resident)
        if (st != null && !st.behavior.holdsPet && !stations.anchoredHere(st)) stations.leaveStation(resident, hopOut = false)
        stations.refreshStationsPanel()
        mischief.onViewChanged()
        if (stations.holdingStation(resident) != null) { layoutAnchored(); return }
        setBunnyAway(resident.act == Act.HIDDEN, fadeIn = true)
        layoutAnchored()
    }

    // =====================================================================
    //  Boucle d'animation (fréquence adaptée à l'activité, suspendue s'il est invisible)
    // =====================================================================
    private fun startLoop() {
        if (looping || hidden || !awake) return
        looping = true
        suspended = false
        lastFrameNs = 0L
        handler.removeCallbacks(suspendedTick)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopLoop() {
        looping = false
        suspended = false
        Choreographer.getInstance().removeFrameCallback(this)
        handler.removeCallbacks(suspendedTick)
    }

    /** Quelque chose de visible va se passer : reprend la boucle si elle était suspendue. */
    override fun requestFrames() {
        if (!looping || !suspended) return
        suspended = false
        handler.removeCallbacks(suspendedTick)
        lastFrameNs = 0L
        placeBunny()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun targetFps(): Int {
        val act = resident.act
        return when {
            act in Act.MOVING || toys.anyMoving() || pages.scrollingRecently() -> Tuning.FPS_MOVING
            act in Act.SLOW || acts.handlerFor(resident)?.slowFrames == true -> Tuning.FPS_SLEEP
            else -> Tuning.FPS_IDLE
        }
    }

    /** Rien de visible à animer : lapin caché / en cage ailleurs / hors de l'écran sur une autre page. */
    private fun canSuspend(): Boolean {
        val invisible = resident.act == Act.HIDDEN || bunnyAway || pages.bunnyOffscreen()
        if (!invisible) return false
        return !touch.menuOpen && !windows.anyBubbleShown && !pages.scrollingRecently() &&
            !world.hasToyOrFood() && !toys.anyMoving() && !stations.animating()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!looping || suspended) return
        val fps = targetFps()
        val cap = max(Tuning.DT_CAP_S, 2f / fps)
        val dt = if (lastFrameNs == 0L) 0.016f else ((frameTimeNanos - lastFrameNs) / 1e9f).coerceIn(0f, cap)
        lastFrameNs = frameTimeNanos
        try {
            update(dt)
        } catch (e: Exception) {
            Log.e(TAG, "frame error", e)
        }
        if (!looping || suspended) return
        if (canSuspend()) {
            suspended = true
            lastSuspendedTick = clock.uptimeMs()
            handler.postDelayed(suspendedTick, Tuning.SUSPENDED_TICK_MS)
            return
        }
        // Moins de 60 i/s : on saute des vsync au lieu de calculer chaque image
        val delay = 1000L / targetFps() - 16L
        if (delay > 0) Choreographer.getInstance().postFrameCallbackDelayed(this, delay)
        else Choreographer.getInstance().postFrameCallback(this)
    }

    /** Boucle suspendue : la simulation avance par petits pas, et la boucle reprend dès qu'il redevient visible. */
    private val suspendedTick = object : Runnable {
        override fun run() {
            if (!looping || !suspended) return
            val now = clock.uptimeMs()
            var left = ((now - lastSuspendedTick) / 1000f).coerceAtMost(1f)
            lastSuspendedTick = now
            try {
                while (left > 0f && suspended) {
                    val step = min(left, Tuning.SUSPENDED_STEP_S)
                    update(step)
                    left -= step
                }
            } catch (e: Exception) {
                Log.e(TAG, "suspended tick error", e)
            }
            if (!looping || !suspended) return
            if (!canSuspend()) requestFrames()
            else handler.postDelayed(this, Tuning.SUSPENDED_TICK_MS)
        }
    }

    private fun update(dt: Float) {
        val p = resident
        p.body.actTime += dt
        p.mind.tick(dt)
        checkView()
        pages.checkPageReturn()
        mischief.checkUserEvents()
        stations.update(dt)

        tickStats(force = false)
        toys.update(dt)
        checkPlatform(p)
        updateBehavior(p, dt)
        stations.checkChestBump(dt)

        // Rendu : chaque animal affiché reçoit son état de l'image
        for (pet in world.pets.values) petWindows[pet.id]?.view?.render(BunnyRenderState.of(pet), dt)
        placeBunny()
        windows.updateBubbles(dt)
        // Au plus un updateViewLayout par fenêtre et par image
        overlayManager.flush()

        menuTimer += dt
        if (menuTimer > 1f) { menuTimer = 0f; touch.refreshMenu() }
        notifTimer += dt
        if (notifTimer > 20f) { notifTimer = 0f; updateNotification(force = false); notifier.checkAlerts() }
    }

    private fun tickStats(force: Boolean) {
        val now = clock.wallMs()
        if (!force && now - lastTickWall < Tuning.STATS_EVERY_MS) return
        val hours = (now - lastTickWall) / 3_600_000f
        lastTickWall = now
        val p = resident
        val newPoops = on(p, PetEvent.Elapsed(hours)).newPoops
        repeat(newPoops) {
            if (toys.poopCount() < p.stats.poops) {
                toys.spawnPoop((p.body.fx - p.body.facing * 40 * arena.d).coerceIn(arena.minX, arena.maxX))
            }
        }
        if (newPoops > 0 && p.act != Act.SLEEP) speech.sayOneOf(Lines.POOPED)
        if (now - lastSaveWall > Tuning.SAVE_EVERY_MS) { save(p); lastSaveWall = now }
    }

    // =====================================================================
    //  Comportement (machine à états)
    // =====================================================================
    private fun updateBehavior(p: Pet, dt: Float) {
        val b = p.body
        val m = p.mind
        val st = p.stats
        val d = arena.d
        // Activité prise en charge par une fonctionnalité (stations…) : elle fait tout
        acts.handlerFor(p)?.let { it.update(p, dt); return }
        when (b.act) {
            Act.IDLE -> {
                b.pose = Pose.IDLE
                b.fy = actor.surfaceY(p)
                if (world.hasToy()) { p.setAct(Act.PLAY); return }
                if (world.hasFood()) { p.setAct(Act.GOTO_FOOD); return }
                m.chatterIn -= dt
                if (m.chatterIn <= 0f) { m.chatterIn = 50f + rnd.nextFloat() * 80f; chatter(p) }
                if (b.actTime > b.actDuration) decideNext(p)
            }
            Act.WALK -> {
                val speed = when {
                    st.isSick || st.energy < rules.walkSlowEnergyBelow -> Tuning.WALK_SLOW_DP * d
                    st.happiness > rules.walkHappyAbove -> Tuning.WALK_HAPPY_DP * d
                    else -> Tuning.WALK_NORMAL_DP * d
                } * (if (p.personality == Personality.LAZY) Tuning.LAZY_SPEED_FACTOR else 1f)
                if (actor.walkToward(p, b.targetX, speed, dt)) p.setAct(Act.IDLE, 2f + rnd.nextFloat() * 4f)
            }
            Act.AIR -> stepAir(p, dt)
            Act.DRAGGED -> b.pose = Pose.DRAGGED
            Act.SLEEP -> {
                b.pose = Pose.SLEEP
                b.fy = actor.surfaceY(p)
                if (!st.sleeping) {
                    p.express(Expr.HAPPY, 2f)
                    speech.sayOneOf(Lines.WOKE_UP)
                    p.setAct(Act.IDLE, 3f)
                }
            }
            Act.TRAVEL_OUT -> {
                if (actor.walkToward(p, b.targetX, Tuning.TRAVEL_OUT_DP * d, dt, clamp = false)) {
                    // Il "passe" dans l'application suivante : réapparaît de l'autre côté
                    b.fx = if (b.targetX < 0) arena.screenW + arena.bw else -arena.bw.toFloat()
                    b.targetX = arena.screenW * (0.2f + rnd.nextFloat() * 0.6f)
                    if (m.travelThen == Act.SIT) b.targetX = arena.maxX - 10 * d
                    p.setAct(Act.TRAVEL_IN)
                }
            }
            Act.TRAVEL_IN -> {
                if (actor.walkToward(p, b.targetX, Tuning.TRAVEL_IN_DP * d, dt, clamp = false)) {
                    m.travelComment?.let { say(it, 4.5f) }
                    m.travelComment = null
                    when (m.travelThen) {
                        Act.SIT -> p.setAct(Act.SIT, 40f)
                        Act.DANCE -> startDance(p)
                        else -> p.setAct(Act.IDLE, 4f)
                    }
                }
            }
            Act.PAGE_OUT -> pages.updatePageOut(p, dt)
            Act.SIT -> {
                b.pose = Pose.SIT
                b.facing = -1f
                if (b.actTime > b.actDuration) p.setAct(Act.IDLE, 2f)
            }
            Act.DIZZY -> {
                b.pose = Pose.IDLE
                p.express(Expr.DIZZY, 0.2f)
                if (b.actTime > 2.2f) { m.exprOverride = null; p.setAct(Act.IDLE, 2f) }
            }
            Act.DANCE -> {
                b.pose = Pose.IDLE
                m.danceTime += dt
                m.danceHopIn -= dt
                if (m.danceTime > 9f) { p.setAct(Act.IDLE, 3f); return }
                if (m.danceHopIn <= 0f) {
                    m.danceHopIn = 0.55f
                    b.facing = -b.facing
                    emit(p, PType.NOTE)
                    actor.jump(p, -420 * d, 0f, then = Act.DANCE)
                }
            }
            // Activité d'une fonctionnalité sans gestionnaire (station introuvable…) : il ne reste pas figé
            else -> p.setAct(Act.IDLE, 2f)
        }
    }

    private fun decideNext(p: Pet) {
        val b = p.body
        val st = p.stats
        val boxes = platforms()
        val input = BrainInput(
            energy = st.energy, satiety = st.satiety, happiness = st.happiness,
            personality = p.personality, hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            x = b.fx, facing = b.facing, minX = arena.minX, maxX = arena.maxX, screenW = arena.screenW, density = arena.d,
            platform = b.platform, platforms = boxes,
            elsewhere = pages.bunnyElsewhere(),
            canPageWander = pages.canPageWander(),
            chestVisible = stations.visibleChest() != null,
            hasToyOrFood = world.hasToyOrFood(),
            nowMs = clock.wallMs(),
            lastSelfServeMs = stations.selfServeAt,
        )
        when (val dec = p.mind.brain.decide(input) { mischief.maybeSeekAttention() }) {
            Decision.Sleep -> startSleep(p)
            Decision.AttentionSought -> {}
            Decision.GoToChest -> { stations.selfServeAt = input.nowMs; p.setAct(Act.GOTO_CHEST) }
            Decision.PageWander -> pages.startPageWander()
            is Decision.JumpDown -> actor.jumpTo(p, dec.targetX, arena.groundY, Act.IDLE)
            is Decision.JumpToPlatform -> {
                b.facing = sign(dec.target.centerX - b.fx)
                actor.jumpTo(p, dec.target.centerX, dec.target.top, Act.IDLE)
            }
            is Decision.TurnAround -> { b.facing = -b.facing; p.setAct(Act.IDLE, dec.idleSeconds) }
            is Decision.Idle -> p.setAct(Act.IDLE, dec.seconds)
            is Decision.Walk -> { b.targetX = dec.targetX; p.setAct(Act.WALK) }
            is Decision.Hop -> actor.jump(p, dec.vy, dec.vx, then = Act.IDLE)
        }
    }

    /** Icônes détectées, converties une seule fois par version de la liste. */
    private fun platforms(): List<Box> {
        val v = Platforms.version
        if (v != platCacheVersion) {
            platCacheVersion = v
            platList = Platforms.list
            platBoxes = platList.map { it.rect.toBox() }
        }
        return platBoxes
    }

    private fun Rect.toBox() = Box(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    /** Si l'icône sous ses pattes a bougé ou disparu (défilement, changement d'app) : il tombe. */
    private fun checkPlatform(p: Pet) {
        val b = p.body
        val cur = b.platform ?: return
        if (b.act == Act.AIR || b.act == Act.DRAGGED) return
        val boxes = platforms()
        if (platCacheVersion == b.platVersionSeen) return
        b.platVersionSeen = platCacheVersion
        val same = boxes.firstOrNull {
            abs(it.top - cur.top) < Tuning.PLATFORM_SAME_DP * arena.d && b.fx >= it.left && b.fx <= it.right
        }
        if (same != null) {
            b.platform = same
            b.fy = same.top
            return
        }
        b.platform = null
        b.phys.stop()
        b.afterAir = if (b.act == Act.SLEEP) Act.SLEEP else Act.IDLE
        p.express(Expr.SURPRISED, 1f)
        if (b.act != Act.SLEEP) speech.sayOneOf(Lines.PLATFORM_LOST, 1.5f)
        p.setAct(Act.AIR)
    }

    private fun stepAir(p: Pet, dt: Float) {
        val b = p.body
        b.pose = Pose.AIR
        val ev = b.phys.stepAir(dt, platforms())
        if (b.phys.facing != 0f) b.facing = b.phys.facing
        when (ev) {
            AirEvent.ForcedLanded -> p.setAct(b.afterAir, 3f)
            AirEvent.Bounced -> haptic(p, HapticFeedbackConstants.KEYBOARD_TAP)
            is AirEvent.Landed -> onLanded(p, ev)
            AirEvent.Flying, AirEvent.HitWall -> {}
        }
    }

    /** Atterrissage sur une icône ou au sol : étourdi si brutal, sinon il reprend l'activité prévue. */
    private fun onLanded(p: Pet, ev: AirEvent.Landed) {
        val b = p.body
        if (ev.onPlatform) {
            b.platform = platBoxes[ev.platform]
            b.platformLabel = platList.getOrNull(ev.platform)?.label
            b.platVersionSeen = platCacheVersion
            haptic(p, HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (ev.hard) {
            if (ev.onPlatform) speech.sayOneOf(Lines.HARD_LANDING_PLATFORM)
            else {
                speech.sayOneOf(Lines.HARD_LANDING_GROUND)
                on(p, PetEvent.HardLanding)
            }
            p.setAct(Act.DIZZY)
            return
        }
        val next = b.afterAir
        val idle = if (ev.onPlatform) 2f + rnd.nextFloat() * 3f else 1.5f + rnd.nextFloat() * 2f
        p.setAct(next, if (next == Act.IDLE) idle else 3f)
        val lbl = b.platformLabel
        if (ev.onPlatform && !lbl.isNullOrBlank() && next == Act.IDLE && rnd.nextFloat() < 0.35f) {
            speech.sayOneOf(Lines.onPlatform(lbl), 2.2f)
        }
    }

    override fun startSleep(pet: Pet) {
        // Dodo pendant le cache-cache : la partie s'arrête proprement
        if (pet.isHiding) {
            mischief.endHiding()
            pet.body.fx = pet.body.fx.coerceIn(arena.minX, arena.maxX)
        }
        on(pet, PetEvent.FellAsleep)
        speech.sayOneOf(Lines.GOING_TO_SLEEP)
        val k = world.stationOf(pet)
        if (k != null && k.behavior.holdsPet) return   // il dort dans sa cage
        if (k != null) stations.leaveStation(pet, hopOut = false)
        pet.setActOrAfterAir(Act.SLEEP)
    }

    private fun startDance(p: Pet) {
        p.mind.danceTime = 0f; p.mind.danceHopIn = 0f
        p.setAct(Act.DANCE)
    }

    // =====================================================================
    //  Toucher, glisser / lancer, menu : voir [TouchHandler]
    // =====================================================================
    override fun closeMenu() { if (::touch.isInitialized) touch.closeMenu() }

    // =====================================================================
    //  Paroles (répliques dans [Lines], tirage et bulle dans [Speech])
    // =====================================================================
    override fun say(text: String, seconds: Float) = speech.say(text, seconds)

    override fun pick(vararg s: String) = speech.pick(*s)

    private fun chatter(p: Pet) {
        say(speech.chatterLine(p.stats.mood))
        if (p.stats.mood == Mood.HAPPY && rnd.nextFloat() < 0.3f) startDance(p)
    }

    // =====================================================================
    //  Voyage d'application en application
    // =====================================================================
    override fun foregroundPkg(): String? = if (::watcher.isInitialized) watcher.currentPkg else null

    private val appPoll = object : Runnable {
        override fun run() {
            if (awake && !hidden) {
                try {
                    watcher.poll()?.let { onAppChanged(resident, it) }
                } catch (e: Exception) {
                    Log.w(TAG, "poll", e)
                }
            }
            handler.postDelayed(this, 1500)
        }
    }

    private fun onAppChanged(p: Pet, app: ForegroundAppWatcher.AppInfo) {
        on(p, PetEvent.AppVisited)
        val comment = commentFor(app)
        val then = when (app.category) {
            ApplicationInfo.CATEGORY_VIDEO -> Act.SIT
            ApplicationInfo.CATEGORY_AUDIO -> Act.DANCE
            else -> Act.IDLE
        }
        val act = p.act
        if (p.isHiding) return
        val feature = acts.handlerFor(p)
        if (feature?.travel == Travel.IGNORES) return
        if (act in Act.BUSY_FOR_TRAVEL || feature?.blocksTravel == true) {
            if (act != Act.SLEEP && rnd.nextFloat() < 0.5f) say(comment, 3f)
            return
        }
        val now = clock.wallMs()
        if (now - p.mind.lastTravelAt < Tuning.TRAVEL_COOLDOWN_MS) { say(comment, 3f); return }
        p.mind.lastTravelAt = now
        closeMenu()
        p.mind.travelComment = comment
        p.mind.travelThen = then
        // Il court vers le bord le plus proche et "saute" dans la nouvelle app
        p.body.targetX = if (p.body.fx < arena.screenW / 2) -arena.bw.toFloat() else arena.screenW + arena.bw
        speech.sayOneOf(Lines.FOLLOWING, 1.5f)
        p.setAct(Act.TRAVEL_OUT)
        requestFrames()
    }

    private fun commentFor(app: ForegroundAppWatcher.AppInfo): String =
        speech.appComment(app.label, app.pkg, app.category, app.isHome)

    // =====================================================================
    //  Notifications : voir [PetNotifier]
    // =====================================================================
    override fun updateNotification(force: Boolean) = notifier.update(force)

    // =====================================================================
    //  PetCtx : le reste de la façade (délégations vers les domaines)
    // =====================================================================
    override val prefs: SharedPreferences get() = Prefs.of(this)

    override fun save(pet: Pet) { repo.save(pet.toRecord()) }

    override fun appLabel(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { null }

    override fun emit(pet: Pet, type: PType, n: Int) { petWindows[pet.id]?.view?.emit(type, n, pet.body.facing) }

    override fun haptic(pet: Pet, feedback: Int) { petWindows[pet.id]?.view?.performHapticFeedback(feedback) }

    override fun vibrate(pattern: LongArray) {
        try {
            val vib = getSystemService(VibratorManager::class.java).defaultVibrator
            vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {}
    }

    override var lastInteraction: Long = clock.wallMs()

    override fun setDockHiding(hiding: Boolean) = windows.setDockHiding(hiding)
}
