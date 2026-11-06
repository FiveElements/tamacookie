package com.tamacookie.feature.toy

object CarrotFood : FoodSpec {
    override val food get() = Food.CARROT
    override val restSpin = 90f
    override val lines = arrayOf("Une carotte !! 🥕", "Miam en vue !", "C'est pour moi ?")
}

object LettuceFood : FoodSpec {
    override val food get() = Food.LETTUCE
    override val lines = arrayOf("De la salade ! 🥬 Croquante !", "Plein de vitamines !")
}

object StrawberryFood : FoodSpec {
    override val food get() = Food.STRAWBERRY
    override val lines = arrayOf("UNE FRAISE ?! 🍓😍", "Mon dessert préféré !")
}

object AppleFood : FoodSpec {
    override val food get() = Food.APPLE
    override val restSpin = 20f
    override val lines = arrayOf("Une pomme ! 🍎", "Croc croc en vue !", "Elle a l'air juteuse !")
}

object HayFood : FoodSpec {
    override val food get() = Food.HAY
    override val lines = arrayOf("Du foin ! 🌾 Ça sent l'été !", "Mon plat de tous les jours !", "Ça gratte, j'adore !")
}

object TreatFood : FoodSpec {
    override val food get() = Food.TREAT
    override val restSpin = 15f
    override val lines = arrayOf("UNE FRIANDISE ?! 🍪😍", "C'est jour de fête ?", "Juste une, promis !")
}

/** Crotte : ramassée d'un tap ; reste sur la vue où elle est tombée (voir `ToyController.spawnPoop`). */
object Poop : PropSpec {
    override val wDp = 34
    override val hDp = 34
    override val touchable = true
}
