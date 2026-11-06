package com.tamacookie

import android.os.SystemClock
import kotlin.math.abs

/**
 * "Vues" (application, ou page du bureau) et défilement du bureau.
 * Chaque objet reste sur la vue où il est posé ; le lapin vit sur une page du bureau :
 * il défile avec elle, peut partir explorer la page voisine, et revient sur la page affichée.
 */
class PageTracker(private val svc: BunnyService) {
    /** Vue affichée ("pkg" ou "pkg#p2"). */
    var currentView = ""

    /** Défilement du bureau, lissé image par image (NaN = inconnu). */
    var scrollShown = Float.NaN
        private set

    /** Page du bureau où se trouve le lapin (0 = pas sur le bureau). */
    var bunnyPage = 0
    /** Décalage à l'écran du lapin quand sa page n'est pas (exactement) celle affichée. */
    var bunnyShift = 0f
    /** Quand il revient sur la page affichée (uptime ms), 0 = pas programmé. */
    var returnAt = 0L
    private var wanderDir = 1

    private var savedPageW = -1f
    private var pageWKey: String? = null

    // Caches (évite des allocations à chaque image)
    private val splitCache = HashMap<String, Pair<String, Int?>>()
    private var lastViewPkg: String? = null
    private var lastViewPage = 0
    private var lastViewKey = ""

    /** [ViewKey.split] mis en cache. */
    fun split(v: String): Pair<String, Int?> {
        splitCache[v]?.let { return it }
        if (splitCache.size > 64) splitCache.clear()
        return ViewKey.split(v).also { splitCache[v] = it }
    }

    /** Nouvelle orientation : la largeur des pages du bureau change. */
    fun onScreenResized() {
        Platforms.pageW = 0f
        savedPageW = -1f
        pageWKey = null
        scrollShown = Float.NaN
    }

    /** Met à jour largeur de page, défilement et vue courante. Retourne true si la vue a changé. */
    fun refresh(): Boolean {
        learnPageWidth()
        updateScroll()
        val v = computeView()
        if (v.isNotEmpty() && v != currentView) {
            currentView = v
            return true
        }
        return false
    }

    /** Largeur d'une page du bureau : mémorisée par largeur d'écran (portrait / paysage). */
    private fun learnPageWidth() {
        val key = pageWKey ?: Prefs.pageWidth(svc.screenW.toInt()).also { pageWKey = it }
        if (savedPageW < 0f) {
            savedPageW = Prefs.of(svc).getFloat(key, 0f)
            if (!ScrollMath.plausiblePageWidth(savedPageW, svc.screenW)) savedPageW = 0f
            if (Platforms.pageW <= 0f && savedPageW > 0f) Platforms.pageW = savedPageW
        } else if (Platforms.pageW > 0f && Platforms.pageW != savedPageW) {
            savedPageW = Platforms.pageW
            Prefs.of(svc).edit().putFloat(key, savedPageW).apply()
        }
    }

    /** Position de défilement du bureau estimée maintenant (extrapolée entre deux événements). */
    private fun updateScroll() {
        val t1 = Platforms.scrollT1
        if (t1 == 0L) return
        val est = ScrollMath.estimate(Platforms.scrollX0, Platforms.scrollT0, Platforms.scrollX1, t1, SystemClock.uptimeMillis())
        scrollShown = if (scrollShown.isNaN() || abs(est - scrollShown) > svc.screenW * 1.5f) est
        else scrollShown + (est - scrollShown) * 0.5f
    }

    /** Le bureau défile (ou vient de défiler). */
    fun scrollingRecently(): Boolean {
        val t1 = Platforms.scrollT1
        return t1 != 0L && SystemClock.uptimeMillis() - t1 < Tuning.SCROLL_ACTIVE_MS
    }

    fun computeView(): String {
        val k = Platforms.viewKey ?: return svc.foregroundPkg() ?: currentView
        val (pkg, page) = split(k)
        // Sur le bureau, la page vient du défilement (instantané) plutôt que de l'indicateur (en retard)
        val pw = Platforms.pageW
        if (page != null && pkg == Platforms.launcherPkg && pw > 0f && !scrollShown.isNaN()) {
            val p = ScrollMath.pageAt(scrollShown, pw, Platforms.pageCount)
            if (pkg != lastViewPkg || p != lastViewPage) {
                lastViewPkg = pkg; lastViewPage = p; lastViewKey = ViewKey.of(pkg, p)
            }
            return lastViewKey
        }
        return k
    }

