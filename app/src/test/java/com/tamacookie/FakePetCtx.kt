package com.tamacookie

import android.content.SharedPreferences
import com.tamacookie.feature.ActRegistry
import kotlin.random.Random

/** Horloge de test, avancée à la main. */
class FakeClock(var uptime: Long = 1_000_000L, var wall: Long = 1_700_000_000_000L) : Clock {
    override fun uptimeMs() = uptime
    override fun wallMs() = wall

    fun advance(ms: Long) { uptime += ms; wall += ms }
}

/**
 * Faux hôte des contrôleurs (JVM pur) : un monde avec un résident (d'autres par [addPet]), une arène
 * 1080 × 2000 px (densité 1), une horloge et un aléatoire maîtrisés (un `Random` seedé par animal), et
 * l'enregistrement de ce que les contrôleurs demandent (répliques — avec qui parle —, effets,
 * vibrations…). Les fenêtres passent par un [FakeOverlayHost]. [pet] est l'animal courant
 * ([World.current]), comme dans le service.
 */
class FakePetCtx(seed: Int = 42, override val prefs: FakePrefs = FakePrefs()) : PetCtx {
    override val world = World()
    override val arena = Arena().apply {
        d = 1f; bw = 120; bh = 140
        screenW = 1080f; topY = 80f; groundY = 2000f; minX = 40f; maxX = 1040f
    }
    override val actor = BunnyActor(arena)
    override val clock = FakeClock()
    /** Aléatoire de l'animal courant (celui du résident : `Random(seed)`). */
    override val rnd: Random get() = pet.mind.rnd
    override val pages: PageTracker by lazy { PageTracker(this) }
    override val screen = FakeScreenSensor()
    override val acts = ActRegistry()
    override val director = Director(world)
    /** Thème saisonnier (évolution 4b) : aucun par défaut, comme toutes les traces seedées d'avant. */
    override var season: Season = Season.NONE

    /** Le résident. */
    val first: Pet = testPet(BunnyPhysics(arena.d), Random(seed)).also {
        it.body.phys.setBounds(arena.minX, arena.maxX, arena.topY, arena.groundY)
        it.body.fx = 500f; it.body.fy = arena.groundY
        world.add(it, resident = true)
    }

    /** Ajoute un compagnon (son propre `Random` seedé) posé au sol en [x]. */
    fun addPet(id: String, x: Float = 300f, seed: Int = id.hashCode(), traits: Traits = Traits()): Pet =
        testPet(BunnyPhysics(arena.d), Random(seed), id = id, traits = traits).also {
            it.body.phys.setBounds(arena.minX, arena.maxX, arena.topY, arena.groundY)
            it.body.fx = x; it.body.fy = arena.groundY
            world.add(it)
        }

    // --- Ce que les contrôleurs ont demandé ---
    val said = ArrayList<String>()
    /** Qui a dit chaque réplique de [said] (l'animal courant au moment de parler). */
    val speakers = ArrayList<PetId>()
    val emitted = ArrayList<PType>()
    val vibrations = ArrayList<LongArray>()
    var appliedViews = 0
    var notifications = 0
    var menuClosed = 0
    var dockShowsHiding = false
    var sleepRequests = 0
    var hideRequests = 0
    /** Animaux sauvegardés (dans l'ordre). */
    val saved = ArrayList<PetId>()

    // --- État piloté par le test ---
    override var hidden = false
    var foreground: String? = null
    val labels = HashMap<String, String>()
    /** Animaux dont la fenêtre est retirée. */
    val away = HashSet<PetId>()
    /** Animaux placés (`place`), dans l'ordre. */
    val placed = ArrayList<PetId>()

    override val speech = Speech({ pet.mind.rnd }) { text, _ -> said += text; speakers += pet.id }
    override fun say(text: String, seconds: Float) = speech.say(text, seconds)
    override fun pick(vararg s: String) = speech.pick(*s)

    override fun foregroundPkg() = foreground
    override fun appLabel(pkg: String): String? = labels[pkg]

    override fun emit(pet: Pet, type: PType, n: Int) { repeat(n) { emitted += type } }
    override fun haptic(pet: Pet, feedback: Int) {}
    override fun vibrate(pattern: LongArray) { vibrations += pattern }

    override var lastInteraction = clock.wallMs()
    override fun save(pet: Pet) { saved += pet.id }
    override fun startSleep(pet: Pet) { sleepRequests++; on(pet, PetEvent.FellAsleep); pet.setActOrAfterAir(Act.SLEEP) }

    override fun requestFrames() {}
    override fun updateNotification(force: Boolean) { notifications++ }
    override fun closeMenu() { menuClosed++ }
    override fun hide() { hideRequests++; hidden = true }

    override val overlay = FakeOverlayHost()
    override fun applyView() { appliedViews++ }
    override fun layoutAnchored() {}
    override fun isAway(pet: Pet) = pet.id in away
    override fun setAway(pet: Pet, away: Boolean, fadeIn: Boolean) { if (away) this.away += pet.id else this.away -= pet.id }
    override fun place(pet: Pet) { placed += pet.id }
    override fun setDockHiding(hiding: Boolean) { dockShowsHiding = hiding }
}
