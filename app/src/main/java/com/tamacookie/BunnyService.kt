package com.tamacookie

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.View
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
 * et câblage des domaines :
 * [BunnyPhysics] (vol), [BunnyBrain] (décisions), [PageTracker] (vues / pages du bureau),
 * [OverlayWindows] (fenêtres, bulle, dock), [StationController] (bain, véto, cage, coffre),
 * [ToyController] (jouets, nourriture, crottes), [MischiefController] (bêtises, cache-cache).
 */
class BunnyService : Service(), Choreographer.FrameCallback {

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

    // --- Domaines ---
    internal lateinit var pet: PetState
    internal lateinit var bunny: BunnyView
    internal lateinit var bunnyLp: WindowManager.LayoutParams
    internal lateinit var windows: OverlayWindows
    internal lateinit var pages: PageTracker
    internal lateinit var stations: StationController
    internal lateinit var toys: ToyController
    internal lateinit var mischief: MischiefController
    internal lateinit var phys: BunnyPhysics
    internal val rnd = Random(System.nanoTime())
    internal val brain = BunnyBrain(rnd)
    internal val handler = Handler(Looper.getMainLooper())
    /** Paroles : bulle (si les fenêtres existent) + reprise de la boucle. */
    internal val speech = Speech(rnd) { text, seconds ->
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

    // --- Géométrie (pixels) ---
    internal var d = 1f
    internal var bw = 0
    internal var bh = 0
    private var feetPad = 0f
    internal var screenW = 0f
    internal var groundY = 0f
    internal var topY = 0f
    internal var minX = 0f
    internal var maxX = 0f

    // --- Corps du lapin (fx,fy = position des pieds, dans les coordonnées de sa page) ---
    internal var fx: Float
        get() = phys.x
        set(v) { phys.x = v }
    internal var fy: Float
        get() = phys.y
        set(v) { phys.y = v }

    // --- Comportement ---
    internal var act = Act.IDLE
        private set
    internal var actTime = 0f
    private var actDuration = 3f
    internal var targetX = 0f
    internal var afterAir = Act.IDLE
    private var exprOverride: Expr? = null
    private var exprTimer = 0f
    private var walkPhase = 0f
    internal var playTime = 0f
    internal var kickCooldown = 0f
    private var danceTime = 0f
    private var danceHopIn = 0f
    internal var travelComment: String? = null
    internal var travelThen = Act.IDLE
    private var lastTravelAt = 0L
    internal var chatterIn = 25f

    // --- Plateforme (icône / bouton) sur laquelle il se tient, null = le sol ---
    internal var platform: Box? = null
    private var platformLabel: String? = null
    private var platVersionSeen = -1
    private var platCacheVersion = -1
    private var platList: List<Platforms.P> = emptyList()
    private var platBoxes: List<Box> = emptyList()

    // --- Visibilité ---
    internal var hidden = false
        private set
    /** Invisible ici (en cage sur une autre page, ou caché) : sa fenêtre est retirée. */
    internal var bunnyAway = false
        private set
    internal var bunnyAdded = false
        private set
    /** Écouteur tactile du lapin (la cage lui transmet les gestes). */
    internal val bunnyTouch: View.OnTouchListener?
        get() = if (::touch.isInitialized) touch.listener else null

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
            pet = { if (::pet.isInitialized) pet else null },
            hidden = { hidden },
            hiding = { ::mischief.isInitialized && mischief.hidingView != null },
        )
        notifier.createChannels()
        if (!Settings.canDrawOverlays(this)) {
            // Lancé par startForegroundService : il faut tout de même passer au premier plan avant de s'arrêter
            tryStartForeground()
            stopSelf(); return
        }
        d = resources.displayMetrics.density
        bw = (Tuning.BUNNY_W_DP * d).toInt()
        bh = (Tuning.BUNNY_H_DP * d).toInt()
        feetPad = Tuning.FEET_PAD_DP * d
        phys = BunnyPhysics(d)
        pet = PetState(this).load()
        watcher = ForegroundAppWatcher(this)
        windows = OverlayWindows(this)
        pages = PageTracker(this)
        stations = StationController(this)
        toys = ToyController(this)
        mischief = MischiefController(this)
        touch = TouchHandler(this)

