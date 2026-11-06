package com.tamacookie

import android.graphics.Rect
import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.BunnyView.Pose
import kotlin.math.abs

/**
 * Bêtises (il cherche l'attention et embête gentiment l'utilisateur : s'asseoir sur un bouton
 * ou le clavier, toc-toc sur l'écran) et cache-cache sur les pages du bureau.
 *
 * Ne dépend que de [PetCtx] (testable avec `FakePetCtx`) ; agit sur l'animal reçu, ou [PetCtx.pet].
 */
class MischiefController(private val ctx: PetCtx) {
    /** 0 sage, 1 taquin, 2 petit monstre. */
    var level = 1
    /** Dernière interaction de l'utilisateur (ms). */
    var lastInteraction = ctx.clock.wallMs()
    private var cooldownUntil = 0L
    private var seenClick = -1
    private var seenTyping = -1
    private var seenScroll = -1
    private var typingCount = 0
    private var knockDone = false
    private var squatLimit = 20f
    private var squatRect = Rect()
    private var squatKbd = false
    private var squatVersion = 0

    // Cache-cache
    /** Vue où il s'est caché (null = pas de cache-cache). */
    var hidingView: String? = null
        private set
    private var hideStarted = 0L
    private var giggleIn = 5f

    private val arena get() = ctx.arena
    private val d get() = arena.d
    private val pet: Pet get() = ctx.pet

    fun loadLevel() { level = ctx.prefs.getInt(Prefs.MISCHIEF, 1) }

    fun noteInteraction() { lastInteraction = ctx.clock.wallMs() }

    private fun exemptApp(): Boolean {
        val pkg = (Platforms.foregroundPkg ?: ctx.foregroundPkg() ?: "").lowercase()
        return EXEMPT.any { it in pkg }
    }

    /** Appelé quand il choisit quoi faire : s'il est ignoré depuis longtemps, il fait une bêtise. */
    fun maybeSeekAttention(): Boolean {
        if (ctx.inStation != null) return false
        val now = ctx.clock.wallMs()
        val thresholdMs = BunnyBrain.attentionThresholdMs(level, pet.stats.personality)
        if (!BunnyBrain.canSeekAttention(now, lastInteraction, cooldownUntil, thresholdMs)) return false
        if (!pet.mind.brain.rollAttention()) return false
        cooldownUntil = now + BunnyBrain.attentionCooldownMs(level)
        lastInteraction = now - thresholdMs / 2      // recommence plus tard s'il est toujours ignoré
        if (level == 0 || exemptApp()) {
            ctx.say(ctx.pick("Tu m'oublies ? 🥺", "Coucou… je suis là ! 👋", "Psst… 👀"))
            return false
        }
        val rnd = ctx.rnd
        // Il va se cacher sur une autre page pour que tu viennes le chercher
        if (canHideSeek() && rnd.nextFloat() < 0.35f && startHideSeek(fromMenu = false)) return true
        val plats = Platforms.list
        if (plats.isNotEmpty() && rnd.nextFloat() < (if (level == 2) 0.6f else 0.35f)) {
            val midY = (arena.topY + arena.groundY) / 2
            val w = arena.screenW
            val p = plats.sortedBy { abs(it.rect.exactCenterX() - w / 2) + abs(it.rect.exactCenterY() - midY) }
                .take(4).random(rnd)
            squatOn(p.rect, (p.rect.exactCenterY() + 46 * d).coerceAtMost(arena.groundY), if (level == 2) 60f else 25f,
                ctx.pick("Je m'installe ici ! 😼", "Tu ne m'as pas vu ? Et là ? 😏", "Coucou ! Je bloque ? 😇"), kbd = false)
            return true
        }
        // (sur une autre page, checkPageReturn le fait accourir : KNOCK fait partie de Act.RUSH_BACK)
        knockDone = false
        pet.setAct(Act.KNOCK)
        return true
    }

