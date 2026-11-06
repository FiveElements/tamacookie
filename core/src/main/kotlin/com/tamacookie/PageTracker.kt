package com.tamacookie

import kotlin.math.abs

/**
 * "Vues" (application, ou page du bureau) et défilement du bureau.
 * Chaque objet reste sur la vue où il est posé ; chaque animal vit sur une page du bureau (la sienne,
 * dans son [Body]) : il défile avec elle, peut partir explorer la page voisine, et revient sur la page
 * affichée. Les fonctions `bunny*` portent sur l'animal courant ([PetCtx.pet]).
 *
 * Ne dépend que de [PetCtx] (écran : [PetCtx.screen]) : testable avec `FakePetCtx`.
 */
class PageTracker(private val ctx: PetCtx) {
    private val arena get() = ctx.arena
    private val pet: Pet get() = ctx.pet
    /** État de l'écran (immuable) : relu à chaque appel. */
    private val screen: ScreenSnapshot get() = ctx.screen.snapshot
    /** Vue affichée ("pkg" ou "pkg#p2"). */
    var currentView = ""

    /** Défilement du bureau, lissé image par image (NaN = inconnu). */
    var scrollShown = Float.NaN
        private set

    /** Page du bureau où se trouve l'animal courant (0 = pas sur le bureau) : [Body.page]. */
    var bunnyPage: Int
        get() = pet.body.page
        set(v) { pet.body.page = v }
    /** Décalage à l'écran de l'animal courant quand sa page n'est pas (exactement) celle affichée. */
    var bunnyShift: Float
        get() = pet.body.pageShift
        set(v) { pet.body.pageShift = v }
    /** Quand il revient sur la page affichée (uptime ms), 0 = pas programmé. */
    var returnAt: Long
        get() = pet.body.pageReturnAt
        set(v) { pet.body.pageReturnAt = v }
    private var wanderDir: Int
        get() = pet.body.wanderDir
        set(v) { pet.body.wanderDir = v }

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
        ctx.screen.setPageWidth(0f)
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
        val key = pageWKey ?: Prefs.pageWidth(arena.screenW.toInt()).also { pageWKey = it }
        if (savedPageW < 0f) {
            savedPageW = ctx.prefs.getFloat(key, 0f)
            if (!ScrollMath.plausiblePageWidth(savedPageW, arena.screenW)) savedPageW = 0f
            if (screen.pageW <= 0f && savedPageW > 0f) ctx.screen.setPageWidth(savedPageW)
        } else {
            val pw = screen.pageW
            if (pw <= 0f || pw == savedPageW) return
            savedPageW = pw
            ctx.prefs.edit { putFloat(key, savedPageW) }
        }
    }

    /** Position de défilement du bureau estimée maintenant (extrapolée entre deux événements). */
    private fun updateScroll() {
        val sc = screen.scroll
        if (sc.t1 == 0L) return
        val est = ScrollMath.estimate(sc.x0, sc.t0, sc.x1, sc.t1, ctx.clock.uptimeMs())
        scrollShown = if (scrollShown.isNaN() || abs(est - scrollShown) > arena.screenW * 1.5f) est
        else scrollShown + (est - scrollShown) * 0.5f
    }

    /** Le bureau défile (ou vient de défiler). */
    fun scrollingRecently(): Boolean {
        val t1 = screen.scroll.t1
        return t1 != 0L && ctx.clock.uptimeMs() - t1 < Tuning.SCROLL_ACTIVE_MS
    }

    fun computeView(): String {
        val s = screen
        val k = s.viewKey ?: return ctx.foregroundPkg() ?: currentView
        val (pkg, page) = split(k)
        // Sur le bureau, la page vient du défilement (instantané) plutôt que de l'indicateur (en retard)
        val pw = s.pageW
        if (page != null && pkg == s.launcherPkg && pw > 0f && !scrollShown.isNaN()) {
            val p = ScrollMath.pageAt(scrollShown, pw, s.pageCount)
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
        val pw = screen.pageW
        if (pw <= 0f || scrollShown.isNaN()) return if (anchor == currentView) 0f else Float.NaN
        return ScrollMath.offset(apage, pw, scrollShown)
    }

    /** Comme [anchorOffset], mais NaN aussi si l'objet est entièrement hors de l'écran. */
    fun visibleOffset(anchor: String?): Float {
        val off = anchorOffset(anchor)
        return if (!off.isNaN() && abs(off) < arena.screenW) off else Float.NaN
    }

    /** Page du bureau affichée, 0 hors du bureau. */
    fun launcherPage(): Int {
        val (pkg, page) = split(currentView)
        return if (page != null && pkg == screen.launcherPkg) page else 0
    }

    /** Vue où se trouve le lapin (sa page du bureau, sinon la vue affichée). */
    fun bunnyView(): String {
        val pkg = screen.launcherPkg
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
        val sx = pet.body.fx + bunnyShift          // position à l'écran (marge = largeur du lapin, pour ses particules)
        return sx + arena.bw < 0f || sx - arena.bw > arena.screenW
    }

    fun updateBunnyPage() {
        val cp = launcherPage()
        if (cp == 0) {
            // On quitte le bureau alors qu'il était sur une autre page : il arrive par le bord
            if (bunnyPage > 0 && abs(bunnyShift) >= arena.screenW * 0.5f && pet.body.act in Act.CAN_COME_BACK_OR_PAGE_OUT) {
                pet.body.fx = if (bunnyShift < 0f) -arena.bw.toFloat() else arena.screenW + arena.bw
                pet.body.targetX = arena.screenW * (0.3f + ctx.rnd.nextFloat() * 0.4f)
                pet.mind.travelThen = Act.IDLE; pet.mind.travelComment = null
                pet.setAct(Act.TRAVEL_IN)
            }
            bunnyPage = 0; bunnyShift = 0f; returnAt = 0L
            return
        }
        val pw = screen.pageW
        if (bunnyPage <= 0 || pet.body.act in Act.NO_PAGE_FOLLOW || pw <= 0f || scrollShown.isNaN()) {
            if (bunnyShift != 0f) pet.body.fx += bunnyShift
            bunnyPage = cp; bunnyShift = 0f
            return
        }
        bunnyShift = ScrollMath.offset(bunnyPage, pw, scrollShown)
    }

    /** Nouvel animal : il arrive sur la page affichée. */
    fun resetBunny() { bunnyPage = 0; bunnyShift = 0f; returnAt = 0L }

    /** Ramène ses coordonnées sur la page affichée sans le déplacer à l'écran (au pire, juste au bord). */
    fun rebaseToActivePage() {
        val cp = launcherPage()
        if (cp == 0) return
        if (bunnyPage == cp && bunnyShift == 0f) return
        val elsewhere = bunnyPage > 0 && bunnyPage != cp
        pet.body.fx = (pet.body.fx + bunnyShift).coerceIn(-arena.bw.toFloat(), arena.screenW + arena.bw)
        bunnyPage = cp; bunnyShift = 0f; returnAt = 0L
        if (elsewhere) {
            pet.body.platform = null; pet.body.phys.airLandY = null
            if (pet.body.act != Act.AIR && pet.body.act != Act.DRAGGED) pet.body.fy = arena.groundY
        }
    }

    /** Il peut partir explorer une page voisine du bureau. */
    fun canPageWander(): Boolean {
        val cp = launcherPage()
        val s = screen
        return cp != 0 && bunnyPage == cp && s.pageCount >= 2 && s.pageW > 0f && !scrollShown.isNaN()
    }

    /** Il part explorer une page voisine du bureau. */
    fun startPageWander(): Boolean {
        if (!canPageWander()) return false
        val cp = launcherPage()
        val n = screen.pageCount
        wanderDir = when { cp <= 1 -> 1; cp >= n -> -1; else -> if (ctx.rnd.nextBoolean()) 1 else -1 }
        pet.body.targetX = if (wanderDir > 0) arena.screenW + arena.bw else -arena.bw.toFloat()
        pet.body.facing = wanderDir.toFloat()
        ctx.say(ctx.pick(*WANDER), 2.2f)
        pet.setAct(Act.PAGE_OUT)
        return true
    }

    /** Act.PAGE_OUT : il marche jusqu'au bord… et passe sur la page voisine du bureau. */
    fun updatePageOut(pet: Pet, dt: Float) {
        val d = arena.d
        if (ctx.actor.walkToward(pet, pet.body.targetX, pet.species.spec.pageOutDp * d, dt, clamp = false)) {
            val pw = screen.pageW
            if (pw <= 0f || bunnyPage <= 0) { pet.body.fx = pet.body.fx.coerceIn(arena.minX, arena.maxX); pet.setAct(Act.IDLE, 2f); return }
            bunnyPage += wanderDir
            pet.body.fx -= wanderDir * pw
            returnAt = ctx.clock.uptimeMs() + pet.mind.brain.pageWanderDurationMs()
            pet.body.targetX = (pet.body.fx + wanderDir * (150 + ctx.rnd.nextFloat() * 350) * d).coerceIn(arena.minX, arena.maxX)
            pet.setAct(Act.WALK)
        }
    }

    /** Sur une autre page : après un moment (ou s'il y a à jouer / manger), il revient sur la page affichée. */
    fun checkPageReturn() {
        if (ctx.hidden || ctx.world.stationOf(pet) != null) return
        val cp = launcherPage()
        if (cp == 0 || bunnyPage <= 0 || bunnyPage == cp) { returnAt = 0L; return }
        val now = ctx.clock.uptimeMs()
        val hasStuff = ctx.world.hasToyOrFood()
        val act = pet.body.act
        when (BunnyBrain.pageReturn(now, screen.scroll.t1, act in Act.RUSH_BACK, hasStuff, returnAt,
            act in Act.CAN_COME_BACK, pet.stats.sleeping)) {
            BunnyBrain.PageReturn.SCROLLING -> {}
            BunnyBrain.PageReturn.RUSH -> rebaseToActivePage()
            BunnyBrain.PageReturn.SCHEDULE -> returnAt = now + pet.mind.brain.pageReturnDelayMs()
            BunnyBrain.PageReturn.WAIT -> if (hasStuff) returnAt = now
            BunnyBrain.PageReturn.COME_BACK -> {
                val fromLeft = bunnyPage < cp
                rebaseToActivePage()
                val w = arena.screenW
                pet.body.targetX = if (fromLeft) w * (0.15f + ctx.rnd.nextFloat() * 0.3f) else w * (0.55f + ctx.rnd.nextFloat() * 0.3f)
                pet.mind.travelThen = Act.IDLE
                pet.mind.travelComment = ctx.pick(*BACK)
                pet.setAct(Act.TRAVEL_IN)
            }
        }
    }

    /** Nom lisible d'une vue : "page 2 du bureau", "Chrome"… */
    fun viewLabel(key: String?): String {
        if (key.isNullOrEmpty()) return "?"
        val (pkg, page) = split(key)
        if (page != null) return desktopPage(page)
        return ctx.appLabel(pkg) ?: pkg.substringAfterLast('.')
    }

    /** Répliques des pages du bureau (l'ordre fixe les tirages) et nom d'une page. */
    private companion object {
        /** Il part explorer une page voisine. */
        val WANDER = arrayOf("Je vais voir la page d'à côté 👀", "Je pars explorer ! 🐾",
            "Qu'est-ce qu'il y a par là ?", "Tu me retrouves ? 😜")
        /** Il revient sur la page affichée. */
        val BACK = arrayOf("Me revoilà ! 🐾", "Tu es parti sans moi ?!", "Attends-moi !",
            "Coucou, je t'ai retrouvé !", "J'ai fait le tour, c'est mieux ici 😄")
        /** Nom d'une page du bureau (panneau 🏠, commentaires). */
        fun desktopPage(n: Int) = "page $n du bureau"
    }
}
