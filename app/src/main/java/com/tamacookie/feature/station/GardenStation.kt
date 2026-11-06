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
 * Jardin : il grignote l'herbe ([NIBBLE_SECONDS]), puis s'y roule ([ROLL_SECONDS]) et ressort
 * (`PetEvent.Grazed` : petite joie, un peu de satiété, un peu de terre).
 */
object GardenStation : StationBehavior {
    const val NIBBLE_SECONDS = 2.5f
    const val ROLL_SECONDS = 2.5f
    /** Il change de côté toutes les [FLIP_SECONDS] en se roulant. */
    const val FLIP_SECONDS = 0.4f
    private val FLOWERS_X = floatArrayOf(0.22f, 0.58f, 0.82f)

    override val name = "🌱 Jardin"
    override val tapHint = "Glisse-moi dans le jardin pour grignoter l'herbe 🌱"
    override val travel = Travel.BUSY
    override val seat = Seat(0f, 12f)

    override fun busyLine(ctx: PetCtx) = "Je suis au jardin ! 🌱"

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        pet.body.eatProgress = 0f
        if (announce) ctx.say(ctx.pick("Le jardin ! 🌱", "Ça sent l'herbe fraîche !", "Des pissenlits !"))
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
            ctx.say(ctx.pick("Quel bonheur, l'herbe ! 🌿", "Un peu de terre… mais heureux !", "J'ai trouvé un trèfle !"))
            ctx.leaveStation(pet, hopOut = true)
        }
    }

    override fun onLeave(ctx: StationCtx, pet: Pet) {
        pet.body.eatProgress = 0f
    }

    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        // bac de terre
        r.set(4 * d, h * 0.55f, w - 4 * d, h - 3 * d)
        p.color = Palette.SOIL; c.drawRoundRect(r, 6 * d, 6 * d, p); c.drawRoundRect(r, 6 * d, 6 * d, stroke)
        // herbe
        stroke.color = Palette.GRASS
        stroke.strokeWidth = 2.6f * d
        for (i in 0..13) {
            val x = w * (0.07f + i * 0.066f)
            c.drawLine(x, h * 0.62f, x + (i % 3 - 1) * 4 * d, h * (0.34f + (i % 4) * 0.05f), stroke)
        }
        stroke.color = Palette.OUTLINE; stroke.strokeWidth = 2f * d
        // fleurs : quatre pétales autour d'un cœur jaune
        for (i in FLOWERS_X.indices) {
            val x = w * FLOWERS_X[i]; val y = h * 0.32f
            p.color = if (i % 2 == 0) Palette.FLOWER_A else Palette.FLOWER_B
            c.drawCircle(x - 4 * d, y, 3.2f * d, p); c.drawCircle(x + 4 * d, y, 3.2f * d, p)
            c.drawCircle(x, y - 4 * d, 3.2f * d, p); c.drawCircle(x, y + 4 * d, 3.2f * d, p)
            p.color = Palette.YELLOW
            c.drawCircle(x, y, 2.4f * d, p)
        }
    }
}
