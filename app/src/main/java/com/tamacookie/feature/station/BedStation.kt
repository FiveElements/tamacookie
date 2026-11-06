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

/**
 * Lit (deux places) : il s'y endort et y récupère plus vite (`PetEvent.Rested`, en plus du temps qui
 * passe). Pas fatigué, il s'y assoit un instant et ressort. Réveillé (caresse, énergie pleine), il saute
 * du lit ; le sortir le réveille en douceur. « Dodo » ne le fait pas sortir ([sleepsInside]).
 */
object BedStation : StationBehavior {
    /** Pas fatigué, ou réveillé : il reste assis ce temps-là (s) avant de sortir. */
    const val AWAKE_SECONDS = 3f
    private val LEFT = Seat(-32f, 20f)
    private val RIGHT = Seat(32f, 20f)

    override val name = "🛏️ Lit"
    override val tapHint = "Glisse-moi dans le lit pour une bonne sieste 🛏️ (on y tient à deux)"
    override val capacity = 2
    override val slowFrames = true
    override val sleepsInside = true
    override val seat = LEFT
    override val occupiedLine = "Chut, je suis au lit ! 💤"

    override fun seatFor(rank: Int) = if (rank == 0) LEFT else RIGHT

    override fun busyLine(ctx: PetCtx) = ctx.pick("Chut… je fais la sieste 💤", "Laisse-moi dormir encore un peu…")

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        if (pet.stats.energy > ctx.rules.notTiredAbove) {
            if (announce) ctx.say(ctx.pick("Pas fatigué… mais qu'il est moelleux ! 🛏️", "Je teste le matelas !"))
            return
        }
        if (!pet.stats.sleeping) ctx.on(pet, PetEvent.FellAsleep)
        pet.express(Expr.SLEEPY, 1f)
        ctx.emit(pet, PType.ZZZ)
        if (announce) ctx.say(ctx.pick("Au lit ! 🛏️", "Bonne nuit… 💤", "Qu'il est douillet !"))
    }

    override fun update(ctx: StationCtx, pet: Pet, dt: Float) {
        val b = pet.body
        if (pet.stats.sleeping) {
            b.pose = Pose.SLEEP
            ctx.on(pet, PetEvent.Rested(dt / 3600f))
            if (ctx.rnd.nextFloat() < dt * 0.8f) ctx.emit(pet, PType.ZZZ)
            b.actTime = 0f               // le temps éveillé se compte depuis le réveil
            return
        }
        b.pose = Pose.SIT
        if (b.actTime > AWAKE_SECONDS) {
            ctx.say(ctx.pick("Hop, debout ! ☀️", "Le lit était tout chaud…", "Quelle bonne sieste !"))
            ctx.leaveStation(pet, hopOut = true)
        }
    }

    override fun onLeave(ctx: StationCtx, pet: Pet) {
        if (pet.stats.sleeping) ctx.on(pet, PetEvent.Woken(grumpy = false))
    }

    override fun onPet(ctx: StationCtx, pet: Pet) {
        if (!pet.stats.sleeping) { ctx.emit(pet, PType.HEART, 1); return }
        // une caresse le réveille en douceur (pas de mauvaise humeur : il est au lit)
        ctx.on(pet, PetEvent.Woken(grumpy = false))
        pet.express(Expr.SLEEPY, 2f)
        ctx.emit(pet, PType.HEART, 1)
        ctx.say(ctx.pick("Mmh… bonjour ! 😴", "Déjà le matin ?"))
        pet.body.actTime = 0f
    }

    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        // tête de lit (à gauche) et pied de lit (à droite)
        r.set(2 * d, h * 0.18f, 14 * d, h - 2 * d)
        p.color = Palette.BED_WOOD; c.drawRoundRect(r, 5 * d, 5 * d, p); c.drawRoundRect(r, 5 * d, 5 * d, stroke)
        r.set(w - 12 * d, h * 0.45f, w - 2 * d, h - 2 * d)
        c.drawRoundRect(r, 4 * d, 4 * d, p); c.drawRoundRect(r, 4 * d, 4 * d, stroke)
        // oreillers
        p.color = Palette.BED_PILLOW
        r.set(16 * d, h * 0.42f, w * 0.38f, h * 0.62f); c.drawRoundRect(r, 8 * d, 8 * d, p); c.drawRoundRect(r, 8 * d, 8 * d, stroke)
        r.set(w * 0.40f, h * 0.42f, w * 0.64f, h * 0.62f); c.drawRoundRect(r, 8 * d, 8 * d, p); c.drawRoundRect(r, 8 * d, 8 * d, stroke)
        // couverture (devant l'animal : il est dessous)
        r.set(12 * d, h * 0.58f, w - 10 * d, h - 8 * d)
        p.color = Palette.BED_BLANKET; c.drawRoundRect(r, 6 * d, 6 * d, p); c.drawRoundRect(r, 6 * d, 6 * d, stroke)
        p.color = Palette.BED_BLANKET_DOT
        for (i in 0..5) c.drawCircle(w * (0.16f + i * 0.14f), h * (0.72f + (i % 2) * 0.08f), 2.4f * d, p)
    }
}
