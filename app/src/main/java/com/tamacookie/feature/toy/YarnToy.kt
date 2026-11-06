package com.tamacookie.feature.toy

import android.graphics.Canvas
import com.tamacookie.Pet
import com.tamacookie.Pose
import com.tamacookie.Prop
import com.tamacookie.PropView
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

    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r
        c.save(); c.rotate(v.spin, w / 2, h / 2)
        val rad = w * 0.42f
        p.color = v.tint; c.drawCircle(w / 2, h / 2, rad, p)
        // fils enroulés
        r.set(w / 2 - rad * 0.8f, h / 2 - rad * 0.5f, w / 2 + rad * 0.8f, h / 2 + rad * 0.5f)
        c.drawArc(r, 200f, 140f, false, stroke)
        r.set(w / 2 - rad * 0.5f, h / 2 - rad * 0.85f, w / 2 + rad * 0.5f, h / 2 + rad * 0.85f)
        c.drawArc(r, 290f, 140f, false, stroke)
        c.drawCircle(w / 2, h / 2, rad, stroke)
        // le bout du fil
        c.drawLine(w / 2 + rad * 0.7f, h / 2 + rad * 0.7f, w * 0.98f, h * 0.98f, stroke)
        c.restore()
    }
}