    /** Il saute et s'assoit SUR un bouton / le clavier (son corps le recouvre). */
    private fun squatOn(rect: Rect, feetY: Float, limit: Float, line: String, kbd: Boolean) {
        // Les coordonnées du bouton sont celles de l'écran affiché : il doit d'abord être sur cette page
        ctx.pages.rebaseToActivePage()
        squatRect = Rect(rect); squatKbd = kbd; squatLimit = limit
        squatVersion = Platforms.version
        pet.mind.chatterIn = 7f
        cooldownUntil = ctx.clock.wallMs() + 60_000
        ctx.actor.jumpTo(pet, rect.exactCenterX().coerceIn(arena.minX, arena.maxX), feetY, Act.SQUAT)
        pet.body.phys.airLandY = feetY
        ctx.say(line, 2.5f)
    }

    /** Act.SQUAT */
    fun updateSquat(pet: Pet, dt: Float) {
        pet.body.pose = Pose.SIT
        ctx.actor.faceToward(pet, arena.screenW / 2)
        if (pet.body.actTime > squatLimit) {
            ctx.say(ctx.pick("Bon… je m'ennuie ici.", "Tu ne veux pas jouer ? Tant pis…"))
            hopOff()
            return
        }
        // L'élément sous lui a disparu (défilement, autre app, clavier fermé) : il tombe
        val gone = if (squatKbd) Platforms.ime == null
        else Platforms.version != squatVersion && Platforms.connected &&
            Platforms.list.none { Rect.intersects(it.rect, squatRect) }
        if (squatKbd || Platforms.version != squatVersion) squatVersion = Platforms.version
        if (gone) {
            pet.body.phys.stop(); pet.body.afterAir = Act.IDLE
            ctx.say(ctx.pick("Whoaaa !", "Oups, tout a bougé !"), 1.4f)
            pet.setAct(Act.AIR)
            return
        }
        pet.mind.chatterIn -= dt
        if (pet.mind.chatterIn <= 0f) {
            pet.mind.chatterIn = 7f + ctx.rnd.nextFloat() * 5f
            ctx.say(ctx.pick("Je ne bouge pas ! 😼", "C'est MA place maintenant", "Un câlin et je m'en vais…",
                "Tu veux appuyer ici ? Hihi 😜", "Un bisou magique pour passer ? 😘"), 2.2f)
        }
    }

    /** Une caresse pendant qu'il squatte : il descend. */
    fun onSquatPetted() {
        pet.stats.pet()
        ctx.emit(pet, PType.HEART, 3)
        pet.express(Expr.HAPPY, 1.5f)
        ctx.say(ctx.pick("Bon d'accord… 😇 je descends", "Un câlin ? Ça marche, je bouge !", "Hihi, gagné ! 💕"), 2f)
        hopOff()
    }

    private fun hopOff() {
        ctx.actor.jumpTo(pet, (pet.body.fx + pet.body.facing * 80 * d).coerceIn(arena.minX, arena.maxX), arena.groundY, Act.IDLE)
    }

    /** Act.KNOCK : il va au milieu de l'écran et toque. */
    fun updateKnock(pet: Pet, dt: Float) {
        if (!knockDone) {
            if (ctx.actor.walkToward(pet, arena.screenW / 2, 140 * d, dt)) {
                knockDone = true; pet.body.actTime = 0f
                pet.body.pose = Pose.IDLE
                knockVibrate()
                pet.express(Expr.SURPRISED, 1.5f)
                ctx.say(ctx.pick("Toc toc toc ! 👊 Y'a quelqu'un ?", "Hé ho ! Occupe-toi de moi !", "Je m'ennuiiiie 😩",
                    "Tu m'as oublié ?! 😤"), 3f)
            }
        } else {
            pet.body.pose = Pose.IDLE
            if (pet.body.actTime > 3.5f) pet.setAct(Act.IDLE, 3f)
        }
    }

