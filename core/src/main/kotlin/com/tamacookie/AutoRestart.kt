package com.tamacookie

/**
 * Faut-il relancer le service à la réception d'un broadcast système (`BootReceiver`) ?
 *
 * Seuls le démarrage du téléphone et la mise à jour de l'app (`adb install -r`, Play Store) relancent : ce sont
 * aussi les deux broadcasts qu'Android 12+ exempte de la restriction de démarrage d'un service au premier plan
 * depuis l'arrière-plan. Valeurs de `android.content.Intent` recopiées (vérifiées par `AndroidConstantsTest`).
 */
object AutoRestart {
    /** `Intent.ACTION_BOOT_COMPLETED`. */
    const val BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"

    /** `Intent.ACTION_MY_PACKAGE_REPLACED` : notre app vient d'être mise à jour (le processus a été tué). */
    const val MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"

    /** Broadcasts qui relancent, dans l'ordre du manifeste. */
    val ACTIONS = listOf(BOOT_COMPLETED, MY_PACKAGE_REPLACED)

    /**
     * Vrai si [action] est l'un des [ACTIONS], que l'utilisateur avait laissé l'animal affiché
     * ([enabled] = `Prefs.ENABLED`) et que la permission de surimpression est toujours accordée.
     */
    fun shouldRestart(action: String?, enabled: Boolean, canDrawOverlays: Boolean): Boolean =
        action in ACTIONS && enabled && canDrawOverlays
}
