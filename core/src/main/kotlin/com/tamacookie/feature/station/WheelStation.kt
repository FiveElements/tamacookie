package com.tamacookie.feature.station

import com.tamacookie.Expr
import com.tamacookie.PType
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.PetEvent
import com.tamacookie.Pose
import com.tamacookie.feature.Travel

/**
 * Roue : il court dedans [RUN_SECONDS] (la roue tourne), puis saute dehors (`PetEvent.WheelRun` :
 * énergie ↓, bonheur ↑). Trop fatigué ou endormi, il refuse et ressort aussitôt.
 */
object WheelStation : StationBehavior {
    const val RUN_SECONDS = 6f

    override val name = "🎡 Roue"
    override val tapHint = "Glisse-moi dans la roue pour me dépenser 🎡"
    override val travel = Travel.BUSY
    override val animated = true
    override val seat = Seat(0f, 24f)

    override fun busyLine(ctx: PetCtx) = ctx.pick("Je cours ! 🎡", "Pas le temps, je m'entraîne !")

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        if (pet.wouldRefuse(PetEvent.WheelRun, ctx.clock.wallMs(), ctx.rules)) {
            ctx.say(ctx.pick("Trop fatigué pour courir… 😮‍💨", "Pas maintenant, je suis épuisé…"))
            ctx.leaveStation(pet, hopOut = true)
            return
        }
        if (announce) ctx.say(ctx.pick("À fond ! 🎡", "Je cours, je cours !", "Regarde comme je vais vite !"))
    }

    override fun update(ctx: StationCtx, pet: Pet, dt: Float) {
        val b = pet.body
        b.pose = Pose.WALK
        b.walkPhase = (b.walkPhase + dt * 3.5f) % 1f
        pet.express(Expr.HAPPY, 0.2f)
        if (ctx.rnd.nextFloat() < dt * 2f) ctx.emit(pet, PType.SPARKLE)
        if (b.actTime >= RUN_SECONDS) {
            b.walkPhase = 0f
            if (!ctx.on(pet, PetEvent.WheelRun).refused) {
                ctx.emit(pet, PType.HEART, 2)
                ctx.say(ctx.pick("Pfiou ! Quel sprint ! 💨", "Record battu !", "J'ai couru jusqu'à la lune !"))
            }
            ctx.leaveStation(pet, hopOut = true)
        }
    }
}