    /** Réagit à ce que fait l'utilisateur (clics, frappe au clavier, défilement). */
    fun checkUserEvents() {
        val cv = Platforms.clickVersion; val tv = Platforms.typingVersion; val sv = Platforms.scrollVersion
        val newClick = seenClick != -1 && cv != seenClick
        val newType = seenTyping != -1 && tv != seenTyping
        val newScroll = seenScroll != -1 && sv != seenScroll
        seenClick = cv; seenTyping = tv; seenScroll = sv
        if (level == 0 || pet.stats.sleeping || ctx.inStation != null || ctx.hidden) return
        if (newType) typingCount++
        val now = ctx.clock.wallMs()
        if (pet.body.act !in Act.FREE || now < cooldownUntil || exemptApp()) return
        val rnd = ctx.rnd

        if (newClick) {
            val r = Platforms.clickRect
            if (r != null && rnd.nextFloat() < (if (level == 2) 0.22f else 0.10f)) {
                if (level == 2 && r.width() < 600 * d && r.height() < 300 * d && r.top > arena.topY + 120 * d) {
                    squatOn(r, (r.exactCenterY() + 46 * d).coerceAtMost(arena.groundY), 40f,
                        ctx.pick("C'est MON bouton maintenant 😼", "Tu voulais appuyer ici ? Trop tard ! 😜"), kbd = false)
                } else {
                    ctx.actor.faceToward(pet, r.exactCenterX())
                    pet.express(Expr.SURPRISED, 1f)
                    ctx.say(ctx.pick("Ooh, qu'est-ce que tu as touché ? 👀", "Je peux appuyer aussi ?", "Clic clic !"))
                    cooldownUntil = now + 30_000
                }
                return
            }
        }
        if (typingCount >= 12) {
            typingCount = 0
            val ime = Platforms.ime
            if (ime != null && rnd.nextFloat() < 0.5f) {
                if (level == 2) {
                    val x = ime.left + ime.width() * (0.25f + rnd.nextFloat() * 0.5f)
                    squatOn(Rect(x.toInt() - 1, ime.top, x.toInt() + 1, ime.top), ime.top.toFloat(), 15f,
                        ctx.pick("Je t'aide à écrire ! ⌨️", "Tu écris à qui ? 👀 Je regarde !"), kbd = true)
                } else {
                    ctx.say(ctx.pick("Tu écris à qui ? 👀", "Tu tapes vite !", "Dis-lui que je fais coucou !"))
                    cooldownUntil = now + 45_000
                }
                return
            }
        }
        if (newScroll && rnd.nextFloat() < 0.04f) {
            ctx.say("Doucement, ça me donne le tournis ! 😵")
            pet.setAct(Act.DIZZY)
            cooldownUntil = now + 40_000
        }
    }

    private fun knockVibrate() = ctx.vibrate(longArrayOf(0, 35, 140, 35, 140, 35))

    // =====================================================================
    //  Cache-cache : il va se cacher sur une autre page du bureau
    // =====================================================================
    fun isHiding() = pet.body.act in Act.HIDING

    /** Possible seulement sur le bureau, avec plusieurs pages (détectées via l'accessibilité). */
    private fun canHideSeek(): Boolean {
        val (pkg, page) = ctx.pages.split(ctx.pages.currentView)
        return Platforms.connected && page != null && Platforms.pageCount >= 2 && pkg == Platforms.launcherPkg
    }

    fun startHideSeek(fromMenu: Boolean): Boolean {
        if (ctx.inStation != null || pet.stats.sleeping || isHiding()) return false
        if (!canHideSeek()) {
            if (fromMenu) ctx.say(
                if (!Platforms.connected) "Active l'accessibilité pour jouer à cache-cache 🫣"
                else "Va sur l'écran d'accueil pour jouer à cache-cache ! 🏠", 3f
            )
            return false
        }
        val (pkg, page) = ctx.pages.split(ctx.pages.currentView)
        val target = (1..Platforms.pageCount).filter { it != page }.random(ctx.rnd)
        hidingView = ViewKey.of(pkg, target)
        pet.body.targetX = if (target > page!!) arena.screenW + arena.bw else -arena.bw.toFloat()
        noteInteraction()
        ctx.closeMenu()
        ctx.say(ctx.pick("Cache-cache ! Compte jusqu'à 10… 🙈", "Tu ne me trouveras jamais ! 🫣", "Attrape-moi si tu peux !"), 2.5f)
        pet.setAct(Act.HIDE_RUN)
        return true
    }

    /** Fin du cache-cache (trouvé, abandon, attrapé, dodo…). */
    fun endHiding() {
        hidingView = null
        ctx.setDockHiding(false)
    }

    /** Changement de vue : arrivé sur la bonne page -> il apparaît caché ; parti -> il redisparaît. */
    fun onViewChanged() {
        val here = ctx.pages.currentView
        if (pet.body.act == Act.HIDDEN && here == hidingView) startPeek()
        else if (pet.body.act == Act.PEEK && here != hidingView) pet.setAct(Act.HIDDEN)
    }

