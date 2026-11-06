package com.tamacookie.feature.station

import com.tamacookie.Pose
import com.tamacookie.Pet
import com.tamacookie.PetCtx

/** Vétérinaire : il s'assoit sur la table, la fiche (stats + soin) s'ouvre à côté ; « Sortir » le libère. */
object VetStation : StationBehavior {
    /** Il entre chez le véto. */
    val ENTER = arrayOf("Bonjour docteur… 😳", "J'ai peur des piqûres…", "Ça va être long ?")
    /** Réplique « occupé » (on lui propose autre chose). */
    const val BUSY = "Je suis chez le docteur !"

    override val name = "🩺 Véto"
    override val tapHint = "Dépose-moi chez le véto pour voir ma santé 🩺"
    override val seat = Seat(12f, 26f)

    override fun busyLine(ctx: PetCtx) = BUSY

    // Répliques du soin (bouton « Soigner » de la fiche, `StationController`)
    /** « Soigner » alors qu'il est en bonne santé. */
    const val HEALTHY = "Il va très bien ! ✅"
    /** Vacciné. */
    val HEALED = arrayOf("Aïe ! … merci docteur 💉", "Même pas mal ! (un peu)")

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        ctx.openVetPanel(pet)
        if (announce) ctx.say(ctx.pick(*ENTER))
    }

    override fun update(ctx: StationCtx, pet: Pet, dt: Float) {
        pet.body.pose = Pose.SIT
        ctx.actor.faceToward(pet, ctx.arena.screenW / 2)
    }

    override fun onLeave(ctx: StationCtx, pet: Pet) {
        ctx.closeVetPanel()
    }
}
