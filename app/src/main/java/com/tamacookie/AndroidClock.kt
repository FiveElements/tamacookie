package com.tamacookie

/** Horloge réelle de l'appareil. */
object AndroidClock : Clock {
    override fun uptimeMs(): Long = android.os.SystemClock.uptimeMillis()
    override fun wallMs(): Long = System.currentTimeMillis()
}
