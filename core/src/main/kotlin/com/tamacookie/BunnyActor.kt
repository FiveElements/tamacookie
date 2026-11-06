package com.tamacookie

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * Gestes élémentaires d'un animal (marcher, se tourner, sauter), en Kotlin pur.
 * Opère sur le [Pet] reçu, dans la géométrie partagée [arena].
 */
class BunnyActor(private val arena: Arena) {

    /** Hauteur des pieds quand il est posé : sur sa plateforme, sinon au sol. */
    fun surfaceY(pet: Pet): Float = pet.body.platform?.top ?: arena.groundY

    /**
     * Marche vers [x] à [speed] px/s. Retourne true une fois arrivé (à [stopDist] près).
     * Perché sur une icône, il saute d'abord au sol (et retourne false).
     */
    fun walkToward(
        pet: Pet, x: Float, speed: Float, dt: Float,
        stopDist: Float = 3 * arena.d, clamp: Boolean = true,
    ): Boolean {
        val b = pet.body
        val d = arena.d
        if (b.platform != null) {
            val dir = if (x > b.fx) 1f else -1f
            jumpTo(pet, (b.fx + dir * 70 * d).coerceIn(arena.minX, arena.maxX), arena.groundY, then = b.act)
            return false
        }
        val tx = if (clamp) x.coerceIn(arena.minX, arena.maxX) else x
        val dx = tx - b.fx
        b.fy = arena.groundY
        if (abs(dx) <= stopDist) {
            b.walkPhase = 0f
            return true
        }
        b.pose = Pose.WALK
        b.facing = sign(dx)
        val step = min(abs(dx), speed * dt)
        b.fx += sign(dx) * step
        b.walkPhase = (b.walkPhase + dt * (1.6f + speed / (60 * d))) % 1f
        return false
    }

    /** Se tourne vers [x] (sauf s'il est déjà quasiment dessus). */
    fun faceToward(pet: Pet, x: Float) {
        val b = pet.body
        if (abs(x - b.fx) > 2 * arena.d) b.facing = sign(x - b.fx)
    }

    /** Saut balistique pour atterrir précisément en ([tx], [ty]), puis [then]. */
    fun jumpTo(pet: Pet, tx: Float, ty: Float, then: Act) {
        val b = pet.body
        b.platform = null
        b.phys.jumpTo(tx, ty, (Tuning.JUMP_APEX_MIN_DP + pet.mind.rnd.nextFloat() * Tuning.JUMP_APEX_RAND_DP) * arena.d)
        b.afterAir = then
        b.setAct(Act.AIR)
    }

    /** Saut avec une impulsion donnée, puis [then]. */
    fun jump(pet: Pet, vyStart: Float, vxStart: Float, then: Act) {
        val b = pet.body
        b.platform = null
        b.phys.jump(vyStart, vxStart)
        b.afterAir = then
        b.setAct(Act.AIR)
    }
}
