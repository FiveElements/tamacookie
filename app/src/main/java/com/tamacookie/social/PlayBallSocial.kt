package com.tamacookie.social

import com.tamacookie.Act
import com.tamacookie.Pet
import com.tamacookie.feature.toy.Toy
import kotlin.math.abs
import kotlin.math.sign

/**
 * Jouer ensemble à la balle : quand deux animaux jouent avec la même balle (tous deux en `PLAY`, ce que
 * fait déjà la boucle quand un jouet est sorti), chaque coup de museau l'envoie vers l'autre — ils se
 * font des passes. Déclenchée par le monde ([observe]), pas par le cerveau ; personne n'est engagé
 * (la partie reste celle du jouet).
 */
object PlayBallSocial : SocialInteraction {
    private val PASS = arrayOf("À toi ! 🎾", "Attrape !", "Passe !", "Hop, à ton tour !")
    /** Délai de frappe que pose la balle au moment d'un coup (`BallToy` : 0,7 s) : « il vient de frapper ». */
    private const val JUST_KICKED = 0.69f

    override fun observe(sys: SocialSystem, a: Pet, b: Pet, dt: Float) {
        if (a.act != Act.PLAY || b.act != Act.PLAY) return
        val ball = sys.ctx.world.currentToy()?.takeIf { it.toy == Toy.BALL } ?: return
        pass(sys, a, b, ball)
        pass(sys, b, a, ball)
    }

    private fun pass(sys: SocialSystem, kicker: Pet, to: Pet, ball: com.tamacookie.Prop) {
        if (kicker.mind.kickCooldown < JUST_KICKED || ball.onGround) return
        val dir = sign(to.body.fx - ball.x)
        if (dir == 0f || sign(ball.vx) == dir) return
        ball.vx = dir * abs(ball.vx)                 // la passe part vers l'autre
        kicker.mind.kickCooldown = JUST_KICKED - 0.01f   // une seule passe par coup
        if (kicker.mind.rnd.nextFloat() < 0.3f) sys.say(kicker, sys.pick(kicker, PASS), 1.4f)
    }
}
