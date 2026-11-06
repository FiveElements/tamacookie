package com.tamacookie.feature.toy

import com.tamacookie.Act
import com.tamacookie.BunnyPhysics
import com.tamacookie.Pose
import com.tamacookie.Pet
import com.tamacookie.Prop
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/** Ballon : il le garde en l'air à coups de tête ; il flotte et ne doit pas toucher le sol. */
object BalloonToy : ToySpec {
    override val toy get() = Toy.BALLOON
    override val wDp = 52
    override val hDp = 86
    override val randomTint = true
    override val launch = Launch(aboveDp = 220f, vxDp = 60f, vyDp = -120f, chestVyDp = -250f)
    override val startLines = arrayOf("Un ballon ! 🎈 Il ne doit pas toucher le sol !", "Je vais le garder en l'air !")
    private val HEADED = arrayOf("Boing !", "En l'air !", "Hop !")
    private val FELL = arrayOf("Oh non, il a touché le sol !", "Raté ! On recommence ?")

    /** Centre du ballon (en haut de la fenêtre, la ficelle pend dessous). */
    private fun centerY(p: Prop) = p.y - p.h + p.w / 2f

    override fun play(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        val d = ctx.arena.d
        val dx = p.x - pet.body.fx
        if (abs(dx) > 26 * d) { ctx.actor.walkToward(pet, p.x, 190 * d, dt, stopDist = 26 * d); return }
        pet.body.pose = Pose.IDLE
        ctx.actor.faceToward(pet, p.x)
        val h = pet.body.fy - centerY(p)
        if (h < 320 * d && p.vy > -60 * d && pet.mind.kickCooldown <= 0f && !ctx.userHolds) {
            val need = (h - 75 * d).coerceAtLeast(15 * d)
            pet.mind.kickCooldown = 0.5f
            ctx.actor.jump(pet, BunnyPhysics.impulseForHeight(pet.body.phys.gravity, need), 0f, then = Act.PLAY)
        }
    }

    override fun step(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        val d = ctx.arena.d
        val rnd = ctx.rnd
        val fx = pet.body.fx; val fy = pet.body.fy
        val act = pet.body.act
        p.vx *= (1f - 0.9f * dt); p.vy *= (1f - 0.6f * dt)
        ctx.stepToy(pet, p, dt, 0.08f, 0.3f, 0.9f, 3f)
        p.spin = sin(p.age * 1.7f) * 8f + p.vx / d * 0.03f
        // coup de tête
        val cy = centerY(p)
        if (hypot(p.x - fx, cy - (fy - 85 * d)) < 42 * d && p.vy > -100 * d && act != Act.DRAGGED && act != Act.SLEEP) {
            p.vy = -(450 + rnd.nextFloat() * 300) * d
            p.vx = (p.x - fx) * 4f + (rnd.nextFloat() * 2f - 1f) * 150 * d
            p.onGround = false
            ctx.hit(pet, Toy.BALLOON)
            if (rnd.nextFloat() < 0.15f) ctx.say(ctx.speech.pickFrom(HEADED), 1.2f)
        }
        if (p.onGround && act == Act.PLAY && ctx.clock.wallMs() - ctx.groundSaidAt > 8000) {
            ctx.groundSaidAt = ctx.clock.wallMs()
            ctx.say(ctx.speech.pickFrom(FELL), 1.8f)
        }
    }
}
