package com.tamacookie

/**
 * Faux capteur d'écran : le test fixe l'état ([update], [platforms]) et envoie des signaux ([emit]).
 * La largeur de page écrite par `PageTracker` ([setPageWidth]) est enregistrée dans [snapshot].
 */
class FakeScreenSensor : ScreenSensor {
    private val state = Observable(ScreenSnapshot())
    override val snapshot: ScreenSnapshot get() = state.value
    private val queue = ArrayList<UserSignal>()
    private var version = 0

    fun update(f: ScreenSnapshot.() -> ScreenSnapshot) = state.set(snapshot.f())

    /** Nouvelle liste de plateformes (nouvelle version). */
    fun platforms(vararg boxes: Box, label: String? = null) =
        update { copy(platforms = PlatformSet(boxes.map { Platform(it, label) }, ++version)) }

    fun emit(vararg s: UserSignal) { queue += s }

    override fun observe(listener: (ScreenSnapshot) -> Unit) = state.observe(listener)

    override fun takeSignals(): List<UserSignal> {
        if (queue.isEmpty()) return emptyList()
        return ArrayList(queue).also { queue.clear() }
    }

    override fun setPageWidth(w: Float) = update { copy(pageW = w) }
}