    /**
     * Décalage horizontal d'un objet ancré par rapport à sa position "de base" :
     * 0 = sur la vue affichée ; sur le bureau, il suit le défilement des pages comme les icônes.
     * NaN = pas visible du tout (autre application).
     */
    fun anchorOffset(anchor: String?): Float {
        if (anchor.isNullOrEmpty()) return Float.NaN
        val (apkg, apage) = split(anchor)
        if (apage == null) return if (anchor == currentView) 0f else Float.NaN
        if (split(currentView).first != apkg) return Float.NaN
        val pw = Platforms.pageW
        if (pw <= 0f || scrollShown.isNaN()) return if (anchor == currentView) 0f else Float.NaN
        return ScrollMath.offset(apage, pw, scrollShown)
    }

    /** Comme [anchorOffset], mais NaN aussi si l'objet est entièrement hors de l'écran. */
    fun visibleOffset(anchor: String?): Float {
        val off = anchorOffset(anchor)
        return if (!off.isNaN() && abs(off) < svc.screenW) off else Float.NaN
    }

    /** Page du bureau affichée, 0 hors du bureau. */
    fun launcherPage(): Int {
        val (pkg, page) = split(currentView)
        return if (page != null && pkg == Platforms.launcherPkg) page else 0
    }

    /** Vue où se trouve le lapin (sa page du bureau, sinon la vue affichée). */
    fun bunnyView(): String {
        val pkg = Platforms.launcherPkg
        return if (bunnyPage > 0 && launcherPage() != 0 && pkg != null) ViewKey.of(pkg, bunnyPage) else currentView
    }

    /** Le lapin est-il sur une autre page que celle affichée ? */
    fun bunnyElsewhere(): Boolean {
        val cp = launcherPage()
        return cp != 0 && bunnyPage > 0 && bunnyPage != cp
    }

    /** Le lapin est sur une autre page, entièrement hors de l'écran. */
    fun bunnyOffscreen(): Boolean {
        if (!bunnyElsewhere()) return false
        val sx = svc.fx + bunnyShift          // position à l'écran (marge = largeur du lapin, pour ses particules)
        return sx + svc.bw < 0f || sx - svc.bw > svc.screenW
    }

    fun updateBunnyPage() {
        val cp = launcherPage()
        if (cp == 0) {
            // On quitte le bureau alors qu'il était sur une autre page : il arrive par le bord
            if (bunnyPage > 0 && abs(bunnyShift) >= svc.screenW * 0.5f && svc.act in Act.CAN_COME_BACK_OR_PAGE_OUT) {
                svc.fx = if (bunnyShift < 0f) -svc.bw.toFloat() else svc.screenW + svc.bw
                svc.targetX = svc.screenW * (0.3f + svc.rnd.nextFloat() * 0.4f)
                svc.travelThen = Act.IDLE; svc.travelComment = null
                svc.setAct(Act.TRAVEL_IN)
            }
            bunnyPage = 0; bunnyShift = 0f; returnAt = 0L
            return
        }
        val pw = Platforms.pageW
        if (bunnyPage <= 0 || svc.act in Act.NO_PAGE_FOLLOW || pw <= 0f || scrollShown.isNaN()) {
            if (bunnyShift != 0f) svc.fx += bunnyShift
            bunnyPage = cp; bunnyShift = 0f
            return
        }
        bunnyShift = ScrollMath.offset(bunnyPage, pw, scrollShown)
    }

    /** Ramène ses coordonnées sur la page affichée sans le déplacer à l'écran (au pire, juste au bord). */
    fun rebaseToActivePage() {
        val cp = launcherPage()
        if (cp == 0) return
        if (bunnyPage == cp && bunnyShift == 0f) return
        val elsewhere = bunnyPage > 0 && bunnyPage != cp
        svc.fx = (svc.fx + bunnyShift).coerceIn(-svc.bw.toFloat(), svc.screenW + svc.bw)
        bunnyPage = cp; bunnyShift = 0f; returnAt = 0L
        if (elsewhere) {
            svc.platform = null; svc.phys.airLandY = null
            if (svc.act != Act.AIR && svc.act != Act.DRAGGED) svc.fy = svc.groundY
        }
    }

