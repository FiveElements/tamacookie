package com.tamacookie.social

import com.tamacookie.Act
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.Pose
import kotlin.math.abs

/**
 * Se suivre : il emboîte le pas d'un autre animal (derrière lui, plus vite s'il est loin) pendant
 * quelques secondes. Le meneur continue sa vie (il n'est pas engagé) ; c'est son tour de parole qui
 * l'annonce.
 */
object FollowSocial : SocialInteraction {
    override val chance = 0.5f
    private val FOLLOWER = arrayOf("Attends-moi !", "Je te suis ! 🐾", "On va où ?", "Hop hop hop !")
    private val LEADER = arrayOf("Suis-moi ! 🐾", "Par ici !", "Viens !")

    /** Distance (dp) derrière le meneur. */
    const val BEHIND_DP = 70f

    override fun canStart(ctx: PetCtx, a: Pet, b: Pet): Boolean =
        b.act in Act.FREE && abs(b.body.fx - a.body.fx) > 120 * ctx.arena.d

    override fun start(sys: SocialSystem, a: Pet, b: Pet) {
        sys.engage(a, this, b, duration = 6f + a.mind.rnd.nextFloat() * 4f, leads = false)
        sys.say(a, sys.pick(a, FOLLOWER), 1.8f)
        sys.say(b, sys.pick(a, LEADER), 1.8f)
    }

    override fun update(sys: SocialSystem, self: Pet, partner: Pet?, e: Engagement, dt: Float): Boolean {
        if (partner == null || e.time > e.duration || partner.stats.sleeping) return false
        val ctx = sys.ctx
        val d = ctx.arena.d
        val target = partner.body.fx - partner.body.facing * BEHIND_DP * d
        val far = abs(target - self.body.fx) > 250 * d
        if (ctx.actor.walkToward(self, target, (if (far) 160f else 95f) * d, dt, stopDist = 12 * d)) {
            self.body.pose = Pose.IDLE
            ctx.actor.faceToward(self, partner.body.fx)
        }
        return true
    }
}