    /** Il apparaît caché sur la bonne page : tête qui dépasse d'un bord. */
    private fun startPeek() {
        val rnd = ctx.rnd
        pet.body.platform = null; pet.body.phys.airLandY = null; pet.body.phys.stop()

        when (rnd.nextInt(3)) {
            0 -> { pet.body.fx = 0f; pet.body.fy = arena.groundY; pet.body.facing = 1f }
            1 -> { pet.body.fx = arena.screenW; pet.body.fy = arena.groundY; pet.body.facing = -1f }
            else -> {
                pet.body.fx = arena.minX + rnd.nextFloat() * (arena.maxX - arena.minX); pet.body.fy = arena.groundY + 62 * d
                pet.body.facing = if (pet.body.fx < arena.screenW / 2) 1f else -1f
            }
        }
        giggleIn = 4f + rnd.nextFloat() * 4f
        pet.setAct(Act.PEEK)
    }

    /** Touché pendant qu'il dépasse : trouvé ! */
    fun foundBunny() {
        endHiding()
        noteInteraction()
        val st = pet.stats
        st.happiness = (st.happiness + 15f).coerceAtMost(100f)
        ctx.emit(pet, PType.HEART, 5)
        pet.express(Expr.HAPPY, 2.5f)
        ctx.say(ctx.pick("Trouvé !! 🎉", "Oh non, tu m'as trouvé ! 😆", "Tu es trop fort ! On rejoue ?"), 2.5f)
        val fx = pet.body.fx
        ctx.actor.jumpTo(pet, (fx.coerceIn(arena.minX, arena.maxX) + (if (fx < arena.screenW / 2) 120 else -120) * d).coerceIn(arena.minX, arena.maxX),
            arena.groundY, Act.IDLE)
    }

    private fun giveUpHiding() {
        val wasHidden = pet.body.act == Act.HIDDEN
        endHiding()
        val st = pet.stats
        st.happiness = (st.happiness - 3f).coerceAtLeast(0f)
        if (wasHidden) {
            pet.body.fx = arena.screenW / 2; pet.body.fy = arena.topY + Tuning.SPAWN_DROP_DP * d; pet.body.phys.stop()
            pet.body.afterAir = Act.IDLE
            pet.setAct(Act.AIR)
            ctx.applyView()
        } else {
            // Depuis PEEK : il dépassait d'un bord (ou du sol), on le ramène dans l'écran
            pet.body.fx = pet.body.fx.coerceIn(arena.minX, arena.maxX); pet.body.fy = arena.groundY
            pet.setAct(Act.IDLE, 2f)
        }
        pet.express(Expr.SAD, 2f)
        ctx.say(ctx.pick("Tu ne m'as pas trouvé… 😢 Je reviens !", "Personne ne me cherche ? 🥺"), 3f)
    }

    /** Act.HIDE_RUN / HIDDEN / PEEK */
    fun updateHide(pet: Pet, dt: Float) {
        when (pet.body.act) {
            Act.HIDE_RUN -> {
                if (ctx.actor.walkToward(pet, pet.body.targetX, Tuning.HIDE_RUN_DP * d, dt, clamp = false)) {
                    hideStarted = ctx.clock.wallMs()
                    pet.setAct(Act.HIDDEN)
                    ctx.setDockHiding(true)
                    ctx.updateNotification(force = true)
                    ctx.applyView()
                }
            }
            Act.HIDDEN -> {
                if (ctx.clock.wallMs() - hideStarted > Tuning.HIDE_GIVE_UP_MS) giveUpHiding()
            }
            Act.PEEK -> {
                pet.body.pose = Pose.SIT
                giggleIn -= dt
                if (giggleIn <= 0f) {
                    giggleIn = 5f + ctx.rnd.nextFloat() * 5f
                    if (ctx.rnd.nextFloat() < 0.5f) ctx.say(ctx.pick("hihi…", "🤭", "chut…"), 1.2f)
                }
                if (ctx.clock.wallMs() - hideStarted > Tuning.HIDE_GIVE_UP_MS) giveUpHiding()
            }
            else -> {}
        }
    }

    private companion object {
        val EXEMPT = listOf("dialer", "incallui", "telecom", "phone")
    }
}
