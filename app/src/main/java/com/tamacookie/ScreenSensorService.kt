package com.tamacookie

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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

    /** Numéro de page extrait d'une "page 2 sur 3" / "page 2 of 3"… */
    val PAGE_RE = Regex("(?i)(?:page|écran|seite|página|pagina)\\s*(\\d+)\\s*(?:sur|of|/|de|von|di)\\s*(\\d+)")

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
 * Service d'accessibilité : il ne lit QUE la position des éléments visibles
 * (et leur nom, pour que Cookie puisse dire "je suis sur Chrome !").
 * Rien n'est stocké ni envoyé.
 */
class ScreenSensorService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var scanPending = false
    private val scan = Runnable { scanPending = false; doScan() }
    private val lateScan = Runnable { doScan() }

    override fun onServiceConnected() {
        Platforms.connected = true
        scheduleScan(100)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == packageName) return
        // Défilement des pages du bureau : on le suit en direct
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
            event.packageName?.toString() == Platforms.launcherPkg && event.scrollY == 0 && event.scrollX >= 0 &&
            (event.scrollDeltaX != 0 || event.scrollX != Platforms.scrollX1)
        ) {
            Platforms.pushScroll(event.scrollX, android.os.SystemClock.uptimeMillis())
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
        Platforms.clear()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
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
        var page: Int? = null

        val d = resources.displayMetrics.density
        val wm = getSystemService(WindowManager::class.java)
        val bounds = wm.currentWindowMetrics.bounds
        val minTop = 230 * d                       // place pour la tête et le saut
        val maxTop = bounds.height() - 150 * d     // pas collé au sol
        val minW = 36 * d; val maxW = 480 * d
        val minH = 36 * d; val maxH = 260 * d

        val out = ArrayList<Platforms.P>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        val r = Rect()
        while (stack.isNotEmpty() && visited < 2000) {
            val n = stack.removeLast()
            visited++
            if (!n.isVisibleToUser) continue
            // Indicateur de page du bureau (le point "sélectionné" = page courante)
            if (page == null && n.isSelected) {
                n.contentDescription?.let { cd ->
                    Platforms.PAGE_RE.find(cd)?.let {
                        page = it.groupValues[1].toIntOrNull()
                        Platforms.pageCount = it.groupValues[2].toIntOrNull() ?: 0
                        Platforms.launcherPkg = pkg
                    }
                }
            }
            n.getBoundsInScreen(r)
            val w = r.width(); val h = r.height()
            val label = (n.text ?: n.contentDescription)?.toString()?.take(30)
            val interesting = n.isClickable || !label.isNullOrBlank()
            if (interesting && w in minW.toInt()..maxW.toInt() && h in minH.toInt()..maxH.toInt()
                && r.top >= minTop && r.top <= maxTop && r.left >= 0 && r.right <= bounds.width()
            ) {
                // élément retenu : on ne descend pas dans ses enfants
                if (out.size < 60 && out.none { Rect.intersects(it.rect, r) && it.rect.top == r.top }) {
                    out.add(Platforms.P(Rect(r), label))
                }
                continue
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
        }
        Platforms.viewKey = if (page != null) "$pkg#p$page" else pkg

        // Bureau au repos : apprendre la largeur d'une page et recaler le défilement
        val p = page
        if (p != null && pkg == Platforms.launcherPkg) {
            val now = android.os.SystemClock.uptimeMillis()
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
}
