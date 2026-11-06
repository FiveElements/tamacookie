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
import android.graphics.Color
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
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.BunnyView.Pose
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
 * [OverlayWindows] (fenêtres, bulle, dock), [StationController] (bain, véto, cage, coffre),
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
    override val pet: Pet get() = resident

    // --- Domaines ---
    private lateinit var bunny: BunnyView
    private lateinit var bunnyLp: WindowManager.LayoutParams
    override lateinit var windows: OverlayWindows
        private set
    override lateinit var pages: PageTracker
        private set
    private lateinit var stations: StationController
    private lateinit var toys: ToyController
    private lateinit var mischief: MischiefController
    private val handler = Handler(Looper.getMainLooper())
    /** Paroles : bulle (si les fenêtres existent) + reprise de la boucle. */
    override val speech = Speech(rnd) { text, seconds ->
        if (::windows.isInitialized) {
            windows.say(text, seconds)
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
    private var bunnyAdded = false

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
            pet = { if (::resident.isInitialized) resident.stats else null },
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
        resident = Pet.resident(PetState(this).load(), BunnyPhysics(d), rnd)
        world.add(resident)
        watcher = ForegroundAppWatcher(this)
        windows = OverlayWindows(this)
        pages = PageTracker(this)
        stations = StationController(this)
        toys = ToyController(this)
        mischief = MischiefController(this)
        touch = TouchHandler(this, stations, mischief)

        computeBounds()
        val b = resident.body
        b.fx = arena.screenW * (0.3f + rnd.nextFloat() * 0.4f)
        b.fy = arena.topY + Tuning.SPAWN_DROP_DP * d          // il tombe du haut de l'écran en arrivant
        b.phys.vy = 0f
        b.setAct(Act.AIR); b.afterAir = Act.IDLE
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
            if (resident.stats.caged) stations.enterStation(StationKind.CAGE, announce = false)
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
            val st = resident.stats
            say(speech.greeting(st.name, st.mood, Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
        }, 900)
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
            ACTION_RELOAD -> {
                // Seulement le nom / l'apparence / le caractère : les jauges en mémoire ne sont pas écrasées
                val st = resident.stats
                st.reloadAppearance()
                mischief.loadLevel()
                applyView()
                bunny.look = st.look
                bunny.emit(PType.SPARKLE, 6)
                speech.sayOneOf(Lines.newLook(st.name))
                requestFrames()
            }
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
            resident.stats.save()
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
            stations.inStation?.let { stations.positionInStation(it) }
            stations.repositionPanels()
            requestFrames()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { awake = false; stopLoop(); handler.removeCallbacks(appPoll); resident.stats.save() }
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
        bunny = BunnyView(this)
        bunny.body = resident.body
        bunny.look = resident.stats.look
        bunnyLp = windows.params(arena.bw, arena.bh, touchable = true)
        placeBunny()
        windows.add(bunny, bunnyLp)
        bunnyAdded = true
        touch.attach(bunny)
        stations.create()
        windows.createDock { stations.toggleStationsPanel() }
        windows.createBubble()
    }

    private fun removeAllWindows() {
        if (!::windows.isInitialized) return
        closeMenu()
        windows.hideBubble()
        if (::stations.isInitialized) {
            stations.closePanels()
            stations.detachAll()
        }
        if (bunnyAdded) { windows.safeRemove(bunny); bunnyAdded = false }
        if (::toys.isInitialized) toys.detachAll()
        windows.removeDock()
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
        placeBunny()
        bunnyAway = false
        windows.add(bunny, bunnyLp)
        bunnyAdded = true
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
        if (away) {
            closeMenu(); windows.hideBubble()
            if (bunnyAdded) { bunnyAdded = false; windows.safeRemove(bunny) }
        } else {
            placeBunny()
            windows.add(bunny, bunnyLp); bunnyAdded = true
            if (fadeIn) { bunny.alpha = 0f; bunny.animate().alpha(1f).setDuration(160).start() }
            requestFrames()
        }
    }

    @Suppress("DEPRECATION")
    private fun computeBounds() {
        val m = windows.wm.currentWindowMetrics
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

    override fun placeBunny() {
        val b = resident.body
        val nx = (b.fx - arena.bw / 2f + pages.bunnyShift).roundToInt()
        val ny = (b.fy - (arena.bh - arena.feetPad)).roundToInt()
        if (nx != bunnyLp.x || ny != bunnyLp.y) {
            bunnyLp.x = nx; bunnyLp.y = ny
            // Boucle suspendue : il est hors de l'écran, inutile de déplacer la fenêtre (fait à la reprise)
            if (bunnyAdded && !suspended) windows.update(bunny, bunnyLp)
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
        if (stations.inStation == StationKind.CAGE) stations.followCage() else pages.updateBunnyPage()
    }

    /** Changement de vue : bain/véto interrompus, cache-cache. */
    override fun applyView() {
        if (hidden) return
        val st = stations.inStation
        if (st != null && st != StationKind.CAGE && !stations.anchoredHere(st)) stations.leaveStation(hopOut = false)
        stations.refreshStationsPanel()
        mischief.onViewChanged()
        if (stations.inStation == StationKind.CAGE) { layoutAnchored(); return }
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
        if (bunnyAdded) windows.update(bunny, bunnyLp)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun targetFps(): Int {
        val act = resident.act
        return when {
            act in Act.MOVING || toys.anyMoving() || pages.scrollingRecently() -> Tuning.FPS_MOVING
            act in Act.SLOW -> Tuning.FPS_SLEEP
            else -> Tuning.FPS_IDLE
        }
    }

    /** Rien de visible à animer : lapin caché / en cage ailleurs / hors de l'écran sur une autre page. */
    private fun canSuspend(): Boolean {
        val invisible = resident.act == Act.HIDDEN || bunnyAway || pages.bunnyOffscreen()
        if (!invisible) return false
        return !touch.menuOpen && !windows.bubbleShown && !pages.scrollingRecently() &&
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

        val st = p.stats
        bunny.dirty = st.cleanliness < 45f || st.poops >= 3
        bunny.veryDirty = st.cleanliness < 20f
        bunny.expr = p.mind.exprOverride ?: moodExpr(st)
        bunny.walkPhase = p.body.walkPhase
        bunny.update(dt)
        placeBunny()
        windows.updateBubble(dt)

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
        val st = p.stats
        val newPoops = st.tick(min(hours, 48f))
        repeat(newPoops) {
            if (toys.poopCount() < st.poops) {
                toys.spawnPoop((p.body.fx - p.body.facing * 40 * arena.d).coerceIn(arena.minX, arena.maxX))
            }
        }
        if (newPoops > 0 && p.act != Act.SLEEP) speech.sayOneOf(Lines.POOPED)
        if (now - lastSaveWall > Tuning.SAVE_EVERY_MS) { st.save(); lastSaveWall = now }
    }

    private fun moodExpr(st: PetState): Expr = when (st.mood) {
        PetState.Mood.HAPPY, PetState.Mood.OK, PetState.Mood.DIRTY -> Expr.NORMAL
        PetState.Mood.HUNGRY, PetState.Mood.SAD -> Expr.SAD
        PetState.Mood.SLEEPY -> Expr.SLEEPY
        PetState.Mood.SICK -> Expr.SICK
    }

    // =====================================================================
    //  Comportement (machine à états)
    // =====================================================================
    private fun updateBehavior(p: Pet, dt: Float) {
        val b = p.body
        val m = p.mind
        val st = p.stats
        val d = arena.d
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
                    st.isSick || st.energy < 20f -> Tuning.WALK_SLOW_DP * d
                    st.happiness > 75f -> Tuning.WALK_HAPPY_DP * d
                    else -> Tuning.WALK_NORMAL_DP * d
                } * (if (st.personality == Personality.LAZY) Tuning.LAZY_SPEED_FACTOR else 1f)
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
            Act.EAT -> {
                b.pose = Pose.EAT
                bunny.eatProgress = b.actTime / 3f
                if (b.actTime >= 3f) {
                    bunny.eatProgress = 0f
                    st.feed(toys.eatingFood)
                    emit(p, PType.HEART, 3)
                    p.express(Expr.HAPPY, 2f)
                    speech.sayOneOf(Lines.ATE)
                    p.setAct(Act.IDLE, 3f)
                }
            }
            Act.GOTO_FOOD -> toys.updateGoToFood(p, dt)
            Act.PLAY -> toys.updatePlay(p, dt)
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
            Act.GOTO_CHEST -> stations.updateGoToChest(p, dt)
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
            Act.BATH -> stations.updateBath(p, dt)
            Act.VET -> {
                b.pose = Pose.SIT
                actor.faceToward(p, arena.screenW / 2)
            }
            Act.CAGED -> stations.updateCaged(p, dt)
            Act.SQUAT -> mischief.updateSquat(p, dt)
            Act.HIDE_RUN, Act.HIDDEN, Act.PEEK -> mischief.updateHide(p, dt)
            Act.KNOCK -> mischief.updateKnock(p, dt)
        }
    }

    private fun decideNext(p: Pet) {
        val b = p.body
        val st = p.stats
        val boxes = platforms()
        val input = BrainInput(
            energy = st.energy, satiety = st.satiety, happiness = st.happiness,
            personality = st.personality, hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
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
        bunny.airVy = b.phys.airVy
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
                p.stats.happiness = (p.stats.happiness - 4f).coerceAtLeast(0f)
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
        pet.stats.sleeping = true
        pet.stats.save()
        speech.sayOneOf(Lines.GOING_TO_SLEEP)
        if (stations.inStation == StationKind.CAGE) return   // il dort dans sa cage
        if (stations.inStation != null) stations.leaveStation(hopOut = false)
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

    override fun forwardTouch(ev: MotionEvent): Boolean =
        (if (::touch.isInitialized) touch.listener else null)?.onTouch(bunny, ev) ?: false

    // =====================================================================
    //  Paroles (répliques dans [Lines], tirage et bulle dans [Speech])
    // =====================================================================
    override fun say(text: String, seconds: Float) = speech.say(text, seconds)

    override fun pick(vararg s: String) = speech.pick(*s)

    private fun chatter(p: Pet) {
        say(speech.chatterLine(p.stats.mood))
        if (p.stats.mood == PetState.Mood.HAPPY && rnd.nextFloat() < 0.3f) startDance(p)
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
        p.stats.visitApp()
        val comment = commentFor(app)
        val then = when (app.category) {
            ApplicationInfo.CATEGORY_VIDEO -> Act.SIT
            ApplicationInfo.CATEGORY_AUDIO -> Act.DANCE
            else -> Act.IDLE
        }
        val act = p.act
        if (act == Act.CAGED || act == Act.VET || p.isHiding) return
        if (act in Act.BUSY_FOR_TRAVEL) {
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
    override val context: Context get() = this
    override val prefs: SharedPreferences get() = Prefs.of(this)

    override fun appLabel(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { null }

    override fun emit(pet: Pet, type: PType, n: Int) = bunny.emit(type, n)

    override fun haptic(pet: Pet, feedback: Int) { bunny.performHapticFeedback(feedback) }

    override fun showFood(pet: Pet, food: Food) { bunny.foodColor = Color.parseColor(food.color) }

    override fun vibrate(pattern: LongArray) {
        try {
            val vib = getSystemService(VibratorManager::class.java).defaultVibrator
            vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {}
    }

    override var lastInteraction: Long
        get() = mischief.lastInteraction
        set(v) { mischief.lastInteraction = v }

    override fun noteInteraction() = mischief.noteInteraction()

    override val inStation: StationKind? get() = stations.inStation

    override fun busyInStation(): Boolean = stations.busyInStation()

    override fun endHiding() = mischief.endHiding()

    override fun feed(food: Food, from: Station?) = toys.feed(food, from)

    override fun startPlay(toy: Toy, from: Station?) = toys.startPlay(toy, from)

    override fun setDockHiding(hiding: Boolean) = windows.setDockHiding(hiding)
}
