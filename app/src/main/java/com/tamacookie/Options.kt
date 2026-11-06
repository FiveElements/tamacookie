package com.tamacookie

import android.graphics.Color

/** Utilitaires de couleur des options d'apparence (côté Android : `Color`). */
object Options {
    fun color(o: ColorOption) = Color.parseColor(o.hex)

    fun isDark(color: Int): Boolean {
        val l = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return l < 110
    }

    fun shade(color: Int, f: Float): Int = Color.rgb(
        (Color.red(color) * f).toInt().coerceIn(0, 255),
        (Color.green(color) * f).toInt().coerceIn(0, 255),
        (Color.blue(color) * f).toInt().coerceIn(0, 255),
    )
}
