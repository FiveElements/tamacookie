package com.tamacookie

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Constantes Android recopiées dans le cœur (Kotlin pur, sans Android) : ce sont bien celles du framework
 * (constantes de compilation, lisibles dans les stubs des tests JVM de l'app).
 */
class AndroidConstantsTest {
    @Test
    fun `retours haptiques`() {
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, Haptic.KEYBOARD_TAP)
    }

    @Test
    fun `categories d'application`() {
        assertEquals(ApplicationInfo.CATEGORY_UNDEFINED, AppCategory.UNDEFINED)
        assertEquals(ApplicationInfo.CATEGORY_GAME, AppCategory.GAME)
        assertEquals(ApplicationInfo.CATEGORY_AUDIO, AppCategory.AUDIO)
        assertEquals(ApplicationInfo.CATEGORY_VIDEO, AppCategory.VIDEO)
        assertEquals(ApplicationInfo.CATEGORY_IMAGE, AppCategory.IMAGE)
        assertEquals(ApplicationInfo.CATEGORY_SOCIAL, AppCategory.SOCIAL)
        assertEquals(ApplicationInfo.CATEGORY_NEWS, AppCategory.NEWS)
        assertEquals(ApplicationInfo.CATEGORY_MAPS, AppCategory.MAPS)
        assertEquals(ApplicationInfo.CATEGORY_PRODUCTIVITY, AppCategory.PRODUCTIVITY)
    }

    @Test
    fun `broadcasts de relance`() {
        assertEquals(Intent.ACTION_BOOT_COMPLETED, AutoRestart.BOOT_COMPLETED)
        assertEquals(Intent.ACTION_MY_PACKAGE_REPLACED, AutoRestart.MY_PACKAGE_REPLACED)
    }
}
