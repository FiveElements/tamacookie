package com.tamacookie.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.tamacookie.BunnyService
import com.tamacookie.MainActivity
import com.tamacookie.R
import com.tamacookie.persist.Pets

/**
 * Widget d'écran d'accueil (évolution 4c, `RemoteViews`, aucune dépendance) : nom et humeur du résident,
 * jauges, « Nourrir » et « Appeler ».
 *
 * - **Lecture** : uniquement par le repository (l'état sauvegardé ; textes : [WidgetTexts]).
 * - **Écriture** : jamais ; les boutons envoient une commande au service ([BunnyService.ACTION_FEED], comme la
 *   notification, et [BunnyService.ACTION_SHOW]), qui démarre au besoin (`getForegroundService` : un toucher
 *   sur un widget autorise le démarrage au premier plan). Sans la permission de surimpression, ils ouvrent l'app.
 * - **Mise à jour** : par le service quand il sauvegarde le résident (au plus une fois par minute,
 *   [UpdateThrottle]), à son démarrage / arrêt, et toutes les 30 min (`updatePeriodMillis`).
 */
class PetWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    companion object {
        private const val TAG = "TamaCookie"
        private val GAUGES = arrayOf(
            Triple(R.id.widget_g0_label, R.id.widget_g0_bar, R.id.widget_g0_value),
            Triple(R.id.widget_g1_label, R.id.widget_g1_bar, R.id.widget_g1_value),
            Triple(R.id.widget_g2_label, R.id.widget_g2_bar, R.id.widget_g2_value),
            Triple(R.id.widget_g3_label, R.id.widget_g3_bar, R.id.widget_g3_value),
        )

        /** Redessine tous les widgets posés (rien s'il n'y en a aucun). */
        fun updateAll(ctx: Context) {
            try {
                val manager = AppWidgetManager.getInstance(ctx) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(ctx, PetWidget::class.java))
                if (ids.isNotEmpty()) update(ctx, manager, ids)
            } catch (e: Exception) {
                Log.w(TAG, "widget", e)
            }
        }

        private fun update(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            manager.updateAppWidget(ids, build(ctx))
        }

        private fun build(ctx: Context): RemoteViews {
            val rec = Pets.repository(ctx).loadResident()
            val c = if (rec == null) WidgetTexts.NO_PET
            else WidgetTexts.content(rec.identity.name, rec.stats, BunnyService.running)
            val v = RemoteViews(ctx.packageName, R.layout.widget_pet)
            v.setTextViewText(R.id.widget_title, c.title)
            v.setTextViewText(R.id.widget_subtitle, c.subtitle)
            v.setViewVisibility(R.id.widget_gauges, if (c.gauges.isEmpty()) View.GONE else View.VISIBLE)
            v.setContentDescription(R.id.widget_gauges, c.statusLine)
            for ((i, g) in c.gauges.withIndex().take(GAUGES.size)) {
                val (label, bar, value) = GAUGES[i]
                v.setTextViewText(label, g.emoji)
                v.setContentDescription(label, g.label)
                v.setProgressBar(bar, 100, g.value, false)
                v.setTextViewText(value, "${g.value}")
            }
            v.setTextViewText(R.id.widget_status, c.status)
            v.setViewVisibility(R.id.widget_status, if (c.status.isEmpty()) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.widget_buttons, if (rec == null) View.GONE else View.VISIBLE)

            val open = PendingIntent.getActivity(
                ctx, 20, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            v.setOnClickPendingIntent(R.id.widget_feed, command(ctx, BunnyService.ACTION_FEED, 21) ?: open)
            v.setOnClickPendingIntent(R.id.widget_call, command(ctx, BunnyService.ACTION_SHOW, 22) ?: open)
            return v
        }

        /** Commande au service (démarré si besoin) ; null sans la permission de surimpression. */
        private fun command(ctx: Context, action: String, code: Int): PendingIntent? {
            if (!Settings.canDrawOverlays(ctx)) return null
            return PendingIntent.getForegroundService(
                ctx, code, BunnyService.userCommand(ctx, action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
