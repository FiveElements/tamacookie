package com.tamacookie.feature.mischief

import com.tamacookie.Act
import com.tamacookie.Expr
import com.tamacookie.Pose
import com.tamacookie.Pet
import java.util.EnumSet

/**
 * Toc-toc : il va au milieu de l'écran et toque (vibration). Dernier recours de la cascade : toujours
 * possible, sans tirage. Sur une autre page, `checkPageReturn` le fait accourir (KNOCK ∈ `Act.RUSH_BACK`).
 */
object KnockMischief : Mischief {
    /** Il toque à l'écran. */
    val KNOCKING = arrayOf(
        "Toc toc toc ! 👊 Y'a quelqu'un ?", "Hé ho ! Occupe-toi de moi !", "Je m'ennuiiiie 😩", "Tu m'as oublié ?! 😤",
    )

    override val acts: Set<Act> = EnumSet.of(Act.KNOCK)

    override fun chance(level: Int) = 1f

    override fun start(ctx: MischiefCtx, pet: Pet): Boolean {
        ctx.state.knockDone = false
        pet.setAct(Act.KNOCK)
        return true
    }

    override fun update(ctx: MischiefCtx, pet: Pet, dt: Float) {
        val s = ctx.state
        if (!s.knockDone) {
            if (ctx.actor.walkToward(pet, ctx.arena.screenW / 2, 140 * ctx.arena.d, dt)) {
                s.knockDone = true; pet.body.actTime = 0f
                pet.body.pose = Pose.IDLE
                ctx.vibrate(longArrayOf(0, 35, 140, 35, 140, 35))
                pet.express(Expr.SURPRISED, 1.5f)
                ctx.say(ctx.pick(*KNOCKING), 3f)
            }
        } else {
            pet.body.pose = Pose.IDLE
            if (pet.body.actTime > 3.5f) pet.setAct(Act.IDLE, 3f)
        }
    }
}
