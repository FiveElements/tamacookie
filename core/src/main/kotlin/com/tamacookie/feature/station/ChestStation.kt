package com.tamacookie.feature.station

import com.tamacookie.PetCtx

/**
 * Coffre : n'accueille pas l'animal. Un tap l'ouvre (nourriture / jouets) ; l'animal s'y sert seul ou le
 * percute en vol (logique encore dans `StationController` : `checkChestBump`, `updateGoToChest`).
 */
object ChestStation : StationBehavior {
    override val name = "🧰 Coffre"
    override val acceptsPet = false
    override val capacity = 0
    override val tapHint = ""

    override fun busyLine(ctx: PetCtx) = ""

    override fun onTap(ctx: StationCtx) = ctx.openChest()

    // Répliques des chocs, dites par `StationController` (« il s'y sert » : `SpeciesLines.GOING_TO_CHEST`)
    /** Il percute le coffre alors qu'un objet est déjà sorti. */
    val BUMPED = arrayOf("Bonk ! 😵", "Aïe, le coffre !")
    /** Il percute le coffre, qui s'ouvre (un objet va en sortir). */
    val BUMPED_OPEN = arrayOf("Bonk ! Oh, le coffre s'ouvre ! 🎁", "Oups, j'ai cogné le coffre !",
        "Sésame, ouvre-toi ! ✨")
}
