package com.tamacookie.feature.station

import android.graphics.Canvas
import com.tamacookie.PetEvent
import com.tamacookie.BunnyView.Expr
import com.tamacookie.BunnyView.PType
import com.tamacookie.BunnyView.Pose
import com.tamacookie.Palette
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.StationView

/**
 * Cage : il y est enfermé (sauvegardé dans `PetStats.caged`, son bonheur chute plus vite), il y dort,
 * la suit d'une vue à l'autre et se plaint de temps en temps. Le sortir (glisser) le libère.
 */
object CageStation : StationBehavior {
    val PETTED = arrayOf("Un câlin à travers les barreaux… 🥺", "Fais-moi sortir !", "Snif, merci…")

    override val name = "🔒 Cage"
    override val tapHint = "La cage… tu ne vas pas m'y mettre, hein ? 🥺"
    override val slowFrames = true
    override val holdsPet = true
    override val seat = Seat(0f, 16f)
    override val occupiedLine = "Je suis dedans ! 🥺"
    override val stayLine = "La cage reste où elle est… avec moi dedans 🥺"

    override fun busyLine(ctx: PetCtx) = ctx.pick("Je ne peux pas sortir… 🥺", "Ouvre la cage d'abord !")

    override fun restores(pet: Pet) = pet.stats.caged

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        ctx.on(pet, PetEvent.Caged)
        pet.mind.chatterIn = 20f
        if (announce) {
            pet.express(Expr.SAD, 2f)
            ctx.say(ctx.pick("Hé ! Pourquoi la cage ?? 🥺", "Laisse-moi sortir ! 😢", "J'ai rien fait !"))
        }
        ctx.updateNotification(force = true)
    }

    override fun update(ctx: StationCtx, pet: Pet, dt: Float) {
        val st = pet.stats
        pet.body.pose = if (st.sleeping) Pose.SLEEP else Pose.SIT
        if (!st.sleeping) {
            pet.express(Expr.SAD, 0.3f)
            pet.mind.chatterIn -= dt
            if (pet.mind.chatterIn <= 0f) {
                pet.mind.chatterIn = 25f + ctx.rnd.nextFloat() * 20f
                ctx.say(ctx.pick("Laisse-moi sortir… 🥺", "J'ai été sage, promis !", "C'est tout petit ici…",
                    "Snif… 😢", "Je m'ennuie dans ma cage"))
            }
        }
    }

    override fun onLeave(ctx: StationCtx, pet: Pet) {
        ctx.on(pet, PetEvent.Released)
        pet.express(Expr.HAPPY, 2f)
        ctx.emit(pet, PType.HEART, 3)
        ctx.say(ctx.pick("Libre !! 🎉", "Enfin dehors ! Merci !", "Je serai sage, promis 😇"))
        ctx.updateNotification(force = true)
    }

    override fun onPet(ctx: StationCtx, pet: Pet) {
        ctx.on(pet, PetEvent.Petted)
        ctx.emit(pet, PType.HEART, 1)
        ctx.speech.sayOneOf(PETTED, 2f)
    }

    override fun onShownAgain(ctx: StationCtx, pet: Pet, onScreen: Boolean) {
        if (onScreen) ctx.say(ctx.pick("Tu es revenu ! 🥺", "Je suis toujours là… dans ma cage"), 2.5f)
    }

    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        val left = 8 * d; val right = w - 8 * d
        val top = 22 * d; val bottom = h - 6 * d
        // socle
        r.set(left - 4 * d, bottom - 10 * d, right + 4 * d, bottom)
        p.color = Palette.CAGE_BASE; c.drawRoundRect(r, 4 * d, 4 * d, p); c.drawRoundRect(r, 4 * d, 4 * d, stroke)
        // dôme
        r.set(left, top - 18 * d, right, top + 18 * d)
        stroke.color = Palette.CAGE_BARS; stroke.strokeWidth = 3f * d
        c.drawArc(r, 180f, 180f, false, stroke)
        // barreaux
        val n = 7
        for (i in 0..n) {
            val x = left + (right - left) * i / n
            c.drawLine(x, top, x, bottom - 10 * d, stroke)
        }
        c.drawLine(left, top + (bottom - top) * 0.45f, right, top + (bottom - top) * 0.45f, stroke)
        // anneau
        c.drawCircle(w / 2, top - 22 * d, 5 * d, stroke)
        stroke.color = Palette.OUTLINE; stroke.strokeWidth = 2.2f * d
        // cadenas
        p.color = Palette.GOLD
        r.set(right - 22 * d, bottom - 40 * d, right - 8 * d, bottom - 26 * d)
        c.drawRoundRect(r, 3 * d, 3 * d, p); c.drawRoundRect(r, 3 * d, 3 * d, stroke)
    }
}
