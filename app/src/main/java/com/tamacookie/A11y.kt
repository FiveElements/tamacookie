package com.tamacookie

import android.content.ContentResolver
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.tamacookie.feature.station.StationKind

/**
 * Accessibilité côté Android (étape 8d) : identifiants des actions TalkBack personnalisées, réglage
 * « Supprimer les animations », petits outils pour les vues. Les textes calculés sont dans le cœur
 * ([A11yTexts]), les libellés fixes dans `strings.xml` (`access_*`).
 */
object A11yActionIds {
    /** Une action « Mettre dans … » par station (ids de ressource : `res/values/ids.xml`). */
    val STATIONS = intArrayOf(
        R.id.access_action_station_0, R.id.access_action_station_1, R.id.access_action_station_2,
        R.id.access_action_station_3, R.id.access_action_station_4, R.id.access_action_station_5,
        R.id.access_action_station_6, R.id.access_action_station_7, R.id.access_action_station_8,
        R.id.access_action_station_9, R.id.access_action_station_10, R.id.access_action_station_11,
    )

    fun station(k: StationKind): Int = STATIONS[k.ordinal]

    fun kindOf(id: Int): StationKind? {
        val i = STATIONS.indexOf(id)
        return if (i < 0) null else StationKind.entries.getOrNull(i)
    }
}

/** Réglages d'animation d'Android, lus pour [ReducedMotion] (au démarrage, à `ACTION_RELOAD`, à leur changement). */
object MotionSettings {
    private val NAMES = listOf(
        Settings.Global.ANIMATOR_DURATION_SCALE,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        Settings.Global.WINDOW_ANIMATION_SCALE,
    )

    /** À observer (`ContentObserver`) pour suivre le réglage sans redémarrer. */
    val URIS: List<Uri> get() = NAMES.map { Settings.Global.getUriFor(it) }

    fun reduced(cr: ContentResolver): Boolean {
        val (animator, transition, window) = NAMES.map { Settings.Global.getFloat(cr, it, 1f) }
        return ReducedMotion.isReduced(animator, transition, window)
    }
}

/** Taille minimale d'une cible tactile (dp). */
const val MIN_TOUCH_DP = 48

/** Cible tactile d'au moins [MIN_TOUCH_DP] de haut (et de large), texte centré verticalement. */
fun TextView.touchTarget(d: Float, width: Boolean = true) {
    minHeight = (MIN_TOUCH_DP * d).toInt()
    if (width) minWidth = (MIN_TOUCH_DP * d).toInt()
}

/** Description lue par le lecteur d'écran : [text] sans ses emojis ([SpokenText.plain]). */
fun View.describePlain(text: CharSequence) {
    contentDescription = SpokenText.plain(text.toString())
}

/** Le lecteur d'écran annonce « Touche deux fois pour [label] » au lieu de « pour activer ». */
fun View.clickLabel(label: CharSequence) {
    accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, label))
        }
    }
}
