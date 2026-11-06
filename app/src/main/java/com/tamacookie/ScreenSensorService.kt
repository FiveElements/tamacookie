package com.tamacookie

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Service d'accessibilité : adaptateur d'entrée du port [ScreenSensor]. Il **publie** dans le
 * [ScreenSensorHub] du processus ([AppGraph]) ; la logique (plateformes, pages, défilement) est pure
 * ([ScreenScan], [ScreenSensorHub]).
 *
 * Fils : les événements arrivent sur le fil principal, qui décide quand scanner (délais, au plus un scan
 * toutes les [MIN_SCAN_INTERVAL_MS]). Le **parcours de l'arbre** (un appel IPC par nœud, jusqu'à
 * [ScreenScan.MAX_NODES]) tourne sur un [HandlerThread] dédié, qui renvoie un [ScanResult] immuable au fil
 * principal (`post`) ; un seul scan à la fois. Le fil est arrêté dans [onDestroy] (`onInterrupt` ne
 * signale qu'une interruption du retour à l'utilisateur : le service reste connecté).
 *
 * Ce qu'il lit, et seulement cela :
 *  - la position (rectangle) des éléments visibles, pour que Cookie saute dessus ;
 *  - le nom court (description, sinon libellé) des icônes et boutons NON éditables,
 *    jamais le contenu d'un champ de saisie ni d'un mot de passe ;
 *  - le nom du package au premier plan et l'indicateur de page du bureau ;
 *  - le fait qu'un bouton soit touché, qu'on fasse défiler ou qu'on tape (sans le texte) ;
 *  - (chasseur de pubs, réglage actif) la présence et la position du bouton « Ignorer / Passer l'annonce »
 *    d'une publicité ([ScreenScan.findAdSkip] : identifiant `…:id/skip_ad_button` ou libellé exact).
 * Ce qu'il fait, et seulement cela : appuyer (`ACTION_CLICK`) sur ce bouton-là quand Cookie saute dessus
 * ([pressAdSkip]) — jamais ailleurs, jamais un geste à une coordonnée.
 * Rien n'est stocké ni envoyé : tout reste en mémoire dans le processus de l'app.
 */
class ScreenSensorService : AccessibilityService() {

    companion object {
        private const val MIN_SCAN_INTERVAL_MS = 600L
        private const val WINDOW_SETTLE_MS = 1500L

        /** Le service est-il activé dans les réglages d'accessibilité ? */
        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, ScreenSensorService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }

