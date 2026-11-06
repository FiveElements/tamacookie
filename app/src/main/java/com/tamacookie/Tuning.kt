package com.tamacookie

/**
 * Constantes de réglage communes à toutes les espèces et aux objets (gabarit et vitesses d'une espèce :
 * `SpeciesSpec`).
 * Suffixes : DP = à multiplier par la densité de l'écran, S = secondes, MS = millisecondes.
 * Aucune dépendance Android : utilisable dans les tests JVM.
 */
object Tuning {
    // --- Animal (limites ; gabarit et vitesses : `SpeciesSpec` de son espèce) ---
    /** Marge latérale : il ne marche pas plus près du bord que ça. */
    const val SIDE_MARGIN_DP = 34f
    const val GROUND_MARGIN_DP = 2f
    /** Hauteur (sous le haut de l'écran) d'où il tombe en arrivant. */
    const val SPAWN_DROP_DP = 150f

    // --- Physique de l.animal ---
    const val GRAVITY_DP = 2600f
    /** Vitesse de chute au-delà de laquelle l'atterrissage est "brutal" (étourdi). */
    const val HARD_LANDING_DP = 2300f
    /** Vitesse de chute au-delà de laquelle il rebondit au sol. */
    const val GROUND_BOUNCE_DP = 1300f
    const val GROUND_BOUNCE_DAMP = 0.32f
    const val GROUND_BOUNCE_FRICTION = 0.6f
    const val WALL_BOUNCE = 0.5f
    /** Plafond : il ne monte pas plus haut que topY + CEILING_DP. */
    const val CEILING_DP = 100f
    const val CEILING_BOUNCE = 0.3f
    /** Marge horizontale pour atterrir sur une icône. */
    const val PLATFORM_MARGIN_DP = 6f
    /** Tolérance (px) sur le haut d'une icône pour détecter l'atterrissage. */
    const val PLATFORM_TOP_TOLERANCE_PX = 2f
    /** Une icône "est la même" après rafraîchissement si son haut a bougé de moins que ça. */
    const val PLATFORM_SAME_DP = 12f
    /** Il se tourne dans le sens du vol au-delà de cette vitesse horizontale. */
    const val FACE_VX_DP = 60f
    const val JUMP_APEX_MIN_DP = 70f
    const val JUMP_APEX_RAND_DP = 50f
    /** Vitesse maximale d'un lancer (animal). */
    const val THROW_MAX_DP = 3500f
    const val THROW_SHOUT_DP = 2400f

    // --- Physique des objets ---
    /** En dessous de cette vitesse d'impact, un objet s'arrête au sol au lieu de rebondir. */
    const val PROP_SETTLE_DP = 250f
    const val PROP_CEILING_BOUNCE = 0.6f
    const val TOY_THROW_MAX_DP = 4000f
    /** Âge (s) au-delà duquel la nourriture non mangée disparaît. */
    const val FOOD_LIFETIME_S = 90f

    // --- Panneaux (largeur souhaitée, réduite sur écran étroit) ---
    const val MENU_W_DP = 320f
    const val STATIONS_PANEL_W_DP = 300f
    const val CHEST_PANEL_W_DP = 330f
    const val VET_PANEL_W_DP = 320f
    /** Espace minimal laissé de chaque côté d'un panneau (dp). */
    const val PANEL_SCREEN_MARGIN_DP = 8f
    /** Marge (px) utilisée pour borner la position x d'un panneau. */
    const val PANEL_EDGE_PX = 8
    const val DOCK_SIZE_DP = 52f

    // --- Boucle d'animation ---
    const val FPS_MOVING = 60
    const val FPS_IDLE = 30
    const val FPS_SLEEP = 12
    /** Plafond du pas de temps à 30-60 i/s. */
    const val DT_CAP_S = 0.05f
    /** Vérification légère quand la boucle est suspendue (animal invisible). */
    const val SUSPENDED_TICK_MS = 300L
    /** Pas de simulation pendant la suspension. */
    const val SUSPENDED_STEP_S = 0.1f
    /** Un défilement du bureau récent garde la boucle rapide. */
    const val SCROLL_ACTIVE_MS = 600L

    // --- Temps ---
    const val STATS_EVERY_MS = 10_000L
    const val SAVE_EVERY_MS = 60_000L
    const val TRAVEL_COOLDOWN_MS = 6_000L
    const val HIDE_GIVE_UP_MS = 180_000L
    const val SELF_SERVE_COOLDOWN_MS = 120_000L
    /** Pas de retour sur la page affichée pendant un défilement. */
    const val PAGE_RETURN_SCROLL_QUIET_MS = 500L
    const val PAGE_RETURN_MIN_MS = 2_500
    const val PAGE_RETURN_RAND_MS = 3_500
    const val PAGE_WANDER_MIN_MS = 15_000
    const val PAGE_WANDER_RAND_MS = 15_000
    const val MENU_REOPEN_GUARD_MS = 350L
    const val TAP_WINDOW_MS = 5_000L
    const val TICKLE_TAPS = 7
}
