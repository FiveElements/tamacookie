package com.tamacookie.feature.mischief

import com.tamacookie.Expr
import com.tamacookie.Act
import com.tamacookie.Pet
import kotlin.math.abs
import kotlin.math.sign

/**
 * Il sursaute : on vient de toucher l'écran **près de lui** (à moins de [RANGE_DP]) et son caractère le
 * rend craintif ([com.tamacookie.species.BehaviorMods.startleChance] > 0 : le peureux). Petit bond en
 * arrière, loin du doigt, yeux ronds. Pas une bêtise à proprement parler : une réaction au clic, rangée
 * ici parce que c'est le chemin des clics (même garde : libre, pas en pause, niveau ≥ 1).
 *
 * **Dernière** de [Mischiefs.ALL] et sans tirage quand `startleChance` vaut 0 (tous les caractères d'avant
 * l'évolution 4b) : les tirages existants ne changent pas. Ne met pas les bêtises en pause.
 */
object StartleMischief : Mischief {
    /** Il sursaute. */
    val STARTLED = arrayOf("Hiii ! 🙈", "Tu m'as fait peur ! 😳", "Oh ! 😱")

    /** Distance (dp) du doigt en deçà de laquelle il peut sursauter. */
    const val RANGE_DP = 140f

    override fun react(ctx: MischiefCtx, pet: Pet, ev: UserEvents): Boolean {
        val r = ev.click ?: return false
        val chance = pet.species.behavior(pet.personality).startleChance
        if (chance <= 0f) return false
        val d = ctx.arena.d
        val dx = pet.body.fx - r.centerX
        if (abs(dx) > RANGE_DP * d) return false
        if (ctx.rnd.nextFloat() >= chance) return false
        val away = if (dx == 0f) -pet.body.facing else sign(dx)
        pet.body.facing = -away                       // il regarde le doigt en reculant
        ctx.actor.jump(pet, -520f * d, away * 140f * d, then = Act.IDLE)
        pet.express(Expr.SURPRISED, 1.6f)
        ctx.say(ctx.pick(*STARTLED), 2f)
        return true
    }
}
