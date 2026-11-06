package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSensorHubTest {

    private val hub = ScreenSensorHub()
    private val launcher = "launcher"
    private val icon = Platform(Box(100f, 600f, 200f, 700f), "Chrome")

    private fun scan(pkg: String? = launcher, page: Pair<Int, Int>? = null, platforms: List<Platform> = listOf(icon),
                     ime: Box? = null, now: Long = 100_000L, startedAt: Int = hub.epoch) =
        hub.onScan(ScanResult(ime, pkg, platforms, page, screenW = 1080), startedAt, now)

    // --- Signaux sans perte ---

    @Test
    fun `deux clics entre deux images donnent deux evenements, dans l'ordre`() {
        val a = Box(0f, 0f, 10f, 10f); val b = Box(20f, 0f, 30f, 10f)
        hub.onClick(a); hub.onTyping(); hub.onClick(b)
        assertEquals(listOf(UserSignal.Click(a), UserSignal.Typing, UserSignal.Click(b)), hub.takeSignals())
        assertTrue(hub.takeSignals().isEmpty())                     // consommés
    }

    @Test
    fun `clic sans bornes ignore, file plafonnee aux plus recents`() {
        hub.onClick(null); hub.onClick(Box(0f, 0f, 0f, 0f))
        assertTrue(hub.takeSignals().isEmpty())
        repeat(ScreenSensorHub.MAX_SIGNALS + 10) { hub.onTyping() }
        hub.onClick(Box(0f, 0f, 5f, 5f))
        val s = hub.takeSignals()
        assertEquals(ScreenSensorHub.MAX_SIGNALS, s.size)
        assertTrue(s.last() is UserSignal.Click)
    }

    @Test
    fun `defilement - signal, plateformes videes, seul celui du bureau est suivi`() {
        scan(page = 1 to 3)
        assertTrue(hub.snapshot.platforms.isNotEmpty())
        hub.onViewScrolled("autre", 500, 0, 10, 1_000)
        assertEquals(listOf(UserSignal.Scroll), hub.takeSignals())
        assertTrue(hub.snapshot.platforms.isEmpty())
        assertEquals(99_000L, hub.snapshot.scroll.t1)               // (calé par le scan sur la page 1)
        hub.onViewScrolled(launcher, 500, 0, 10, 1_000)
        hub.onViewScrolled(launcher, 700, 0, 0, 1_100)
        assertEquals(ScrollSamples(500, 1_000, 700, 1_100), hub.snapshot.scroll)
        hub.onViewScrolled(launcher, 700, 0, 0, 1_200)               // même position, pas de delta : ignoré
        hub.onViewScrolled(launcher, 900, 5, 10, 1_300)              // défilement vertical : ignoré
        assertEquals(1_100L, hub.snapshot.scroll.t1)
    }

    // --- Scans ---

    @Test
    fun `scan - application, clavier et plateformes publiees avec une nouvelle version`() {
        val kb = Box(0f, 1500f, 1080f, 2000f)
        scan(pkg = "com.android.chrome", ime = kb)
        val s = hub.snapshot
        assertEquals("com.android.chrome", s.foregroundPkg)
        assertEquals("com.android.chrome", s.viewKey)
        assertEquals(kb, s.ime)
        assertEquals(listOf(icon), s.platforms.items)
        assertEquals(listOf(icon.box), s.platforms.boxes)
        val v = s.platforms.version
        scan(pkg = "com.android.chrome", ime = kb)                    // même liste : même version
        assertSame(s.platforms, hub.snapshot.platforms)
        scan(pkg = "com.android.chrome", platforms = emptyList())
        assertTrue(hub.snapshot.platforms.version > v)
    }

    @Test
    fun `scan sans fenetre lisible - plateformes videes, vue gardee`() {
        scan(pkg = "com.android.chrome")
        scan(pkg = null, ime = null)
        assertTrue(hub.snapshot.platforms.isEmpty())
        assertEquals("com.android.chrome", hub.snapshot.viewKey)
        assertEquals("com.android.chrome", hub.snapshot.foregroundPkg)
    }

    @Test
    fun `scan du bureau - page, nombre de pages et package du bureau`() {
        scan(page = 2 to 4)
        val s = hub.snapshot
        assertEquals("launcher#p2", s.viewKey)
        assertEquals(4, s.pageCount)
        assertEquals(launcher, s.launcherPkg)
    }

    @Test
    fun `bureau au repos - largeur de page apprise du defilement`() {
        scan(page = 1 to 3)
        hub.onViewScrolled(launcher, 2158, 0, 5, 99_000)
        hub.onViewScrolled(launcher, 2160, 0, 2, 99_100)
        scan(page = 3 to 3, now = 99_500)                           // animation pas finie depuis 700 ms
        assertEquals(0f, hub.snapshot.pageW, 0f)
        scan(page = 3 to 3, now = 100_000)
        assertEquals(1080f, hub.snapshot.pageW, 0f)
        assertEquals(2160, hub.snapshot.scroll.x1)                  // déjà calé : pas de recalage
    }

    @Test
    fun `bureau au repos - defilement recale (bouton Accueil sans animation)`() {
        hub.setPageWidth(1080f)
        scan(page = 1 to 3)                                         // aucun défilement connu : calé sur la page 1
        assertEquals(ScrollSamples(0, 99_000, 0, 99_000), hub.snapshot.scroll)
        hub.onViewScrolled(launcher, 2158, 0, 10, 100_000)
        hub.onViewScrolled(launcher, 2160, 0, 2, 100_100)
        scan(page = 1 to 3, now = 200_000)                          // revenu page 1 sans événement
        assertEquals(0, hub.snapshot.scroll.x1)
        assertEquals(199_000L, hub.snapshot.scroll.t1)
    }

    @Test
    fun `scan lance avant un defilement - vue mise a jour, plateformes pas republiees`() {
        val started = hub.epoch
        hub.onViewScrolled(launcher, 100, 0, 10, 1_000)
        scan(pkg = "com.android.chrome", startedAt = started)
        assertEquals("com.android.chrome", hub.snapshot.viewKey)
        assertTrue(hub.snapshot.platforms.isEmpty())
        scan(pkg = "com.android.chrome")
        assertTrue(hub.snapshot.platforms.isNotEmpty())
    }

    @Test
    fun `ecran eteint - plateformes videes et scans ignores jusqu'au rallumage`() {
        hub.onConnected(screenOn = true)
        scan()
        hub.onScreenOff()
        assertFalse(hub.snapshot.screenOn)
        assertTrue(hub.snapshot.platforms.isEmpty())
        scan(pkg = "com.android.chrome")
        assertEquals(launcher, hub.snapshot.foregroundPkg)
        hub.onScreenOn()
        scan(pkg = "com.android.chrome")
        assertEquals("com.android.chrome", hub.snapshot.foregroundPkg)
    }

    @Test
    fun `connexion et deconnexion du service`() {
        hub.onConnected(screenOn = false)
        assertTrue(hub.snapshot.connected)
        assertFalse(hub.snapshot.screenOn)
        hub.onScreenOn(); scan()
        hub.onDisconnected()
        assertFalse(hub.snapshot.connected)
        assertTrue(hub.snapshot.screenOn)
        assertTrue(hub.snapshot.platforms.isEmpty())
    }

    // --- Observable ---

    @Test
    fun `les abonnes recoivent chaque nouvel etat, pas les doublons`() {
        val seen = ArrayList<ScreenSnapshot>()
        val stop = hub.observe { seen += it }
        hub.setPageWidth(1000f)
        hub.setPageWidth(1000f)
        hub.onTyping()                                              // un signal ne change pas l'état
        assertEquals(1, seen.size)
        assertEquals(1000f, seen.single().pageW, 0f)
        stop()
        hub.setPageWidth(0f)
        assertEquals(1, seen.size)
        assertEquals(0f, hub.snapshot.pageW, 0f)
    }
}
