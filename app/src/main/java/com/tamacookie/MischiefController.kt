package com.tamacookie

import com.tamacookie.feature.ActHandler
import com.tamacookie.feature.ActRegistry
import com.tamacookie.feature.mischief.HideSeekMischief
import com.tamacookie.feature.mischief.Mischief
import com.tamacookie.feature.mischief.MischiefCtx
import com.tamacookie.feature.mischief.MischiefState
import com.tamacookie.feature.mischief.Mischiefs
import com.tamacookie.feature.mischief.UserEvents

/**
 * Bêtises : le cadre (niveau, délai entre deux bêtises, applications exemptées, lecture des clics /
 * frappes / défilements) et le choix parmi les bêtises enregistrées ([Mischiefs.ALL]). Ce que fait
 * chaque bêtise est dans son `object` (`feature/mischief/`) ; ses activités passent par [ActRegistry]
 * ([registerActs]).
 *
 * Ne dépend que de [PetCtx] (testable avec `FakePetCtx`) ; agit sur [PetCtx.pet].
 */
class MischiefController(private val ctx: PetCtx, private val all: List<Mischief> = Mischiefs.ALL) {
    /** 0 sage, 1 taquin, 2 petit monstre. */
    var level = 1
    private var cooldownUntil = 0L
    private var seenClick = -1
    private var seenTyping = -1
    private var seenScroll = -1
    private val events = UserEvents()
    private val state = MischiefState()

    private val pet: Pet get() = ctx.pet

    /** Ce que reçoivent les bêtises : la façade + niveau, délai et état. */
    private val mctx: MischiefCtx = object : MischiefCtx, PetCtx by ctx {
        override val level: Int get() = this@MischiefController.level
        override var cooldownUntil: Long
            get() = this@MischiefController.cooldownUntil
            set(v) { this@MischiefController.cooldownUntil = v }
        override val state: MischiefState get() = this@MischiefController.state
    }

    /** Une bêtise en cours : la boucle et le toucher lui délèguent ses activités. */
    private inner class Running(private val m: Mischief) : ActHandler {
        override val slowFrames get() = m.slowFrames
        override val travel get() = m.travel
        override fun update(pet: Pet, dt: Float) = m.update(mctx, pet, dt)
        override fun onPet(pet: Pet) = m.onPet(mctx, pet)
    }

    /** Enregistre les activités de chaque bêtise ([Mischief.acts]). */
    fun registerActs(acts: ActRegistry) {
        for (m in all) {
            val h = Running(m)
            for (a in m.acts) acts.register(a) { h }
        }
    }

    /** Vue où il s'est caché (null = pas de cache-cache). */
    val hidingView: String? get() = state.hidingView

    fun loadLevel() { level = ctx.prefs.getInt(Prefs.MISCHIEF, 1) }

    private fun exemptApp(): Boolean {
        val pkg = (Platforms.foregroundPkg ?: ctx.foregroundPkg() ?: "").lowercase()
        return EXEMPT.any { it in pkg }
    }

    /** Appelé quand il choisit quoi faire : s'il est ignoré depuis longtemps, il fait une bêtise. */
    fun maybeSeekAttention(): Boolean {
        if (ctx.world.stationOf(pet) != null) return false
        val now = ctx.clock.wallMs()
        val thresholdMs = BunnyBrain.attentionThresholdMs(level, pet.personality)
        if (!BunnyBrain.canSeekAttention(now, ctx.lastInteraction, cooldownUntil, thresholdMs)) return false
        if (!pet.mind.brain.rollAttention()) return false
        cooldownUntil = now + BunnyBrain.attentionCooldownMs(level)
        ctx.lastInteraction = now - thresholdMs / 2      // recommence plus tard s'il est toujours ignoré
        if (level == 0 || exemptApp()) {
            ctx.say(ctx.pick("Tu m'oublies ? 🥺", "Coucou… je suis là ! 👋", "Psst… 👀"))
            return false
        }
        val p = pet
        return Mischiefs.seek(all, level, ctx.rnd, { it.canStart(mctx, p) }, { it.start(mctx, p) }) != null
    }

    /** Réagit à ce que fait l'utilisateur (clics, frappe au clavier, défilement). */
    fun checkUserEvents() {
        val cv = Platforms.clickVersion; val tv = Platforms.typingVersion; val sv = Platforms.scrollVersion
        val newClick = seenClick != -1 && cv != seenClick
        val newType = seenTyping != -1 && tv != seenTyping
        val newScroll = seenScroll != -1 && sv != seenScroll
        seenClick = cv; seenTyping = tv; seenScroll = sv
        if (level == 0 || pet.stats.sleeping || ctx.world.stationOf(pet) != null || ctx.hidden) return
        if (newType) events.typing++
        val now = ctx.clock.wallMs()
        if (pet.body.act !in Act.FREE || now < cooldownUntil || exemptApp()) return
        events.click = newClick; events.scroll = newScroll; events.now = now
        val p = pet
        Mischiefs.react(all) { it.react(mctx, p, events) }
    }

    // --- Cache-cache (menu, changement de vue, dodo, glisser) ---
    fun startHideSeek(fromMenu: Boolean): Boolean = HideSeekMischief.startFrom(mctx, pet, fromMenu)

    /** Fin du cache-cache (attrapé, dodo…). */
    fun endHiding() = HideSeekMischief.end(mctx)

    fun onViewChanged() = HideSeekMischief.onViewChanged(mctx, pet)

    private companion object {
        val EXEMPT = listOf("dialer", "incallui", "telecom", "phone")
    }
}
