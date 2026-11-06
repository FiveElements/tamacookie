package com.tamacookie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/** Relance Cookie au démarrage du téléphone s'il était actif. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = Prefs.of(context).getBoolean(Prefs.ENABLED, false)
        if (!enabled || !Settings.canDrawOverlays(context)) return
        try {
            BunnyService.start(context)
        } catch (e: Exception) {
            // ex. ForegroundServiceStartNotAllowedException : on n'insiste pas, l'app relancera plus tard
            Log.w("TamaCookie", "Démarrage au boot impossible", e)
        }
    }
}
