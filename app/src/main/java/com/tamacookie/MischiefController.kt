package com.tamacookie

import android.graphics.Rect
import android.os.VibrationEffect
import android.os.VibratorManager
import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.BunnyView.Pose
import kotlin.math.abs

/**
 * Bêtises (il cherche l'attention et embête gentiment l'utilisateur : s'asseoir sur un bouton
 * ou le clavier, toc-toc sur l'écran) et cache-cache sur les pages du bureau.
 */
class MischiefController(private val svc: BunnyService) {
    /** 0 sage, 1 taquin, 2 petit monstre. */
    var level = 1
    /** Dernière interaction de l'utilisateur (ms). */
    var lastInteraction = System.currentTimeMillis()
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

    private val d get() = svc.d

    fun loadLevel() { level = Prefs.of(svc).getInt(Prefs.MISCHIEF, 1) }

    fun noteInteraction() { lastInteraction = System.currentTimeMillis() }

    private fun exemptApp(): Boolean {
        val pkg = (Platforms.foregroundPkg ?: svc.foregroundPkg() ?: "").lowercase()
        return EXEMPT.any { it in pkg }
    }

    /** Appelé quand il choisit quoi faire : s'il est ignoré depuis longtemps, il fait une bêtise. */
    fun maybeSeekAttention(): Boolean {
        if (svc.stations.inStation != null) return false
        val now = System.currentTimeMillis()
        val thresholdMs = BunnyBrain.attentionThresholdMs(level, svc.pet.personality)
        if (!BunnyBrain.canSeekAttention(now, lastInteraction, cooldownUntil, thresholdMs)) return false
        if (!svc.brain.rollAttention()) return false
        cooldownUntil = now + BunnyBrain.attentionCooldownMs(level)
        lastInteraction = now - thresholdMs / 2      // recommence plus tard s'il est toujours ignoré
        if (level == 0 || exemptApp()) {
            svc.say(svc.pick("Tu m'oublies ? 🥺", "Coucou… je suis là ! 👋", "Psst… 👀"))
            return false
        }
        val rnd = svc.rnd
        // Il va se cacher sur une autre page pour que tu viennes le chercher
        if (canHideSeek() && rnd.nextFloat() < 0.35f && startHideSeek(fromMenu = false)) return true
        val plats = Platforms.list
        if (plats.isNotEmpty() && rnd.nextFloat() < (if (level == 2) 0.6f else 0.35f)) {
            val midY = (svc.topY + svc.groundY) / 2
            val w = svc.screenW
            val p = plats.sortedBy { abs(it.rect.exactCenterX() - w / 2) + abs(it.rect.exactCenterY() - midY) }
                .take(4).random(rnd)
            squatOn(p.rect, (p.rect.exactCenterY() + 46 * d).coerceAtMost(svc.groundY), if (level == 2) 60f else 25f,
                svc.pick("Je m'installe ici ! 😼", "Tu ne m'as pas vu ? Et là ? 😏", "Coucou ! Je bloque ? 😇"), kbd = false)
            return true
        }
        // (sur une autre page, checkPageReturn le fait accourir : KNOCK fait partie de Act.RUSH_BACK)
        knockDone = false
        svc.setAct(Act.KNOCK)
        return true
    }

    /** Il saute et s'assoit SUR un bouton / le clavier (son corps le recouvre). */
    private fun squatOn(rect: Rect, feetY: Float, limit: Float, line: String, kbd: Boolean) {
        // Les coordonnées du bouton sont celles de l'écran affiché : il doit d'abord être sur cette page
        svc.pages.rebaseToActivePage()
        squatRect = Rect(rect); squatKbd = kbd; squatLimit = limit
        squatVersion = Platforms.version
        svc.chatterIn = 7f
        cooldownUntil = System.currentTimeMillis() + 60_000
        svc.jumpTo(rect.exactCenterX().coerceIn(svc.minX, svc.maxX), feetY, Act.SQUAT)
        svc.phys.airLandY = feetY
        svc.say(line, 2.5f)
    }

