package com.tamacookie.feature.station

import com.tamacookie.Pose
import com.tamacookie.Pet
import com.tamacookie.PetCtx

/** Vétérinaire : il s'assoit sur la table, la fiche (stats + soin) s'ouvre à côté ; « Sortir » le libère. */
object VetStation : StationBehavior {
    override val name = "🩺 Véto"
    override val tapHint = "Dépose-moi chez le véto pour voir ma santé 🩺"
    override val seat = Seat(12f, 26f)

    override fun busyLine(ctx: PetCtx) = "Je suis chez le docteur !"

    // Répliques du soin (bouton « Soigner » de la fiche, `StationController`)
    /** « Soigner » alors qu'il est en bonne santé. */
    const val HEALTHY = "Il va très bien ! ✅"
    /** Vacciné. */
    val HEALED = arrayOf("Aïe ! … merci docteur 💉", "Même pas mal ! (un peu)")

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        ctx.openVetPanel(pet)
        if (announce) ctx.say(ctx.pick("Bonjour docteur… 😳", "J'ai peur des piqûres…", "Ça va être long ?"))
    }

    override fun update(ctx: StationCtx, pet: Pet, dt: Float) {
        pet.body.pose = Pose.SIT
        ctx.actor.faceToward(pet, ctx.arena.screenW / 2)
    }

    override fun onLeave(ctx: StationCtx, pet: Pet) {
        ctx.closeVetPanel()
    }
}
