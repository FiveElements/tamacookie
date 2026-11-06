package com.tamacookie.feature.station

import com.tamacookie.PetEvent
import com.tamacookie.Expr
import com.tamacookie.PType
import com.tamacookie.Pose
import com.tamacookie.Pet
import com.tamacookie.PetCtx

/**
 * Cage : il y est enfermé (sauvegardé dans `PetStats.caged`, son bonheur chute plus vite), il y dort,
 * la suit d'une vue à l'autre et se plaint de temps en temps. Le sortir (glisser) le libère.
 */
object CageStation : StationBehavior {
    /** Réplique « occupé » (on lui propose autre chose). */
    val BUSY = arrayOf("Je ne peux pas sortir… 🥺", "Ouvre la cage d'abord !")
    /** On le met en cage. */
    val ENTER = arrayOf("Hé ! Pourquoi la cage ?? 🥺", "Laisse-moi sortir ! 😢", "J'ai rien fait !")
    /** Il se plaint de temps en temps. */
    val INSIDE = arrayOf(
        "Laisse-moi sortir… 🥺", "J'ai été sage, promis !", "C'est tout petit ici…", "Snif… 😢",
        "Je m'ennuie dans ma cage",
    )
    /** Il sort. */
    val RELEASED = arrayOf("Libre !! 🎉", "Enfin dehors ! Merci !", "Je serai sage, promis 😇")
    /** On revient sur la vue de sa cage. */
    val BACK = arrayOf("Tu es revenu ! 🥺", "Je suis toujours là… dans ma cage")

    val PETTED = arrayOf("Un câlin à travers les barreaux… 🥺", "Fais-moi sortir !", "Snif, merci…")

    override val name = "🔒 Cage"
    override val tapHint = "La cage… tu ne vas pas m'y mettre, hein ? 🥺"
    override val slowFrames = true
    override val holdsPet = true
    override val seat = Seat(0f, 16f)
    override val occupiedLine = "Je suis dedans ! 🥺"
    override val stayLine = "La cage reste où elle est… avec moi dedans 🥺"

    override fun busyLine(ctx: PetCtx) = ctx.pick(*BUSY)

    override fun restores(pet: Pet) = pet.stats.caged

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        ctx.on(pet, PetEvent.Caged)
        pet.mind.chatterIn = 20f
        if (announce) {
            pet.express(Expr.SAD, 2f)
            ctx.say(ctx.pick(*ENTER))
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
                ctx.say(ctx.pick(*INSIDE))
            }
        }
    }

    override fun onLeave(ctx: StationCtx, pet: Pet) {
        ctx.on(pet, PetEvent.Released)
        pet.express(Expr.HAPPY, 2f)
        ctx.emit(pet, PType.HEART, 3)
        ctx.say(ctx.pick(*RELEASED))
        ctx.updateNotification(force = true)
    }

    override fun onPet(ctx: StationCtx, pet: Pet) {
        ctx.on(pet, PetEvent.Petted)
        ctx.emit(pet, PType.HEART, 1)
        ctx.speech.sayOneOf(PETTED, 2f)
    }

    override fun onShownAgain(ctx: StationCtx, pet: Pet, onScreen: Boolean) {
        if (onScreen) ctx.say(ctx.pick(*BACK), 2.5f)
    }
}
