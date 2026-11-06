package com.tamacookie.feature.mischief

import com.tamacookie.Act
import com.tamacookie.Pet

/** Le défilement lui donne le tournis (rarement). */
object ScrollDizzy : Mischief {
    /** On fait défiler trop vite. */
    const val DIZZY = "Doucement, ça me donne le tournis ! 😵"

    override fun react(ctx: MischiefCtx, pet: Pet, ev: UserEvents): Boolean {
        if (!ev.scroll || ctx.rnd.nextFloat() >= 0.04f) return false
        ctx.say(DIZZY)
        pet.setAct(Act.DIZZY)
        ctx.cooldownUntil = ev.now + 40_000
        return true
    }
}
