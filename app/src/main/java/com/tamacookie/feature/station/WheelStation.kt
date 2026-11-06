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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Roue : il court dedans [RUN_SECONDS] (la roue tourne), puis saute dehors (`PetEvent.WheelRun` :
 * énergie ↓, bonheur ↑). Trop fatigué ou endormi, il refuse et ressort aussitôt.
 */
object WheelStation : StationBehavior {
    const val RUN_SECONDS = 6f
    /** Tours de roue par seconde quand il court (dessin). */
    const val TURNS_PER_S = 0.8f

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

    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        val cx = w / 2; val cy = h * 0.46f
        val rad = minOf(w, h) * 0.42f
        // pied
        p.color = Palette.WHEEL_STAND
        r.set(cx - 5 * d, cy, cx + 5 * d, h - 6 * d); c.drawRect(r, p)
        r.set(cx - rad * 0.8f, h - 10 * d, cx + rad * 0.8f, h - 2 * d)
        c.drawRoundRect(r, 4 * d, 4 * d, p); c.drawRoundRect(r, 4 * d, 4 * d, stroke)
        // rayons : elle tourne quand il court
        val turn = if (v.occupied) v.t * TURNS_PER_S * 6.2832f else 0f
        stroke.color = Palette.WHEEL_SPOKE
        for (i in 0 until 6) {
            val a = turn + i * 1.0472f
            c.drawLine(cx, cy, cx + cos(a) * rad, cy + sin(a) * rad, stroke)
        }
        // jante
        stroke.color = Palette.WHEEL; stroke.strokeWidth = 5f * d
        c.drawCircle(cx, cy, rad, stroke)
        stroke.color = Palette.OUTLINE; stroke.strokeWidth = 2f * d
        p.color = Palette.WHEEL
        c.drawCircle(cx, cy, 5 * d, p); c.drawCircle(cx, cy, 5 * d, stroke)
    }
}
