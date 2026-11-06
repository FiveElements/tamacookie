package com.tamacookie.feature.mischief

import com.tamacookie.Act
import com.tamacookie.Expr
import com.tamacookie.Pet
import com.tamacookie.Pose
import java.util.EnumSet

/**
 * Il tire la langue : on vient de toucher l'écran (un clic qu'aucune autre bêtise n'a pris), il se tourne
 * vers le doigt et fait « bleeeh » ([TEASE_SECONDS]). Une caresse le fait rire et arrêter.
 *
 * Réaction seulement (pas dans la cascade de l'attention) et **dernière** de [Mischiefs.ALL] : elle ne tire
 * au sort qu'après toutes les autres, ce qui laisse leurs tirages inchangés (évolution 4a).
 */
object TongueMischief : Mischief {
    const val TEASE_SECONDS = 2.5f

    override val acts: Set<Act> = EnumSet.of(Act.TEASE)

    /** Probabilité de réagir à un clic. */
    fun clickChance(level: Int) = if (level == 2) 0.08f else 0.05f

    override fun react(ctx: MischiefCtx, pet: Pet, ev: UserEvents): Boolean {
        val r = ev.click ?: return false
        if (ctx.rnd.nextFloat() >= clickChance(ctx.level)) return false
        ctx.actor.faceToward(pet, r.centerX)
        pet.setAct(Act.TEASE)
        pet.express(Expr.TONGUE, TEASE_SECONDS)
        ctx.say(ctx.pick("😛 Bleeeh !", "Nananère ! 😜", "Même pas cap ! 😝"), 2f)
        ctx.cooldownUntil = ev.now + 30_000
        return true
    }

    override fun update(ctx: MischiefCtx, pet: Pet, dt: Float) {
        pet.body.pose = Pose.SIT
        pet.express(Expr.TONGUE, 0.2f)
        if (pet.body.actTime >= TEASE_SECONDS) pet.setAct(Act.IDLE, 3f)
    }

    override fun onPet(ctx: MischiefCtx, pet: Pet): Boolean {
        pet.express(Expr.HAPPY, 1.5f)
        ctx.say(ctx.pick("Hihi, je rigolais ! 😇", "Bon, d'accord… 😊"), 2f)
        pet.setAct(Act.IDLE, 3f)
        return true
    }
}
