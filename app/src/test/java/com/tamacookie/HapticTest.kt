package com.tamacookie

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Test

/** Les retours haptiques du cœur ([Haptic], Kotlin pur) sont bien les constantes d'Android. */
class HapticTest {
    @Test
    fun `constantes recopiees d'Android`() {
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, Haptic.KEYBOARD_TAP)
    }
}
