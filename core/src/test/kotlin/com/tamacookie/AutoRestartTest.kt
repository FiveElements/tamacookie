package com.tamacookie

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRestartTest {
    @Test
    fun `relance au demarrage et apres une mise a jour`() {
        assertTrue(AutoRestart.shouldRestart(AutoRestart.BOOT_COMPLETED, enabled = true, canDrawOverlays = true))
        assertTrue(AutoRestart.shouldRestart(AutoRestart.MY_PACKAGE_REPLACED, enabled = true, canDrawOverlays = true))
    }

    @Test
    fun `pas de relance si l'animal etait range ou sans surimpression`() {
        for (action in AutoRestart.ACTIONS) {
            assertFalse(AutoRestart.shouldRestart(action, enabled = false, canDrawOverlays = true))
            assertFalse(AutoRestart.shouldRestart(action, enabled = true, canDrawOverlays = false))
        }
    }

    @Test
    fun `autres broadcasts ignores`() {
        assertFalse(AutoRestart.shouldRestart(null, enabled = true, canDrawOverlays = true))
        assertFalse(AutoRestart.shouldRestart("android.intent.action.PACKAGE_REPLACED", true, true))
        assertFalse(AutoRestart.shouldRestart("android.intent.action.LOCKED_BOOT_COMPLETED", true, true))
    }
}
