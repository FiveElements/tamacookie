package com.tamacookie.feature.station

import com.tamacookie.Expr
import com.tamacookie.PType
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.PetEvent
import com.tamacookie.Pose
import com.tamacookie.feature.Travel
import com.tamacookie.feature.toy.Food

/**
 * Jardin : il grignote l'herbe ([NIBBLE_SECONDS]), puis s'y roule ([ROLL_SECONDS]) et ressort
 * (`PetEvent.Grazed` : petite joie, un peu de satiété, un peu de terre).
 */
object GardenStation : StationBehavior {
    /** Il arrive au jardin. */
    val ENTER = arrayOf("Le jardin ! 🌱", "Ça sent l'herbe fraîche !", "Des pissenlits !")
    /** Il a grignoté et s'est roulé dans l'herbe. */
    val DONE = arrayOf("Quel bonheur, l'herbe ! 🌿", "Un peu de terre… mais heureux !", "J'ai trouvé un trèfle !")
    /** Réplique « occupé » (on lui propose autre chose). */
    const val BUSY = "Je suis au jardin ! 🌱"

    const val NIBBLE_SECONDS = 2.5f
    const val ROLL_SECONDS = 2.5f
    /** Il change de côté toutes les [FLIP_SECONDS] en se roulant. */
    const val FLIP_SECONDS = 0.4f

    override val name = "🌱 Jardin"
    override val tapHint = "Glisse-moi dans le jardin pour grignoter l'herbe 🌱"
    override val travel = Travel.BUSY
    override val seat = Seat(0f, 12f)

    override fun busyLine(ctx: PetCtx) = BUSY

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        pet.body.eatProgress = 0f
        if (announce) ctx.say(ctx.pick(*ENTER))
    }

    override fun update(ctx: StationCtx, pet: Pet, dt: Float) {
        val b = pet.body
        val t = b.actTime
        if (t < NIBBLE_SECONDS) {
            b.eatingFood = Food.LETTUCE           // un brin d'herbe dans la bouche
            b.pose = Pose.EAT
            b.eatProgress = t / NIBBLE_SECONDS
            return
        }
        b.eatProgress = 0f
        b.pose = Pose.SIT
        b.facing = if (((t - NIBBLE_SECONDS) / FLIP_SECONDS).toInt() % 2 == 0) 1f else -1f
        pet.express(Expr.HAPPY, 0.2f)
        if (ctx.rnd.nextFloat() < dt * 3f) ctx.emit(pet, PType.SPARKLE)
        if (t >= NIBBLE_SECONDS + ROLL_SECONDS) {
            ctx.on(pet, PetEvent.Grazed)
            ctx.emit(pet, PType.HEART, 1)
            ctx.say(ctx.pick(*DONE))
            ctx.leaveStation(pet, hopOut = true)
        }
    }

    override fun onLeave(ctx: StationCtx, pet: Pet) {
        pet.body.eatProgress = 0f
    }
}
