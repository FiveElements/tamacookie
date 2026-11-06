package com.tamacookie.feature.station

import com.tamacookie.Expr
import com.tamacookie.PType
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.PetEvent
import com.tamacookie.Pose

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
}
