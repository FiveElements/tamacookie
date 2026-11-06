package com.tamacookie

import kotlin.math.max
import kotlin.math.min

/** Ce dont la boucle d'images a besoin de la plateforme (Choreographer + Handler côté Android, faux en test). */
interface FrameScheduler {
    /** Programme une image dans [delayMs] ms (0 = prochaine vsync). */
    fun postFrame(delayMs: Long)
    fun cancelFrames()
    /** Programme une vérification de la boucle suspendue dans [delayMs] ms. */
    fun postTick(delayMs: Long)
    fun cancelTicks()
}

/**
 * Boucle d'animation (Kotlin pur) : fréquence adaptée à l'activité ([targetFps]), pas de temps borné, et
 * **suspension** quand rien de visible n'est à animer ([canSuspend]) — la simulation avance alors par petits
 * pas ([Tuning.SUSPENDED_STEP_S]) à chaque vérification ([Tuning.SUSPENDED_TICK_MS]).
 *
 * [step] fait avancer le jeu **et le rendu** de dt secondes ; ses exceptions sont signalées à [onError] sans
 * arrêter la boucle.
 */
class FrameLoop(
    private val scheduler: FrameScheduler,
    private val uptimeMs: () -> Long,
    private val targetFps: () -> Int,
    private val canSuspend: () -> Boolean,
    private val onError: (String, Exception) -> Unit = { _, _ -> },
    /** Appelé quand les images reprennent (la fenêtre de l'animal n'était plus déplacée). */
    private val onResume: () -> Unit = {},
    private val step: (Float) -> Unit,
) {
    var looping = false
        private set
    /** Boucle suspendue (rien de visible) : simple vérification périodique ([onTick]). */
    var suspended = false
        private set
    private var lastFrameNs = 0L
    private var lastTick = 0L

    /** Démarre la boucle (sans effet si elle tourne déjà). */
    fun start() {
        if (looping) return
        looping = true
        suspended = false
        lastFrameNs = 0L
        scheduler.cancelTicks()
        scheduler.postFrame(0)
    }

    fun stop() {
        looping = false
        suspended = false
        scheduler.cancelFrames()
        scheduler.cancelTicks()
    }

    /** Reprend les images si la boucle était suspendue ; vrai si elle vient de reprendre. */
    fun resume(): Boolean {
        if (!looping || !suspended) return false
        suspended = false
        scheduler.cancelTicks()
        lastFrameNs = 0L
        onResume()
        scheduler.postFrame(0)
        return true
    }

    /** Une image (horodatage vsync en ns). */
    fun onFrame(frameTimeNanos: Long) {
        if (!looping || suspended) return
        val fps = targetFps()
        val cap = max(Tuning.DT_CAP_S, 2f / fps)
        val dt = if (lastFrameNs == 0L) 0.016f else ((frameTimeNanos - lastFrameNs) / 1e9f).coerceIn(0f, cap)
        lastFrameNs = frameTimeNanos
        try {
            step(dt)
        } catch (e: Exception) {
            onError("frame error", e)
        }
        if (!looping || suspended) return
        if (canSuspend()) {
            suspended = true
            lastTick = uptimeMs()
            scheduler.postTick(Tuning.SUSPENDED_TICK_MS)
            return
        }
        // Moins de 60 i/s : on saute des vsync au lieu de calculer chaque image
        scheduler.postFrame(frameDelayMs(targetFps()))
    }

    /** Vérification de la boucle suspendue : la simulation avance, et les images reprennent s'il redevient visible. */
    fun onTick() {
        if (!looping || !suspended) return
        val now = uptimeMs()
        var left = ((now - lastTick) / 1000f).coerceAtMost(1f)
        lastTick = now
        try {
            while (left > 0f && suspended) {
                val s = min(left, Tuning.SUSPENDED_STEP_S)
                step(s)
                left -= s
            }
        } catch (e: Exception) {
            onError("suspended tick error", e)
        }
        if (!looping || !suspended) return
        if (!canSuspend()) resume()
        else scheduler.postTick(Tuning.SUSPENDED_TICK_MS)
    }

    companion object {
        /** Délai avant l'image suivante pour viser [fps] images/s (0 = chaque vsync). */
        fun frameDelayMs(fps: Int): Long = (1000L / fps - 16L).coerceAtLeast(0L)
    }
}
