package com.tamacookie.feature.toy

import android.graphics.Canvas
import android.graphics.Color
import com.tamacookie.Act
import com.tamacookie.BunnyPhysics
import com.tamacookie.BunnyView.Pose
import com.tamacookie.Palette
import com.tamacookie.Pet
import com.tamacookie.Prop
import com.tamacookie.PropView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Frisbee : il l'attrape au vol et le relance. */
object FrisbeeToy : ToySpec {
    override val toy get() = Toy.FRISBEE
    override val wDp = 52
    override val hDp = 30
    override val randomTint = true
    override val launch = Launch(vxDp = 650f, vyDp = -380f)
    override val complainsWhenTaken = true
    override val startLines = arrayOf("Un frisbee ! 🥏 Lance-le, je l'attrape !", "Je suis un champion du frisbee !")
    private val THROWN = arrayOf("À toi ! 🥏", "Attrape !", "Super lancer !")
    private val CAUGHT = arrayOf("Attrapé ! 🥏", "Trop fort !", "Dans les dents !")

    override fun play(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        val a = ctx.arena
        val d = a.d
        val rnd = ctx.rnd
        if (p.held) {
            ctx.holdTime += dt
            pet.body.pose = Pose.IDLE
            if (ctx.holdTime > 1.3f) {
                val dir = if (pet.body.fx < a.screenW / 2) 1f else -1f
                pet.body.facing = dir
                p.held = false; p.onGround = false
                p.x = pet.body.fx + dir * 30 * d; p.y = pet.body.fy - 40 * d
                p.vx = dir * (750 + rnd.nextFloat() * 450) * d
                p.vy = -(250 + rnd.nextFloat() * 250) * d
                ctx.catchCooldown = 0.9f
                if (rnd.nextFloat() < 0.35f) ctx.say(ctx.speech.pickFrom(THROWN), 1.4f)
            }
            return
        }
        val airborne = !p.onGround
        val tx = if (airborne) p.x + p.vx * 0.25f else p.x
        if (abs(tx - pet.body.fx) > 24 * d) ctx.actor.walkToward(pet, tx, 230 * d, dt, stopDist = 24 * d) else pet.body.pose = Pose.IDLE
        if (pet.body.act != Act.PLAY) return
        val dx = p.x - pet.body.fx
        val h = pet.body.fy - (p.y - p.lp.height / 2f)
        if (airborne && abs(dx) < 90 * d && h > 60 * d && h < 240 * d && pet.mind.kickCooldown <= 0f && !ctx.userHolds) {
            pet.mind.kickCooldown = 0.6f
            ctx.actor.jump(pet, BunnyPhysics.impulseForHeight(pet.body.phys.gravity, (h - 30 * d).coerceAtLeast(10 * d)), dx * 2.5f, then = Act.PLAY)
        }
    }

    override fun step(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        val d = ctx.arena.d
        val fy = pet.body.fy
        val act = pet.body.act
        val mouthX = pet.body.fx + pet.body.facing * 18 * d
        if (p.held) {
            p.x = mouthX + pet.body.facing * 8 * d
            p.y = fy - 52 * d + p.lp.height / 2f
            p.vx = 0f; p.vy = 0f
        } else {
            val lift = (abs(p.vx) / (900 * d)).coerceAtMost(1f) * 0.85f
            p.vx *= (1f - 0.35f * dt)
            ctx.stepToy(pet, p, dt, 1f - lift, 0.15f, 0.6f, 5f)
            if (!p.onGround) p.view.spin += 900f * dt
            // attrapé ?
            val cy = p.y - p.lp.height / 2f
            if (ctx.catchCooldown <= 0f && (act == Act.PLAY || act == Act.AIR) &&
                hypot(p.x - mouthX, cy - (fy - 55 * d)) < 45 * d
            ) {
                val wasFlying = !p.onGround
                p.held = true; ctx.holdTime = 0f
                ctx.hit(pet, Toy.FRISBEE, if (wasFlying) 4f else 1f)
                if (wasFlying && ctx.rnd.nextFloat() < 0.5f) ctx.say(ctx.speech.pickFrom(CAUGHT), 1.5f)
            }
        }
    }

    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        c.save(); c.rotate(v.spin * 0.08f, w / 2, h / 2)
        p.color = v.tint
        r.set(w * 0.04f, h * 0.30f, w * 0.96f, h * 0.70f); c.drawOval(r, p); c.drawOval(r, stroke)
        p.color = Palette.FRISBEE_SHINE
        r.set(w * 0.22f, h * 0.38f, w * 0.78f, h * 0.58f); c.drawOval(r, p)
        // petit repère qui tourne
        val a = Math.toRadians(v.spin.toDouble())
        p.color = Color.WHITE
        c.drawCircle(w / 2 + (cos(a) * w * 0.32f).toFloat(), h / 2 + (sin(a) * h * 0.12f).toFloat(), 2.2f * d, p)
        c.restore()
    }
}