        computeBounds()
        fx = screenW * (0.3f + rnd.nextFloat() * 0.4f)
        fy = topY + Tuning.SPAWN_DROP_DP * d          // il tombe du haut de l'écran en arrivant
        phys.vy = 0f
        setAct(Act.AIR); afterAir = Act.IDLE
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
            if (pet.caged) stations.enterStation(StationKind.CAGE, announce = false)
            applyView()
        }, 300)
        lastTickWall = System.currentTimeMillis()
        lastSaveWall = lastTickWall

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_ON)
        }, Context.RECEIVER_NOT_EXPORTED)

        startLoop()
        handler.postDelayed(appPoll, 2000)
        handler.postDelayed({
            say(speech.greeting(pet.name, pet.mood, Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
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
                pet.reloadAppearance()
                mischief.loadLevel()
                applyView()
                bunny.look = pet.look
                bunny.emit(PType.SPARKLE, 6)
                speech.sayOneOf(Lines.newLook(pet.name))
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
            pet.save()
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
            fx = fx.coerceIn(minX, maxX)
            platform = null
            if (act != Act.AIR && act != Act.DRAGGED) fy = groundY
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
                Intent.ACTION_SCREEN_OFF -> { awake = false; stopLoop(); handler.removeCallbacks(appPoll); pet.save() }
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
        if (greet && !pet.sleeping) speech.sayOneOf(Lines.WELCOME_BACK)
    }

    // =====================================================================
    //  Fenêtres
    // =====================================================================
    private fun createWindows() {
        bunny = BunnyView(this)
        bunny.look = pet.look
        bunnyLp = windows.params(bw, bh, touchable = true)
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

    internal fun hide() {
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
    internal fun setBunnyAway(away: Boolean, fadeIn: Boolean = false) {
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
        screenW = b.width().toFloat()
        groundY = b.height() - ins.bottom - Tuning.GROUND_MARGIN_DP * d
        topY = ins.top.toFloat()
        minX = ins.left + Tuning.SIDE_MARGIN_DP * d
        maxX = screenW - ins.right - Tuning.SIDE_MARGIN_DP * d
        phys.setBounds(minX, maxX, topY, groundY)
    }

    internal fun placeBunny() {
        val nx = (fx - bw / 2f + pages.bunnyShift).roundToInt()
        val ny = (fy - (bh - feetPad)).roundToInt()
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
    internal fun layoutAnchored() {
        if (hidden) return
        stations.layout()
        toys.layoutPoops()
        if (stations.inStation == StationKind.CAGE) stations.followCage() else pages.updateBunnyPage()
    }

    /** Changement de vue : bain/véto interrompus, cache-cache. */
    internal fun applyView() {
        if (hidden) return
        val st = stations.inStation
        if (st != null && st != StationKind.CAGE && !stations.anchoredHere(st)) stations.leaveStation(hopOut = false)
        stations.refreshStationsPanel()
        mischief.onViewChanged()
        if (stations.inStation == StationKind.CAGE) { layoutAnchored(); return }
        setBunnyAway(act == Act.HIDDEN, fadeIn = true)
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
    internal fun requestFrames() {
        if (!looping || !suspended) return
        suspended = false
        handler.removeCallbacks(suspendedTick)
        lastFrameNs = 0L
        if (bunnyAdded) windows.update(bunny, bunnyLp)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun targetFps(): Int = when {
        act in Act.MOVING || toys.anyMoving() || pages.scrollingRecently() -> Tuning.FPS_MOVING
        act in Act.SLOW -> Tuning.FPS_SLEEP
        else -> Tuning.FPS_IDLE
    }

    /** Rien de visible à animer : lapin caché / en cage ailleurs / hors de l'écran sur une autre page. */
    private fun canSuspend(): Boolean {
        val invisible = act == Act.HIDDEN || bunnyAway || pages.bunnyOffscreen()
        if (!invisible) return false
        return !touch.menuOpen && !windows.bubbleShown && !pages.scrollingRecently() &&
            !toys.hasToyOrFood() && !toys.anyMoving() && !stations.animating()
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
            lastSuspendedTick = SystemClock.uptimeMillis()
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
            val now = SystemClock.uptimeMillis()
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
        actTime += dt
        if (exprTimer > 0f) { exprTimer -= dt; if (exprTimer <= 0f) exprOverride = null }
        if (kickCooldown > 0f) kickCooldown -= dt
        checkView()
        pages.checkPageReturn()
        mischief.checkUserEvents()
        stations.update(dt)

        tickStats(force = false)
        toys.update(dt)
        checkPlatform()
        updateBehavior(dt)
        stations.checkChestBump(dt)

        bunny.dirty = pet.cleanliness < 45f || pet.poops >= 3
        bunny.veryDirty = pet.cleanliness < 20f
        bunny.expr = exprOverride ?: moodExpr()
        bunny.walkPhase = walkPhase
        bunny.update(dt)
        placeBunny()
        windows.updateBubble(dt)

        menuTimer += dt
        if (menuTimer > 1f) { menuTimer = 0f; touch.refreshMenu() }
        notifTimer += dt
        if (notifTimer > 20f) { notifTimer = 0f; updateNotification(force = false); notifier.checkAlerts() }
    }

    private fun tickStats(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastTickWall < Tuning.STATS_EVERY_MS) return
        val hours = (now - lastTickWall) / 3_600_000f
        lastTickWall = now
        val newPoops = pet.tick(min(hours, 48f))
        repeat(newPoops) {
            if (toys.poopCount() < pet.poops) {
                toys.spawnPoop((fx - bunny.facing * 40 * d).coerceIn(minX, maxX))
            }
        }
        if (newPoops > 0 && act != Act.SLEEP) speech.sayOneOf(Lines.POOPED)
        if (now - lastSaveWall > Tuning.SAVE_EVERY_MS) { pet.save(); lastSaveWall = now }
    }

    private fun moodExpr(): Expr = when (pet.mood) {
        PetState.Mood.HAPPY, PetState.Mood.OK, PetState.Mood.DIRTY -> Expr.NORMAL
        PetState.Mood.HUNGRY, PetState.Mood.SAD -> Expr.SAD
        PetState.Mood.SLEEPY -> Expr.SLEEPY
        PetState.Mood.SICK -> Expr.SICK
    }

    internal fun setAct(a: Act, duration: Float = 3f) {
        act = a; actTime = 0f; actDuration = duration
    }

    /** Comme [setAct], mais en plein vol l'activité est seulement programmée pour l'atterrissage. */
    internal fun setActOrAfterAir(a: Act, duration: Float = 3f) {
        if (act == Act.AIR) afterAir = a else setAct(a, duration)
    }

    internal fun express(e: Expr, seconds: Float) { exprOverride = e; exprTimer = seconds }

    // =====================================================================
    //  Comportement (machine à états)
    // =====================================================================
    private fun updateBehavior(dt: Float) {
        when (act) {
            Act.IDLE -> {
                bunny.pose = Pose.IDLE
                fy = surfaceY()
                if (toys.hasToy()) { setAct(Act.PLAY); return }
                if (toys.hasFood()) { setAct(Act.GOTO_FOOD); return }
                chatterIn -= dt
                if (chatterIn <= 0f) { chatterIn = 50f + rnd.nextFloat() * 80f; chatter() }
                if (actTime > actDuration) decideNext()
            }
            Act.WALK -> {
                val speed = when {
                    pet.isSick || pet.energy < 20f -> Tuning.WALK_SLOW_DP * d
                    pet.happiness > 75f -> Tuning.WALK_HAPPY_DP * d
                    else -> Tuning.WALK_NORMAL_DP * d
                } * (if (pet.personality == Personality.LAZY) Tuning.LAZY_SPEED_FACTOR else 1f)
                if (walkToward(targetX, speed, dt)) setAct(Act.IDLE, 2f + rnd.nextFloat() * 4f)
            }
            Act.AIR -> stepAir(dt)
            Act.DRAGGED -> bunny.pose = Pose.DRAGGED
            Act.SLEEP -> {
                bunny.pose = Pose.SLEEP
                fy = surfaceY()
                if (!pet.sleeping) {
                    express(Expr.HAPPY, 2f)
                    speech.sayOneOf(Lines.WOKE_UP)
                    setAct(Act.IDLE, 3f)
                }
            }
            Act.EAT -> {
                bunny.pose = Pose.EAT
                bunny.eatProgress = actTime / 3f
                if (actTime >= 3f) {
                    bunny.eatProgress = 0f
                    pet.feed(toys.eatingFood)
                    bunny.emit(PType.HEART, 3)
                    express(Expr.HAPPY, 2f)
                    speech.sayOneOf(Lines.ATE)
                    setAct(Act.IDLE, 3f)
                }
            }
            Act.GOTO_FOOD -> toys.updateGoToFood(dt)
            Act.PLAY -> toys.updatePlay(dt)
            Act.TRAVEL_OUT -> {
                if (walkToward(targetX, Tuning.TRAVEL_OUT_DP * d, dt, clamp = false)) {
                    // Il "passe" dans l'application suivante : réapparaît de l'autre côté
                    fx = if (targetX < 0) screenW + bw else -bw.toFloat()
                    targetX = screenW * (0.2f + rnd.nextFloat() * 0.6f)
                    if (travelThen == Act.SIT) targetX = maxX - 10 * d
                    setAct(Act.TRAVEL_IN)
                }
            }
            Act.TRAVEL_IN -> {
                if (walkToward(targetX, Tuning.TRAVEL_IN_DP * d, dt, clamp = false)) {
                    travelComment?.let { say(it, 4.5f) }
                    travelComment = null
                    when (travelThen) {
                        Act.SIT -> setAct(Act.SIT, 40f)
                        Act.DANCE -> startDance()
                        else -> setAct(Act.IDLE, 4f)
                    }
                }
            }
            Act.GOTO_CHEST -> stations.updateGoToChest(dt)
            Act.PAGE_OUT -> pages.updatePageOut(dt)
            Act.SIT -> {
                bunny.pose = Pose.SIT
                bunny.facing = -1f
                if (actTime > actDuration) setAct(Act.IDLE, 2f)
            }
            Act.DIZZY -> {
                bunny.pose = Pose.IDLE
                express(Expr.DIZZY, 0.2f)
                if (actTime > 2.2f) { exprOverride = null; setAct(Act.IDLE, 2f) }
            }
            Act.DANCE -> {
                bunny.pose = Pose.IDLE
                danceTime += dt
                danceHopIn -= dt
                if (danceTime > 9f) { setAct(Act.IDLE, 3f); return }
                if (danceHopIn <= 0f) {
                    danceHopIn = 0.55f
                    bunny.facing = -bunny.facing
                    bunny.emit(PType.NOTE)
                    jump(-420 * d, 0f, then = Act.DANCE)
                }
            }
            Act.BATH -> stations.updateBath(dt)
            Act.VET -> {
                bunny.pose = Pose.SIT
                faceToward(screenW / 2)
            }
            Act.CAGED -> stations.updateCaged(dt)
            Act.SQUAT -> mischief.updateSquat(dt)
            Act.HIDE_RUN, Act.HIDDEN, Act.PEEK -> mischief.updateHide(dt)
            Act.KNOCK -> mischief.updateKnock(dt)
        }
    }

    /** Marche vers x. Retourne true une fois arrivé. */
    internal fun walkToward(x: Float, speed: Float, dt: Float, stopDist: Float = 3 * d, clamp: Boolean = true): Boolean {
        if (platform != null) {
            // Perché sur une icône : il saute d'abord au sol puis reprend sa route
            val dir = if (x > fx) 1f else -1f
            jumpTo((fx + dir * 70 * d).coerceIn(minX, maxX), groundY, then = act)
            return false
        }
        val tx = if (clamp) x.coerceIn(minX, maxX) else x
        val dx = tx - fx
        fy = groundY
        if (abs(dx) <= stopDist) {
            walkPhase = 0f
            return true
        }
        bunny.pose = Pose.WALK
        bunny.facing = sign(dx)
        val step = min(abs(dx), speed * dt)
        fx += sign(dx) * step
        walkPhase = (walkPhase + dt * (1.6f + speed / (60 * d))) % 1f
        return false
    }

    internal fun faceToward(x: Float) { if (abs(x - fx) > 2 * d) bunny.facing = sign(x - fx) }

    private fun decideNext() {
        val boxes = platforms()
        val input = BrainInput(
            energy = pet.energy, satiety = pet.satiety, happiness = pet.happiness,
            personality = pet.personality, hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            x = fx, facing = bunny.facing, minX = minX, maxX = maxX, screenW = screenW, density = d,
            platform = platform, platforms = boxes,
            elsewhere = pages.bunnyElsewhere(),
            canPageWander = pages.canPageWander(),
            chestVisible = stations.visibleChest() != null,
            hasToyOrFood = toys.hasToyOrFood(),
            nowMs = System.currentTimeMillis(),
            lastSelfServeMs = stations.selfServeAt,
        )
        when (val dec = brain.decide(input) { mischief.maybeSeekAttention() }) {
            Decision.Sleep -> startSleep()
            Decision.AttentionSought -> {}
            Decision.GoToChest -> { stations.selfServeAt = input.nowMs; setAct(Act.GOTO_CHEST) }
            Decision.PageWander -> pages.startPageWander()
            is Decision.JumpDown -> jumpTo(dec.targetX, groundY, Act.IDLE)
            is Decision.JumpToPlatform -> {
                bunny.facing = sign(dec.target.centerX - fx)
                jumpTo(dec.target.centerX, dec.target.top, Act.IDLE)
            }
            is Decision.TurnAround -> { bunny.facing = -bunny.facing; setAct(Act.IDLE, dec.idleSeconds) }
            is Decision.Idle -> setAct(Act.IDLE, dec.seconds)
            is Decision.Walk -> { targetX = dec.targetX; setAct(Act.WALK) }
            is Decision.Hop -> jump(dec.vy, dec.vx, then = Act.IDLE)
        }
    }

    private fun surfaceY(): Float = platform?.top ?: groundY

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

    /** Saut balistique pour atterrir précisément en (tx, ty). */
    internal fun jumpTo(tx: Float, ty: Float, then: Act) {
        platform = null
        phys.jumpTo(tx, ty, (Tuning.JUMP_APEX_MIN_DP + rnd.nextFloat() * Tuning.JUMP_APEX_RAND_DP) * d)
        afterAir = then
        setAct(Act.AIR)
    }

    internal fun jump(vyStart: Float, vxStart: Float, then: Act) {
        platform = null
        phys.jump(vyStart, vxStart)
        afterAir = then
        setAct(Act.AIR)
    }

    /** Si l'icône sous ses pattes a bougé ou disparu (défilement, changement d'app) : il tombe. */
    private fun checkPlatform() {
        val cur = platform ?: return
        if (act == Act.AIR || act == Act.DRAGGED) return
        val boxes = platforms()
        if (platCacheVersion == platVersionSeen) return
        platVersionSeen = platCacheVersion
        val same = boxes.firstOrNull {
            abs(it.top - cur.top) < Tuning.PLATFORM_SAME_DP * d && fx >= it.left && fx <= it.right
        }
        if (same != null) {
            platform = same
            fy = same.top
            return
        }
        platform = null
        phys.stop()
        afterAir = if (act == Act.SLEEP) Act.SLEEP else Act.IDLE
        express(Expr.SURPRISED, 1f)
        if (act != Act.SLEEP) speech.sayOneOf(Lines.PLATFORM_LOST, 1.5f)
        setAct(Act.AIR)
    }

    private fun stepAir(dt: Float) {
        bunny.pose = Pose.AIR
        val ev = phys.stepAir(dt, platforms())
        bunny.airVy = phys.airVy
        if (phys.facing != 0f) bunny.facing = phys.facing
        when (ev) {
            AirEvent.ForcedLanded -> setAct(afterAir, 3f)
            AirEvent.Bounced -> bunny.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            is AirEvent.Landed -> onLanded(ev)
            AirEvent.Flying, AirEvent.HitWall -> {}
        }
    }

    /** Atterrissage sur une icône ou au sol : étourdi si brutal, sinon il reprend l'activité prévue. */
    private fun onLanded(ev: AirEvent.Landed) {
        if (ev.onPlatform) {
            platform = platBoxes[ev.platform]
            platformLabel = platList.getOrNull(ev.platform)?.label
            platVersionSeen = platCacheVersion
            bunny.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (ev.hard) {
            if (ev.onPlatform) speech.sayOneOf(Lines.HARD_LANDING_PLATFORM)
            else {
                speech.sayOneOf(Lines.HARD_LANDING_GROUND)
                pet.happiness = (pet.happiness - 4f).coerceAtLeast(0f)
            }
            setAct(Act.DIZZY)
            return
        }
        val next = afterAir
        val idle = if (ev.onPlatform) 2f + rnd.nextFloat() * 3f else 1.5f + rnd.nextFloat() * 2f
        setAct(next, if (next == Act.IDLE) idle else 3f)
        val lbl = platformLabel
        if (ev.onPlatform && !lbl.isNullOrBlank() && next == Act.IDLE && rnd.nextFloat() < 0.35f) {
            speech.sayOneOf(Lines.onPlatform(lbl), 2.2f)
        }
    }

    internal fun startSleep() {
        // Dodo pendant le cache-cache : la partie s'arrête proprement
        if (mischief.isHiding()) {
            mischief.endHiding()
            fx = fx.coerceIn(minX, maxX)
        }
        pet.sleeping = true
        pet.save()
        speech.sayOneOf(Lines.GOING_TO_SLEEP)
        if (stations.inStation == StationKind.CAGE) return   // il dort dans sa cage
        if (stations.inStation != null) stations.leaveStation(hopOut = false)
        setActOrAfterAir(Act.SLEEP)
    }

    private fun startDance() {
        danceTime = 0f; danceHopIn = 0f
        setAct(Act.DANCE)
    }

    // =====================================================================
    //  Toucher, glisser / lancer, menu : voir [TouchHandler]
    // =====================================================================
    internal fun closeMenu() { if (::touch.isInitialized) touch.closeMenu() }

    // =====================================================================
    //  Paroles (répliques dans [Lines], tirage et bulle dans [Speech])
    // =====================================================================
    internal fun say(text: String, seconds: Float = Speech.DEFAULT_SECONDS) = speech.say(text, seconds)

    internal fun pick(vararg s: String) = speech.pick(*s)

    private fun chatter() {
        say(speech.chatterLine(pet.mood))
        if (pet.mood == PetState.Mood.HAPPY && rnd.nextFloat() < 0.3f) startDance()
    }

    // =====================================================================
    //  Voyage d'application en application
    // =====================================================================
    internal fun foregroundPkg(): String? = if (::watcher.isInitialized) watcher.currentPkg else null

    private val appPoll = object : Runnable {
        override fun run() {
            if (awake && !hidden) {
                try {
                    watcher.poll()?.let { onAppChanged(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "poll", e)
                }
            }
            handler.postDelayed(this, 1500)
        }
    }

    private fun onAppChanged(app: ForegroundAppWatcher.AppInfo) {
        pet.visitApp()
        val comment = commentFor(app)
        val then = when (app.category) {
            ApplicationInfo.CATEGORY_VIDEO -> Act.SIT
            ApplicationInfo.CATEGORY_AUDIO -> Act.DANCE
            else -> Act.IDLE
        }
        if (act == Act.CAGED || act == Act.VET || mischief.isHiding()) return
        if (act in Act.BUSY_FOR_TRAVEL) {
            if (act != Act.SLEEP && rnd.nextFloat() < 0.5f) say(comment, 3f)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTravelAt < Tuning.TRAVEL_COOLDOWN_MS) { say(comment, 3f); return }
        lastTravelAt = now
        closeMenu()
        travelComment = comment
        travelThen = then
        // Il court vers le bord le plus proche et "saute" dans la nouvelle app
        targetX = if (fx < screenW / 2) -bw.toFloat() else screenW + bw
        speech.sayOneOf(Lines.FOLLOWING, 1.5f)
        setAct(Act.TRAVEL_OUT)
        requestFrames()
    }

    private fun commentFor(app: ForegroundAppWatcher.AppInfo): String =
        speech.appComment(app.label, app.pkg, app.category, app.isHome)

    // =====================================================================
    //  Notifications : voir [PetNotifier]
    // =====================================================================
    internal fun updateNotification(force: Boolean) = notifier.update(force)
}