    /** Act.SQUAT */
    fun updateSquat(dt: Float) {
        svc.bunny.pose = Pose.SIT
        svc.faceToward(svc.screenW / 2)
        if (svc.actTime > squatLimit) {
            svc.say(svc.pick("Bon… je m'ennuie ici.", "Tu ne veux pas jouer ? Tant pis…"))
            hopOff()
            return
        }
        // L'élément sous lui a disparu (défilement, autre app, clavier fermé) : il tombe
        val gone = if (squatKbd) Platforms.ime == null
        else Platforms.version != squatVersion && Platforms.connected &&
            Platforms.list.none { Rect.intersects(it.rect, squatRect) }
        if (squatKbd || Platforms.version != squatVersion) squatVersion = Platforms.version
        if (gone) {
            svc.phys.stop(); svc.afterAir = Act.IDLE
            svc.say(svc.pick("Whoaaa !", "Oups, tout a bougé !"), 1.4f)
            svc.setAct(Act.AIR)
            return
        }
        svc.chatterIn -= dt
        if (svc.chatterIn <= 0f) {
            svc.chatterIn = 7f + svc.rnd.nextFloat() * 5f
            svc.say(svc.pick("Je ne bouge pas ! 😼", "C'est MA place maintenant", "Un câlin et je m'en vais…",
                "Tu veux appuyer ici ? Hihi 😜", "Un bisou magique pour passer ? 😘"), 2.2f)
        }
    }

    /** Une caresse pendant qu'il squatte : il descend. */
    fun onSquatPetted() {
        svc.pet.pet()
        svc.bunny.emit(PType.HEART, 3)
        svc.express(Expr.HAPPY, 1.5f)
        svc.say(svc.pick("Bon d'accord… 😇 je descends", "Un câlin ? Ça marche, je bouge !", "Hihi, gagné ! 💕"), 2f)
        hopOff()
    }

    private fun hopOff() {
        svc.jumpTo((svc.fx + svc.bunny.facing * 80 * d).coerceIn(svc.minX, svc.maxX), svc.groundY, Act.IDLE)
    }

    /** Act.KNOCK : il va au milieu de l'écran et toque. */
    fun updateKnock(dt: Float) {
        val bunny = svc.bunny
        if (!knockDone) {
            if (svc.walkToward(svc.screenW / 2, 140 * d, dt)) {
                knockDone = true; svc.actTime = 0f
                bunny.pose = Pose.IDLE
                knockVibrate()
                svc.express(Expr.SURPRISED, 1.5f)
                svc.say(svc.pick("Toc toc toc ! 👊 Y'a quelqu'un ?", "Hé ho ! Occupe-toi de moi !", "Je m'ennuiiiie 😩",
                    "Tu m'as oublié ?! 😤"), 3f)
            }
        } else {
            bunny.pose = Pose.IDLE
            if (svc.actTime > 3.5f) svc.setAct(Act.IDLE, 3f)
        }
    }

