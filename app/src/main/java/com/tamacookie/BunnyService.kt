package com.tamacookie

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.BunnyView.Pose
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Service de premier plan qui fait vivre Cookie en surimpression sur l'écran.
 */
class BunnyService : Service(), Choreographer.FrameCallback {

    companion object {
        const val ACTION_FEED = "com.tamacookie.FEED"
        const val ACTION_TOGGLE = "com.tamacookie.TOGGLE"
        const val ACTION_STOP = "com.tamacookie.STOP"
        const val ACTION_RELOAD = "com.tamacookie.RELOAD"
        private const val NOTIF_ID = 1
        private const val CH_PET = "pet"
        private const val CH_ALERT = "alerts"

        @Volatile var running = false
            private set

        fun start(ctx: Context) {
            ctx.getSharedPreferences("pet", MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
            ctx.startForegroundService(Intent(ctx, BunnyService::class.java))
        }

        fun send(ctx: Context, action: String) {
            if (running) ctx.startService(Intent(ctx, BunnyService::class.java).setAction(action))
        }

        fun stop(ctx: Context) {
            ctx.getSharedPreferences("pet", MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            ctx.stopService(Intent(ctx, BunnyService::class.java))
        }
    }

    private enum class Act { IDLE, WALK, AIR, DRAGGED, SLEEP, EAT, GOTO_FOOD, PLAY, TRAVEL_OUT, TRAVEL_IN, SIT, DIZZY, DANCE, BATH, VET, CAGED, SQUAT, KNOCK, HIDE_RUN, HIDDEN, PEEK, PAGE_OUT, GOTO_CHEST }

    private lateinit var wm: WindowManager
    private lateinit var pet: PetState
    private lateinit var bunny: BunnyView
    private lateinit var bunnyLp: WindowManager.LayoutParams
    private lateinit var bubble: SpeechBubble
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private lateinit var watcher: ForegroundAppWatcher
    private val handler = Handler(Looper.getMainLooper())
    private val rnd = Random(System.nanoTime())

    private var menu: PetMenu? = null
    private var menuLp: WindowManager.LayoutParams? = null
    private var lastMenuClose = 0L
    private val props = ArrayList<Prop>()

    // Géométrie (pixels)
    private var d = 1f
    private var bw = 0
    private var bh = 0
    private var feetPad = 0f
    private var screenW = 0f
    private var groundY = 0f
    private var topY = 0f
    private var minX = 0f
    private var maxX = 0f
    private var gravity = 0f

    // Physique / comportement du lapin (fx,fy = position des pieds à l'écran)
    private var fx = 0f
    private var fy = 0f
    private var vx = 0f
    private var vy = 0f
    private var act = Act.IDLE
    private var actTime = 0f
    private var actDuration = 3f
    private var targetX = 0f
    private var afterAir = Act.IDLE
    private var hardLanding = false
    private var exprOverride: Expr? = null
    private var exprTimer = 0f
    private var walkPhase = 0f
    private var playTime = 0f
    private var kickCooldown = 0f
    private var danceTime = 0f
    private var danceHopIn = 0f
    private var travelComment: String? = null
    private var travelThen = Act.IDLE
    private var lastTravelAt = 0L
    private var chatterIn = 25f
    private val tapTimes = ArrayDeque<Long>()

    // Plateforme (icône / bouton) sur laquelle il se tient, null = le sol
    private var platform: Rect? = null
    private var platformLabel: String? = null
    private var platVersionSeen = -1

    // Bulle
    private var bubbleShown = false
    private var bubbleTime = 0f
    private var bubbleW = 0
    private var bubbleH = 0

    // Boucle
    private var hidden = false
    private var looping = false
    private var screenOn = true
    private var lastFrameNs = 0L
    private var lastTickWall = 0L
    private var lastSaveWall = 0L
    private var lastNotifText = ""
    private var notifTimer = 0f
    private var menuTimer = 0f
    private var lastAlertMood: PetState.Mood? = null

    // =====================================================================
    //  Cycle de vie
    // =====================================================================
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(WindowManager::class.java)
        d = resources.displayMetrics.density
        bw = (130 * d).toInt()
        bh = (140 * d).toInt()
        feetPad = 6 * d
        gravity = 2600 * d
        pet = PetState(this).load()
        watcher = ForegroundAppWatcher(this)

        createChannels()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf(); return
        }

        computeBounds()
        fx = screenW * (0.3f + rnd.nextFloat() * 0.4f)
        fy = topY + 150 * d          // il tombe du haut de l'écran en arrivant
        vy = 0f
        setAct(Act.AIR); afterAir = Act.IDLE

        val prefs = getSharedPreferences("pet", MODE_PRIVATE)
        mischief = prefs.getInt("mischief", 1)
        stationsAlways = false
        createWindows()
        restorePoops()
        handler.postDelayed({
            currentView = computeView()
            if (pet.caged) enterStation(StationKind.CAGE, announce = false)
            applyView(0)
        }, 300)
        lastTickWall = System.currentTimeMillis()
        lastSaveWall = lastTickWall

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_ON)
        })

        startLoop()
        handler.postDelayed(appPoll, 2000)
        handler.postDelayed({ say(greeting()) }, 900)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FEED -> { if (hidden) show(); feed() }
            ACTION_TOGGLE -> if (hidden) show() else hide()
            ACTION_STOP -> { stop(this); return START_NOT_STICKY }
            ACTION_RELOAD -> {
                pet.load()
                val prefs = getSharedPreferences("pet", MODE_PRIVATE)
                mischief = prefs.getInt("mischief", 1)
                stationsAlways = false
                applyView(0)
                if (::bunny.isInitialized) {
                    bunny.look = pet.look
                    bunny.emit(PType.SPARKLE, 6)
                }
                say(pick("Je m'appelle ${pet.name} ! ✨", "Tu aimes mon nouveau look ? ✨"))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        stopLoop()
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        if (::pet.isInitialized) { pet.save() }
        removeAllWindows()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handler.post {
            computeBounds()
            // nouvelle orientation : la largeur des pages du bureau change
            Platforms.pageW = 0f; savedPageW = -1f; scrollShown = Float.NaN
            fx = fx.coerceIn(minX, maxX)
            platform = null
            if (act != Act.AIR && act != Act.DRAGGED) fy = groundY
            for (p in props) {
                p.x = p.x.coerceIn(minX, maxX)
                if (p.onGround || p.view.kind == PropView.Kind.POOP) p.y = groundY
            }
            closeMenu()
            for (s in stations.values) { placeStation(s, s.cx, s.cy) }
            placeDock(dockLp.x.toFloat(), dockLp.y.toFloat())
            inStation?.let { positionInStation(it) }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; stopLoop(); pet.save() }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (!screenOn) {
                        screenOn = true
                        tickStats(force = true)
                        if (!hidden) startLoop()
                        if (i.action == Intent.ACTION_USER_PRESENT && !pet.sleeping) say(pick("Te revoilà ! 💕", "Coucou toi !", "Tu m'as manqué !"))
                    }
                }
            }
        }
    }

    // =====================================================================
    //  Fenêtres
    // =====================================================================
    private fun overlayParams(w: Int, h: Int, touchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            // Android 12+ : une fenêtre non tactile doit avoir une opacité <= 0.8
            // pour laisser passer les touchers vers l'application en dessous.
            if (!touchable) alpha = 0.8f
        }
    }

    private fun createWindows() {
        bunny = BunnyView(this)
        bunny.look = pet.look
        bunnyLp = overlayParams(bw, bh, touchable = true)
        placeBunny()
        wm.addView(bunny, bunnyLp)
        bunnyAdded = true
        setupBunnyTouch()
        createStations()
        createDock()

        bubble = SpeechBubble(this)
        bubbleLp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, touchable = false)
        bubbleLp.alpha = 0.8f
    }

    private fun removeAllWindows() {
        closeMenu()
        hideBubble()
        closeVetPanel()
        closeStationsPanel()
        closeChest()
        if (bunnyAdded) { safeRemove(bunny); bunnyAdded = false }
        for (p in props) if (p.attached) { safeRemove(p.view); p.attached = false }
        for (s in stations.values) if (s.shown) { safeRemove(s.view); s.shown = false }
        if (dockAdded) { safeRemove(dock); dockAdded = false }
    }

    /**
     * Retire une fenêtre sans dépendre de isAttachedToWindow (qui ne passe à true
     * qu'après la première frame : un retrait trop rapide laissait une fenêtre orpheline).
     */
    private fun safeRemove(v: View) {
        try { wm.removeViewImmediate(v) } catch (e: Exception) {
            android.util.Log.w("TamaCookie", "removeView", e)
        }
    }

    private var bunnyAdded = false

    private fun hide() {
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
        wm.addView(bunny, bunnyLp)
        bunnyAdded = true
        for (p in props) if (p.view.kind != PropView.Kind.POOP || p.anchor == currentView) {
            placeProp(p, layout = false); wm.addView(p.view, p.lp); p.attached = true
        }
        wm.addView(dock, dockLp); dockAdded = true
        refreshStations()
        applyView(0)   // gère aussi le lapin en cage sur une autre vue
        startLoop()
        say(pick("Coucou, me revoilà !", "Je suis là ! 🐰", "Hop !"))
        updateNotification(force = true)
    }

    @Suppress("DEPRECATION")
    private fun computeBounds() {
        val m = wm.currentWindowMetrics
        val b = m.bounds
        val ins = m.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        screenW = b.width().toFloat()
        groundY = b.height() - ins.bottom - 2 * d
        topY = ins.top.toFloat()
        minX = ins.left + 34 * d
        maxX = screenW - ins.right - 34 * d
    }

    private fun placeBunny() {
        val nx = (fx - bw / 2f + bunnyShift).roundToInt()
        val ny = (fy - (bh - feetPad)).roundToInt()
        if (sliding.containsKey(bunny)) return
        if (nx != bunnyLp.x || ny != bunnyLp.y) {
            bunnyLp.x = nx; bunnyLp.y = ny
            if (bunnyAdded) wm.updateViewLayout(bunny, bunnyLp)
        }
    }

    // =====================================================================
    //  Boucle d'animation
    // =====================================================================
    private fun startLoop() {
        if (looping || hidden || !screenOn) return
        looping = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopLoop() {
        looping = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!looping) return
        val dt = if (lastFrameNs == 0L) 0.016f else ((frameTimeNanos - lastFrameNs) / 1e9f).coerceIn(0f, 0.05f)
        lastFrameNs = frameTimeNanos
        try {
            update(dt)
        } catch (e: Exception) {
            android.util.Log.e("TamaCookie", "frame error", e)
        }
        if (looping) Choreographer.getInstance().postFrameCallback(this)
    }

    private fun update(dt: Float) {
        actTime += dt
        if (exprTimer > 0f) { exprTimer -= dt; if (exprTimer <= 0f) exprOverride = null }
        if (kickCooldown > 0f) kickCooldown -= dt
        checkView()
        checkPageReturn()
        checkUserEvents()
        for (s in stations.values) if (s.shown && s.kind == StationKind.BATH) { s.view.t += dt; s.view.invalidate() }
        vetTimer += dt
        if (vetTimer > 1f) { vetTimer = 0f; vetPanel?.refresh() }

        tickStats(force = false)
        updateProps(dt)
        checkPlatform()
        updateBehavior(dt)
        checkChestBump(dt)

        bunny.dirty = pet.cleanliness < 45f || pet.poops >= 3
        bunny.veryDirty = pet.cleanliness < 20f
        bunny.expr = exprOverride ?: moodExpr()
        bunny.walkPhase = walkPhase
        bunny.update(dt)
        placeBunny()
        updateBubble(dt)

        menuTimer += dt
        if (menuTimer > 1f) { menuTimer = 0f; menu?.refresh() }
        notifTimer += dt
        if (notifTimer > 20f) { notifTimer = 0f; updateNotification(force = false); checkAlerts() }
    }

    private fun tickStats(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastTickWall < 10_000) return
        val hours = (now - lastTickWall) / 3_600_000f
        lastTickWall = now
        val newPoops = pet.tick(min(hours, 48f))
        repeat(newPoops) {
            if (props.count { it.view.kind == PropView.Kind.POOP } < pet.poops) {
                spawnPoop((fx - bunny.facing * 40 * d).coerceIn(minX, maxX))
            }
        }
        if (newPoops > 0 && act != Act.SLEEP) say(pick("Oups… 💩", "C'est pas moi !", "Hihi… pardon"))
        if (now - lastSaveWall > 60_000) { pet.save(); lastSaveWall = now }
    }

    private fun moodExpr(): Expr = when (pet.mood) {
        PetState.Mood.HAPPY, PetState.Mood.OK, PetState.Mood.DIRTY -> Expr.NORMAL
        PetState.Mood.HUNGRY, PetState.Mood.SAD -> Expr.SAD
        PetState.Mood.SLEEPY -> Expr.SLEEPY
        PetState.Mood.SICK -> Expr.SICK
    }

    private fun setAct(a: Act, duration: Float = 3f) {
        act = a; actTime = 0f; actDuration = duration
    }

    private fun express(e: Expr, seconds: Float) { exprOverride = e; exprTimer = seconds }

    // =====================================================================
    //  Comportement (machine à états)
    // =====================================================================
    private fun updateBehavior(dt: Float) {
        when (act) {
            Act.IDLE -> {
                bunny.pose = Pose.IDLE
                fy = surfaceY()
                if (props.any { it.toy != null }) { setAct(Act.PLAY); return }
                if (props.any { it.view.kind.isFood }) { setAct(Act.GOTO_FOOD); return }
                chatterIn -= dt
                if (chatterIn <= 0f) { chatterIn = 50f + rnd.nextFloat() * 80f; chatter() }
                if (actTime > actDuration) decideNext()
            }
            Act.WALK -> {
                val speed = when {
                    pet.isSick || pet.energy < 20f -> 45 * d
                    pet.happiness > 75f -> 95 * d
                    else -> 75 * d
                } * (if (pet.personality == Personality.LAZY) 0.7f else 1f)
                if (walkToward(targetX, speed, dt)) setAct(Act.IDLE, 2f + rnd.nextFloat() * 4f)
            }
            Act.AIR -> stepAir(dt)
            Act.DRAGGED -> {
                bunny.pose = Pose.DRAGGED
            }
            Act.SLEEP -> {
                bunny.pose = Pose.SLEEP
                fy = surfaceY()
                if (!pet.sleeping) {
                    express(Expr.HAPPY, 2f)
                    say(pick("Bien dormi ! ☀️", "*s'étire* Bonjour !", "Quelle bonne sieste !"))
                    setAct(Act.IDLE, 3f)
                }
            }
            Act.EAT -> {
                bunny.pose = Pose.EAT
                bunny.eatProgress = actTime / 3f
                if (actTime >= 3f) {
                    bunny.eatProgress = 0f
                    pet.feed(eatingFood)
                    bunny.emit(PType.HEART, 3)
                    express(Expr.HAPPY, 2f)
                    say(pick("Miam ! 🥕", "Trop bon !", "Merci ! *rot*", "Délicieux !"))
                    setAct(Act.IDLE, 3f)
                }
            }
            Act.GOTO_FOOD -> {
                val c = props.firstOrNull { it.view.kind.isFood }
                if (c == null) { setAct(Act.IDLE); return }
                if (!c.onGround) {
                    bunny.pose = Pose.IDLE
                    bunny.facing = sign(c.x - fx).takeIf { it != 0f } ?: bunny.facing
                    express(Expr.SURPRISED, 0.3f)
                } else if (walkToward(c.x, 130 * d, dt, stopDist = 26 * d)) {
                    eatingFood = c.view.kind.food
                    bunny.foodColor = android.graphics.Color.parseColor(eatingFood.color)
                    removeProp(c)
                    setAct(Act.EAT, 3f)
                }
            }
            Act.PLAY -> updatePlay(dt)
            Act.TRAVEL_OUT -> {
                if (walkToward(targetX, 260 * d, dt, clamp = false)) {
                    // Il "passe" dans l'application suivante : réapparaît de l'autre côté
                    fx = if (targetX < 0) screenW + bw else -bw.toFloat()
                    targetX = screenW * (0.2f + rnd.nextFloat() * 0.6f)
                    if (travelThen == Act.SIT) targetX = maxX - 10 * d
                    setAct(Act.TRAVEL_IN)
                }
            }
            Act.TRAVEL_IN -> {
                if (walkToward(targetX, 200 * d, dt, clamp = false)) {
                    travelComment?.let { say(it, 4.5f) }
                    travelComment = null
                    when (travelThen) {
                        Act.SIT -> setAct(Act.SIT, 40f)
                        Act.DANCE -> startDance()
                        else -> setAct(Act.IDLE, 4f)
                    }
                }
            }
            Act.GOTO_CHEST -> {
                // Il va se servir tout seul : il se place à côté du coffre et saute dedans
                val s = visibleChest()
                if (s == null || props.any { it.toy != null || it.view.kind.isFood }) { setAct(Act.IDLE, 2f); return }
                val cx = s.lp.x + s.lp.width / 2f - bunnyShift
                val side = if (fx < cx) -1f else 1f
                if (walkToward(cx + side * 110 * d, 140 * d, dt)) {
                    bunny.facing = -side
                    say(pick("Je vais me servir ! 😋", "Hop, au coffre !", "Il y a sûrement des carottes là-dedans…",
                        "Je sais où sont les jouets ! 🎁"), 1.8f)
                    jumpTo(cx, s.lp.y + s.lp.height * 0.6f, Act.IDLE)
                }
            }
            Act.PAGE_OUT -> {
                // Il marche jusqu'au bord… et passe sur la page voisine du bureau
                if (walkToward(targetX, 150 * d, dt, clamp = false)) {
                    val pw = Platforms.pageW
                    if (pw <= 0f || bunnyPage <= 0) { fx = fx.coerceIn(minX, maxX); setAct(Act.IDLE, 2f); return }
                    bunnyPage += wanderDir
                    fx -= wanderDir * pw
                    returnAt = android.os.SystemClock.uptimeMillis() + 15_000 + rnd.nextInt(15_000)
                    targetX = (fx + wanderDir * (150 + rnd.nextFloat() * 350) * d).coerceIn(minX, maxX)
                    setAct(Act.WALK)
                }
            }
            Act.SIT -> {
                bunny.pose = Pose.SIT
                bunny.facing = -1f
                if (actTime > actDuration) setAct(Act.IDLE, 2f)
            }
            Act.DIZZY -> {
                bunny.pose = Pose.IDLE
                exprOverride = Expr.DIZZY; exprTimer = 0.2f
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
            Act.BATH -> {
                bunny.pose = Pose.IDLE
                express(Expr.HAPPY, 0.2f)
                if (rnd.nextFloat() < dt * 14f) bunny.emit(PType.SPARKLE)
                if (rnd.nextFloat() < dt * 6f) bunny.emit(PType.BUBBLE)
                if (actTime > 4f) {
                    pet.clean()
                    say(pick("Tout propre ! ✨", "Ça sent bon la fraise !", "Brrr… mais je brille !"))
                    leaveStation(hopOut = true)
                }
            }
            Act.VET -> {
                bunny.pose = Pose.SIT
                faceToward(screenW / 2)
            }
            Act.CAGED -> {
                bunny.pose = if (pet.sleeping) Pose.SLEEP else Pose.SIT
                if (!pet.sleeping) {
                    exprOverride = Expr.SAD; exprTimer = 0.3f
                    chatterIn -= dt
                    if (chatterIn <= 0f) {
                        chatterIn = 25f + rnd.nextFloat() * 20f
                        say(pick("Laisse-moi sortir… 🥺", "J'ai été sage, promis !", "C'est tout petit ici…",
                            "Snif… 😢", "Je m'ennuie dans ma cage"))
                    }
                }
            }
            Act.SQUAT -> updateSquat(dt)
            Act.HIDE_RUN, Act.HIDDEN, Act.PEEK -> updateHide(dt)
            Act.KNOCK -> {
                if (!knockDone) {
                    if (walkToward(screenW / 2, 140 * d, dt)) {
                        knockDone = true; actTime = 0f
                        bunny.pose = Pose.IDLE
                        knockVibrate()
                        express(Expr.SURPRISED, 1.5f)
                        say(pick("Toc toc toc ! 👊 Y'a quelqu'un ?", "Hé ho ! Occupe-toi de moi !", "Je m'ennuiiiie 😩",
                            "Tu m'as oublié ?! 😤"), 3f)
                    }
                } else {
                    bunny.pose = Pose.IDLE
                    if (actTime > 3.5f) setAct(Act.IDLE, 3f)
                }
            }
        }
    }

    /** Marche vers x. Retourne true une fois arrivé. */
    private fun walkToward(x: Float, speed: Float, dt: Float, stopDist: Float = 3 * d, clamp: Boolean = true): Boolean {
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

    private fun decideNext() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = hour >= 22 || hour < 7
        val lazy = pet.personality == Personality.LAZY
        if (pet.energy < (if (lazy) 30f else 12f) ||
            (night && pet.energy < (if (lazy) 75f else 55f) && rnd.nextFloat() < 0.5f) ||
            (lazy && pet.energy < 60f && rnd.nextFloat() < 0.12f)
        ) {
            startSleep(); return
        }
        // Ignoré depuis longtemps ? Il cherche l'attention…
        if (maybeSeekAttention()) return
        // Sur une autre page que celle affichée : il se promène sans sauter sur les icônes d'ici
        val elsewhere = bunnyElsewhere()
        // Faim ou envie de jouer : il va ouvrir le coffre lui-même
        if (!elsewhere && wantsChest()) { selfServeAt = System.currentTimeMillis(); setAct(Act.GOTO_CHEST); return }
        if (!elsewhere && platform == null &&
            rnd.nextFloat() < (if (pet.personality == Personality.CURIOUS) 0.12f else 0.06f) && startPageWander()
        ) return
        val r = rnd.nextFloat()
        // Sur une icône : redescendre, sauter sur une autre, ou rester perché
        if (platform != null) {
            when {
                r < 0.30f -> jumpTo((fx + (rnd.nextFloat() * 2f - 1f) * 160 * d).coerceIn(minX, maxX), groundY, Act.IDLE)
                r < 0.60f && pet.energy > 20f && jumpToPlatform() -> {}
                r < 0.75f -> { bunny.facing = -bunny.facing; setAct(Act.IDLE, 2f + rnd.nextFloat() * 3f) }
                else -> setAct(Act.IDLE, 3f + rnd.nextFloat() * 5f)
            }
            return
        }
        val platChance = if (pet.personality == Personality.CURIOUS) 0.6f else 0.35f
        if (!elsewhere && r < platChance && pet.energy > 25f && jumpToPlatform()) return
        when {
            r < 0.55f -> {
                val range = screenW * 0.45f
                targetX = (fx + (rnd.nextFloat() * 2f - 1f) * range).coerceIn(minX, maxX)
                setAct(Act.WALK)
            }
            r < 0.68f && pet.energy > 25f -> jump(-650 * d, bunny.facing * 90 * d, then = Act.IDLE)
            r < 0.80f -> { bunny.facing = -bunny.facing; setAct(Act.IDLE, 1.5f + rnd.nextFloat() * 2f) }
            else -> setAct(Act.IDLE, 3f + rnd.nextFloat() * 5f)
        }
    }

    private fun surfaceY(): Float = platform?.top?.toFloat() ?: groundY

    /** Saut balistique pour atterrir précisément en (tx, ty). */
    private fun jumpTo(tx: Float, ty: Float, then: Act) {
        val apex = min(fy, ty) - (70 + rnd.nextFloat() * 50) * d
        val vy0 = -sqrt(2f * gravity * (fy - apex))
        val t = -vy0 / gravity + sqrt(2f * (ty - apex) / gravity)
        val keep = if (act == Act.DANCE) danceTime else 0f
        platform = null
        airLandY = null
        vy = vy0
        vx = (tx - fx) / t
        afterAir = then
        hardLanding = false
        setAct(Act.AIR)
        danceTime = keep
    }

    /** Choisit une icône à portée et saute dessus. */
    private fun jumpToPlatform(): Boolean {
        val cur = platform
        val candidates = Platforms.list.filter {
            val cx = it.rect.exactCenterX()
            abs(cx - fx) < screenW * 0.5f && abs(cx - fx) > 30 * d &&
                cx in minX..maxX && (cur == null || it.rect != cur)
        }
        if (candidates.isEmpty()) return false
        val p = candidates[rnd.nextInt(candidates.size)]
        bunny.facing = sign(p.rect.exactCenterX() - fx)
        jumpTo(p.rect.exactCenterX(), p.rect.top.toFloat(), Act.IDLE)
        return true
    }

    /** Si l'icône sous ses pattes a bougé ou disparu (défilement, changement d'app) : il tombe. */
    private fun checkPlatform() {
        val cur = platform ?: return
        if (act == Act.AIR || act == Act.DRAGGED) return
        if (Platforms.version == platVersionSeen) return
        platVersionSeen = Platforms.version
        val same = Platforms.list.firstOrNull {
            abs(it.rect.top - cur.top) < 12 * d && fx >= it.rect.left && fx <= it.rect.right
        }
        if (same != null) {
            platform = Rect(same.rect)
            fy = same.rect.top.toFloat()
            return
        }
        platform = null
        vx = 0f; vy = 0f
        afterAir = if (act == Act.SLEEP) Act.SLEEP else Act.IDLE
        express(Expr.SURPRISED, 1f)
        if (act != Act.SLEEP) say(pick("Oups !", "Whoaaa !", "Ça bouge !"), 1.5f)
        setAct(Act.AIR)
    }

    private fun jump(vyStart: Float, vxStart: Float, then: Act) {
        platform = null
        airLandY = null
        vy = vyStart; vx = vxStart
        afterAir = then
        hardLanding = false
        val keep = if (act == Act.DANCE) danceTime else 0f
        setAct(Act.AIR)
        danceTime = keep
    }

    private fun stepAir(dt: Float) {
        bunny.pose = Pose.AIR
        val prevFy = fy
        vy += gravity * dt
        fx += vx * dt
        fy += vy * dt

        // Saut vers un point précis (bêtise : s'asseoir SUR un bouton / le clavier)
        val land = airLandY
        if (land != null) {
            bunny.airVy = vy
            if (abs(vx) > 60 * d) bunny.facing = sign(vx)
            if (vy > 0f && fy >= land) {
                fy = land; vx = 0f; vy = 0f; bunny.airVy = 0f
                airLandY = null
                setAct(afterAir, 3f)
            }
            return
        }

        // Atterrissage sur une icône (y compris quand on le lance dessus !)
        if (vy > 0f) {
            val margin = 6 * d
            val p = Platforms.list.firstOrNull {
                prevFy <= it.rect.top + 2f && fy >= it.rect.top &&
                    fx >= it.rect.left + margin && fx <= it.rect.right - margin
            }
            if (p != null) {
                fy = p.rect.top.toFloat()
                platform = Rect(p.rect)
                platformLabel = p.label
                platVersionSeen = Platforms.version
                val hard = vy > 2300 * d
                vx = 0f; vy = 0f; bunny.airVy = 0f
                bunny.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (hard) {
                    say(pick("Ouille ! Ça tourne…", "Aïe aïe aïe…"))
                    setAct(Act.DIZZY)
                } else {
                    val next = afterAir
                    val dance = danceTime
                    setAct(next, if (next == Act.IDLE) 2f + rnd.nextFloat() * 3f else 3f)
                    if (next == Act.DANCE) danceTime = dance
                    val lbl = platformLabel
                    if (!lbl.isNullOrBlank() && next == Act.IDLE && rnd.nextFloat() < 0.35f) {
                        say(pick("Je suis sur $lbl !", "Coucou depuis $lbl 👋", "La vue est belle d'ici !", "Hop, sur $lbl !"), 2.2f)
                    }
                }
                return
            }
        }
        bunny.airVy = vy
        if (abs(vx) > 60 * d) bunny.facing = sign(vx)

        if (fx < minX) { fx = minX; vx = abs(vx) * 0.5f }
        if (fx > maxX) { fx = maxX; vx = -abs(vx) * 0.5f }
        val ceiling = topY + 100 * d
        if (fy < ceiling) { fy = ceiling; vy = abs(vy) * 0.3f }

        if (fy >= groundY) {
            fy = groundY
            if (vy > 2300 * d) hardLanding = true
            if (vy > 1300 * d) {
                vy = -vy * 0.32f
                vx *= 0.6f
                bunny.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                vx = 0f; vy = 0f
                bunny.airVy = 0f
                if (hardLanding) {
                    hardLanding = false
                    say(pick("Ouille ! Ça tourne…", "Aïe aïe aïe…", "Doucement !"))
                    pet.happiness = (pet.happiness - 4f).coerceAtLeast(0f)
                    setAct(Act.DIZZY)
                } else {
                    val next = afterAir
                    val dance = danceTime
                    setAct(next, if (next == Act.IDLE) 1.5f + rnd.nextFloat() * 2f else 3f)
                    if (next == Act.DANCE) danceTime = dance
                }
            }
        }
    }

    private fun startSleep() {
        pet.sleeping = true
        pet.save()
        say(pick("Bonne nuit… 💤", "*bâille* Je fais une petite sieste", "Zzz…"))
        if (inStation == StationKind.CAGE) return   // il dort dans sa cage
        if (inStation != null) leaveStation(hopOut = false)
        setAct(Act.SLEEP)
    }

    private fun startDance() {
        danceTime = 0f; danceHopIn = 0f
        setAct(Act.DANCE)
    }

    // =====================================================================
    //  Jouets : balle, ballon, frisbee, doudou
    // =====================================================================
    private var toyHeldByUser = false
    private var toyHoldTime = 0f
    private var hugTime = 0f
    private var catchCooldown = 0f
    private var balloonGroundSaid = 0L

    private fun currentToy(): Prop? = props.firstOrNull { it.toy != null }

    private fun startPlay(toy: Toy, from: Station? = null) {
        if (busyInStation()) return
        if (pet.sleeping) { say("Chut… il dort"); return }
        if (!pet.startPlay()) { say(pick("Trop fatigué… 😴", "Plus tard, j'ai plus d'énergie")); return }
        props.filter { it.toy != null }.forEach { removeProp(it) }
        val p = when (toy) {
            Toy.BALL -> addProp(PropView.Kind.BALL, 42, 42, touchable = true)
            Toy.BALLOON -> addProp(PropView.Kind.BALLOON, 52, 86, touchable = true)
            Toy.FRISBEE -> addProp(PropView.Kind.FRISBEE, 52, 30, touchable = true)
            Toy.PLUSH -> addProp(PropView.Kind.PLUSH, 40, 44, touchable = true)
        }
        if (toy == Toy.BALLOON || toy == Toy.FRISBEE) p.view.tint = Options.accColor(rnd.nextInt(Options.accColors.size))
        p.x = (fx + bunny.facing * 70 * d).coerceIn(minX, maxX)
        p.y = fy - 120 * d
        p.vx = bunny.facing * 250 * d; p.vy = -500 * d
        when (toy) {
            Toy.BALLOON -> { p.y = fy - 220 * d; p.vy = -120 * d; p.vx = bunny.facing * 60 * d }
            Toy.FRISBEE -> { p.vx = bunny.facing * 650 * d; p.vy = -380 * d }
            else -> {}
        }
        if (from != null) {
            // le jouet jaillit du coffre
            p.x = from.cx; p.y = from.cy
            p.vx = ((fx - from.cx) * 0.9f).coerceIn(-700 * d, 700 * d)
            p.vy = if (toy == Toy.BALLOON) -250 * d else -950 * d
        }
        placeProp(p)
        setupToyTouch(p)
        playTime = 0f; toyHoldTime = 0f; hugTime = 0f; catchCooldown = 0f
        noteInteraction()
        val fav = toy == pet.favoriteToy
        say(
            if (fav) pick("Mon jouet préféré !! ⭐", "Ouiii, le ${toy.label.lowercase()} !! ⭐")
            else when (toy) {
                Toy.BALL -> pick("Une balle !! 🎾", "Lance-la moi !")
                Toy.BALLOON -> pick("Un ballon ! 🎈 Il ne doit pas toucher le sol !", "Je vais le garder en l'air !")
                Toy.FRISBEE -> pick("Un frisbee ! 🥏 Lance-le, je l'attrape !", "Je suis un champion du frisbee !")
                Toy.PLUSH -> pick("Un doudou ! 🧸", "Oh, un nouveau copain !")
            }
        )
        if (fav) { bunny.emit(PType.HEART, 4); express(Expr.HAPPY, 2f) }
        setAct(Act.PLAY)
    }

    private fun updatePlay(dt: Float) {
        val p = currentToy()
        playTime += dt
        if (p == null) { setAct(Act.IDLE); return }
        val limit = if (pet.personality == Personality.PLAYFUL) 50f else 35f
        if (playTime > limit || pet.energy < 8f) { endPlay(p); return }
        when (p.toy!!) {
            Toy.BALL -> playBall(p, dt)
            Toy.BALLOON -> playBalloon(p, dt)
            Toy.FRISBEE -> playFrisbee(p, dt)
            Toy.PLUSH -> playPlush(p, dt)
        }
    }

    private fun endPlay(p: Prop) {
        removeProp(p)
        say(pick("C'était trop bien ! 💕", "Pfiou, je suis épuisé !", "Encore demain ?"))
        express(Expr.HAPPY, 2f)
        setAct(Act.IDLE, 4f)
    }

    private fun hit(toy: Toy, amount: Float = 2f) {
        pet.playHit(toy, amount)
        bunny.emit(if (rnd.nextBoolean()) PType.HEART else PType.NOTE)
        express(Expr.HAPPY, 0.8f)
    }

    private fun faceToward(x: Float) { if (abs(x - fx) > 2 * d) bunny.facing = sign(x - fx) }

    // --- Balle : il la pousse du museau ---
    private fun playBall(ball: Prop, dt: Float) {
        val dx = ball.x - fx
        if (abs(dx) > 30 * d) {
            walkToward(ball.x, 170 * d, dt, stopDist = 30 * d)
        } else {
            bunny.pose = Pose.IDLE
            faceToward(ball.x)
            if (ball.y > groundY - 60 * d && kickCooldown <= 0f && !toyHeldByUser) {
                val dir = if (rnd.nextFloat() < 0.75f) bunny.facing else -bunny.facing
                ball.vx = dir * (350 + rnd.nextFloat() * 600) * d
                ball.vy = -(500 + rnd.nextFloat() * 700) * d
                ball.onGround = false
                kickCooldown = 0.7f
                hit(Toy.BALL)
                if (rnd.nextFloat() < 0.25f) say(pick("Bim !", "Attrape !", "Hop là !", "Goaaal ⚽"), 1.6f)
                jump(-420 * d, 0f, then = Act.PLAY)
            }
        }
    }

    // --- Ballon : il le garde en l'air à coups de tête ---
    private fun balloonCenterY(p: Prop) = p.y - p.lp.height + p.lp.width / 2f

    private fun playBalloon(p: Prop, dt: Float) {
        val dx = p.x - fx
        if (abs(dx) > 26 * d) { walkToward(p.x, 190 * d, dt, stopDist = 26 * d); return }
        bunny.pose = Pose.IDLE
        faceToward(p.x)
        val h = fy - balloonCenterY(p)
        if (h < 320 * d && p.vy > -60 * d && kickCooldown <= 0f && !toyHeldByUser) {
            val need = (h - 75 * d).coerceAtLeast(15 * d)
            kickCooldown = 0.5f
            jump(-sqrt(2f * gravity * need), 0f, then = Act.PLAY)
        }
    }

    // --- Frisbee : il l'attrape au vol et le relance ---
    private fun playFrisbee(p: Prop, dt: Float) {
        if (p.held) {
            toyHoldTime += dt
            bunny.pose = Pose.IDLE
            if (toyHoldTime > 1.3f) {
                val dir = if (fx < screenW / 2) 1f else -1f
                bunny.facing = dir
                p.held = false; p.onGround = false
                p.x = fx + dir * 30 * d; p.y = fy - 40 * d
                p.vx = dir * (750 + rnd.nextFloat() * 450) * d
                p.vy = -(250 + rnd.nextFloat() * 250) * d
                catchCooldown = 0.9f
                if (rnd.nextFloat() < 0.35f) say(pick("À toi ! 🥏", "Attrape !", "Super lancer !"), 1.4f)
            }
            return
        }
        val airborne = !p.onGround
        val tx = if (airborne) p.x + p.vx * 0.25f else p.x
        if (abs(tx - fx) > 24 * d) walkToward(tx, 230 * d, dt, stopDist = 24 * d) else bunny.pose = Pose.IDLE
        if (act != Act.PLAY) return
        val dx = p.x - fx
        val h = fy - (p.y - p.lp.height / 2f)
        if (airborne && abs(dx) < 90 * d && h > 60 * d && h < 240 * d && kickCooldown <= 0f && !toyHeldByUser) {
            kickCooldown = 0.6f
            jump(-sqrt(2f * gravity * (h - 30 * d).coerceAtLeast(10 * d)), dx * 2.5f, then = Act.PLAY)
        }
    }

    // --- Doudou : câlins, promenade, lancers ---
    private fun playPlush(p: Prop, dt: Float) {
        if (!p.held) {
            if (!p.onGround) { bunny.pose = Pose.IDLE; faceToward(p.x); return }
            if (walkToward(p.x, 150 * d, dt, stopDist = 22 * d)) {
                p.held = true; hugTime = 0f
                hit(Toy.PLUSH, 3f)
                say(pick("Mon doudou ! 🧸", "Je t'ai !", "Viens là toi !"), 1.6f)
            }
            return
        }
        val before = hugTime
        hugTime += dt
        when {
            hugTime < 4f -> {
                bunny.pose = Pose.IDLE
                express(Expr.HAPPY, 0.2f)
                if (rnd.nextFloat() < dt * 2.5f) { bunny.emit(PType.HEART); pet.playHit(Toy.PLUSH, 0.6f) }
            }
            hugTime < 10f -> {
                if (before < 4f) targetX = (fx + (rnd.nextFloat() * 2f - 1f) * screenW * 0.3f).coerceIn(minX, maxX)
                if (walkToward(targetX, 80 * d, dt)) bunny.pose = Pose.IDLE
            }
            else -> {
                if (pet.energy < 35f) { say("Dodo avec mon doudou… 💤"); startSleep(); return }
                p.held = false; p.onGround = false
                p.vy = -(650 + rnd.nextFloat() * 250) * d
                p.vx = bunny.facing * rnd.nextFloat() * 120 * d
                catchCooldown = 0.5f
                hugTime = 0f
                say(pick("Wiii, vole doudou !", "Hop !", "Il vole !"), 1.4f)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupToyTouch(t: Prop) {
        var vt: VelocityTracker? = null
        t.view.setOnTouchListener { _, ev ->
            val e2 = MotionEvent.obtain(ev); e2.setLocation(ev.rawX, ev.rawY)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    toyHeldByUser = true
                    noteInteraction()
                    if (t.held) {
                        t.held = false
                        if (t.toy == Toy.PLUSH || t.toy == Toy.FRISBEE) {
                            express(Expr.ANGRY, 1.2f)
                            say(pick("Hé ! C'est à moi !", "Rends-le moi ! 😾", "Mon jouet !"), 1.6f)
                        }
                    }
                    vt = VelocityTracker.obtain(); vt?.addMovement(e2)
                }
                MotionEvent.ACTION_MOVE -> {
                    vt?.addMovement(e2)
                    t.x = ev.rawX; t.y = ev.rawY + t.view.height / 2f
                    t.vx = 0f; t.vy = 0f; t.onGround = false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vt?.addMovement(e2)
                    vt?.computeCurrentVelocity(1000)
                    t.vx = (vt?.xVelocity ?: 0f).coerceIn(-4000 * d, 4000 * d)
                    t.vy = (vt?.yVelocity ?: 0f).coerceIn(-4000 * d, 4000 * d)
                    vt?.recycle(); vt = null
                    toyHeldByUser = false
                    t.onGround = false
                    catchCooldown = 0.3f
                    if (act == Act.PLAY && hypot(t.vx, t.vy) > 1500 * d) say(pick("Waouh, quel lancer !", "J'y vais !"), 1.6f)
                }
            }
            e2.recycle()
            true
        }
    }

    // =====================================================================
    //  Objets (carotte, crottes, jouets)
    // =====================================================================
    private fun addProp(kind: PropView.Kind, sizeDp: Int, touchable: Boolean): Prop =
        addProp(kind, sizeDp, sizeDp, touchable)

    private fun addProp(kind: PropView.Kind, wDp: Int, hDp: Int, touchable: Boolean): Prop {
        val v = PropView(this, kind)
        val lp = overlayParams((wDp * d).toInt(), (hDp * d).toInt(), touchable)
        val p = Prop(v, lp)
        props.add(p)
        if (!hidden) { placeProp(p, layout = false); wm.addView(v, lp); p.attached = true }
        return p
    }

    private fun removeProp(p: Prop) {
        if (p.attached) { safeRemove(p.view); p.attached = false }
        props.remove(p)
    }

    private fun placeProp(p: Prop, layout: Boolean = true) {
        val nx = (p.x - p.lp.width / 2f).roundToInt()
        val ny = (p.y - p.lp.height).roundToInt()
        if (nx != p.lp.x || ny != p.lp.y) {
            p.lp.x = nx; p.lp.y = ny
            if (layout && p.attached) wm.updateViewLayout(p.view, p.lp)
        }
    }

    /** Physique générique : gravité (facteur), rebonds, frottement au sol. */
    private fun physics(p: Prop, dt: Float, g: Float, bounce: Float, wallBounce: Float, groundFriction: Float) {
        p.vy += gravity * g * dt
        p.x += p.vx * dt
        p.y += p.vy * dt
        val half = p.lp.width / 2f
        if (p.x < half) { p.x = half; p.vx = abs(p.vx) * wallBounce }
        if (p.x > screenW - half) { p.x = screenW - half; p.vx = -abs(p.vx) * wallBounce }
        if (p.y - p.lp.height < topY) { p.y = topY + p.lp.height; p.vy = abs(p.vy) * 0.6f }
        if (p.y >= groundY) {
            p.y = groundY
            p.vy = if (abs(p.vy) > 250 * d) -abs(p.vy) * bounce else 0f
            p.vx *= (1f - groundFriction * dt).coerceAtLeast(0f)
            p.onGround = p.vy == 0f
        } else if (p.y < groundY - 2f) p.onGround = false
    }

    private fun updateProps(dt: Float) {
        if (catchCooldown > 0f) catchCooldown -= dt
        val mouthX = fx + bunny.facing * 18 * d
        for (p in props.toList()) {
            p.age += dt
            val userHolds = toyHeldByUser && p.toy != null
            when (p.view.kind) {
                PropView.Kind.POOP -> { p.y = groundY }
                PropView.Kind.CARROT, PropView.Kind.LETTUCE, PropView.Kind.STRAWBERRY -> {
                    if (!p.onGround) {
                        p.vy += gravity * 0.5f * dt
                        p.x = (p.x + p.vx * dt).coerceIn(minX, maxX)
                        p.y += p.vy * dt
                        p.view.spin += 200f * dt
                        if (p.y >= groundY) {
                            p.y = groundY; p.onGround = true; p.vx = 0f
                            p.view.spin = if (p.view.kind == PropView.Kind.CARROT) 90f else 0f
                        }
                        p.view.invalidate()
                    }
                    if (p.age > 90f) removeProp(p)
                }
                PropView.Kind.BALL -> if (!userHolds) {
                    physics(p, dt, 1f, 0.62f, 0.8f, 2.2f)
                    p.view.spin += p.vx / d * dt * 2.5f
                    p.view.invalidate()
                }
                PropView.Kind.BALLOON -> if (!userHolds) {
                    p.vx *= (1f - 0.9f * dt); p.vy *= (1f - 0.6f * dt)
                    physics(p, dt, 0.08f, 0.3f, 0.9f, 3f)
                    p.view.spin = sin(p.age * 1.7f) * 8f + p.vx / d * 0.03f
                    p.view.invalidate()
                    // coup de tête
                    val cy = balloonCenterY(p)
                    if (hypot(p.x - fx, cy - (fy - 85 * d)) < 42 * d && p.vy > -100 * d && act != Act.DRAGGED && act != Act.SLEEP) {
                        p.vy = -(450 + rnd.nextFloat() * 300) * d
                        p.vx = (p.x - fx) * 4f + (rnd.nextFloat() * 2f - 1f) * 150 * d
                        p.onGround = false
                        hit(Toy.BALLOON)
                        if (rnd.nextFloat() < 0.15f) say(pick("Boing !", "En l'air !", "Hop !"), 1.2f)
                    }
                    if (p.onGround && act == Act.PLAY && System.currentTimeMillis() - balloonGroundSaid > 8000) {
                        balloonGroundSaid = System.currentTimeMillis()
                        say(pick("Oh non, il a touché le sol !", "Raté ! On recommence ?"), 1.8f)
                    }
                }
                PropView.Kind.FRISBEE -> if (!userHolds) {
                    if (p.held) {
                        p.x = mouthX + bunny.facing * 8 * d
                        p.y = fy - 52 * d + p.lp.height / 2f
                        p.vx = 0f; p.vy = 0f
                    } else {
                        val lift = (abs(p.vx) / (900 * d)).coerceAtMost(1f) * 0.85f
                        p.vx *= (1f - 0.35f * dt)
                        physics(p, dt, 1f - lift, 0.15f, 0.6f, 5f)
                        if (!p.onGround) p.view.spin += 900f * dt
                        // attrapé ?
                        val cy = p.y - p.lp.height / 2f
                        if (catchCooldown <= 0f && (act == Act.PLAY || act == Act.AIR) &&
                            hypot(p.x - mouthX, cy - (fy - 55 * d)) < 45 * d
                        ) {
                            val wasFlying = !p.onGround
                            p.held = true; toyHoldTime = 0f
                            hit(Toy.FRISBEE, if (wasFlying) 4f else 1f)
                            if (wasFlying && rnd.nextFloat() < 0.5f) say(pick("Attrapé ! 🥏", "Trop fort !", "Dans les dents !"), 1.5f)
                        }
                    }
                    p.view.invalidate()
                }
                PropView.Kind.PLUSH -> if (!userHolds) {
                    if (p.held) {
                        val sleepingNow = act == Act.SLEEP
                        p.x = fx + bunny.facing * (if (sleepingNow) 30 else 16) * d
                        p.y = fy - (if (sleepingNow) 0 else 6) * d
                        p.vx = 0f; p.vy = 0f; p.view.spin = if (sleepingNow) -70f else 0f
                    } else {
                        physics(p, dt, 1f, 0.3f, 0.6f, 6f)
                        if (!p.onGround) p.view.spin += p.vx / d * dt * 3f + 120f * dt
                        else p.view.spin *= (1f - 6f * dt)
                        if (catchCooldown <= 0f && act == Act.PLAY && !p.onGround &&
                            hypot(p.x - fx, (p.y - p.lp.height / 2f) - (fy - 40 * d)) < 40 * d
                        ) {
                            p.held = true; hugTime = 0f
                            hit(Toy.PLUSH, 3f)
                        }
                    }
                    p.view.invalidate()
                }
            }
            if (p.attached && p.view.kind != PropView.Kind.POOP) placeProp(p)
        }
    }

    private fun spawnPoop(x: Float) {
        val p = addProp(PropView.Kind.POOP, 34, touchable = true)
        p.anchor = bunnyView()
        p.x = x; p.y = groundY
        placeProp(p)
        p.view.setOnClickListener {
            removeProp(p)
            pet.removePoop()
            bunny.emit(PType.SPARKLE, 4)
            express(Expr.HAPPY, 1.5f)
            say(pick("Merci ! ✨", "Ouf, ça sentait…", "Tu es le meilleur !"), 2f)
        }
    }

    private fun restorePoops() {
        repeat(pet.poops) {
            spawnPoop(minX + rnd.nextFloat() * (maxX - minX))
        }
    }

    // =====================================================================
    //  Interactions tactiles avec le lapin
    // =====================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun setupBunnyTouch() {
        val slop = ViewConfiguration.get(this).scaledTouchSlop * 1.5f
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onPetTap(); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean { toggleMenu(); return true }
            override fun onLongPress(e: MotionEvent) {
                bunny.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                toggleMenu()
            }
        })
        var downX = 0f; var downY = 0f
        var offX = 0f; var offY = 0f
        var dragging = false
        var lastRawX = 0f
        var vt: VelocityTracker? = null

        bunnyTouch = View.OnTouchListener { _, ev ->
            val e2 = MotionEvent.obtain(ev); e2.setLocation(ev.rawX, ev.rawY)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; lastRawX = ev.rawX
                    dragging = false
                    vt?.recycle(); vt = VelocityTracker.obtain()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(ev.rawX - downX, ev.rawY - downY) > slop) {
                        dragging = true
                        val cancel = MotionEvent.obtain(ev); cancel.action = MotionEvent.ACTION_CANCEL
                        gd.onTouchEvent(cancel); cancel.recycle()
                        rebaseToActivePage()
                        offX = downX - fx; offY = downY - fy
                        startDrag()
                    }
                    if (dragging) {
                        fx = (ev.rawX - offX).coerceIn(0f, screenW)
                        fy = min(ev.rawY - offY, groundY).coerceAtLeast(topY + 90 * d)
                        if (abs(ev.rawX - lastRawX) > 2 * d) bunny.facing = sign(ev.rawX - lastRawX)
                        lastRawX = ev.rawX
                        updateStationHover()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vt?.addMovement(e2)
                    if (dragging) {
                        vt?.computeCurrentVelocity(1000)
                        val target = hoverStation
                        clearStationHover()
                        if (target != null) enterStation(target)
                        else {
                            release(vt?.xVelocity ?: 0f, vt?.yVelocity ?: 0f)
                            hideTempStations()
                        }
                        dragging = false
                        vt?.recycle(); vt = null
                        e2.recycle()
                        return@OnTouchListener true   // pas de "tap" après un lancer
                    }
                    vt?.recycle(); vt = null
                }
            }
            vt?.addMovement(e2)
            e2.recycle()
            if (!dragging) gd.onTouchEvent(ev)
            true
        }
        bunny.setOnTouchListener(bunnyTouch)
    }

    private var bunnyTouch: View.OnTouchListener? = null

    private fun startDrag() {
        if (act == Act.PEEK) {
            hidingView = null; dock.text = "🏠"
            pet.happiness = (pet.happiness + 15f).coerceAtMost(100f)
            say("Trouvé !! 🎉", 1.5f)
        } else if (isHiding()) { hidingView = null; dock.text = "🏠" }
        closeMenu()
        platform = null
        airLandY = null
        if (inStation != null) leaveStation(hopOut = false)
        showStations(temporary = true)
        if (pet.sleeping) { pet.sleeping = false; pet.happiness = (pet.happiness - 3f).coerceAtLeast(0f) }
        currentToy()?.let { if (it.held) { it.held = false; it.onGround = false } }
        noteInteraction()
        setAct(Act.DRAGGED)
        say(pick("Waaah !", "Hé ! Pose-moi !", "Je voleeee !", "Hiii !"), 1.8f)
        bunny.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun release(vxPx: Float, vyPx: Float) {
        vx = vxPx.coerceIn(-3500 * d, 3500 * d)
        vy = vyPx.coerceIn(-3500 * d, 3500 * d)
        if (hypot(vx, vy) > 2400 * d) say(pick("Wiiiiii !", "Je vole !!", "Aaaaah !"), 1.6f)
        hardLanding = false
        afterAir = Act.IDLE
        setAct(Act.AIR)
    }

    private fun onPetTap() {
        val now = System.currentTimeMillis()
        bunny.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        noteInteraction()
        if (act == Act.PEEK) { foundBunny(); return }
        if (act == Act.HIDE_RUN) return
        if (act == Act.SQUAT) {
            pet.pet()
            bunny.emit(PType.HEART, 3)
            express(Expr.HAPPY, 1.5f)
            say(pick("Bon d'accord… 😇 je descends", "Un câlin ? Ça marche, je bouge !", "Hihi, gagné ! 💕"), 2f)
            jumpTo((fx + bunny.facing * 80 * d).coerceIn(minX, maxX), groundY, Act.IDLE)
            return
        }
        if (act == Act.CAGED) {
            pet.pet()
            bunny.emit(PType.HEART, 1)
            say(pick("Un câlin à travers les barreaux… 🥺", "Fais-moi sortir !", "Snif, merci…"), 2f)
            return
        }
        if (act == Act.BATH || act == Act.VET) { bunny.emit(PType.HEART, 1); return }
        if (act == Act.SLEEP || pet.sleeping) {
            pet.sleeping = false
            pet.happiness = (pet.happiness - 3f).coerceAtLeast(0f)
            express(Expr.ANGRY, 2.5f)
            say(pick("Hmpf… je dormais !", "Encore 5 minutes…", "Grrr… 😾"))
            setAct(Act.IDLE, 3f)
            return
        }
        tapTimes.addLast(now)
        while (tapTimes.isNotEmpty() && now - tapTimes.first() > 5000) tapTimes.removeFirst()
        if (tapTimes.size >= 7) {
            tapTimes.clear()
            express(Expr.HAPPY, 1.5f)
            say(pick("Ça chatouille !! 🤭", "Arrête, hihi !"))
            targetX = if (fx > screenW / 2) fx - screenW * 0.35f else fx + screenW * 0.35f
            setAct(Act.WALK)
            return
        }
        pet.pet()
        bunny.emit(PType.HEART, 2 + rnd.nextInt(3))
        express(Expr.HAPPY, 1.6f)
        if (rnd.nextFloat() < 0.45f) say(
            pick("Hihi ! 💕", "Encore !", "J'adore les câlins", "Mmmh…", "Tu es gentil·le !", "*ronronne* (oui, les lapins aussi)"),
            2f
        )
        if (act == Act.IDLE || act == Act.WALK || act == Act.SIT) {
            if (rnd.nextFloat() < 0.35f) jump(-560 * d, 0f, then = Act.IDLE) else setAct(Act.IDLE, 2.5f)
        }
    }

    // =====================================================================
    //  Menu de soins
    // =====================================================================
    @SuppressLint("ClickableViewAccessibility")
    private fun toggleMenu() {
        if (menu != null) { closeMenu(); return }
        if (System.currentTimeMillis() - lastMenuClose < 350) return
        lateinit var m: PetMenu
        m = PetMenu(this, pet) { a ->
            // Sécurité : si ce panneau n'est plus le menu courant, on le retire quand même
            if (menu !== m) { safeRemove(m); if (a != PetMenu.Action.CLOSE) onMenuAction(a) }
            else onMenuAction(a)
        }
        noteInteraction()
        val w = (320 * d).toInt()
        val lp = overlayParams(w, WindowManager.LayoutParams.WRAP_CONTENT, touchable = true)
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        m.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val mh = m.measuredHeight
        lp.x = (fx - w / 2f).roundToInt().coerceIn(8, (screenW - w - 8).toInt())
        var y = (fy - 125 * d - mh).roundToInt()
        if (y < topY + 8 * d) y = (topY + 8 * d).roundToInt()
        lp.y = y
        m.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                if (menu === m) closeMenu() else safeRemove(m); true
            } else false
        }
        wm.addView(m, lp)
        m.alpha = 0f; m.scaleX = 0.9f; m.scaleY = 0.9f
        m.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start()
        menu = m; menuLp = lp
        if (act == Act.WALK) setAct(Act.IDLE, 6f)
    }

    private fun closeMenu() {
        menu?.let {
            safeRemove(it)
            lastMenuClose = System.currentTimeMillis()
        }
        menu = null; menuLp = null
    }

    private fun onMenuAction(a: PetMenu.Action) {
        when (a) {
            PetMenu.Action.FEED -> { closeMenu(); feed() }
            PetMenu.Action.PLAY_BALL -> { closeMenu(); startPlay(Toy.BALL) }
            PetMenu.Action.PLAY_BALLOON -> { closeMenu(); startPlay(Toy.BALLOON) }
            PetMenu.Action.PLAY_FRISBEE -> { closeMenu(); startPlay(Toy.FRISBEE) }
            PetMenu.Action.PLAY_PLUSH -> { closeMenu(); startPlay(Toy.PLUSH) }
            PetMenu.Action.STATIONS -> { closeMenu(); toggleStations() }
            PetMenu.Action.HIDE_SEEK -> { closeMenu(); startHideSeek(fromMenu = true) }
            PetMenu.Action.SLEEP -> {
                closeMenu()
                if (pet.sleeping) {
                    pet.sleeping = false; pet.save()
                    express(Expr.SLEEPY, 2f)
                    say("*bâille* D'accord, je me lève…")
                    setAct(Act.IDLE, 3f)
                } else if (pet.energy > 92f) {
                    say(pick("Je ne suis pas fatigué !", "Dodo ? Déjà ?!"))
                } else startSleep()
            }
            PetMenu.Action.HIDE -> { closeMenu(); hide() }
            PetMenu.Action.CLOSE -> closeMenu()
        }
    }

    /** Vrai (et il le dit) s'il est dans la baignoire / chez le véto / en cage. */
    private fun busyInStation(): Boolean {
        if (isHiding()) { say("Je joue à cache-cache ! 🫣"); return true }
        val s = inStation ?: return false
        say(when (s) {
            StationKind.CAGE -> pick("Je ne peux pas sortir… 🥺", "Ouvre la cage d'abord !")
            StationKind.BATH -> "Attends, je prends mon bain ! 🫧"
            StationKind.VET -> "Je suis chez le docteur !"
            StationKind.CHEST -> ""
        })
        return true
    }

    private var eatingFood = Food.CARROT

    /** Donne à manger. [from] = le coffre d'où sort la nourriture (sinon elle tombe du ciel). */
    private fun feed(food: Food = Food.CARROT, from: Station? = null) {
        if (busyInStation()) return
        noteInteraction()
        if (pet.sleeping) { say("Chut… il dort 💤"); return }
        if (pet.satiety > 95f && food != Food.STRAWBERRY) { say(pick("J'ai plus faim !", "Mon ventre va exploser !")); return }
        if (props.any { it.view.kind.isFood }) return
        val kind = when (food) {
            Food.CARROT -> PropView.Kind.CARROT
            Food.LETTUCE -> PropView.Kind.LETTUCE
            Food.STRAWBERRY -> PropView.Kind.STRAWBERRY
        }
        val c = addProp(kind, 38, touchable = false)
        if (from != null) {
            // jaillit du coffre en direction du lapin
            c.x = from.cx; c.y = from.cy
            c.vy = -900 * d
            c.vx = ((fx - from.cx) * 0.9f).coerceIn(-600 * d, 600 * d)
        } else {
            c.x = (fx + (if (rnd.nextBoolean()) 1 else -1) * (90 + rnd.nextFloat() * 120) * d).coerceIn(minX, maxX)
            c.y = topY + 60 * d
            c.vy = 0f
        }
        placeProp(c)
        express(Expr.SURPRISED, 1f)
        say(when (food) {
            Food.CARROT -> pick("Une carotte !! 🥕", "Miam en vue !", "C'est pour moi ?")
            Food.LETTUCE -> pick("De la salade ! 🥬 Croquante !", "Plein de vitamines !")
            Food.STRAWBERRY -> pick("UNE FRAISE ?! 🍓😍", "Mon dessert préféré !")
        }, 2f)
        setAct(Act.GOTO_FOOD)
    }

    // =====================================================================
    //  Bulle de dialogue
    // =====================================================================
    private fun say(text: String, seconds: Float = 3.2f) {
        if (hidden || bunnyAway || !::bubble.isInitialized) return
        bubble.text.text = text
        bubble.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        bubbleW = bubble.measuredWidth; bubbleH = bubble.measuredHeight
        bubbleTime = seconds
        positionBubble()
        if (!bubbleShown) { wm.addView(bubble, bubbleLp); bubbleShown = true }
        else wm.updateViewLayout(bubble, bubbleLp)
    }

    private fun positionBubble() {
        val headTop = if (act == Act.SLEEP) 70 * d else 112 * d
        bubbleLp.x = (fx + bunnyShift - bubbleW / 2f).roundToInt().coerceIn((4 * d).toInt(), (screenW - bubbleW - 4 * d).toInt().coerceAtLeast(0))
        bubbleLp.y = (fy - headTop - bubbleH).roundToInt().coerceAtLeast(topY.toInt())
    }

    private fun updateBubble(dt: Float) {
        if (!bubbleShown) return
        bubbleTime -= dt
        if (bubbleTime <= 0f) { hideBubble(); return }
        val ox = bubbleLp.x; val oy = bubbleLp.y
        positionBubble()
        if (ox != bubbleLp.x || oy != bubbleLp.y) wm.updateViewLayout(bubble, bubbleLp)
    }

    private fun hideBubble() {
        if (bubbleShown && ::bubble.isInitialized) {
            safeRemove(bubble)
            bubbleShown = false
        }
    }

    private fun pick(vararg s: String) = s[rnd.nextInt(s.size)]

    private fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            pet.mood == PetState.Mood.HUNGRY -> "Coucou… j'ai faim 🥺"
            h < 11 -> "Bonjour ! ☀️ C'est moi, ${pet.name} !"
            h < 18 -> "Coucou ! C'est ${pet.name} 🐰"
            else -> "Bonsoir ! 🌙 ${pet.name} est là"
        }
    }

    private fun chatter() {
        val line = when (pet.mood) {
            PetState.Mood.HUNGRY -> pick("J'ai faim… 🥕", "Mon ventre gargouille…", "Une petite carotte ?")
            PetState.Mood.SLEEPY -> pick("*bâille*", "Je suis fatigué…", "Une sieste me ferait du bien")
            PetState.Mood.DIRTY -> pick("Je suis tout sale…", "Un bain ? 🫧", "Ça sent pas très bon ici…")
            PetState.Mood.SAD -> pick("Je m'ennuie…", "Tu joues avec moi ?", "Un câlin ? 🥺")
            PetState.Mood.SICK -> pick("Je ne me sens pas bien… 🤒", "Atchoum !", "J'ai besoin d'un médicament")
            PetState.Mood.HAPPY -> pick("La vie est belle ! ✨", "♪ La la la ♫", "Je t'aime bien 💕", "Tu fais quoi ?", "Il fait beau sur ton écran !")
            PetState.Mood.OK -> pick("Hmm hmm…", "*renifle*", "Tu fais quoi ?", "Je me promène 🐾")
        }
        say(line)
        if (pet.mood == PetState.Mood.HAPPY && rnd.nextFloat() < 0.3f) startDance()
    }

    // =====================================================================
    //  Voyage d'application en application
    // =====================================================================
    private val appPoll = object : Runnable {
        override fun run() {
            if (screenOn && !hidden) {
                try {
                    watcher.poll()?.let { onAppChanged(it) }
                } catch (e: Exception) {
                    android.util.Log.w("TamaCookie", "poll", e)
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
        if (act == Act.CAGED || act == Act.VET || isHiding()) return
        val busy = act in setOf(Act.DRAGGED, Act.SLEEP, Act.EAT, Act.GOTO_FOOD, Act.PLAY, Act.AIR, Act.BATH, Act.SQUAT, Act.KNOCK)
        if (busy) {
            if (act != Act.SLEEP && rnd.nextFloat() < 0.5f) say(comment, 3f)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTravelAt < 6000) { say(comment, 3f); return }
        lastTravelAt = now
        closeMenu()
        travelComment = comment
        travelThen = then
        // Il court vers le bord le plus proche et "saute" dans la nouvelle app
        targetX = if (fx < screenW / 2) -bw.toFloat() else screenW + bw
        say(pick("J'arrive !", "Attends-moi !", "Hop, on change d'app !", "Je te suis ! 🐾"), 1.5f)
        setAct(Act.TRAVEL_OUT)
    }

    private fun commentFor(app: ForegroundAppWatcher.AppInfo): String {
        val n = app.label
        val pkg = app.pkg.lowercase()
        return when {
            app.isHome -> pick("De retour à la maison 🏠", "Ah, l'écran d'accueil !", "On fait quoi maintenant ?")
            "camera" in pkg -> pick("Prends-moi en photo ! 📸", "Je fais mon plus beau sourire")
            "clock" in pkg || "alarm" in pkg -> pick("Il est l'heure de… manger ?", "Tic tac 🕰️")
            "store" in pkg || "vending" in pkg -> pick("On fait les boutiques ? 🛍️", "Tu m'achètes une carotte ?")
            app.category == ApplicationInfo.CATEGORY_GAME -> pick("Un jeu ! Je peux jouer ? 🎮", "Vas-y, bats le record !")
            app.category == ApplicationInfo.CATEGORY_VIDEO -> pick("Pop-corn ? 🍿 Je regarde avec toi", "Chut, ça commence !")
            app.category == ApplicationInfo.CATEGORY_AUDIO -> pick("J'adore cette musique ♪", "On danse ? ♫")
            app.category == ApplicationInfo.CATEGORY_SOCIAL -> pick("Dis bonjour de ma part sur $n !", "Qui t'écrit ? 👀")
            app.category == ApplicationInfo.CATEGORY_MAPS -> pick("On part en voyage ? 🗺️", "Je peux venir ?")
            app.category == ApplicationInfo.CATEGORY_NEWS -> pick("Quoi de neuf dans le monde ?", "Des nouvelles des lapins ?")
            app.category == ApplicationInfo.CATEGORY_IMAGE -> pick("Montre-moi tes photos !", "Il y a des photos de moi ?")
            app.category == ApplicationInfo.CATEGORY_PRODUCTIVITY -> pick("Courage pour le travail 💪", "Je ne dérange pas… promis")
            else -> pick("Coucou $n !", "Oh, $n ! On fait quoi ici ?", "Je visite $n 🐾", "C'est joli chez $n")
        }
    }

    // =====================================================================
    //  "Widgets" flottants : baignoire, véto, cage + bouton 🏠 pour les afficher
    // =====================================================================
    private val stations = LinkedHashMap<StationKind, Station>()
    private var stationsAlways = false      // option "toujours afficher" (sur toutes les vues)
    private var tempStations = false        // affichées le temps de glisser le lapin
    private var inStation: StationKind? = null
    private var hoverStation: StationKind? = null
    private var vetPanel: VetPanel? = null
    private var vetTimer = 0f
    private lateinit var dock: TextView
    private lateinit var dockLp: WindowManager.LayoutParams
    private var dockAdded = false

    // ---------------------------------------------------------------------
    //  Vues : chaque objet reste sur l'app / la page du bureau où il est posé
    // ---------------------------------------------------------------------
    private var currentView = ""
    private var bunnyAway = false           // invisible ici (en cage sur une autre page, ou caché)
    private val sliding = HashMap<View, android.animation.ValueAnimator>()
    private var bunnyShift = 0f             // décalage du lapin quand sa cage suit le défilement du bureau

    // Défilement du bureau, lissé image par image
    private var scrollShown = Float.NaN

    /** Position de défilement du bureau estimée maintenant (extrapolée entre deux événements). */
    private fun scrollEstimate(): Float? {
        val t1 = Platforms.scrollT1
        if (t1 == 0L) return null
        return ScrollMath.estimate(Platforms.scrollX0, Platforms.scrollT0, Platforms.scrollX1, t1,
            android.os.SystemClock.uptimeMillis())
    }

    private fun updateScroll() {
        val est = scrollEstimate() ?: return
        scrollShown = if (scrollShown.isNaN() || abs(est - scrollShown) > screenW * 1.5f) est
        else scrollShown + (est - scrollShown) * 0.5f
    }

    private fun computeView(): String {
        val k = Platforms.viewKey ?: return watcher.currentPkg ?: currentView
        val (pkg, page) = splitView(k)
        // Sur le bureau, la page vient du défilement (instantané) plutôt que de l'indicateur (en retard)
        val pw = Platforms.pageW
        if (page != null && pkg == Platforms.launcherPkg && pw > 0f && !scrollShown.isNaN()) {
            return ViewKey.of(pkg, ScrollMath.pageAt(scrollShown, pw, Platforms.pageCount))
        }
        return k
    }

    /** Page du bureau d'une clé de vue ("pkg#p2" -> pkg, 2). */
    private fun splitView(v: String): Pair<String, Int?> = ViewKey.split(v)

    private var savedPageW = -1f

    private fun checkView() {
        // Largeur d'une page du bureau : mémorisée par largeur d'écran (portrait / paysage)
        val key = "pageW2_${screenW.toInt()}"
        if (savedPageW < 0f) {
            savedPageW = getSharedPreferences("pet", MODE_PRIVATE).getFloat(key, 0f)
            if (!ScrollMath.plausiblePageWidth(savedPageW, screenW)) savedPageW = 0f
            if (Platforms.pageW <= 0f && savedPageW > 0f) Platforms.pageW = savedPageW
        } else if (Platforms.pageW > 0f && Platforms.pageW != savedPageW) {
            savedPageW = Platforms.pageW
            getSharedPreferences("pet", MODE_PRIVATE).edit().putFloat(key, savedPageW).apply()
        }
        updateScroll()
        val v = computeView()
        if (v.isNotEmpty() && v != currentView) {
            currentView = v
            applyView(0)
        }
        layoutAnchored()
    }

    /**
     * Décalage horizontal d'un objet ancré par rapport à sa position "de base" :
     * 0 = sur la vue affichée ; sur le bureau, il suit le défilement des pages comme les icônes.
     * null = pas visible du tout (autre application).
     */
    private fun anchorOffset(anchor: String?): Float? {
        if (anchor.isNullOrEmpty()) return null
        val (apkg, apage) = splitView(anchor)
        if (apage == null) return if (anchor == currentView) 0f else null
        val (cpkg, _) = splitView(currentView)
        if (cpkg != apkg) return null
        val pw = Platforms.pageW
        if (pw <= 0f || scrollShown.isNaN()) return if (anchor == currentView) 0f else null
        return ScrollMath.offset(apage, pw, scrollShown)
    }

    /** Place, montre ou cache chaque objet ancré selon la vue et le défilement. Appelé à chaque image. */
    private fun layoutAnchored() {
        if (hidden) return
        for (s in stations.values) {
            val off = anchorOffset(s.anchor)
            val vis = off != null && abs(off) < screenW
            if (vis) {
                val x = (s.cx - s.lp.width / 2f + off!!).roundToInt()
                if (!s.shown) {
                    s.lp.x = x
                    wm.addView(s.view, s.lp); s.shown = true
                    if (off == 0f) { s.view.alpha = 0f; s.view.animate().alpha(1f).setDuration(160).start() } else s.view.alpha = 1f
                } else if (s.lp.x != x) {
                    s.lp.x = x; wm.updateViewLayout(s.view, s.lp)
                }
            } else if (s.shown) {
                safeRemove(s.view); s.shown = false; s.view.highlight = false
            }
        }
        for (p in props) if (p.view.kind == PropView.Kind.POOP) {
            if (p.anchor.isEmpty()) p.anchor = currentView
            val off = anchorOffset(p.anchor)
            val vis = off != null && abs(off) < screenW
            if (vis) {
                val x = (p.x - p.lp.width / 2f + off!!).roundToInt()
                p.lp.y = (p.y - p.lp.height).roundToInt()
                if (!p.attached) { p.lp.x = x; wm.addView(p.view, p.lp); p.attached = true }
                else if (p.lp.x != x) { p.lp.x = x; wm.updateViewLayout(p.view, p.lp) }
            } else if (p.attached) { safeRemove(p.view); p.attached = false }
        }
        // Lapin dans une cage : il suit la cage (et disparaît avec elle)
        val cage = stations[StationKind.CAGE]!!
        if (inStation == StationKind.CAGE) {
            val off = anchorOffset(cage.anchor)
            val vis = off != null && abs(off) < screenW
            bunnyShift = off ?: 0f
            if (!vis && !bunnyAway) {
                bunnyAway = true
                closeMenu(); hideBubble()
                if (bunnyAdded) { bunnyAdded = false; safeRemove(bunny) }
            } else if (vis && bunnyAway) {
                bunnyAway = false
                positionInStation(StationKind.CAGE)
                // le lapin doit rester SOUS la cage
                if (cage.shown) { safeRemove(cage.view); cage.shown = false }
                placeBunny()
                wm.addView(bunny, bunnyLp); bunnyAdded = true
                wm.addView(cage.view, cage.lp); cage.shown = true
                if (off == 0f) say(pick("Tu es revenu ! 🥺", "Je suis toujours là… dans ma cage"), 2.5f)
            }
            splitView(cage.anchor ?: "").second?.let { bunnyPage = it }
        } else updateBunnyPage()
    }

    // ---------------------------------------------------------------------
    //  Le lapin vit sur une page du bureau : il défile avec elle, peut partir
    //  explorer la page voisine, et revient sur la page affichée.
    // ---------------------------------------------------------------------
    private var bunnyPage = 0          // page du bureau où se trouve le lapin (0 = pas sur le bureau)
    private var returnAt = 0L          // quand il revient sur la page affichée (uptime ms)
    private var wanderDir = 1
    private val noPageFollow = setOf(Act.DRAGGED, Act.HIDE_RUN, Act.HIDDEN, Act.PEEK)
    private val canComeBack = setOf(Act.IDLE, Act.WALK, Act.SIT, Act.DANCE)

    /** Page du bureau affichée, ou null hors du bureau. */
    private fun launcherPage(): Int? {
        val (pkg, page) = splitView(currentView)
        return if (page != null && pkg == Platforms.launcherPkg) page else null
    }

    /** Vue où se trouve le lapin (sa page du bureau, sinon la vue affichée). */
    private fun bunnyView(): String {
        val pkg = Platforms.launcherPkg
        return if (bunnyPage > 0 && launcherPage() != null && pkg != null) "$pkg#p$bunnyPage" else currentView
    }

    /** Le lapin est-il sur une autre page que celle affichée ? */
    private fun bunnyElsewhere(): Boolean {
        val cp = launcherPage() ?: return false
        return bunnyPage > 0 && bunnyPage != cp
    }

    private fun updateBunnyPage() {
        val cp = launcherPage()
        if (cp == null) {
            // On quitte le bureau alors qu'il était sur une autre page : il arrive par le bord
            if (bunnyPage > 0 && abs(bunnyShift) >= screenW * 0.5f && act in canComeBack + Act.PAGE_OUT) {
                fx = if (bunnyShift < 0f) -bw.toFloat() else screenW + bw
                targetX = screenW * (0.3f + rnd.nextFloat() * 0.4f)
                travelThen = Act.IDLE; travelComment = null
                setAct(Act.TRAVEL_IN)
            }
            bunnyPage = 0; bunnyShift = 0f; returnAt = 0L
            return
        }
        val pw = Platforms.pageW
        if (bunnyPage <= 0 || act in noPageFollow || pw <= 0f || scrollShown.isNaN()) {
            if (bunnyShift != 0f) fx += bunnyShift
            bunnyPage = cp; bunnyShift = 0f
            return
        }
        bunnyShift = ScrollMath.offset(bunnyPage, pw, scrollShown)
    }

    /** Ramène ses coordonnées sur la page affichée sans le déplacer à l'écran (au pire, juste au bord). */
    private fun rebaseToActivePage() {
        val cp = launcherPage() ?: return
        if (bunnyPage == cp && bunnyShift == 0f) return
        val elsewhere = bunnyPage > 0 && bunnyPage != cp
        fx = (fx + bunnyShift).coerceIn(-bw.toFloat(), screenW + bw)
        bunnyPage = cp; bunnyShift = 0f; returnAt = 0L
        if (elsewhere) { platform = null; airLandY = null; if (act != Act.AIR && act != Act.DRAGGED) fy = groundY }
    }

    /** Il part explorer une page voisine du bureau. */
    private fun startPageWander(): Boolean {
        val cp = launcherPage() ?: return false
        val n = Platforms.pageCount
        if (bunnyPage != cp || n < 2 || Platforms.pageW <= 0f || scrollShown.isNaN()) return false
        wanderDir = when { cp <= 1 -> 1; cp >= n -> -1; else -> if (rnd.nextBoolean()) 1 else -1 }
        targetX = if (wanderDir > 0) screenW + bw else -bw.toFloat()
        bunny.facing = wanderDir.toFloat()
        say(pick("Je vais voir la page d'à côté 👀", "Je pars explorer ! 🐾", "Qu'est-ce qu'il y a par là ?",
            "Tu me retrouves ? 😜"), 2.2f)
        setAct(Act.PAGE_OUT)
        return true
    }

    /** Sur une autre page : après un moment (ou s'il y a à jouer / manger), il revient sur la page affichée. */
    private fun checkPageReturn() {
        if (hidden || inStation != null) return
        val cp = launcherPage()
        if (cp == null || bunnyPage <= 0 || bunnyPage == cp) { returnAt = 0L; return }
        val now = android.os.SystemClock.uptimeMillis()
        if (now - Platforms.scrollT1 < 500) return            // défilement en cours
        // Il a quelque chose à faire ici : il accourt tout de suite
        if (act in setOf(Act.KNOCK, Act.SQUAT, Act.GOTO_FOOD, Act.PLAY)) { rebaseToActivePage(); return }
        if (props.any { it.toy != null || it.view.kind.isFood }) returnAt = now
        if (returnAt == 0L) { returnAt = now + 2500 + rnd.nextInt(3500); return }
        if (now < returnAt || act !in canComeBack || pet.sleeping) return
        val fromLeft = bunnyPage < cp
        rebaseToActivePage()
        targetX = if (fromLeft) screenW * (0.15f + rnd.nextFloat() * 0.3f) else screenW * (0.55f + rnd.nextFloat() * 0.3f)
        travelThen = Act.IDLE
        travelComment = pick("Me revoilà ! 🐾", "Tu es parti sans moi ?!", "Attends-moi !", "Coucou, je t'ai retrouvé !",
            "J'ai fait le tour, c'est mieux ici 😄")
        setAct(Act.TRAVEL_IN)
    }

    /** Changement de vue : bain/véto interrompus, cache-cache. */
    private fun applyView(@Suppress("UNUSED_PARAMETER") dir: Int) {
        if (hidden) return
        val st = inStation
        if (st != null && st != StationKind.CAGE && !anchoredHere(stations[st]!!)) leaveStation(hopOut = false)
        stationsPanel?.refresh()
        // Cache-cache : arrivé sur la bonne page -> il apparaît caché ; parti -> il redisparaît
        if (act == Act.HIDDEN && currentView == hidingView) startPeek()
        else if (act == Act.PEEK && currentView != hidingView) setAct(Act.HIDDEN)
        if (inStation == StationKind.CAGE) { layoutAnchored(); return }
        val away = act == Act.HIDDEN
        if (away && !bunnyAway) {
            bunnyAway = true
            closeMenu(); hideBubble()
            if (bunnyAdded) { bunnyAdded = false; safeRemove(bunny) }
        } else if (!away && bunnyAway) {
            bunnyAway = false
            placeBunny()
            wm.addView(bunny, bunnyLp); bunnyAdded = true
            bunny.alpha = 0f; bunny.animate().alpha(1f).setDuration(160).start()
        }
        layoutAnchored()
    }

    private fun cancelSlide(v: View) { sliding.remove(v)?.cancel() }

    private fun anchoredHere(s: Station) = s.anchor != null && s.anchor == currentView

    private fun refreshStations(@Suppress("UNUSED_PARAMETER") dir: Int = 0) = layoutAnchored()

    private fun createStations() {
        val prefs = getSharedPreferences("pet", MODE_PRIVATE)
        StationKind.values().forEachIndexed { i, k ->
            val v = StationView(this, k)
            val lp = overlayParams((k.wDp * d).toInt(), (k.hDp * d).toInt(), touchable = true)
            val s = Station(k, v, lp)
            val defX = screenW * (0.2f + 0.2f * i)
            val defY = topY + (groundY - topY) * 0.32f
            val cx = prefs.getFloat("st_${k}_fx", -1f).let { if (it < 0) defX else it * screenW }
            val cy = prefs.getFloat("st_${k}_fy", -1f).let { if (it < 0) defY else it * groundY }
            s.anchor = prefs.getString("st_${k}_anchor", null)
            placeStation(s, cx, cy)
            setupStationTouch(s)
            stations[k] = s
        }
    }

    private fun saveAnchor(s: Station) {
        getSharedPreferences("pet", MODE_PRIVATE).edit().putString("st_${s.kind}_anchor", s.anchor).apply()
    }

    private fun placeStation(s: Station, cx: Float, cy: Float) {
        val hw = s.lp.width / 2f; val hh = s.lp.height / 2f
        s.cx = cx.coerceIn(hw, screenW - hw)
        s.cy = cy.coerceIn(topY + hh, groundY - hh)
        s.lp.x = (s.cx - hw).roundToInt(); s.lp.y = (s.cy - hh).roundToInt()
        if (s.shown && !sliding.containsKey(s.view)) wm.updateViewLayout(s.view, s.lp)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun addStationWindow(s: Station, dir: Int = 0) = layoutAnchored()

    private fun showStations(temporary: Boolean = false) {
        if (temporary) { tempStations = true; refreshStations() }
    }

    private fun hideTempStations() {
        handler.postDelayed({
            if (act != Act.DRAGGED) { tempStations = false; refreshStations() }
        }, 700)
    }

    /** Nom lisible d'une vue : "page 2 du bureau", "Chrome"… */
    private fun viewLabel(key: String?): String {
        if (key.isNullOrEmpty()) return "?"
        val (pkg, page) = splitView(key)
        if (page != null) return "page $page du bureau"
        return try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
        catch (_: Exception) { pkg.substringAfterLast('.') }
    }

    private var stationsPanel: StationsPanel? = null

    /** Bouton 🏠 : panneau pour installer / ranger chaque objet sur la vue courante. */
    private fun toggleStations() {
        noteInteraction()
        if (stationsPanel != null) { closeStationsPanel(); return }
        closeMenu()
        val panel = StationsPanel(this,
            status = { k ->
                val s = stations[k]!!
                when {
                    s.anchor == null -> 2 to ""
                    s.anchor == currentView -> 0 to ""
                    else -> 1 to viewLabel(s.anchor)
                }
            },
            onToggle = { k ->
                val s = stations[k]!!
                if (s.anchor == currentView) {
                    if (inStation == k) say(if (k == StationKind.CAGE) "Je suis dedans ! 🥺" else "Attends, je suis dedans !")
                    else { s.anchor = null; saveAnchor(s) }
                } else {
                    if (inStation == k && k == StationKind.CAGE) {
                        say("La cage reste où elle est… avec moi dedans 🥺")
                    } else {
                        if (inStation == k) leaveStation(hopOut = false)
                        s.anchor = currentView; saveAnchor(s)
                    }
                }
                refreshStations()
            },
            onClose = { closeStationsPanel() })
        val w = (300 * d).toInt()
        val lp = overlayParams(w, WindowManager.LayoutParams.WRAP_CONTENT, touchable = true)
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        panel.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val dockCx = dockLp.x + dockLp.width / 2f
        lp.x = (if (dockCx > screenW / 2) dockLp.x - w - 8 * d else dockLp.x + dockLp.width + 8 * d).roundToInt()
            .coerceIn(8, (screenW - w - 8).toInt())
        lp.y = (dockLp.y + dockLp.height / 2f - panel.measuredHeight / 2f).roundToInt()
            .coerceIn(topY.toInt(), (groundY - panel.measuredHeight).toInt().coerceAtLeast(topY.toInt()))
        panel.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { closeStationsPanel(); true } else false
        }
        wm.addView(panel, lp)
        stationsPanel = panel
    }

    private fun closeStationsPanel() {
        stationsPanel?.let { safeRemove(it) }
        stationsPanel = null
    }

    private fun saveStationPos(s: Station) {
        getSharedPreferences("pet", MODE_PRIVATE).edit()
            .putFloat("st_${s.kind}_fx", s.cx / screenW).putFloat("st_${s.kind}_fy", s.cy / groundY).apply()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupStationTouch(s: Station) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
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
            if (forwarding) return@setOnTouchListener bunnyTouch?.onTouch(bunny, ev) ?: false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startCx = s.cx; startCy = s.cy
                    moving = false
                    handler.postDelayed(longPress, 350)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (moving) placeStation(s, startCx + ev.rawX - downX, startCy + ev.rawY - downY)
                    else if (hypot(ev.rawX - downX, ev.rawY - downY) > slop) handler.removeCallbacks(longPress)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    if (moving) {
                        s.view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                        saveStationPos(s)
                    } else if (ev.actionMasked == MotionEvent.ACTION_UP && hypot(ev.rawX - downX, ev.rawY - downY) < slop && s.kind == StationKind.CHEST) {
                        openChest(s)
                    } else if (ev.actionMasked == MotionEvent.ACTION_UP && hypot(ev.rawX - downX, ev.rawY - downY) < slop) {
                        say(when (s.kind) {
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

    private fun updateStationHover() {
        val hx = fx; val hy = fy - 45 * d
        var found: StationKind? = null
        for (s in stations.values) if (s.shown && s.kind != StationKind.CHEST && s.contains(hx, hy, 12 * d)) { found = s.kind; break }
        if (found != hoverStation) {
            hoverStation?.let { stations[it]?.view?.highlight = false }
            hoverStation = found
            found?.let {
                stations[it]?.view?.highlight = true
                bunny.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    private fun clearStationHover() {
        hoverStation?.let { stations[it]?.view?.highlight = false }
        hoverStation = null
    }

    private fun positionInStation(k: StationKind) {
        val s = stations[k] ?: return
        when (k) {
            StationKind.BATH -> { fx = s.cx; fy = s.bottom - 14 * d }
            StationKind.VET -> { fx = s.cx + 12 * d; fy = s.bottom - 26 * d }
            StationKind.CAGE -> { fx = s.cx; fy = s.bottom - 16 * d }
            StationKind.CHEST -> {}
        }
        placeBunny()
    }

    // --- Coffre : nourriture et jouets ---
    private var chestPanel: ChestPanel? = null
    private var chestOf: Station? = null

    private fun openChest(s: Station) {
        noteInteraction()
        closeChest()
        closeMenu()
        s.view.open = true
        val panel = ChestPanel(this, pet,
            onFood = { f -> closeChest(); feed(f, from = s) },
            onToy = { t -> closeChest(); startPlay(t, from = s) },
            onClose = { closeChest() })
        val w = (330 * d).toInt()
        val lp = overlayParams(w, WindowManager.LayoutParams.WRAP_CONTENT, touchable = true)
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        panel.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        lp.x = (s.cx - w / 2f).roundToInt().coerceIn(8, (screenW - w - 8).toInt())
        val above = s.cy - s.lp.height / 2f - panel.measuredHeight - 8 * d
        lp.y = (if (above > topY) above else s.cy + s.lp.height / 2f + 8 * d).roundToInt()
            .coerceIn(topY.toInt(), (groundY - panel.measuredHeight).toInt().coerceAtLeast(topY.toInt()))
        panel.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) { closeChest(); true } else false
        }
        wm.addView(panel, lp)
        chestPanel = panel; chestOf = s
    }

    // --- Le lapin percute le coffre : il s'ouvre et un objet en jaillit ---
    private var chestBumpCd = 0f
    private var chestLid = 0f
    private var chestPop: Station? = null
    private var selfServeAt = 0L

    /** Le coffre s'il est visible sur la page du lapin (et pas déjà ouvert par l'utilisateur). */
    private fun visibleChest(): Station? {
        val s = stations[StationKind.CHEST] ?: return null
        if (!s.shown || chestPanel != null || bunnyElsewhere()) return null
        if (s.lp.x + s.lp.width < 0 || s.lp.x > screenW) return null
        return s
    }

    private fun wantsChest(): Boolean {
        if (System.currentTimeMillis() - selfServeAt < 120_000) return false
        if (props.any { it.toy != null || it.view.kind.isFood }) return false
        if (visibleChest() == null) return false
        val hungry = pet.satiety < 45f
        val bored = pet.happiness < 55f && pet.energy > 35f
        return (hungry || bored) && rnd.nextFloat() < 0.5f
    }

    private fun checkChestBump(dt: Float) {
        if (chestBumpCd > 0f) chestBumpCd -= dt
        if (chestLid > 0f) {
            chestLid -= dt
            if (chestLid <= 0f && chestPanel == null) stations[StationKind.CHEST]?.view?.open = false
        }
        // L'objet sort une fois le lapin retombé (pas en plein vol)
        val pop = chestPop
        if (pop != null) {
            if (act != Act.AIR && act != Act.DRAGGED) { chestPop = null; popFromChest(pop) }
            return
        }
        if (act != Act.AIR || chestBumpCd > 0f) return
        val s = visibleChest() ?: return
        val bx = fx + bunnyShift
        val hw = 26 * d
        val l = s.lp.x.toFloat(); val t = s.lp.y.toFloat()
        val r = l + s.lp.width; val b = t + s.lp.height
        if (bx + hw < l || bx - hw > r || fy < t + 10 * d || fy - 95 * d > b) return
        // Boum !
        chestBumpCd = 3f
        s.view.open = true; chestLid = 1.6f
        vx = -vx * 0.35f; vy = kotlin.math.max(vy, 150 * d)
        airLandY = null
        bunny.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        express(Expr.SURPRISED, 1f)
        if (props.any { it.toy != null || it.view.kind.isFood }) {
            say(pick("Bonk ! 😵", "Aïe, le coffre !"), 1.5f)
        } else {
            chestPop = s
            say(pick("Bonk ! Oh, le coffre s'ouvre ! 🎁", "Oups, j'ai cogné le coffre !", "Sésame, ouvre-toi ! ✨"), 2f)
        }
    }

    /** Sort de quoi manger (s'il a faim) ou un jouet du coffre. */
    private fun popFromChest(s: Station) {
        if (!s.shown) return
        s.view.open = true; chestLid = 0.9f
        val keep = lastInteraction            // ce n'est pas l'utilisateur qui s'est occupé de lui
        val glutton = pet.personality == Personality.GLUTTON && rnd.nextFloat() < 0.6f
        if (pet.satiety < 60f || glutton || pet.energy < 25f) {
            val f = when {
                pet.health < 60f -> Food.LETTUCE
                pet.happiness < 40f -> Food.STRAWBERRY
                rnd.nextFloat() < 0.7f -> Food.CARROT
                else -> Food.STRAWBERRY
            }
            feed(f, from = s)
        } else {
            val t = if (rnd.nextFloat() < 0.5f) pet.favoriteToy else Toy.values()[rnd.nextInt(Toy.values().size)]
            startPlay(t, from = s)
        }
        lastInteraction = keep
    }

    private fun closeChest() {
        chestPanel?.let { safeRemove(it) }
        chestPanel = null
        chestOf?.view?.open = false
        chestOf = null
    }

    private fun enterStation(k: StationKind, announce: Boolean = true) {
        if (k == StationKind.CHEST) return
        val s = stations[k] ?: return
        noteInteraction()
        // Une station utilisée est installée sur la vue courante
        if (!stationsAlways && currentView.isNotEmpty() && s.anchor != currentView) { s.anchor = currentView; saveAnchor(s) }
        addStationWindow(s)
        inStation = k
        s.view.occupied = true
        platform = null; airLandY = null; vx = 0f; vy = 0f
        currentToy()?.held = false
        positionInStation(k)
        when (k) {
            StationKind.CHEST -> {}
            StationKind.BATH -> {
                pet.sleeping = false
                setAct(Act.BATH)
                if (announce) say(pick("Un bain ! 🫧", "Splash ! 💦", "Brrr, elle est froide !"))
            }
            StationKind.VET -> {
                setAct(Act.VET)
                openVetPanel(s)
                if (announce) say(pick("Bonjour docteur… 😳", "J'ai peur des piqûres…", "Ça va être long ?"))
            }
            StationKind.CAGE -> {
                pet.caged = true; pet.save()
                chatterIn = 20f
                setAct(Act.CAGED)
                if (announce) { express(Expr.SAD, 2f); say(pick("Hé ! Pourquoi la cage ?? 🥺", "Laisse-moi sortir ! 😢", "J'ai rien fait !")) }
                updateNotification(force = true)
            }
        }
        tempStations = false
        refreshStations()
    }

    private fun leaveStation(hopOut: Boolean) {
        val k = inStation ?: return
        stations[k]?.view?.occupied = false
        inStation = null
        if (k == StationKind.VET) closeVetPanel()
        if (k == StationKind.CAGE) {
            pet.caged = false; pet.save()
            express(Expr.HAPPY, 2f)
            bunny.emit(PType.HEART, 3)
            say(pick("Libre !! 🎉", "Enfin dehors ! Merci !", "Je serai sage, promis 😇"))
            updateNotification(force = true)
        }
        tempStations = false
        refreshStations()
        if (hopOut) jumpTo((fx + (if (fx < screenW / 2) 90 else -90) * d).coerceIn(minX, maxX), groundY, Act.IDLE)
        else setAct(Act.IDLE, 2f)
    }

    private fun openVetPanel(s: Station) {
        closeVetPanel()
        val p = VetPanel(this, pet,
            onHeal = {
                if (pet.health >= 90f) say("Il va très bien ! ✅")
                else {
                    pet.heal()
                    bunny.emit(PType.SPARKLE, 6)
                    express(Expr.SAD, 1.5f)
                    say(pick("Aïe ! … merci docteur 💉", "Même pas mal ! (un peu)"))
                }
                vetPanel?.refresh()
            },
            onClose = { leaveStation(hopOut = true) })
        val w = (320 * d).toInt()
        val lp = overlayParams(w, WindowManager.LayoutParams.WRAP_CONTENT, touchable = true)
        p.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        val right = s.cx + s.lp.width / 2f + 12 * d
        lp.x = (if (right + w < screenW) right else s.cx - s.lp.width / 2f - 12 * d - w).roundToInt().coerceIn(8, (screenW - w - 8).toInt())
        lp.y = (s.cy - p.measuredHeight / 2f).roundToInt().coerceIn(topY.toInt(), (groundY - p.measuredHeight).toInt().coerceAtLeast(topY.toInt()))
        wm.addView(p, lp)
        vetPanel = p
    }

    private fun closeVetPanel() {
        vetPanel?.let { safeRemove(it) }
        vetPanel = null
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun createDock() {
        val size = (52 * d).toInt()
        dock = TextView(this).apply {
            text = "🏠"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#FFFDF9"))
                setStroke((2 * d).toInt(), android.graphics.Color.parseColor("#5B4A55"))
            }
        }
        dockLp = overlayParams(size, size, touchable = true)
        val prefs = getSharedPreferences("pet", MODE_PRIVATE)
        placeDock(prefs.getFloat("dock_fx", 1f) * screenW, prefs.getFloat("dock_fy", 0.42f) * groundY)
        val slop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        dock.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; startX = dockLp.x; startY = dockLp.y; moved = false
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (moved || hypot(ev.rawX - downX, ev.rawY - downY) > slop) {
                        moved = true
                        placeDock(startX + ev.rawX - downX, startY + ev.rawY - downY)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (moved) {
                        // se colle au bord le plus proche
                        val snapX = if (dockLp.x + size / 2f < screenW / 2) 4 * d else screenW - size - 4 * d
                        placeDock(snapX, dockLp.y.toFloat())
                        prefs.edit().putFloat("dock_fx", dockLp.x / screenW).putFloat("dock_fy", dockLp.y / groundY).apply()
                    } else if (ev.actionMasked == MotionEvent.ACTION_UP) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        toggleStations()
                    }
                }
            }
            true
        }
        wm.addView(dock, dockLp)
        dockAdded = true
    }

    private fun placeDock(x: Float, y: Float) {
        if (!::dockLp.isInitialized) return
        dockLp.x = x.roundToInt().coerceIn(0, (screenW - dockLp.width).toInt())
        dockLp.y = y.roundToInt().coerceIn(topY.toInt(), (groundY - dockLp.height).toInt())
        if (dockAdded) wm.updateViewLayout(dock, dockLp)
    }

    // =====================================================================
    //  Cache-cache : il va se cacher sur une autre page du bureau
    // =====================================================================
    private var hidingView: String? = null
    private var hideStarted = 0L
    private var giggleIn = 5f

    private fun isHiding() = act == Act.HIDE_RUN || act == Act.HIDDEN || act == Act.PEEK

    /** Possible seulement sur le bureau, avec plusieurs pages (détectées via l'accessibilité). */
    private fun canHideSeek(): Boolean {
        val (pkg, page) = splitView(currentView)
        return Platforms.connected && page != null && Platforms.pageCount >= 2 && pkg == Platforms.launcherPkg
    }

    private fun startHideSeek(fromMenu: Boolean): Boolean {
        if (inStation != null || pet.sleeping || isHiding()) return false
        if (!canHideSeek()) {
            if (fromMenu) say(
                if (!Platforms.connected) "Active l'accessibilité pour jouer à cache-cache 🫣"
                else "Va sur l'écran d'accueil pour jouer à cache-cache ! 🏠", 3f
            )
            return false
        }
        val (pkg, page) = splitView(currentView)
        val target = (1..Platforms.pageCount).filter { it != page }.random(rnd)
        hidingView = "$pkg#p$target"
        targetX = if (target > page!!) screenW + bw else -bw.toFloat()
        noteInteraction()
        closeMenu()
        say(pick("Cache-cache ! Compte jusqu'à 10… 🙈", "Tu ne me trouveras jamais ! 🫣", "Attrape-moi si tu peux !"), 2.5f)
        setAct(Act.HIDE_RUN)
        return true
    }

    /** Il apparaît caché sur la bonne page : tête qui dépasse d'un bord. */
    private fun startPeek() {
        platform = null; airLandY = null; vx = 0f; vy = 0f
        when (rnd.nextInt(3)) {
            0 -> { fx = 0f; fy = groundY; bunny.facing = 1f }
            1 -> { fx = screenW; fy = groundY; bunny.facing = -1f }
            else -> { fx = minX + rnd.nextFloat() * (maxX - minX); fy = groundY + 62 * d; bunny.facing = if (fx < screenW / 2) 1f else -1f }
        }
        giggleIn = 4f + rnd.nextFloat() * 4f
        setAct(Act.PEEK)
    }

    private fun foundBunny() {
        hidingView = null
        noteInteraction()
        pet.happiness = (pet.happiness + 15f).coerceAtMost(100f)
        bunny.emit(PType.HEART, 5)
        express(Expr.HAPPY, 2.5f)
        say(pick("Trouvé !! 🎉", "Oh non, tu m'as trouvé ! 😆", "Tu es trop fort ! On rejoue ?"), 2.5f)
        dock.text = "🏠"
        jumpTo((fx.coerceIn(minX, maxX) + (if (fx < screenW / 2) 120 else -120) * d).coerceIn(minX, maxX), groundY, Act.IDLE)
    }

    private fun giveUpHiding() {
        val wasHidden = act == Act.HIDDEN
        hidingView = null
        dock.text = "🏠"
        pet.happiness = (pet.happiness - 3f).coerceAtLeast(0f)
        if (wasHidden) {
            fx = screenW / 2; fy = topY + 150 * d; vx = 0f; vy = 0f
            afterAir = Act.IDLE
            setAct(Act.AIR)
            applyView(0)
        } else setAct(Act.IDLE, 2f)
        express(Expr.SAD, 2f)
        say(pick("Tu ne m'as pas trouvé… 😢 Je reviens !", "Personne ne me cherche ? 🥺"), 3f)
    }

    private fun updateHide(dt: Float) {
        when (act) {
            Act.HIDE_RUN -> {
                if (walkToward(targetX, 300 * d, dt, clamp = false)) {
                    hideStarted = System.currentTimeMillis()
                    setAct(Act.HIDDEN)
                    dock.text = "🫣"
                    updateNotification(force = true)
                    applyView(0)
                }
            }
            Act.HIDDEN -> {
                if (System.currentTimeMillis() - hideStarted > 180_000) giveUpHiding()
            }
            Act.PEEK -> {
                bunny.pose = Pose.SIT
                giggleIn -= dt
                if (giggleIn <= 0f) {
                    giggleIn = 5f + rnd.nextFloat() * 5f
                    if (rnd.nextFloat() < 0.5f) say(pick("hihi…", "🤭", "chut…"), 1.2f)
                }
                if (System.currentTimeMillis() - hideStarted > 180_000) giveUpHiding()
            }
            else -> {}
        }
    }

    // =====================================================================
    //  Bêtises : il cherche l'attention et embête (gentiment) l'utilisateur
    // =====================================================================
    private var mischief = 1                 // 0 sage, 1 taquin, 2 petit monstre
    private var lastInteraction = System.currentTimeMillis()
    private var mischiefCooldownUntil = 0L
    private var seenClick = -1
    private var seenTyping = -1
    private var seenScroll = -1
    private var typingCount = 0
    private var airLandY: Float? = null
    private var knockDone = false
    private var squatLimit = 20f
    private var squatRect = Rect()
    private var squatKbd = false
    private var squatVersion = 0

    private fun noteInteraction() { lastInteraction = System.currentTimeMillis() }

    private fun exemptApp(): Boolean {
        val pkg = (Platforms.foregroundPkg ?: watcher.currentPkg ?: "").lowercase()
        return listOf("dialer", "incallui", "telecom", "phone").any { it in pkg }
    }

    /** Appelé quand il choisit quoi faire : s'il est ignoré depuis longtemps, il fait une bêtise. */
    private fun maybeSeekAttention(): Boolean {
        if (inStation != null) return false
        val now = System.currentTimeMillis()
        val thresholdMin = when (mischief) { 0 -> 12f; 1 -> 6f; else -> 3f } *
            (if (pet.personality == Personality.CUDDLY) 0.6f else 1f)
        val thresholdMs = (thresholdMin * 60_000).toLong()
        if (now - lastInteraction < thresholdMs || now < mischiefCooldownUntil) return false
        if (rnd.nextFloat() > 0.5f) return false
        mischiefCooldownUntil = now + if (mischief == 2) 50_000 else 100_000
        lastInteraction = now - thresholdMs / 2      // recommence plus tard s'il est toujours ignoré
        if (mischief == 0 || exemptApp()) {
            say(pick("Tu m'oublies ? 🥺", "Coucou… je suis là ! 👋", "Psst… 👀"))
            return false
        }
        // Il va se cacher sur une autre page pour que tu viennes le chercher
        if (canHideSeek() && rnd.nextFloat() < 0.35f && startHideSeek(fromMenu = false)) return true
        val plats = Platforms.list
        if (plats.isNotEmpty() && rnd.nextFloat() < (if (mischief == 2) 0.6f else 0.35f)) {
            val midY = (topY + groundY) / 2
            val p = plats.sortedBy { abs(it.rect.exactCenterX() - screenW / 2) + abs(it.rect.exactCenterY() - midY) }
                .take(4).random(rnd)
            squatOn(p.rect, (p.rect.exactCenterY() + 46 * d).coerceAtMost(groundY), if (mischief == 2) 60f else 25f,
                pick("Je m'installe ici ! 😼", "Tu ne m'as pas vu ? Et là ? 😏", "Coucou ! Je bloque ? 😇"), kbd = false)
            return true
        }
        knockDone = false
        setAct(Act.KNOCK)
        return true
    }

    /** Il saute et s'assoit SUR un bouton / le clavier (son corps le recouvre). */
    private fun squatOn(rect: Rect, feetY: Float, limit: Float, line: String, kbd: Boolean) {
        squatRect = Rect(rect); squatKbd = kbd; squatLimit = limit
        squatVersion = Platforms.version
        chatterIn = 7f
        mischiefCooldownUntil = System.currentTimeMillis() + 60_000
        jumpTo(rect.exactCenterX().coerceIn(minX, maxX), feetY, Act.SQUAT)
        airLandY = feetY
        say(line, 2.5f)
    }

    private fun updateSquat(dt: Float) {
        bunny.pose = Pose.SIT
        faceToward(screenW / 2)
        if (actTime > squatLimit) {
            say(pick("Bon… je m'ennuie ici.", "Tu ne veux pas jouer ? Tant pis…"))
            jumpTo((fx + bunny.facing * 80 * d).coerceIn(minX, maxX), groundY, Act.IDLE)
            return
        }
        // L'élément sous lui a disparu (défilement, autre app, clavier fermé) : il tombe
        val gone = if (squatKbd) Platforms.ime == null
        else Platforms.version != squatVersion && Platforms.connected &&
            Platforms.list.none { Rect.intersects(it.rect, squatRect) }
        if (squatKbd || Platforms.version != squatVersion) squatVersion = Platforms.version
        if (gone) {
            vx = 0f; vy = 0f; afterAir = Act.IDLE
            say(pick("Whoaaa !", "Oups, tout a bougé !"), 1.4f)
            setAct(Act.AIR)
            return
        }
        chatterIn -= dt
        if (chatterIn <= 0f) {
            chatterIn = 7f + rnd.nextFloat() * 5f
            say(pick("Je ne bouge pas ! 😼", "C'est MA place maintenant", "Un câlin et je m'en vais…",
                "Tu veux appuyer ici ? Hihi 😜", "Un bisou magique pour passer ? 😘"), 2.2f)
        }
    }

    /** Réagit à ce que fait l'utilisateur (clics, frappe au clavier, défilement). */
    private fun checkUserEvents() {
        val cv = Platforms.clickVersion; val tv = Platforms.typingVersion; val sv = Platforms.scrollVersion
        val newClick = seenClick != -1 && cv != seenClick
        val newType = seenTyping != -1 && tv != seenTyping
        val newScroll = seenScroll != -1 && sv != seenScroll
        seenClick = cv; seenTyping = tv; seenScroll = sv
        if (mischief == 0 || pet.sleeping || inStation != null || hidden) return
        if (newType) typingCount++
        val free = act == Act.IDLE || act == Act.WALK || act == Act.SIT
        val now = System.currentTimeMillis()
        if (!free || now < mischiefCooldownUntil || exemptApp()) return

        if (newClick) {
            val r = Platforms.clickRect
            if (r != null && rnd.nextFloat() < (if (mischief == 2) 0.22f else 0.10f)) {
                if (mischief == 2 && r.width() < 600 * d && r.height() < 300 * d && r.top > topY + 120 * d) {
                    squatOn(r, (r.exactCenterY() + 46 * d).coerceAtMost(groundY), 40f,
                        pick("C'est MON bouton maintenant 😼", "Tu voulais appuyer ici ? Trop tard ! 😜"), kbd = false)
                } else {
                    faceToward(r.exactCenterX())
                    express(Expr.SURPRISED, 1f)
                    say(pick("Ooh, qu'est-ce que tu as touché ? 👀", "Je peux appuyer aussi ?", "Clic clic !"))
                    mischiefCooldownUntil = now + 30_000
                }
                return
            }
        }
        if (typingCount >= 12) {
            typingCount = 0
            val ime = Platforms.ime
            if (ime != null && rnd.nextFloat() < 0.5f) {
                if (mischief == 2) {
                    val x = ime.left + ime.width() * (0.25f + rnd.nextFloat() * 0.5f)
                    squatOn(Rect(x.toInt() - 1, ime.top, x.toInt() + 1, ime.top), ime.top.toFloat(), 15f,
                        pick("Je t'aide à écrire ! ⌨️", "Tu écris à qui ? 👀 Je regarde !"), kbd = true)
                } else {
                    say(pick("Tu écris à qui ? 👀", "Tu tapes vite !", "Dis-lui que je fais coucou !"))
                    mischiefCooldownUntil = now + 45_000
                }
                return
            }
        }
        if (newScroll && rnd.nextFloat() < 0.04f) {
            say("Doucement, ça me donne le tournis ! 😵")
            setAct(Act.DIZZY)
            mischiefCooldownUntil = now + 40_000
        }
    }

    private fun knockVibrate() {
        try {
            val vib = getSystemService(android.os.VibratorManager::class.java).defaultVibrator
            vib.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 35, 140, 35, 140, 35), -1))
        } catch (_: Exception) {}
    }

    // =====================================================================
    //  Notifications
    // =====================================================================
    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PET, "Cookie est là", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Notification permanente du lapin"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Besoins de Cookie", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Quand Cookie a faim, est sale ou malade"
        })
    }

    private fun pi(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, BunnyService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun statusLine(): String =
        "🥕${pet.satiety.toInt()}  💖${pet.happiness.toInt()}  ⚡${pet.energy.toInt()}  🛁${pet.cleanliness.toInt()}"

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val moodTxt = if (hidingView != null) "se cache… trouve-le sur une autre page ! 🫣" else when (pet.mood) {
            PetState.Mood.HAPPY -> "est aux anges"
            PetState.Mood.OK -> "se promène"
            PetState.Mood.HUNGRY -> "a faim"
            PetState.Mood.SLEEPY -> if (pet.sleeping) "dort" else "a sommeil"
            PetState.Mood.DIRTY -> "a besoin d'un bain"
            PetState.Mood.SAD -> "se sent seul"
            PetState.Mood.SICK -> "est malade"
        }
        return Notification.Builder(this, CH_PET)
            .setSmallIcon(R.drawable.ic_bunny)
            .setContentTitle("${pet.name} $moodTxt" + (if (pet.caged) " (en cage 🔒)" else "") + if (hidden) " (caché)" else "")
            .setContentText(statusLine())
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "🥕 Nourrir", pi(ACTION_FEED, 1)).build())
            .addAction(Notification.Action.Builder(null, if (hidden) "Montrer" else "Cacher", pi(ACTION_TOGGLE, 2)).build())
            .addAction(Notification.Action.Builder(null, "Ranger", pi(ACTION_STOP, 3)).build())
            .build()
    }

    private fun updateNotification(force: Boolean) {
        val key = statusLine() + pet.mood + hidden
        if (!force && key == lastNotifText) return
        lastNotifText = key
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun checkAlerts() {
        val m = pet.mood
        if (m == lastAlertMood) return
        lastAlertMood = m
        val txt = when (m) {
            PetState.Mood.HUNGRY -> "${pet.name} a faim ! 🥕"
            PetState.Mood.DIRTY -> "${pet.name} a besoin d'un bain 🫧"
            PetState.Mood.SICK -> "${pet.name} est malade 🤒"
            PetState.Mood.SAD -> "${pet.name} s'ennuie de toi 🥺"
            else -> return
        }
        val n = Notification.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_bunny)
            .setContentTitle(txt)
            .setContentText("Touche pour t'occuper de lui")
            .setContentIntent(pi(ACTION_TOGGLE, 4).takeIf { hidden }
                ?: PendingIntent.getActivity(this, 5, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(2, n)
    }
}
