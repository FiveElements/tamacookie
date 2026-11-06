package com.tamacookie.feature.toy

import android.graphics.Canvas
import com.tamacookie.Palette
import com.tamacookie.Pet
import com.tamacookie.Pose
import com.tamacookie.Prop
import com.tamacookie.PropView
import kotlin.math.abs

/**
 * Tunnel : posé au sol, il y entre d'un côté et ressort de l'autre, encore et encore. Les fenêtres des
 * objets sont au-dessus de celle de l'animal (`WindowLayer`) : le tunnel le cache pendant la traversée.
 *
 * Phase dans [ToyCtx.holdTime] (0 = il va à l'entrée la plus proche, > 0 = il traverse), bout visé dans
 * `Body.targetX`, pause entre deux traversées dans `Mind.kickCooldown`.
 */
object TunnelToy : ToySpec {
    override val toy get() = Toy.TUNNEL
    override val wDp = 130
    override val hDp = 56
    override val launch = Launch(aboveDp = 80f, vxDp = 0f, vyDp = -150f, chestVyDp = -700f)
    override val startLines = arrayOf("Un tunnel ! 🕳️", "Je parie que je ressors de l'autre côté !", "Une cachette !")
    private val OUT = arrayOf("Coucou ! 👋", "Me revoilà !", "Encore !", "Tadaaa !")

    /** Les deux bouts du tunnel (pieds de l'animal juste dehors), bornés à l'écran. */
    fun ends(ctx: ToyCtx, p: Prop): Pair<Float, Float> {
        val a = ctx.arena
        val half = p.w / 2f + 24 * a.d
        return (p.x - half).coerceIn(a.minX, a.maxX) to (p.x + half).coerceIn(a.minX, a.maxX)
    }

    override fun play(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        val d = ctx.arena.d
        val b = pet.body
        if (!p.onGround || ctx.userHolds || pet.mind.kickCooldown > 0f) {
            b.pose = Pose.IDLE; ctx.actor.faceToward(pet, p.x); return
        }
        val (left, right) = ends(ctx, p)
        val near = 2 * d
        if (abs(b.targetX - left) > near && abs(b.targetX - right) > near) {
            // première fois (ou tunnel déplacé) : vers l'entrée la plus proche
            b.targetX = if (abs(b.fx - left) <= abs(b.fx - right)) left else right
            ctx.holdTime = 0f
        }
        if (!ctx.actor.walkToward(pet, b.targetX, 190 * d, dt, stopDist = near)) return
        if (ctx.holdTime > 0f) {
            // il vient de traverser
            ctx.hit(pet, Toy.TUNNEL, 3f)
            if (ctx.rnd.nextFloat() < 0.4f) ctx.say(ctx.speech.pickFrom(OUT), 1.4f)
        }
        ctx.holdTime = 1f
        b.targetX = if (abs(b.targetX - left) <= near) right else left
        pet.mind.kickCooldown = 0.5f + ctx.rnd.nextFloat() * 0.8f
    }

    override fun step(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float) {
        ctx.stepToy(pet, p, dt, 1f, 0f, 0.2f, 8f)
        p.spin = 0f
    }

    override fun draw(c: Canvas, v: PropView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        // tube couché
        r.set(w * 0.06f, h * 0.10f, w * 0.94f, h * 0.98f)
        p.color = Palette.TUNNEL; c.drawRoundRect(r, h * 0.45f, h * 0.45f, p); c.drawRoundRect(r, h * 0.45f, h * 0.45f, stroke)
        // anneaux
        p.color = Palette.TUNNEL_RING
        for (i in 1..4) {
            val x = w * (0.06f + 0.88f * i / 5f)
            r.set(x - 2 * d, h * 0.12f, x + 2 * d, h * 0.96f); c.drawRect(r, p)
        }
        // ouvertures aux deux bouts
        p.color = Palette.TUNNEL_HOLE
        r.set(w * 0.02f, h * 0.22f, w * 0.14f, h * 0.98f); c.drawOval(r, p); c.drawOval(r, stroke)
        r.set(w * 0.86f, h * 0.22f, w * 0.98f, h * 0.98f); c.drawOval(r, p); c.drawOval(r, stroke)
    }
}
