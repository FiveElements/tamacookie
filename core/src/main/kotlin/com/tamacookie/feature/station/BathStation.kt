package com.tamacookie.feature.station

import com.tamacookie.PetEvent
import com.tamacookie.Expr
import com.tamacookie.PType
import com.tamacookie.Pose
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.feature.Travel

/** Baignoire : il y prend un bain de 4 s puis ressort tout propre. */
object BathStation : StationBehavior {
    /** Il entre dans le bain. */
    val ENTER = arrayOf("Un bain ! 🫧", "Splash ! 💦", "Brrr, elle est froide !")
    /** Bain fini. */
    val CLEAN = arrayOf("Tout propre ! ✨", "Ça sent bon la fraise !", "Brrr… mais je brille !")
    /** Réplique « occupé » (on lui propose autre chose). */
    const val BUSY = "Attends, je prends mon bain ! 🫧"

    /** Durée du bain (s). */
    const val BATH_SECONDS = 4f

    override val name = "🛁 Baignoire"
    override val tapHint = "Glisse-moi dans la baignoire pour me laver 🛁"
    /** Occupé, mais il commente parfois l'application où l'on va. */
    override val travel = Travel.BUSY
    override val animated = true
    override val seat = Seat(0f, 14f)

    override fun busyLine(ctx: PetCtx) = BUSY

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        ctx.on(pet, PetEvent.Woken(grumpy = false))
        if (announce) ctx.say(ctx.pick(*ENTER))
    }

    override fun update(ctx: StationCtx, pet: Pet, dt: Float) {
        pet.body.pose = Pose.IDLE
        pet.express(Expr.HAPPY, 0.2f)
        if (ctx.rnd.nextFloat() < dt * 14f) ctx.emit(pet, PType.SPARKLE)
        if (ctx.rnd.nextFloat() < dt * 6f) ctx.emit(pet, PType.BUBBLE)
        if (pet.body.actTime > BATH_SECONDS) {
            ctx.on(pet, PetEvent.Bathed)
            ctx.say(ctx.pick(*CLEAN))
            ctx.leaveStation(pet, hopOut = true)
        }
    }
}
