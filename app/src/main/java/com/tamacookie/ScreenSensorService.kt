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
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.abs

/**
 * Plateformes détectées à l'écran (icônes, boutons…) sur lesquelles Cookie peut sauter.
 * Partagé dans le même processus entre le service d'accessibilité et BunnyService.
 */
object Platforms {
    data class P(val rect: Rect, val label: String?)

    @Volatile var list: List<P> = emptyList()
        private set
    @Volatile var version = 0
        private set
    @Volatile var connected = false
    /** Écran allumé ? (mis à jour par ScreenSensorService ; vrai par défaut). */
    @Volatile var screenOn = true

    // Signaux pour les bêtises (jamais le contenu tapé : seulement "il tape")
    @Volatile var ime: Rect? = null
    @Volatile var clickRect: Rect? = null
    @Volatile var clickVersion = 0
    @Volatile var typingVersion = 0
    @Volatile var scrollVersion = 0
    @Volatile var foregroundPkg: String? = null

    /**
     * Identifiant de la "vue" courante : l'application, et pour l'écran d'accueil
     * la page affichée (ex. "com.sec.android.app.launcher#p2").
     */
    @Volatile var viewKey: String? = null
    // Défilement horizontal du bureau (événements TYPE_VIEW_SCROLLED du launcher, ~10/s)
    @Volatile var scrollX0 = 0
    @Volatile var scrollX1 = 0
    @Volatile var scrollT0 = 0L
    @Volatile var scrollT1 = 0L
    /** Largeur de défilement d'une page du bureau (apprise), 0 = inconnue. */
    @Volatile var pageW = 0f

    fun pushScroll(x: Int, t: Long) {
        scrollX0 = scrollX1; scrollT0 = scrollT1
        scrollX1 = x; scrollT1 = t
    }

    /** Nombre de pages du bureau (0 = inconnu). */
    @Volatile var pageCount = 0
    /** Package du bureau (launcher) quand il a été vu avec des pages. */
    @Volatile var launcherPkg: String? = null

    /**
     * Numéro de page extrait d'une "page 2 sur 3" / "page 2 of 3" / "Pagina 2 van 3" /
     * "Strona 2 z 3"… (groupes 1 et 2), ou d'un simple "2 / 3" (groupes 3 et 4).
     * Utiliser [parsePage] plutôt que les groupes directement.
     */
    val PAGE_RE = Regex(
        "(?iu)(?:page|écran|ecran|seite|página|pagina|pagine|strona|stránka|stranka|sida|side|sivu|oldal|" +
            "страница|экран)\\s*(\\d+)\\s*(?:sur|of|/|de|do|von|di|van|z|ze|av|af|из|-)\\s*(\\d+)" +
            "|^\\s*(\\d+)\\s*/\\s*(\\d+)\\s*$"
    )

    /** (page courante, nombre de pages) d'une description d'indicateur de page, sinon null. */
    fun parsePage(desc: CharSequence?): Pair<Int, Int>? {
        if (desc.isNullOrEmpty()) return null
        val m = PAGE_RE.find(desc) ?: return null
        val g = m.groupValues
        val page = (g[1].ifEmpty { g[3] }).toIntOrNull() ?: return null
        val count = (g[2].ifEmpty { g[4] }).toIntOrNull() ?: return null
        return if (page in 1..count && count <= 50) page to count else null
    }

    /**
     * Bande verticale [minTop, maxTop] (px) où l'on cherche des plateformes : proportionnelle
     * à la hauteur pour rester non vide en paysage sur téléphone.
     */
    fun detectionBand(height: Int, density: Float): Pair<Float, Float> {
        val minTop = maxOf(120 * density, 0.22f * height)          // place pour la tête et le saut
        val maxTop = height - maxOf(90 * density, 0.12f * height)  // pas collé au sol
        return minTop to maxTop
    }

    fun publish(l: List<P>) {
        if (l == list) return
        list = l
        version++
    }

    fun clear() { publish(emptyList()) }