    private val hub: ScreenSensorHub by lazy { AppGraph.of(this).screen }
    private val handler = Handler(Looper.getMainLooper())
    private var scanPending = false
    private val scan = Runnable { scanPending = false; runScan() }
    private val lateScan = Runnable { runScan() }
    /** Instant du dernier scan complet (uptime ms) : au plus un scan toutes les [MIN_SCAN_INTERVAL_MS]. */
    private var lastScanAt = 0L
    private var lastWindowChangeAt = 0L
    /** Chasseur de pubs : recherche légère du bouton « Ignorer l'annonce », et appui (port [ScreenActions]). */
    private val adSkip by lazy { AdSkipAccess(this, hub, handler, ::worker) }
    private var receiverRegistered = false
    /** Fil du parcours de l'arbre (créé au premier scan, arrêté dans [onDestroy]). */
    private var scanThread: HandlerThread? = null
    private var scanHandler: Handler? = null
    /** Un scan tourne sur [scanThread] ; [rescan] : un autre a été demandé entre-temps. */
    private var scanInFlight = false
    private var rescan = false
    private var destroyed = false
    private val homePkgs: Set<String> by lazy {
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    handler.removeCallbacks(scan); handler.removeCallbacks(lateScan)
                    scanPending = false
                    hub.onScreenOff()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    hub.onScreenOn()
                    scheduleScan(300)
                }
            }
        }
    }

    override fun onServiceConnected() {
        hub.onConnected(getSystemService(PowerManager::class.java)?.isInteractive ?: true)
        try {
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        } catch (e: Exception) {
            Log.w("TamaCookie", "screen receiver", e)
        }
        AppGraph.of(this).screenActions.presser = { target -> adSkip.press(target) }
        scheduleScan(100)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == packageName) return
        if (!hub.snapshot.screenOn) return
        val evPkg = event.packageName?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Hors bureau, les changements de contenu (très fréquents) ne déclenchent un scan
            // que juste après un changement de fenêtre (le temps que l'app finisse de s'afficher).
            // Les changements de fenêtre eux-mêmes (TYPE_WINDOW_STATE_CHANGED…) scannent toujours.
            if (evPkg != hub.snapshot.launcherPkg &&
                SystemClock.uptimeMillis() - lastWindowChangeAt > WINDOW_SETTLE_MS
            ) {
                // …mais le bouton « Ignorer l'annonce » apparaît en cours de vidéo : recherche légère (4 IPC)
                adSkip.schedule()
                return
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            lastWindowChangeAt = SystemClock.uptimeMillis()
        }
        // Lapin rangé : personne n'utilise les plateformes, on ne scanne pas l'arbre (batterie).
        // Au redémarrage, le prochain événement d'une autre app relance un scan normalement.
        if (!BunnyService.running) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Défilement des pages du bureau suivi en direct ; pendant un défilement, tout bouge :
                // les plateformes sont vidées, puis on rescanne
                hub.onViewScrolled(evPkg, event.scrollX, event.scrollY, event.scrollDeltaX, SystemClock.uptimeMillis())
                scheduleScan(350)
                // L'indicateur de page du bureau se met à jour après l'animation : on relit plus tard
                handler.removeCallbacks(lateScan)
                handler.postDelayed(lateScan, 900)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val r = Rect()
                event.source?.getBoundsInScreen(r)
                hub.onClick(r.toBox())
                scheduleScan(300)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> hub.onTyping()
            else -> scheduleScan(250)
        }
    }

    private fun scheduleScan(delay: Long) {
        if (scanPending) handler.removeCallbacks(scan)
        scanPending = true
        handler.postDelayed(scan, delay)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        destroyed = true
        AppGraph.of(this).screenActions.presser = null
        adSkip.stop()
        handler.removeCallbacksAndMessages(null)
        scanThread?.quit()             // les scans en attente sont abandonnés ; celui en cours ne publiera pas
        scanThread = null; scanHandler = null
        hub.onDisconnected()
        super.onDestroy()
    }

    /**
     * Plafonne les scans complets (batterie) : reporte le scan s'il arrive trop tôt, ou s'il y en a déjà un
     * en cours. Fil principal : capture ce dont le scan a besoin, le lance sur [scanThread], publie le
     * résultat dans le [hub] à son retour.
     */
    private fun runScan() {
        if (destroyed || !hub.snapshot.screenOn || !BunnyService.running) return
        if (scanInFlight) { rescan = true; return }
        val now = SystemClock.uptimeMillis()
        val wait = lastScanAt + MIN_SCAN_INTERVAL_MS - now
        if (wait > 0) { scheduleScan(wait); return }
        lastScanAt = now
        val epoch = hub.epoch
        val launcherPkg = hub.snapshot.launcherPkg
        val adWanted = hub.adSkipWanted
        val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val g = ScreenScan.Geometry(bounds.width(), bounds.height(), resources.displayMetrics.density)
        scanInFlight = true
        worker().post {
            val result = try { doScan(launcherPkg, g, adWanted) } catch (e: Exception) {
                Log.w("TamaCookie", "scan", e); null
            }
            handler.post { onScanDone(result, epoch) }
        }
    }

    /** Fil principal : résultat d'un scan (null = échec). */
    private fun onScanDone(result: ScanResult?, epoch: Int) {
        scanInFlight = false
        if (destroyed) return
        if (result != null) hub.onScan(result, epoch, SystemClock.uptimeMillis())
        if (rescan) { rescan = false; scheduleScan(0) }
    }

    private fun worker(): Handler = scanHandler ?: run {
        val t = HandlerThread("TamaCookie-scan").also { it.start() }
        scanThread = t
        Handler(t.looper).also { scanHandler = it }
    }

    /**
     * **Fil du scan** : lit l'arbre de la fenêtre active (appels IPC) ; [launcherPkg] = bureau déjà reconnu.
     * Ne touche à aucun état partagé (tout est passé en paramètre ou renvoyé dans le [ScanResult]).
     */
    private fun doScan(launcherPkg: String?, g: ScreenScan.Geometry, adWanted: Boolean): ScanResult {
        // Clavier affiché ?
        val ime = try {
            windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                ?.let { w -> Rect().also { w.getBoundsInScreen(it) }.toBox() }
        } catch (_: Exception) { null }

        val root = try { rootInActiveWindow } catch (_: Exception) { null }
            ?: return ScanResult(ime, null)
        val pkg = root.packageName?.toString()
        // Notre propre app et le volet de notifications ne changent pas la "vue"
        if (pkg == null || ScreenScan.ignoredPkg(pkg, packageName)) return ScanResult(ime, null)
        val mayBeHome = pkg == launcherPkg || pkg in homePkgs
        val reader = NodeInfoReader()
        val walk = ScreenScan.walk(root, reader, g, mayBeHome)
        // Chasseur de pubs : quelques recherches ciblées, indépendantes du parcours (et de ses limites)
        val ad = if (!adWanted) null
        else ScreenScan.findAdSkip(root, reader, NodeInfoFinder, pkg)?.let { reader.bounds(it) }
        return ScanResult(ime, pkg, walk.platforms, walk.page, g.width, ad)
    }

    /** [ScreenScan.NodeReader] sur `AccessibilityNodeInfo` (minSdk 33 : plus de recyclage à faire). */
    internal class NodeInfoReader : ScreenScan.NodeReader<AccessibilityNodeInfo> {
        private val r = Rect()
        override fun isVisible(n: AccessibilityNodeInfo) = n.isVisibleToUser
        override fun isEditable(n: AccessibilityNodeInfo) = n.isEditable
        override fun isPassword(n: AccessibilityNodeInfo) = n.isPassword
        override fun isSelected(n: AccessibilityNodeInfo) = n.isSelected
        override fun isClickable(n: AccessibilityNodeInfo) = n.isClickable
        override fun description(n: AccessibilityNodeInfo): CharSequence? = n.contentDescription
        override fun text(n: AccessibilityNodeInfo): CharSequence? = n.text
        override fun bounds(n: AccessibilityNodeInfo): Box { n.getBoundsInScreen(r); return r.toBox() }
        override fun childCount(n: AccessibilityNodeInfo) = n.childCount
        override fun child(n: AccessibilityNodeInfo, i: Int): AccessibilityNodeInfo? = n.getChild(i)
    }
}

private fun Rect.toBox() = Box(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
