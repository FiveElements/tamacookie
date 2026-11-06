package com.tamacookie

import android.content.SharedPreferences
import com.tamacookie.BunnyView.PType
import com.tamacookie.feature.ActRegistry
import kotlin.random.Random

/** Horloge de test, avancée à la main. */
class FakeClock(var uptime: Long = 1_000_000L, var wall: Long = 1_700_000_000_000L) : Clock {
    override fun uptimeMs() = uptime
    override fun wallMs() = wall

    fun advance(ms: Long) { uptime += ms; wall += ms }
}

/**
 * Faux hôte des contrôleurs (JVM pur) : un monde à un animal, une arène 1080 × 2000 px (densité 1),
 * une horloge et un aléatoire maîtrisés, et l'enregistrement de ce que les contrôleurs demandent
 * (répliques, effets, vibrations…). Les fenêtres passent par un [FakeOverlayHost].
 */
class FakePetCtx(seed: Int = 42, override val prefs: FakePrefs = FakePrefs()) : PetCtx {
    override val world = World()
    override val arena = Arena().apply {
        d = 1f; bw = 120; bh = 140
        screenW = 1080f; topY = 80f; groundY = 2000f; minX = 40f; maxX = 1040f
    }
    override val actor = BunnyActor(arena)
    override val clock = FakeClock()
    override val rnd = Random(seed)
    override val pages: PageTracker by lazy { PageTracker(this) }
    override val acts = ActRegistry()

    val stats: PetState = newPet(prefs)
    override val pet: Pet = Pet.resident(stats, BunnyPhysics(arena.d), rnd).also {
        it.body.phys.setBounds(arena.minX, arena.maxX, arena.topY, arena.groundY)
        it.body.fx = 500f; it.body.fy = arena.groundY
        world.add(it)
    }

    // --- Ce que les contrôleurs ont demandé ---
    val said = ArrayList<String>()
    val emitted = ArrayList<PType>()
    val vibrations = ArrayList<LongArray>()
    var appliedViews = 0
    var notifications = 0
    var menuClosed = 0
    var dockShowsHiding = false
    var sleepRequests = 0
    var hideRequests = 0

    // --- État piloté par le test ---
    override var hidden = false
    var foreground: String? = null
    val labels = HashMap<String, String>()
    override var bunnyAway = false

    override val speech = Speech(rnd) { text, _ -> said += text }
    override fun say(text: String, seconds: Float) = speech.say(text, seconds)
    override fun pick(vararg s: String) = speech.pick(*s)

    override fun foregroundPkg() = foreground
    override fun appLabel(pkg: String): String? = labels[pkg]

    override fun emit(pet: Pet, type: PType, n: Int) { repeat(n) { emitted += type } }
    override fun haptic(pet: Pet, feedback: Int) {}
    override fun vibrate(pattern: LongArray) { vibrations += pattern }

    override var lastInteraction = clock.wallMs()
    override fun startSleep(pet: Pet) { sleepRequests++; pet.stats.sleeping = true; pet.setActOrAfterAir(Act.SLEEP) }

    override fun requestFrames() {}
    override fun updateNotification(force: Boolean) { notifications++ }
    override fun closeMenu() { menuClosed++ }
    override fun hide() { hideRequests++; hidden = true }

    override val overlay = FakeOverlayHost()
    override fun applyView() { appliedViews++ }
    override fun layoutAnchored() {}
    override fun setBunnyAway(away: Boolean, fadeIn: Boolean) { bunnyAway = away }
    override fun placeBunny() {}
    override fun setDockHiding(hiding: Boolean) { dockShowsHiding = hiding }
}

/** Remet l'état global des capteurs à zéro (il est partagé entre les tests). */
fun resetPlatforms() {
    Platforms.clear()                                  // liste vide (version incrémentée)
    Platforms.connected = false; Platforms.screenOn = true
    Platforms.ime = null; Platforms.clickRect = null
    Platforms.clickVersion = 0; Platforms.typingVersion = 0; Platforms.scrollVersion = 0
    Platforms.foregroundPkg = null; Platforms.viewKey = null
    Platforms.scrollX0 = 0; Platforms.scrollX1 = 0; Platforms.scrollT0 = 0L; Platforms.scrollT1 = 0L
    Platforms.pageW = 0f; Platforms.pageCount = 0; Platforms.launcherPkg = null
}