    fun isEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, ScreenSensorService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

/**
 * Service d'accessibilité. Ce qu'il lit, et seulement cela :
 *  - la position (rectangle) des éléments visibles, pour que Cookie saute dessus ;
 *  - le nom court (description, sinon libellé) des icônes et boutons NON éditables,
 *    jamais le contenu d'un champ de saisie ni d'un mot de passe ;
 *  - le nom du package au premier plan et l'indicateur de page du bureau ;
 *  - le fait qu'un bouton soit touché, qu'on fasse défiler ou qu'on tape (sans le texte).
 * Rien n'est stocké ni envoyé : tout reste en mémoire dans le processus de l'app.
 */
class ScreenSensorService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var scanPending = false
    private val scan = Runnable { scanPending = false; runScan() }
    private val lateScan = Runnable { runScan() }
    /** Instant du dernier scan complet (uptime ms) : au plus un scan toutes les [MIN_SCAN_INTERVAL_MS]. */
    private var lastScanAt = 0L
    private var lastWindowChangeAt = 0L
    private var receiverRegistered = false
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
                    Platforms.screenOn = false
                    handler.removeCallbacks(scan); handler.removeCallbacks(lateScan)
                    scanPending = false
                    Platforms.clear()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    Platforms.screenOn = true
                    scheduleScan(300)
                }
            }
        }
    }

    override fun onServiceConnected() {
        Platforms.connected = true
        Platforms.screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
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
        scheduleScan(100)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == packageName) return
        if (!Platforms.screenOn) return
        val evPkg = event.packageName?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Hors bureau, les changements de contenu (très fréquents) ne déclenchent un scan
            // que juste après un changement de fenêtre (le temps que l'app finisse de s'afficher).
            // Les changements de fenêtre eux-mêmes (TYPE_WINDOW_STATE_CHANGED…) scannent toujours.
            if (evPkg != Platforms.launcherPkg &&
                SystemClock.uptimeMillis() - lastWindowChangeAt > WINDOW_SETTLE_MS
            ) return
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            lastWindowChangeAt = SystemClock.uptimeMillis()
        }
        // Défilement des pages du bureau : on le suit en direct
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            event.packageName?.toString() == Platforms.launcherPkg && event.scrollY == 0 && event.scrollX >= 0 &&
            (event.scrollDeltaX != 0 || event.scrollX != Platforms.scrollX1)
        ) {
            Platforms.pushScroll(event.scrollX, SystemClock.uptimeMillis())
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Pendant un défilement, tout bouge : on vide puis on rescanne
                Platforms.scrollVersion++
                Platforms.clear(); scheduleScan(350)
                // L'indicateur de page du bureau se met à jour après l'animation : on relit plus tard
                handler.removeCallbacks(lateScan)
                handler.postDelayed(lateScan, 900)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val r = Rect()
                event.source?.getBoundsInScreen(r)
                if (!r.isEmpty) { Platforms.clickRect = r; Platforms.clickVersion++ }
                scheduleScan(300)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> Platforms.typingVersion++
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
        Platforms.connected = false
        Platforms.screenOn = true
        if (receiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        Platforms.clear()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Plafonne les scans complets (batterie) : reporte le scan s'il arrive trop tôt. */
    private fun runScan() {
        if (!Platforms.screenOn) return
        val now = SystemClock.uptimeMillis()
        val wait = lastScanAt + MIN_SCAN_INTERVAL_MS - now
        if (wait > 0) { scheduleScan(wait); return }
        lastScanAt = now
        doScan()
    }

    private fun doScan() {
        // Clavier affiché ?
        Platforms.ime = try {
            windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                ?.let { w -> Rect().also { w.getBoundsInScreen(it) } }
        } catch (_: Exception) { null }

        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        if (root == null) { Platforms.clear(); return }
        val pkg = root.packageName?.toString()
        // Notre propre app et le volet de notifications ne changent pas la "vue"
        if (pkg == packageName || pkg == "com.android.systemui") { Platforms.clear(); return }
        Platforms.foregroundPkg = pkg
        var page: Int? = null                // depuis le point "sélectionné" (prioritaire)
        var pageAny: Pair<Int, Int>? = null  // depuis un indicateur non sélectionné (Pixel)
        val mayBeHome = pkg == Platforms.launcherPkg || pkg in homePkgs

        val d = resources.displayMetrics.density
        val wm = getSystemService(WindowManager::class.java)
        val bounds = wm.currentWindowMetrics.bounds
        val (minTop, maxTop) = Platforms.detectionBand(bounds.height(), d)
        val minW = 36 * d; val maxW = 480 * d
        val minH = 36 * d; val maxH = 260 * d

        val out = ArrayList<Platforms.P>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        val r = Rect()
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val n = stack.removeLast()
            visited++
            if (!n.isVisibleToUser) continue
            // Indicateur de page du bureau (le point "sélectionné" = page courante)
            // (Pixel Launcher : "Page 2 of 3" sur l'indicateur lui-même, non sélectionné ;
            //  le point sélectionné reste prioritaire)
            if (page == null && !n.isEditable && !n.isPassword) {
                val cd = n.contentDescription
                if (cd != null && (n.isSelected || (pageAny == null && mayBeHome))) {
                    Platforms.parsePage(cd)?.let { (pg, count) ->
                        if (n.isSelected) {
                            page = pg
                            Platforms.pageCount = count
                            Platforms.launcherPkg = pkg
                        } else pageAny = pg to count
                    }
                }
            }
            n.getBoundsInScreen(r)
            val w = r.width(); val h = r.height()
            if (w in minW.toInt()..maxW.toInt() && h in minH.toInt()..maxH.toInt()
                && r.top >= minTop && r.top <= maxTop && r.left >= 0 && r.right <= bounds.width()
            ) {
                // Nom court seulement pour les éléments non éditables (jamais le texte saisi)
                val label = if (!n.isEditable && !n.isPassword)
                    (n.contentDescription ?: n.text)?.toString()?.take(30) else null
                if (n.isClickable || !label.isNullOrBlank()) {
                    // élément retenu : on ne descend pas dans ses enfants
                    if (out.size < 60 && out.none { Rect.intersects(it.rect, r) && it.rect.top == r.top }) {
                        out.add(Platforms.P(Rect(r), label))
                    }
                    continue
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
        }
        if (page == null) pageAny?.let { (pg, count) ->
            page = pg
            Platforms.pageCount = count
            Platforms.launcherPkg = pkg
        }
        Platforms.viewKey = if (page != null) "$pkg#p$page" else pkg

        // Bureau au repos : apprendre la largeur d'une page et recaler le défilement
        val p = page
        if (p != null && pkg == Platforms.launcherPkg) {
            val now = SystemClock.uptimeMillis()
            // Au repos = l'animation s'est terminée (derniers pas de 0-3 px), pas un doigt
            // immobile au milieu d'un glissé (plus d'événements mais position intermédiaire).
            val ended = Platforms.scrollT1 == 0L || ScrollMath.ended(Platforms.scrollX0, Platforms.scrollX1)
            val settled = ended && (Platforms.scrollT1 == 0L || now - Platforms.scrollT1 > 700)
            if (settled) {
                if (p > 1 && Platforms.scrollT1 != 0L && Platforms.scrollX1 > 0) {
                    val w = Platforms.scrollX1.toFloat() / (p - 1)
                    val sw = bounds.width().toFloat()
                    if (ScrollMath.plausiblePageWidth(w, sw) &&(Platforms.pageW <= 0f || abs(w - Platforms.pageW) > 4f)) Platforms.pageW = w
                }
                val pw = Platforms.pageW
                val expected = if (pw > 0f) ((p - 1) * pw).toInt() else if (p == 1) 0 else -1
                if (expected >= 0 && (Platforms.scrollT1 == 0L || abs(Platforms.scrollX1 - expected) > 8)) {
                    // ex. bouton Accueil qui ramène à la page 1 sans animation
                    Platforms.pushScroll(expected, now - 1000); Platforms.pushScroll(expected, now - 1000)
                }
            }
        }
        Platforms.publish(out)
    }

    private companion object {
        const val MIN_SCAN_INTERVAL_MS = 600L
        const val MAX_NODES = 800
        const val WINDOW_SETTLE_MS = 1500L
    }
}
