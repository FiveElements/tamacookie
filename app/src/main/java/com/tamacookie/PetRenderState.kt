package com.tamacookie

import com.tamacookie.feature.toy.Food

/** Posture affichée (commune aux espèces : chaque renderer la dessine à sa façon). */
enum class Pose { IDLE, WALK, AIR, SLEEP, EAT, DRAGGED, SIT }

/** Expression du visage. */
enum class Expr { NORMAL, HAPPY, SAD, SLEEPY, SURPRISED, SICK, ANGRY, DIZZY }

/** Particules émises au-dessus de la tête (dessinées par [PetView], communes aux espèces). */
enum class PType { HEART, ZZZ, NOTE, SPARKLE, DROP, CRUMB, STAR, BUBBLE }

/**
 * Ce qu'affiche la fenêtre d'un animal à une image donnée (immuable). Produit à chaque tick à partir
 * du [Pet] par [of] ; [PetView.render] ne lit que ça (la vue n'écrit ni ne lit l'état du jeu).
 * Les aperçus (accueil, adoption) en fabriquent eux-mêmes.
 */
data class PetRenderState(
    val pose: Pose = Pose.IDLE,
    val expr: Expr = Expr.NORMAL,
    /** 1 = vers la droite, -1 = vers la gauche. */
    val facing: Float = 1f,
    /** 0..1 : cycle de sautillement pendant la marche. */
    val walkPhase: Float = 0f,
    /** Vitesse verticale en vol (px/s) : étirement. */
    val airVy: Float = 0f,
    /** 0..1 : avancement du repas. */
    val eatProgress: Float = 0f,
    /** Ce qu'il a dans la bouche quand il mange. */
    val food: Food = Food.CARROT,
    val dirty: Boolean = false,
    val veryDirty: Boolean = false,
    val look: Look = Look(),
) {
    companion object {
        /** L'état affiché de [pet] ([Look] est immuable : partagée sans copie). */
        fun of(pet: Pet, rules: GameRules = GameRules.DEFAULT): PetRenderState {
            val b = pet.body
            val st = pet.stats
            return PetRenderState(
                pose = b.pose,
                expr = pet.mind.exprOverride ?: moodExpr(st.mood),
                facing = b.facing,
                walkPhase = b.walkPhase,
                airVy = b.phys.airVy,
                eatProgress = b.eatProgress,
                food = b.eatingFood,
                dirty = st.cleanliness < rules.looksDirtyBelow || st.poops >= rules.dirtyPoops,
                veryDirty = st.cleanliness < rules.looksVeryDirtyBelow,
                look = pet.look,
            )
        }

        /** Expression de repos selon l'humeur (sans expression forcée). */
        fun moodExpr(mood: Mood): Expr = when (mood) {
            Mood.HAPPY, Mood.OK, Mood.DIRTY -> Expr.NORMAL
            Mood.HUNGRY, Mood.SAD -> Expr.SAD
            Mood.SLEEPY -> Expr.SLEEPY
            Mood.SICK -> Expr.SICK
        }
    }
}
