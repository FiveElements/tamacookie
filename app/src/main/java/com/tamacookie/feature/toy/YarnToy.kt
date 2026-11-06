package com.tamacookie.feature.toy

import com.tamacookie.Pet
import com.tamacookie.Pose
import com.tamacookie.Prop
import kotlin.math.abs

/** Pelote : il la pousse de la patte ; elle roule loin mais rebondit à peine. */
object YarnToy : ToySpec {
    /** Rebond au sol (la balle : 0,62). */
    const val BOUNCE = 0.18f
    /** Frottement au sol (la balle : 2,2) : elle roule loin. */
    const val FRICTION = 1.2f

    override val toy get() = Toy.YARN
    override val wDp = 36
    override val hDp = 36
    override val randomTint = true
    override val launch = Launch(aboveDp = 100f, vxDp = 220f, vyDp = -300f)
    override val startLines = arrayOf("Une pelote ! 🧶", "Elle roule, elle roule…", "Je vais tout dérouler !")
    private val PUSHED = arrayOf("Pouf !", "Roule, pelote !", "Patte de velours !")

    override fun play(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        val d = ctx.arena.d
        val rnd = ctx.rnd
        if (abs(p.x - pet.body.fx) > 28 * d) {
            ctx.actor.walkToward(pet, p.x, 150 * d, dt, stopDist = 28 * d)
            return
        }
        pet.body.pose = Pose.IDLE
        ctx.actor.faceToward(pet, p.x)
        if (p.onGround && pet.mind.kickCooldown <= 0f && !ctx.userHolds) {
            // un coup de patte au ras du sol (pas de saut, contrairement à la balle)
            val dir = if (rnd.nextFloat() < 0.8f) pet.body.facing else -pet.body.facing
            p.vx = dir * (250 + rnd.nextFloat() * 250) * d
            p.vy = -(120 + rnd.nextFloat() * 140) * d
            p.onGround = false
            pet.mind.kickCooldown = 0.9f
            ctx.hit(pet, Toy.YARN)
            if (rnd.nextFloat() < 0.2f) ctx.say(ctx.speech.pickFrom(PUSHED), 1.4f)
        }
    }

    override fun step(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        ctx.stepToy(pet, p, dt, 1f, BOUNCE, 0.4f, FRICTION)
        p.spin += p.vx / ctx.arena.d * dt * 3f
    }
}
