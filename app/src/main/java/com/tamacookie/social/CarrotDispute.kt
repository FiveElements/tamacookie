package com.tamacookie.social

import com.tamacookie.Expr
import com.tamacookie.Pet
import com.tamacookie.Pose

/**
 * Se disputer une carotte : deux animaux courent vers la même nourriture ; le `Director` la donne au plus
 * proche (`foodWinner`) et note le différend ; ici, le perdant boude en tapant du pied ([DURATION_S] s)
 * pendant que le gagnant se vante. Lancée par [SocialSystem.startDispute], pas par le cerveau.
 */
object CarrotDispute : SocialInteraction {
    const val DURATION_S = 2.5f
    private val LOSER = arrayOf("Hé ! C'était pour moi !", "Pfff, trop rapide 😤", "La prochaine est à moi !", "C'est pas juste !")
    private val WINNER = arrayOf("Premier arrivé, premier servi ! 😋", "Miam, trop tard !", "Hihi, à moi !")

    override fun start(sys: SocialSystem, a: Pet, b: Pet) {
        sys.engage(a, this, b, duration = DURATION_S, leads = false)
        a.express(Expr.ANGRY, DURATION_S)
        sys.say(a, sys.pick(a, LOSER), 2f)
        sys.say(b, sys.pick(b, WINNER), 2f)
    }

    override fun update(sys: SocialSystem, self: Pet, partner: Pet?, e: Engagement, dt: Float): Boolean {
        val ctx = sys.ctx
        self.body.pose = Pose.IDLE
        partner?.let { ctx.actor.faceToward(self, it.body.fx) }
        self.express(Expr.ANGRY, 0.3f)
        return e.time < e.duration
    }
}
