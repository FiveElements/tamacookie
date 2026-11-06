package com.tamacookie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Relance Cookie s'il était actif, au démarrage du téléphone et après une mise à jour de l'app
 * (`ACTION_MY_PACKAGE_REPLACED` : `adb install -r` ou Play Store tuent le processus). Décision : [AutoRestart].
 * Ces deux broadcasts sont exemptés (Android 12+) de l'interdiction de démarrer un service au premier plan
 * depuis l'arrière-plan.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in AutoRestart.ACTIONS) return
        val enabled = Prefs.of(context).getBoolean(Prefs.ENABLED, false)
        if (!AutoRestart.shouldRestart(action, enabled, Settings.canDrawOverlays(context))) return
        try {
            BunnyService.start(context)
        } catch (e: Exception) {
            // ex. ForegroundServiceStartNotAllowedException : on n'insiste pas, l'app relancera plus tard
            Log.w("TamaCookie", "Relance impossible ($action)", e)
        }
    }
}
