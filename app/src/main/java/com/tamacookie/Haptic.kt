package com.tamacookie

/**
 * Retours haptiques demandés par le cœur ([PetCtx.haptic]) : valeurs de `android.view.HapticFeedbackConstants`
 * recopiées pour rester en Kotlin pur (vérifiées par `HapticTest` dans l'app).
 */
object Haptic {
    /** `HapticFeedbackConstants.KEYBOARD_TAP` : petit tic (rebond, entrée dans une station). */
    const val KEYBOARD_TAP = 3
}
