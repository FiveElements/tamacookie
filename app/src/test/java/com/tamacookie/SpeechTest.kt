package com.tamacookie

import com.tamacookie.species.rabbit.RabbitLines
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Répliques ([Lines]), tirage ([Speech]), textes de notification ([PetNotifier]) et [Palette]. */
class SpeechTest {

    private val said = mutableListOf<Pair<String, Float>>()
    private fun speech(seed: Int = 1) = Speech(Random(seed)) { t, s -> said += t to s }

    // --- Speech ---

    @Test fun say_transmetTexteEtDureeParDefaut() {
        speech().say("Coucou")
        assertEquals(listOf("Coucou" to Speech.DEFAULT_SECONDS), said)
    }

    @Test fun sayOneOf_tireDansLaListe() {
        val sp = speech()
        repeat(50) { sp.sayOneOf(RabbitLines.PETTED, 2f) }
        assertTrue(said.all { (t, s) -> t in RabbitLines.PETTED && s == 2f })
    }

    @Test fun pick_memeTirageQueLAncienPick() {
        // Même graine, même suite : pickFrom(tableau) et pick(vararg) consomment le Random à l'identique
        val a = Speech(Random(42)) { _, _ -> }
        val b = Speech(Random(42)) { _, _ -> }
        repeat(20) { assertEquals(a.pick(*RabbitLines.ATE), b.pickFrom(RabbitLines.ATE)) }
    }

    @Test fun chatter_couvreToutesLesHumeurs() {
        val sp = speech()
        for (m in Mood.values()) {
            val lines = RabbitLines.chatter(m)
            assertTrue(lines.isNotEmpty())
            assertTrue(sp.chatterLine(m) in lines)
        }
        assertTrue("J'ai faim… 🥕" in RabbitLines.chatter(Mood.HUNGRY))
    }

    // --- Lines ---

    @Test fun greeting_selonFaimEtHeure() {
        assertEquals("Coucou… j'ai faim 🥺", RabbitLines.greeting("Cookie", hungry = true, hour = 9))
        assertEquals("Bonjour ! ☀️ C'est moi, Cookie !", RabbitLines.greeting("Cookie", hungry = false, hour = 10))
        assertEquals("Coucou ! C'est Cookie 🐰", RabbitLines.greeting("Cookie", hungry = false, hour = 11))
        assertEquals("Coucou ! C'est Cookie 🐰", RabbitLines.greeting("Cookie", hungry = false, hour = 17))
        assertEquals("Bonsoir ! 🌙 Cookie est là", RabbitLines.greeting("Cookie", hungry = false, hour = 18))
    }

    @Test fun appComments_prioriteAccueilPuisPaquetPuisCategorie() {
        val home = RabbitLines.appComments("Accueil", "com.android.camera", ApplicationInfo.CATEGORY_GAME, isHome = true)
        assertTrue("De retour à la maison 🏠" in home)
        val cam = RabbitLines.appComments("Photo", "com.Vendor.CAMERA2", ApplicationInfo.CATEGORY_GAME, isHome = false)
        assertTrue("Prends-moi en photo ! 📸" in cam)
        assertTrue("Tic tac 🕰️" in RabbitLines.appComments("Horloge", "x.alarm", -1, false))
        assertTrue("Tu m'achètes une carotte ?" in RabbitLines.appComments("Play", "com.android.vending", -1, false))
    }

    @Test fun appComments_categoriesAndroid() {
        fun c(cat: Int) = RabbitLines.appComments("App", "com.example", cat, false)
        assertTrue("Vas-y, bats le record !" in c(ApplicationInfo.CATEGORY_GAME))
        assertTrue("On danse ? ♫" in c(ApplicationInfo.CATEGORY_AUDIO))
        assertTrue("Chut, ça commence !" in c(ApplicationInfo.CATEGORY_VIDEO))
        assertTrue("Montre-moi tes photos !" in c(ApplicationInfo.CATEGORY_IMAGE))
        assertTrue("Dis bonjour de ma part sur App !" in c(ApplicationInfo.CATEGORY_SOCIAL))
        assertTrue("Des nouvelles des lapins ?" in c(ApplicationInfo.CATEGORY_NEWS))
        assertTrue("Je peux venir ?" in c(ApplicationInfo.CATEGORY_MAPS))
        assertTrue("Courage pour le travail 💪" in c(ApplicationInfo.CATEGORY_PRODUCTIVITY))
        assertTrue("Je visite App 🐾" in c(ApplicationInfo.CATEGORY_UNDEFINED))
    }

    @Test fun onPlatform_inclutLeLibelle() {
        assertTrue("Hop, sur Chrome !" in RabbitLines.onPlatform("Chrome"))
    }

