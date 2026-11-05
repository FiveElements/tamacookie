package com.tamacookie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Relance Cookie au démarrage du téléphone s'il était actif. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = context.getSharedPreferences("pet", Context.MODE_PRIVATE).getBoolean("enabled", false)
        if (enabled && Settings.canDrawOverlays(context)) BunnyService.start(context)
    }
}
