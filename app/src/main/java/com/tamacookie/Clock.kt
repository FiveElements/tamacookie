package com.tamacookie

/**
 * Temps injecté (port) : le cœur ne lit jamais `SystemClock` / `System.currentTimeMillis()` directement,
 * ce qui le rend déterministe en test (voir `FakeClock`).
 */
interface Clock {
    /** Temps monotone depuis le démarrage (ms), pour les durées et le défilement (`SystemClock.uptimeMillis` sur Android). */
    fun uptimeMs(): Long

    /** Heure murale (ms depuis l'époque), pour ce qui est sauvegardé ou dure entre deux lancements. */
    fun wallMs(): Long
}
