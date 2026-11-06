package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundAppSourceTest {

    /** Fausse source UsageStats : renvoie [next] puis rien, compte les requêtes. */
    private class Usage(var next: String? = null) : ForegroundAppSource {
        var queries = 0
        override fun foreground(): String? { queries++; return next.also { next = null } }
    }

    private val screen = FakeScreenSensor()
    private val usage = Usage()
    private val source = ConnectionAwareForegroundSource(screen, usage)
    private var awake = true
    private val watcher = ForegroundAppWatcher(source, { awake }, setOf("com.tamacookie", "com.android.systemui")) {
        ForegroundAppWatcher.AppInfo(it, it.substringAfterLast('.'), 0, it == "launcher")
    }

    @Test
    fun `accessibilite connectee - son app, UsageStats jamais interroge`() {
        screen.update { copy(connected = true, foregroundPkg = "com.android.chrome") }
        usage.next = "com.other"
        assertEquals("com.android.chrome", source.foreground())
        assertEquals(0, usage.queries)
        screen.update { copy(foregroundPkg = null) }
        assertNull(source.foreground())                             // inconnue : pas de repli
        assertEquals(0, usage.queries)
    }

    @Test
    fun `accessibilite absente ou deconnectee - repli sur UsageStats`() {
        usage.next = "com.android.chrome"
        screen.update { copy(foregroundPkg = "perime") }             // ce qu'a vu l'accessibilité avant
        assertEquals("com.android.chrome", source.foreground())
        assertEquals(1, usage.queries)
        screen.update { copy(connected = true) }
        assertEquals("perime", source.foreground())
        screen.update { copy(connected = false) }
        assertNull(source.foreground())
        assertEquals(2, usage.queries)
    }

    @Test
    fun `seulement les changements, notre app et le volet ignores`() {
        screen.update { copy(connected = true, foregroundPkg = "com.android.chrome") }
        val app = watcher.poll()!!
        assertEquals("com.android.chrome", app.pkg)
        assertEquals("chrome", app.label)
        assertEquals("com.android.chrome", watcher.currentPkg)
        assertNull(watcher.poll())                                  // pas de changement
        screen.update { copy(foregroundPkg = "com.tamacookie") }
        assertNull(watcher.poll())
        screen.update { copy(foregroundPkg = "com.android.systemui") }
        assertNull(watcher.poll())
        assertEquals("com.android.chrome", watcher.currentPkg)
        screen.update { copy(foregroundPkg = "launcher") }
        assertEquals(true, watcher.poll()!!.isHome)
    }

    @Test
    fun `l'app courante survit au changement de source`() {
        usage.next = "com.android.chrome"
        assertEquals("com.android.chrome", watcher.poll()!!.pkg)
        // L'accessibilité se connecte et voit la même app : pas de « nouvelle » app
        screen.update { copy(connected = true, foregroundPkg = "com.android.chrome") }
        assertNull(watcher.poll())
    }

    @Test
    fun `ecran eteint - aucune source interrogee`() {
        awake = false
        usage.next = "com.android.chrome"
        assertNull(watcher.poll())
        assertEquals(0, usage.queries)
        awake = true
        assertEquals("com.android.chrome", watcher.poll()!!.pkg)
    }
}
