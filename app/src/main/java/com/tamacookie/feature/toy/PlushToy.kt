package com.tamacookie.feature.toy

import android.graphics.Canvas
import com.tamacookie.PetEvent
import com.tamacookie.Act
import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.BunnyView.Pose
import com.tamacookie.Palette
import com.tamacookie.Pet
import com.tamacookie.Prop
import com.tamacookie.PropView
import kotlin.math.hypot

/** Doudou : câlins, promenade, lancers ; il s'endort avec s'il est fatigué. */
object PlushToy : ToySpec {
    override val toy get() = Toy.PLUSH
    override val wDp = 40
    override val hDp = 44
    override val complainsWhenTaken = true
    override val startLines = arrayOf("Un doudou ! 🧸", "Oh, un nouveau copain !")
    private val GRABBED = arrayOf("Mon doudou ! 🧸", "Je t'ai !", "Viens là toi !")
    private val THROWN = arrayOf("Wiii, vole doudou !", "Hop !", "Il vole !")

    override fun play(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        val a = ctx.arena
        val d = a.d
        val rnd = ctx.rnd
        if (!p.held) {
            if (!p.onGround) { pet.body.pose = Pose.IDLE; ctx.actor.faceToward(pet, p.x); return }
            if (ctx.actor.walkToward(pet, p.x, 150 * d, dt, stopDist = 22 * d)) {
                p.held = true; ctx.holdTime = 0f
                ctx.hit(pet, Toy.PLUSH, 3f)
                ctx.say(ctx.speech.pickFrom(GRABBED), 1.6f)
            }
            return
        }
        val before = ctx.holdTime
        ctx.holdTime += dt
        val hug = ctx.holdTime
        when {
            hug < 4f -> {
                pet.body.pose = Pose.IDLE
                pet.express(Expr.HAPPY, 0.2f)
                if (rnd.nextFloat() < dt * 2.5f) { ctx.emit(pet, PType.HEART); ctx.on(pet, PetEvent.PlayHit(Toy.PLUSH, 0.6f)) }
            }
            hug < 10f -> {
                if (before < 4f) pet.body.targetX = (pet.body.fx + (rnd.nextFloat() * 2f - 1f) * a.screenW * 0.3f).coerceIn(a.minX, a.maxX)
                if (ctx.actor.walkToward(pet, pet.body.targetX, 80 * d, dt)) pet.body.pose = Pose.IDLE
            }
            else -> {
                if (pet.stats.energy < ctx.rules.plushNapEnergyBelow) { ctx.say("Dodo avec mon doudou… 💤"); ctx.startSleep(pet); return }
                p.held = false; p.onGround = false
                p.vy = -(650 + rnd.nextFloat() * 250) * d
                p.vx = pet.body.facing * rnd.nextFloat() * 120 * d
                ctx.catchCooldown = 0.5f
                ctx.holdTime = 0f
                ctx.say(ctx.speech.pickFrom(THROWN), 1.4f)
            }
        }
    }

    override fun step(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        val d = ctx.arena.d
        val fx = pet.body.fx; val fy = pet.body.fy
        val act = pet.body.act
        if (p.held) {
            val sleepingNow = act == Act.SLEEP
            p.x = fx + pet.body.facing * (if (sleepingNow) 30 else 16) * d
            p.y = fy - (if (sleepingNow) 0 else 6) * d
            p.vx = 0f; p.vy = 0f; p.spin = if (sleepingNow) -70f else 0f
        } else {
            ctx.stepToy(pet, p, dt, 1f, 0.3f, 0.6f, 6f)
            if (!p.onGround) p.spin += p.vx / d * dt * 3f + 120f * dt
            else p.spin *= (1f - 6f * dt)
            if (ctx.catchCooldown <= 0f && act == Act.PLAY && !p.onGround &&
                hypot(p.x - fx, (p.y - p.h / 2f) - (fy - 40 * d)) < 40 * d
            ) {
                p.held = true; ctx.holdTime = 0f
                ctx.hit(pet, Toy.PLUSH, 3f)
            }
        }
    }

    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        // petit ours en peluche
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        c.save(); c.rotate(v.spin, w / 2, h / 2)
        p.color = Palette.WOOD
        c.drawCircle(w * 0.28f, h * 0.20f, w * 0.11f, p); c.drawCircle(w * 0.28f, h * 0.20f, w * 0.11f, stroke)
        c.drawCircle(w * 0.72f, h * 0.20f, w * 0.11f, p); c.drawCircle(w * 0.72f, h * 0.20f, w * 0.11f, stroke)
        r.set(w * 0.20f, h * 0.48f, w * 0.80f, h * 0.98f); c.drawOval(r, p); c.drawOval(r, stroke)
        c.drawCircle(w / 2, h * 0.36f, w * 0.26f, p); c.drawCircle(w / 2, h * 0.36f, w * 0.26f, stroke)
        p.color = Palette.PLUSH_LIGHT
        r.set(w * 0.38f, h * 0.38f, w * 0.62f, h * 0.52f); c.drawOval(r, p)
        r.set(w * 0.34f, h * 0.62f, w * 0.66f, h * 0.88f); c.drawOval(r, p)
        p.color = Palette.INK
        c.drawCircle(w * 0.41f, h * 0.31f, 1.8f * d, p); c.drawCircle(w * 0.59f, h * 0.31f, 1.8f * d, p)
        c.drawCircle(w / 2, h * 0.42f, 1.8f * d, p)
        c.restore()
    }
}