    /** Réagit à ce que fait l'utilisateur (clics, frappe au clavier, défilement). */
    fun checkUserEvents() {
        val cv = Platforms.clickVersion; val tv = Platforms.typingVersion; val sv = Platforms.scrollVersion
        val newClick = seenClick != -1 && cv != seenClick
        val newType = seenTyping != -1 && tv != seenTyping
        val newScroll = seenScroll != -1 && sv != seenScroll
        seenClick = cv; seenTyping = tv; seenScroll = sv
        if (level == 0 || svc.pet.sleeping || svc.stations.inStation != null || svc.hidden) return
        if (newType) typingCount++
        val now = System.currentTimeMillis()
        if (svc.act !in Act.FREE || now < cooldownUntil || exemptApp()) return
        val rnd = svc.rnd

        if (newClick) {
            val r = Platforms.clickRect
            if (r != null && rnd.nextFloat() < (if (level == 2) 0.22f else 0.10f)) {
                if (level == 2 && r.width() < 600 * d && r.height() < 300 * d && r.top > svc.topY + 120 * d) {
                    squatOn(r, (r.exactCenterY() + 46 * d).coerceAtMost(svc.groundY), 40f,
                        svc.pick("C'est MON bouton maintenant 😼", "Tu voulais appuyer ici ? Trop tard ! 😜"), kbd = false)
                } else {
                    svc.faceToward(r.exactCenterX())
                    svc.express(Expr.SURPRISED, 1f)
                    svc.say(svc.pick("Ooh, qu'est-ce que tu as touché ? 👀", "Je peux appuyer aussi ?", "Clic clic !"))
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
                        svc.pick("Je t'aide à écrire ! ⌨️", "Tu écris à qui ? 👀 Je regarde !"), kbd = true)
                } else {
                    svc.say(svc.pick("Tu écris à qui ? 👀", "Tu tapes vite !", "Dis-lui que je fais coucou !"))
                    cooldownUntil = now + 45_000
                }
                return
            }
        }
        if (newScroll && rnd.nextFloat() < 0.04f) {
            svc.say("Doucement, ça me donne le tournis ! 😵")
            svc.setAct(Act.DIZZY)
            cooldownUntil = now + 40_000
        }
    }

    private fun knockVibrate() {
        try {
            val vib = svc.getSystemService(VibratorManager::class.java).defaultVibrator
            vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 35, 140, 35, 140, 35), -1))
        } catch (_: Exception) {}
    }

    // =====================================================================
    //  Cache-cache : il va se cacher sur une autre page du bureau
    // =====================================================================
    fun isHiding() = svc.act in Act.HIDING

    /** Possible seulement sur le bureau, avec plusieurs pages (détectées via l'accessibilité). */
    private fun canHideSeek(): Boolean {
        val (pkg, page) = svc.pages.split(svc.pages.currentView)
        return Platforms.connected && page != null && Platforms.pageCount >= 2 && pkg == Platforms.launcherPkg
    }

    fun startHideSeek(fromMenu: Boolean): Boolean {
        if (svc.stations.inStation != null || svc.pet.sleeping || isHiding()) return false
        if (!canHideSeek()) {
            if (fromMenu) svc.say(
                if (!Platforms.connected) "Active l'accessibilité pour jouer à cache-cache 🫣"
                else "Va sur l'écran d'accueil pour jouer à cache-cache ! 🏠", 3f
            )
            return false
        }
        val (pkg, page) = svc.pages.split(svc.pages.currentView)
        val target = (1..Platforms.pageCount).filter { it != page }.random(svc.rnd)
        hidingView = ViewKey.of(pkg, target)
        svc.targetX = if (target > page!!) svc.screenW + svc.bw else -svc.bw.toFloat()
        noteInteraction()
        svc.closeMenu()
        svc.say(svc.pick("Cache-cache ! Compte jusqu'à 10… 🙈", "Tu ne me trouveras jamais ! 🫣", "Attrape-moi si tu peux !"), 2.5f)
        svc.setAct(Act.HIDE_RUN)
        return true
    }

    /** Fin du cache-cache (trouvé, abandon, attrapé, dodo…). */
    fun endHiding() {
        hidingView = null
        svc.windows.setDockHiding(false)
    }

    /** Changement de vue : arrivé sur la bonne page -> il apparaît caché ; parti -> il redisparaît. */
    fun onViewChanged() {
        val here = svc.pages.currentView
        if (svc.act == Act.HIDDEN && here == hidingView) startPeek()
        else if (svc.act == Act.PEEK && here != hidingView) svc.setAct(Act.HIDDEN)
    }

    /** Il apparaît caché sur la bonne page : tête qui dépasse d'un bord. */
    private fun startPeek() {
        val rnd = svc.rnd
        svc.platform = null; svc.phys.airLandY = null; svc.phys.stop()
        val bunny = svc.bunny
        when (rnd.nextInt(3)) {
            0 -> { svc.fx = 0f; svc.fy = svc.groundY; bunny.facing = 1f }
            1 -> { svc.fx = svc.screenW; svc.fy = svc.groundY; bunny.facing = -1f }
            else -> {
                svc.fx = svc.minX + rnd.nextFloat() * (svc.maxX - svc.minX); svc.fy = svc.groundY + 62 * d
                bunny.facing = if (svc.fx < svc.screenW / 2) 1f else -1f
            }
        }
        giggleIn = 4f + rnd.nextFloat() * 4f
        svc.setAct(Act.PEEK)
    }

    /** Touché pendant qu'il dépasse : trouvé ! */
    fun foundBunny() {
        endHiding()
        noteInteraction()
        val pet = svc.pet
        pet.happiness = (pet.happiness + 15f).coerceAtMost(100f)
        svc.bunny.emit(PType.HEART, 5)
        svc.express(Expr.HAPPY, 2.5f)
        svc.say(svc.pick("Trouvé !! 🎉", "Oh non, tu m'as trouvé ! 😆", "Tu es trop fort ! On rejoue ?"), 2.5f)
        val fx = svc.fx
        svc.jumpTo((fx.coerceIn(svc.minX, svc.maxX) + (if (fx < svc.screenW / 2) 120 else -120) * d).coerceIn(svc.minX, svc.maxX),
            svc.groundY, Act.IDLE)
    }

    private fun giveUpHiding() {
        val wasHidden = svc.act == Act.HIDDEN
        endHiding()
        val pet = svc.pet
        pet.happiness = (pet.happiness - 3f).coerceAtLeast(0f)
        if (wasHidden) {
            svc.fx = svc.screenW / 2; svc.fy = svc.topY + Tuning.SPAWN_DROP_DP * d; svc.phys.stop()
            svc.afterAir = Act.IDLE
            svc.setAct(Act.AIR)
            svc.applyView()
        } else {
            // Depuis PEEK : il dépassait d'un bord (ou du sol), on le ramène dans l'écran
            svc.fx = svc.fx.coerceIn(svc.minX, svc.maxX); svc.fy = svc.groundY
            svc.setAct(Act.IDLE, 2f)
        }
        svc.express(Expr.SAD, 2f)
        svc.say(svc.pick("Tu ne m'as pas trouvé… 😢 Je reviens !", "Personne ne me cherche ? 🥺"), 3f)
    }

    /** Act.HIDE_RUN / HIDDEN / PEEK */
    fun updateHide(dt: Float) {
        when (svc.act) {
            Act.HIDE_RUN -> {
                if (svc.walkToward(svc.targetX, Tuning.HIDE_RUN_DP * d, dt, clamp = false)) {
                    hideStarted = System.currentTimeMillis()
                    svc.setAct(Act.HIDDEN)
                    svc.windows.setDockHiding(true)
                    svc.updateNotification(force = true)
                    svc.applyView()
                }
            }
            Act.HIDDEN -> {
                if (System.currentTimeMillis() - hideStarted > Tuning.HIDE_GIVE_UP_MS) giveUpHiding()
            }
            Act.PEEK -> {
                svc.bunny.pose = Pose.SIT
                giggleIn -= dt
                if (giggleIn <= 0f) {
                    giggleIn = 5f + svc.rnd.nextFloat() * 5f
                    if (svc.rnd.nextFloat() < 0.5f) svc.say(svc.pick("hihi…", "🤭", "chut…"), 1.2f)
                }
                if (System.currentTimeMillis() - hideStarted > Tuning.HIDE_GIVE_UP_MS) giveUpHiding()
            }
            else -> {}
        }
    }

    private companion object {
        val EXEMPT = listOf("dialer", "incallui", "telecom", "phone")
    }
}
