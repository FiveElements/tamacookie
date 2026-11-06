package com.tamacookie.feature.station

import android.graphics.Canvas
import com.tamacookie.Expr
import com.tamacookie.PType
import com.tamacookie.Palette
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.PetEvent
import com.tamacookie.Pose
import com.tamacookie.StationView
import com.tamacookie.feature.Travel
import com.tamacookie.feature.toy.Food

/**
 * Gamelle (de foin) : il renifle ; s'il a faim ([hungry]), il mange ([EAT_SECONDS], comme un repas
 * ordinaire : `PetEvent.Fed(Food.HAY)`), puis ressort. Sinon il ressort tout de suite.
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

    override fun busyLine(ctx: PetCtx) = "Je mange ma gamelle ! 🥣"

    /** Il a faim : sous [com.tamacookie.GameRules.bowlHungryBelow], et il ne refuserait pas le repas. */
    fun hungry(ctx: PetCtx, pet: Pet): Boolean =
        pet.stats.satiety < ctx.rules.bowlHungryBelow && !pet.wouldRefuse(PetEvent.Fed(FOOD), ctx.clock.wallMs(), ctx.rules)

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        pet.body.eatProgress = 0f
        if (announce) ctx.say(ctx.pick("La gamelle ! 🥣", "Qu'est-ce qu'il y a à manger ?", "Snif snif…"))
    }

    override fun update(ctx: StationCtx, pet: Pet, dt: Float) {
        val b = pet.body
        if (b.actTime < SNIFF_SECONDS) { b.pose = Pose.IDLE; return }
        if (!hungry(ctx, pet)) {
            b.pose = Pose.IDLE
            ctx.say(ctx.pick("Pas faim pour l'instant…", "Je garde ça pour plus tard !"))
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

    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val path = pen.path; val d = pen.d
        val top = h * 0.52f
        // foin qui dépasse
        stroke.color = Palette.HAY_DARK
        for (i in 0..7) {
            val x = w * (0.2f + i * 0.085f)
            c.drawLine(x, top + 4 * d, x + (i % 3 - 1) * 5 * d, top - (6 + i % 3 * 3) * d, stroke)
        }
        p.color = Palette.HAY
        r.set(w * 0.14f, top - 6 * d, w * 0.86f, top + 8 * d); c.drawOval(r, p)
        stroke.color = Palette.OUTLINE
        // bol
        path.reset()
        path.moveTo(w * 0.08f, top)
        path.lineTo(w * 0.92f, top)
        path.cubicTo(w * 0.90f, h - 4 * d, w * 0.70f, h - 4 * d, w * 0.5f, h - 4 * d)
        path.cubicTo(w * 0.30f, h - 4 * d, w * 0.10f, h - 4 * d, w * 0.08f, top)
        path.close()
        p.color = Palette.BOWL; c.drawPath(path, p); c.drawPath(path, stroke)
        r.set(w * 0.06f, top - 3 * d, w * 0.94f, top + 4 * d)
        p.color = Palette.BOWL_RIM; c.drawRoundRect(r, 3 * d, 3 * d, p); c.drawRoundRect(r, 3 * d, 3 * d, stroke)
        // os décoratif
        p.color = Palette.BOWL_RIM
        r.set(w * 0.40f, h * 0.72f, w * 0.60f, h * 0.80f); c.drawRect(r, p)
        c.drawCircle(w * 0.40f, h * 0.72f, 3 * d, p); c.drawCircle(w * 0.40f, h * 0.80f, 3 * d, p)
        c.drawCircle(w * 0.60f, h * 0.72f, 3 * d, p); c.drawCircle(w * 0.60f, h * 0.80f, 3 * d, p)
    }
}
