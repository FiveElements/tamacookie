package com.tamacookie.feature.mischief

import com.tamacookie.Act
import com.tamacookie.Expr
import com.tamacookie.PType
import com.tamacookie.Pet
import com.tamacookie.Pose
import java.util.EnumSet
import kotlin.math.abs

/**
 * Il mâchouille le bord de l'écran : ignoré alors qu'il est près d'un bord ([canStart] : à moins de
 * [EDGE_DP]), il y court, s'y colle et grignote [CHEW_SECONDS] (des miettes tombent). Une caresse l'arrête.
 *
 * Dans la cascade avant toc-toc ; la condition ne consomme pas d'aléatoire, donc tant qu'il n'est pas
 * près d'un bord le tirage des autres bêtises est celui d'avant (évolution 4a).
 */
object ChewEdgeMischief : Mischief {
    const val EDGE_DP = 160f
    const val CHEW_SECONDS = 4f

    override val acts: Set<Act> = EnumSet.of(Act.CHEW_EDGE)

    override fun chance(level: Int) = if (level == 2) 0.4f else 0.3f

    /** Le bord dont il est le plus près ([com.tamacookie.Arena.minX] / `maxX`), s'il est à moins de [EDGE_DP]. */
    fun nearEdge(ctx: MischiefCtx, pet: Pet): Float? {
        val a = ctx.arena
        val x = pet.body.fx
        val edge = if (x - a.minX <= a.maxX - x) a.minX else a.maxX
        return edge.takeIf { abs(x - it) <= EDGE_DP * a.d }
    }

    override fun canStart(ctx: MischiefCtx, pet: Pet) = pet.body.platform == null && nearEdge(ctx, pet) != null

    override fun start(ctx: MischiefCtx, pet: Pet): Boolean {
        val edge = nearEdge(ctx, pet) ?: return false
        ctx.state.chewing = false
        pet.body.targetX = edge
        pet.setAct(Act.CHEW_EDGE)
        return true
    }

    override fun update(ctx: MischiefCtx, pet: Pet, dt: Float) {
        val s = ctx.state
        val b = pet.body
        val a = ctx.arena
        if (!s.chewing) {
            if (!ctx.actor.walkToward(pet, b.targetX, 150 * a.d, dt)) return
            s.chewing = true
            b.actTime = 0f
            b.facing = if (b.targetX <= a.minX) -1f else 1f          // tourné vers le bord
            ctx.say(ctx.pick("Miam, le bord de l'écran ! 😋", "Croc croc… ça croustille !", "Il a un goût de pixel…"), 2.5f)
        }
        b.pose = Pose.EAT
        b.eatProgress = 0.9f                     // un petit bout d'écran dans la bouche
        pet.express(Expr.HAPPY, 0.2f)
        if (ctx.rnd.nextFloat() < dt * 3f) ctx.emit(pet, PType.CRUMB)
        if (b.actTime >= CHEW_SECONDS) stop(ctx, pet, null)
    }

    override fun onPet(ctx: MischiefCtx, pet: Pet): Boolean {
        stop(ctx, pet, ctx.pick("Oups, pris la patte dans le sac ! 😇", "Je goûtais, c'est tout !"))
        pet.express(Expr.SURPRISED, 1f)
        return true
    }

    private fun stop(ctx: MischiefCtx, pet: Pet, line: String?) {
        ctx.state.chewing = false
        pet.body.eatProgress = 0f
        pet.body.pose = Pose.IDLE
        if (line != null) ctx.say(line, 2f)
        pet.setAct(Act.IDLE, 3f)
    }
}
