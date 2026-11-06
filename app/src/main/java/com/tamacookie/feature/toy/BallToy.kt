package com.tamacookie.feature.toy

import android.graphics.Canvas
import com.tamacookie.Act
import com.tamacookie.BunnyView.Pose
import com.tamacookie.Palette
import com.tamacookie.Pet
import com.tamacookie.Prop
import com.tamacookie.PropView
import kotlin.math.abs

/** Balle : il la pousse du museau. */
object BallToy : ToySpec {
    override val toy get() = Toy.BALL
    override val wDp = 42
    override val hDp = 42
    override val startLines = arrayOf("Une balle !! 🎾", "Lance-la moi !")
    private val KICKED = arrayOf("Bim !", "Attrape !", "Hop là !", "Goaaal ⚽")

    override fun play(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        val d = ctx.arena.d
        val rnd = ctx.rnd
        val dx = p.x - pet.body.fx
        if (abs(dx) > 30 * d) {
            ctx.actor.walkToward(pet, p.x, 170 * d, dt, stopDist = 30 * d)
        } else {
            pet.body.pose = Pose.IDLE
            ctx.actor.faceToward(pet, p.x)
            if (p.y > ctx.arena.groundY - 60 * d && pet.mind.kickCooldown <= 0f && !ctx.userHolds) {
                val dir = if (rnd.nextFloat() < 0.75f) pet.body.facing else -pet.body.facing
                p.vx = dir * (350 + rnd.nextFloat() * 600) * d
                p.vy = -(500 + rnd.nextFloat() * 700) * d
                p.onGround = false
                pet.mind.kickCooldown = 0.7f
                ctx.hit(pet, Toy.BALL)
                if (rnd.nextFloat() < 0.25f) ctx.say(ctx.speech.pickFrom(KICKED), 1.6f)
                ctx.actor.jump(pet, -420 * d, 0f, then = Act.PLAY)
            }
        }
    }

    override fun step(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        ctx.stepToy(pet, p, dt, 1f, 0.62f, 0.8f, 2.2f)
        p.spin += p.vx / ctx.arena.d * dt * 2.5f
    }

    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val r = pen.r
        c.save(); c.rotate(v.spin, w / 2, h / 2)
        val rad = w * 0.44f
        p.color = Palette.BALL; c.drawCircle(w / 2, h / 2, rad, p)
        p.color = Palette.BALL_STRIPE
        r.set(w / 2 - rad, h / 2 - rad * 0.3f, w / 2 + rad, h / 2 + rad * 0.3f)
        c.drawRect(r, p)
        c.drawCircle(w / 2, h / 2, rad, pen.stroke)
        c.restore()
    }
}
