package com.tamacookie.social

import com.tamacookie.Act
import com.tamacookie.Expr
import com.tamacookie.PType
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.Pose
import kotlin.math.abs

/**
 * Un câlin : celui qui le propose rejoint l'autre (qui l'attend en le regardant), puis ils restent
 * blottis l'un contre l'autre ([HUG_S] s) avec des cœurs. Mutuel : les deux sont engagés.
 */
object HugSocial : SocialInteraction {
    override val chance = 0.35f
    const val HUG_S = 2.5f
    /** Abandon si l'approche dure trop (s). */
    const val GIVE_UP_S = 8f
    /** Écart (dp) entre les deux pendant le câlin. */
    const val CLOSE_DP = 46f
    private val ASK = arrayOf("Un câlin ? 🤗", "Viens là, toi !", "Câlin !!")
    private val END = arrayOf("Je t'aime bien ! 💕", "Trop doux…", "Encore un ?")

    override fun canStart(ctx: PetCtx, a: Pet, b: Pet): Boolean =
        b.act in Act.FREE && !a.stats.sleeping && !b.stats.sleeping &&
            abs(b.body.fx - a.body.fx) < 400 * ctx.arena.d && a.stats.happiness > 30f

    override fun start(sys: SocialSystem, a: Pet, b: Pet) {
        sys.engage(a, this, b, duration = GIVE_UP_S + HUG_S, leads = true)
        sys.engage(b, this, a, duration = GIVE_UP_S + HUG_S, leads = false)
        sys.say(a, sys.pick(a, ASK), 1.8f)
    }

    override fun update(sys: SocialSystem, self: Pet, partner: Pet?, e: Engagement, dt: Float): Boolean {
        val pe = partner?.mind?.social
        // Le partenaire est parti (attrapé, caché…) ou ne fait plus ce câlin avec lui : c'est fini
        if (partner == null || pe == null || pe.interaction !== this || pe.partner != self.id) return false
        val ctx = sys.ctx
        val d = ctx.arena.d
        val b = self.body
        if (e.phase == 0) {
            if (e.time > GIVE_UP_S) return false
            if (!e.leads) {                                        // il attend en le regardant
                b.pose = Pose.IDLE
                ctx.actor.faceToward(self, partner.body.fx)
                return true
            }
            val side = if (b.fx < partner.body.fx) -1f else 1f
            if (ctx.actor.walkToward(self, partner.body.fx + side * CLOSE_DP * d, 120 * d, dt, stopDist = 6 * d)) {
                e.phase = 1; e.time = 0f
                pe.phase = 1; pe.time = 0f
            }
            return true
        }
        // Câlin : face à face, cœurs, sourires
        b.pose = Pose.IDLE
        ctx.actor.faceToward(self, partner.body.fx)
        self.express(Expr.HAPPY, 0.3f)
        if (e.leads && (e.time == 0f || (e.time % 0.8f) < dt)) ctx.world.asPet(self) { ctx.emit(self, PType.HEART, 2) }
        if (e.time < HUG_S) return true
        if (e.leads) sys.say(self, sys.pick(self, END), 2f)
        return false
    }
}
