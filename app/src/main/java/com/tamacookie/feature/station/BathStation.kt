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
    /** Durée du bain (s). */
    const val BATH_SECONDS = 4f

    override val name = "🛁 Baignoire"
    override val tapHint = "Glisse-moi dans la baignoire pour me laver 🛁"
    /** Occupé, mais il commente parfois l'application où l'on va. */
    override val travel = Travel.BUSY
    override val animated = true
    override val seat = Seat(0f, 14f)

    override fun busyLine(ctx: PetCtx) = "Attends, je prends mon bain ! 🫧"

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        ctx.on(pet, PetEvent.Woken(grumpy = false))
        if (announce) ctx.say(ctx.pick("Un bain ! 🫧", "Splash ! 💦", "Brrr, elle est froide !"))
    }

    override fun update(ctx: StationCtx, pet: Pet, dt: Float) {
        pet.body.pose = Pose.IDLE
        pet.express(Expr.HAPPY, 0.2f)
        if (ctx.rnd.nextFloat() < dt * 14f) ctx.emit(pet, PType.SPARKLE)
        if (ctx.rnd.nextFloat() < dt * 6f) ctx.emit(pet, PType.BUBBLE)
        if (pet.body.actTime > BATH_SECONDS) {
            ctx.on(pet, PetEvent.Bathed)
            ctx.say(ctx.pick("Tout propre ! ✨", "Ça sent bon la fraise !", "Brrr… mais je brille !"))
            ctx.leaveStation(pet, hopOut = true)
        }
    }
}