    /** Il peut partir explorer une page voisine du bureau. */
    fun canPageWander(): Boolean {
        val cp = launcherPage()
        return cp != 0 && bunnyPage == cp && Platforms.pageCount >= 2 && Platforms.pageW > 0f && !scrollShown.isNaN()
    }

    /** Il part explorer une page voisine du bureau. */
    fun startPageWander(): Boolean {
        if (!canPageWander()) return false
        val cp = launcherPage()
        val n = Platforms.pageCount
        wanderDir = when { cp <= 1 -> 1; cp >= n -> -1; else -> if (svc.rnd.nextBoolean()) 1 else -1 }
        svc.targetX = if (wanderDir > 0) svc.screenW + svc.bw else -svc.bw.toFloat()
        svc.bunny.facing = wanderDir.toFloat()
        svc.say(svc.pick("Je vais voir la page d'à côté 👀", "Je pars explorer ! 🐾", "Qu'est-ce qu'il y a par là ?",
            "Tu me retrouves ? 😜"), 2.2f)
        svc.setAct(Act.PAGE_OUT)
        return true
    }

    /** Act.PAGE_OUT : il marche jusqu'au bord… et passe sur la page voisine du bureau. */
    fun updatePageOut(dt: Float) {
        val d = svc.d
        if (svc.walkToward(svc.targetX, Tuning.PAGE_OUT_DP * d, dt, clamp = false)) {
            val pw = Platforms.pageW
            if (pw <= 0f || bunnyPage <= 0) { svc.fx = svc.fx.coerceIn(svc.minX, svc.maxX); svc.setAct(Act.IDLE, 2f); return }
            bunnyPage += wanderDir
            svc.fx -= wanderDir * pw
            returnAt = SystemClock.uptimeMillis() + svc.brain.pageWanderDurationMs()
            svc.targetX = (svc.fx + wanderDir * (150 + svc.rnd.nextFloat() * 350) * d).coerceIn(svc.minX, svc.maxX)
            svc.setAct(Act.WALK)
        }
    }

    /** Sur une autre page : après un moment (ou s'il y a à jouer / manger), il revient sur la page affichée. */
    fun checkPageReturn() {
        if (svc.hidden || svc.stations.inStation != null) return
        val cp = launcherPage()
        if (cp == 0 || bunnyPage <= 0 || bunnyPage == cp) { returnAt = 0L; return }
        val now = SystemClock.uptimeMillis()
        val hasStuff = svc.toys.hasToyOrFood()
        val act = svc.act
        when (BunnyBrain.pageReturn(now, Platforms.scrollT1, act in Act.RUSH_BACK, hasStuff, returnAt,
            act in Act.CAN_COME_BACK, svc.pet.sleeping)) {
            BunnyBrain.PageReturn.SCROLLING -> {}
            BunnyBrain.PageReturn.RUSH -> rebaseToActivePage()
            BunnyBrain.PageReturn.SCHEDULE -> returnAt = now + svc.brain.pageReturnDelayMs()
            BunnyBrain.PageReturn.WAIT -> if (hasStuff) returnAt = now
            BunnyBrain.PageReturn.COME_BACK -> {
                val fromLeft = bunnyPage < cp
                rebaseToActivePage()
                val w = svc.screenW
                svc.targetX = if (fromLeft) w * (0.15f + svc.rnd.nextFloat() * 0.3f) else w * (0.55f + svc.rnd.nextFloat() * 0.3f)
                svc.travelThen = Act.IDLE
                svc.travelComment = svc.pick("Me revoilà ! 🐾", "Tu es parti sans moi ?!", "Attends-moi !",
                    "Coucou, je t'ai retrouvé !", "J'ai fait le tour, c'est mieux ici 😄")
                svc.setAct(Act.TRAVEL_IN)
            }
        }
    }

    /** Nom lisible d'une vue : "page 2 du bureau", "Chrome"… */
    fun viewLabel(key: String?): String {
        if (key.isNullOrEmpty()) return "?"
        val (pkg, page) = split(key)
        if (page != null) return "page $page du bureau"
        val pm = svc.packageManager
        return try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
        catch (_: Exception) { pkg.substringAfterLast('.') }
    }
}
