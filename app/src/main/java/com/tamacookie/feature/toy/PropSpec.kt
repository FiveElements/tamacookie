package com.tamacookie.feature.toy

import com.tamacookie.PetEvent
import com.tamacookie.BunnyPhysics
import com.tamacookie.Expr
import com.tamacookie.PType
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.Prop
import com.tamacookie.Tuning

/**
 * Ce qu'est un objet posé à l'écran (jouet, nourriture, crotte) : taille de sa fenêtre et nature.
 * Un `object` par objet ; aucun état (il est dans [Prop] et dans le contrôleur). Le dessin est côté
 * Android (`PropDrawers.of(spec)`, un `…Drawer` par objet dans `:app`).
 */
interface PropSpec {
    val wDp: Int
    val hDp: Int
    /** La fenêtre reçoit les touchers (jouet qu'on lance, crotte qu'on ramasse). */
    val touchable: Boolean
    /** Le jouet qu'il représente (null = pas un jouet). */
    val toy: Toy? get() = null
    /** La nourriture qu'il représente (null = pas de la nourriture). */
    val food: Food? get() = null
}

/**
 * Lancer d'un jouet qu'on vient de sortir (dp, dp/s ; vitesse horizontale dans le sens où il regarde) :
 * il apparaît au-dessus de l'animal, ou jaillit du coffre avec [chestVyDp].
 */
class Launch(
    val aboveDp: Float = 120f,
    val vxDp: Float = 250f,
    val vyDp: Float = -500f,
    val chestVyDp: Float = -950f,
) {
    companion object {
        val DEFAULT = Launch()
    }
}

/**
 * Un jouet : taille, teinte, lancer, jeu ([play], pendant `Act.PLAY`), physique propre
 * ([step], à chaque image), répliques et réaction quand on le lui prend. Rattaché à [Toy.spec].
 */
interface ToySpec : PropSpec {
    override val toy: Toy
    override val touchable: Boolean get() = true
    /** Couleur tirée au hasard parmi celles des accessoires (sinon couleur fixe du dessin). */
    val randomTint: Boolean get() = false
    val launch: Launch get() = Launch.DEFAULT
    /** Ce qu'il dit en le recevant (sauf si c'est son jouet préféré : réplique commune). */
    val startLines: Array<String>
    /** Il proteste quand l'utilisateur le lui prend (dans la gueule, dans les pattes). */
    val complainsWhenTaken: Boolean get() = false

    /** `Act.PLAY` : ce qu'il fait avec le jouet [p]. */
    fun play(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float)

    /** Physique du jouet à chaque image, quand l'utilisateur ne le tient pas. */
    fun step(ctx: ToyCtx, pet: Pet, p: Prop, dt: Float)
}

/** Une nourriture : taille, répliques. La chute et la péremption sont communes. */
interface FoodSpec : PropSpec {
    override val food: Food
    override val wDp: Int get() = 38
    override val hDp: Int get() = 38
    override val touchable: Boolean get() = false
    /** Rotation une fois tombée au sol. */
    val restSpin: Float get() = 0f
    /** Ce qu'il dit en la voyant arriver. */
    val lines: Array<String>
}

/**
 * Ce que le contrôleur des objets offre aux jouets, en plus de [PetCtx] : l'état de la partie en
 * cours (un seul jouet à la fois) et deux gestes communs.
 */
interface ToyCtx : PetCtx {
    /** L'utilisateur tient le jouet. */
    val userHolds: Boolean
    /** Depuis quand il tient le jouet (frisbee dans la gueule, câlin au doudou), s. */
    var holdTime: Float
    /** Délai avant qu'il puisse rattraper le jouet (s). */
    var catchCooldown: Float
    /** Dernière remarque « il est tombé par terre » (heure murale, ms). */
    var groundSaidAt: Long

    /** Il touche le jouet : jauges (double pour le préféré), cœur ou note, sourire. */
    fun hit(pet: Pet, toy: Toy, amount: Float = 2f) {
        on(pet, PetEvent.PlayHit(toy, amount))
        emit(pet, if (rnd.nextBoolean()) PType.HEART else PType.NOTE)
        pet.express(Expr.HAPPY, 0.8f)
    }

    /** Physique commune : gravité × [gFactor], rebonds au sol / aux bords, frottement. */
    fun stepToy(pet: Pet, p: Prop, dt: Float, gFactor: Float, bounce: Float, wallBounce: Float, friction: Float) {
        val a = arena
        BunnyPhysics.stepProp(
            p, dt, pet.body.phys.gravity, gFactor, bounce, wallBounce, friction,
            p.w / 2f, p.h.toFloat(), a.screenW, a.topY, a.groundY, Tuning.PROP_SETTLE_DP * a.d,
        )
    }
}
