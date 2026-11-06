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
}
