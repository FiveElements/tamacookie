package com.tamacookie.feature.wellbeing

import com.tamacookie.Act
import com.tamacookie.Expr
import com.tamacookie.PType
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.feature.wellbeing.WellbeingTracker.Cue

/**
 * Ce que fait l'animal quand le [WellbeingTracker] lui fait signe (évolution 4c) : des gestes et des
 * répliques douces, **jamais** de jauge touchée ni d'écran bloqué. Il ne réagit que s'il est visible,
 * réveillé et pas en train de jouer à cache-cache ; sinon le signal passe (la bouderie, elle, reste un état).
 */
object WellbeingCoach {
    val BREAK = arrayOf(
        "*bâille* Et si on faisait une pause ? ☕",
        "*s'étire* Ça fait un moment qu'on est là… une petite pause ? 🌿",
        "Tes yeux méritent une pause, non ? 👀💤",
    )
    val SULK = arrayOf(
        "Hmpf… je boude. Une pause, s'il te plaît ! 😤",
        "Ça fait trop longtemps… je ne te parle plus. 😾",
    )
    val MADE_UP = arrayOf(
        "Bon… d'accord, je ne boude plus 💕",
        "Une caresse et j'oublie tout 🥰",
    )
    val RELIEVED = arrayOf(
        "Ah, ça fait du bien de changer ! 💕",
        "Merci ! On respire un peu 🌸",
    )

    /** Durée de l'expression de bouderie, renouvelée à chaque relevé tant qu'il boude ([maintain]). */
    const val SULK_EXPR_SECONDS = 2.5f

    /** Il peut réagir : affiché, réveillé, pas caché. */
    fun available(ctx: PetCtx, pet: Pet): Boolean =
        !ctx.hidden && !ctx.isAway(pet) && pet.act != Act.SLEEP && !pet.isHiding && !pet.stats.sleeping

    /** Réagit à [cue] (avec [pet] pour animal courant : réplique et tirage sont les siens). */
    fun react(ctx: PetCtx, pet: Pet, cue: Cue) {
        if (!available(ctx, pet)) return
        when (cue) {
            Cue.BREAK -> {
                pet.express(Expr.SLEEPY, 2.5f)
                // Il s'assoit pour s'étirer s'il ne faisait rien de particulier
                if (pet.act == Act.IDLE || pet.act == Act.WALK) pet.setAct(Act.SIT, 3f)
                ctx.speech.sayOneOf(BREAK)
            }
            Cue.SULK -> {
                pet.express(Expr.ANGRY, SULK_EXPR_SECONDS)
                ctx.speech.sayOneOf(SULK)
            }
            Cue.MADE_UP -> {
                pet.mind.exprOverride = null
                pet.express(Expr.HAPPY, 1.5f)
                ctx.emit(pet, PType.HEART, 2)
                ctx.speech.sayOneOf(MADE_UP)
            }
            Cue.RELIEVED -> {
                pet.mind.exprOverride = null
                ctx.speech.sayOneOf(RELIEVED)
            }
        }
        ctx.requestFrames()
    }

    /** Tant qu'il boude, son visage le montre (sans écraser une autre expression en cours). */
    fun maintain(ctx: PetCtx, pet: Pet, sulking: Boolean) {
        if (!sulking || !available(ctx, pet)) return
        val e = pet.mind.exprOverride
        if (e == null || e == Expr.ANGRY) pet.express(Expr.ANGRY, SULK_EXPR_SECONDS)
    }
}
