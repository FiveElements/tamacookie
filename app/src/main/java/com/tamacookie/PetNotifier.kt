package com.tamacookie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Notifications du service : notification permanente (premier plan) et alertes de besoins.
 *
 * Dépendances étroites : l'état est lu au moment de construire la notification via [pet] (le résident,
 * null tant que le service n'est pas initialisé), [hidden] et [hiding] ; avec des compagnons, titre,
 * ligne et alertes résument tous les animaux ([Director.title], [Director.statusLine], [Director.alerts]).
 */
class PetNotifier(
    private val ctx: Context,
    private val pet: () -> Pet?,
    private val director: () -> Director,
    private val hidden: () -> Boolean,
    private val hiding: () -> Boolean,
) {
    companion object {
        const val NOTIF_ID = 1
        private const val ALERT_ID = 2
        private const val CH_PET = "pet"
        private const val CH_ALERT = "alerts"
    }

    private var lastNotifText = ""

    private val nm: NotificationManager get() = ctx.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        nm.createNotificationChannel(NotificationChannel(CH_PET, "Cookie est là", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Notification permanente du lapin"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Besoins de Cookie", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Quand Cookie a faim, est sale ou malade"
        })
    }

    private fun pi(action: String, code: Int): PendingIntent = PendingIntent.getService(
        ctx, code, Intent(ctx, BunnyService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun statusLine(p: Pet): String = p.stats.let { NotifierTexts.statusLine(it.satiety, it.happiness, it.energy, it.cleanliness) }

    fun build(): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = Notification.Builder(ctx, CH_PET)
            .setSmallIcon(R.drawable.ic_bunny)
            .setContentIntent(open)
            .setOngoing(true)
        // Avant l'initialisation (permission manquante) : notification minimale
        val p = pet() ?: return b.setContentTitle("TamaCookie").build()
        val hidden = hidden()
        val moodTxt = NotifierTexts.moodText(p.stats.mood, p.stats.sleeping, hiding())
        return b
            .setContentTitle(director().title { "$moodTxt" + (if (p.stats.caged) " (en cage 🔒)" else "") } + if (hidden) " (caché)" else "")
            .setContentText(director().statusLine(::statusLine))
            .addAction(Notification.Action.Builder(null, "🥕 Nourrir", pi(BunnyService.ACTION_FEED, 1)).build())
            .addAction(Notification.Action.Builder(null, if (hidden) "Montrer" else "Cacher", pi(BunnyService.ACTION_TOGGLE, 2)).build())
            .addAction(Notification.Action.Builder(null, "Ranger", pi(BunnyService.ACTION_STOP, 3)).build())
            .build()
    }

    /** Met à jour la notification permanente si son contenu a changé (ou si [force]). */
    fun update(force: Boolean) {
        val p = pet() ?: return
        val key = director().statusLine(::statusLine) + p.stats.mood + hidden() + director().title { "" }
        if (!force && key == lastNotifText) return
        lastNotifText = key
        nm.notify(NOTIF_ID, build())
    }

    /** Alerte quand l'humeur bascule vers un besoin (faim, saleté, maladie, ennui). */
    fun checkAlerts() {
        pet() ?: return
        val txt = director().alerts { NotifierTexts.alertText(it.name, it.stats.mood) } ?: return
        val n = Notification.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_bunny)
            .setContentTitle(txt)
            .setContentText("Touche pour t'occuper de lui")
            .setContentIntent(pi(BunnyService.ACTION_TOGGLE, 4).takeIf { hidden() }
                ?: PendingIntent.getActivity(ctx, 5, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
        nm.notify(ALERT_ID, n)
    }
}
