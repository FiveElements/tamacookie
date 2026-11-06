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
 * Gamelle (de foin) : il renifle ; s'il a faim ([hungry]), il mange ([EAT_SECONDS], comme un repas
 * ordinaire : `PetEvent.Fed(Food.HAY)`), puis ressort. Sinon il ressort tout de suite.
 *
 * Il y va aussi **de lui-même** quand il a faim et qu'elle l'attend sur sa page (`Act.GOTO_BOWL`,
 * `StationController.bowlAvailable`).
 */
object BowlStation : StationBehavior {
    const val SNIFF_SECONDS = 1f
    const val EAT_SECONDS = 3f
    /** Ce qu'il y a dans la gamelle. */
    val FOOD = Food.HAY

    override val name = "🥣 Gamelle"
    override val tapHint = "Glisse-moi dans la gamelle quand j'ai faim 🥣"
    override val travel = Travel.BUSY
    override val seat = Seat(0f, 12f)

    /** Il arrive dans la gamelle (déposé, ou venu de lui-même). */
    val ENTER = arrayOf("La gamelle ! 🥣", "Qu'est-ce qu'il y a à manger ?", "Snif snif…")
    /** Il ressort sans manger (pas faim). */
    val NOT_HUNGRY = arrayOf("Pas faim pour l'instant…", "Je garde ça pour plus tard !")
    /** Il part de lui-même vers la gamelle (`Act.GOTO_BOWL`). */
    val GOING = arrayOf("J'ai un petit creux… 🥣", "À la gamelle !", "Miam, la gamelle m'attend !")
    const val BUSY = "Je mange ma gamelle ! 🥣"

    override fun busyLine(ctx: PetCtx) = BUSY

    /** Il a faim : sous [com.tamacookie.GameRules.bowlHungryBelow], et il ne refuserait pas le repas. */
    fun hungry(ctx: PetCtx, pet: Pet): Boolean =
        pet.stats.satiety < ctx.rules.bowlHungryBelow && !pet.wouldRefuse(PetEvent.Fed(FOOD), ctx.clock.wallMs(), ctx.rules)

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        pet.body.eatProgress = 0f
        if (announce) ctx.say(ctx.pick(*ENTER))
    }

    override fun update(ctx: StationCtx, pet: Pet, dt: Float) {
        val b = pet.body
        if (b.actTime < SNIFF_SECONDS) { b.pose = Pose.IDLE; return }
        if (!hungry(ctx, pet)) {
            b.pose = Pose.IDLE
            ctx.say(ctx.pick(*NOT_HUNGRY))
            ctx.leaveStation(pet, hopOut = true)
            return
        }
        b.eatingFood = FOOD
        b.pose = Pose.EAT
        b.eatProgress = (b.actTime - SNIFF_SECONDS) / EAT_SECONDS
        if (b.actTime >= SNIFF_SECONDS + EAT_SECONDS) {
            b.eatProgress = 0f
            ctx.on(pet, PetEvent.Fed(FOOD))
            ctx.emit(pet, PType.HEART, 2)
            pet.express(Expr.HAPPY, 2f)
            ctx.speech.sayOneOf(pet.species.lines.ATE)
            ctx.leaveStation(pet, hopOut = true)
        }
    }

    override fun onLeave(ctx: StationCtx, pet: Pet) {
        pet.body.eatProgress = 0f
    }
}