    // --- PetNotifier (textes purs) ---

    @Test fun statusLine_tronqueLesJauges() {
        assertEquals("🥕12  💖99  ⚡0  🛁50", NotifierTexts.statusLine(12.9f, 99.99f, 0.4f, 50f))
    }

    @Test fun moodText_cacheEtSommeil() {
        assertEquals("se cache… trouve-le sur une autre page ! 🫣", NotifierTexts.moodText(Mood.SICK, false, hiding = true))
        assertEquals("dort", NotifierTexts.moodText(Mood.SLEEPY, sleeping = true, hiding = false))
        assertEquals("a sommeil", NotifierTexts.moodText(Mood.SLEEPY, sleeping = false, hiding = false))
    }

    @Test fun alertText_seulementPourLesBesoins() {
        assertEquals("Cookie a faim ! 🥕", NotifierTexts.alertText("Cookie", Mood.HUNGRY))
        assertEquals("Cookie s'ennuie de toi 🥺", NotifierTexts.alertText("Cookie", Mood.SAD))
        assertNull(NotifierTexts.alertText("Cookie", Mood.HAPPY))
        assertNull(NotifierTexts.alertText("Cookie", Mood.OK))
        assertNull(NotifierTexts.alertText("Cookie", Mood.SLEEPY))
    }

    // --- Palette : mêmes valeurs que les anciens Color.parseColor / Color.argb ---

    private fun parse(hex: String): Int {
        val v = hex.removePrefix("#").toLong(16)
        return (if (hex.length == 7) v or 0xFF000000L else v).toInt()
    }

    @Test fun palette_valeursIdentiquesAuxChainesDOrigine() {
        val expected = listOf(
            Palette.INK to "#3A2E39", Palette.OUTLINE to "#5B4A55", Palette.OUTLINE_DARK to "#1E181C",
            Palette.CREAM to "#F3E9DD", Palette.FUR to "#FFFBF5", Palette.FUR_SHADE to "#F1E6DC",
            Palette.PINK to "#F6AFC0", Palette.CHEEK to "#66F07893", Palette.SICK_CHEEK to "#669BC48A",
            Palette.NOSE to "#F07893", Palette.DIRT to "#9C7A5B", Palette.PATCH_LIGHT to "#7A5A48",
            Palette.HAT to "#4A3B45", Palette.YELLOW to "#FFD54A", Palette.HEART to "#FF5C8A",
            Palette.ZZZ to "#7A8CD6", Palette.NOTE to "#B05CE0", Palette.SPARKLE to "#7FD3FF",
            Palette.DROP to "#6EC6FF", Palette.STAR to "#FFC83D", Palette.DIRT_BUBBLE to "#8DA05A",
            Palette.CARROT to "#F28C28", Palette.LEAF to "#4CAF50", Palette.LETTUCE_DARK to "#5FAE4E",
            Palette.LETTUCE to "#8FD67E", Palette.LETTUCE_VEIN to "#C9EFC0", Palette.STRAWBERRY to "#E5484D",
            Palette.SEED to "#FFE08A", Palette.POOP to "#8B5E3C", Palette.BALL to "#FF6F61",
            Palette.BALL_STRIPE to "#FFD166", Palette.WOOD to "#B07A4F", Palette.PLUSH_LIGHT to "#E8C39E",
            Palette.WOOD_DARK to "#7A5236", Palette.GOLD to "#E9B949", Palette.CHEST_GLOW to "#FFE9A8",
            Palette.BATH_FEET to "#C9A227", Palette.BATH_RIM to "#E8F4FB", Palette.BATH_BUBBLE to "#8FD3F4",
            Palette.METAL to "#B8B4BE", Palette.VET_GREEN to "#3DBE7A", Palette.VET_TABLE to "#9FD8F2",
            Palette.STETHOSCOPE to "#6E7B8B", Palette.CAGE_BASE to "#8C5F3F", Palette.CAGE_BARS to "#7A7480",
            Palette.BAR_BG to "#EFE7E1", Palette.BAR_LOW to "#E5484D",
        )
        for ((c, hex) in expected) assertEquals(hex, parse(hex), c)
        assertEquals(parse("#3A2E39"), Palette.INK)
        assertEquals(parse("#E5484D"), Palette.BAR_LOW)
    }

    @Test fun palette_transparences() {
        fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
        assertEquals(argb(45, 0, 0, 0), Palette.SHADOW)
        assertEquals(argb(60, 255, 255, 255), Palette.LENS)
        assertEquals(argb(110, 255, 255, 255), Palette.BALLOON_SHINE)
        assertEquals(argb(90, 255, 255, 255), Palette.FRISBEE_SHINE)
    }
}
