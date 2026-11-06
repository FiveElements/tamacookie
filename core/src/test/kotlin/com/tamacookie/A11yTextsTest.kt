package com.tamacookie

import com.tamacookie.feature.menu.MenuEntries
import com.tamacookie.feature.station.StationKind
import com.tamacookie.feature.toy.BallToy
import com.tamacookie.feature.toy.CarrotFood
import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.Poop
import com.tamacookie.feature.toy.Toy
import com.tamacookie.species.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Accessibilité (étape 8d) : textes du lecteur d'écran ([A11yTexts]), animations réduites, contrastes. */
class A11yTextsTest {
    private val nbsp = " "

    // --- Jauges ---

    @Test fun `une jauge se lit en pourcentage`() {
        assertEquals("Satiété 80$nbsp%", A11yTexts.gauge(A11yTexts.SATIETY, 80.7f))
        assertEquals("Santé 0$nbsp%", A11yTexts.gauge(A11yTexts.HEALTH, -3f))
        assertEquals("Bonheur 100$nbsp%", A11yTexts.gauge(A11yTexts.HAPPINESS, 140f))
    }

    @Test fun `les jauges suivent l'ordre de la notification, sante en dernier`() {
        val s = PetStats(satiety = 80f, happiness = 70f, energy = 60f, cleanliness = 50f, health = 90f)
        assertEquals(
            "Satiété 80$nbsp%, Bonheur 70$nbsp%, Énergie 60$nbsp%, Propreté 50$nbsp%, Santé 90$nbsp%",
            A11yTexts.gauges(s),
        )
        assertFalse(A11yTexts.gauges(s, health = false).contains(A11yTexts.HEALTH))
    }

    // --- L'animal ---

    @Test fun `apercu - nom, espece, humeur`() {
        assertEquals("Cookie, lapin, content", A11yTexts.preview("Cookie", Species.DEFAULT.label))
        assertEquals("Cookie, lapin, dort", A11yTexts.preview("Cookie", "Lapin", Mood.SLEEPY, sleeping = true))
    }

    @Test fun `description de l'animal - humeur au repos, activite sinon`() {
        assertEquals("Cookie, lapin, content", A11yTexts.pet("Cookie", "Lapin", Act.IDLE, Mood.OK, false))
        assertEquals("Cookie, lapin, aux anges", A11yTexts.pet("Cookie", "Lapin", Act.WALK, Mood.HAPPY, false))
        assertEquals("Cookie, lapin, joue", A11yTexts.pet("Cookie", "Lapin", Act.PLAY, Mood.HAPPY, false))
        // un besoin se dit même quand il est occupé
        assertEquals("Cookie, lapin, joue, a faim", A11yTexts.pet("Cookie", "Lapin", Act.PLAY, Mood.HUNGRY, false))
        // endormi dans une station (cage) : « dort » une seule fois
        assertEquals(
            "Cookie, lapin, dans Cage, dort",
            A11yTexts.pet("Cookie", "Lapin", Act.IN_STATION, Mood.SLEEPY, true, "🔒 Cage"),
        )
        assertEquals(
            "Cookie, lapin, dans Baignoire, a besoin d'un bain",
            A11yTexts.pet("Cookie", "Lapin", Act.IN_STATION, Mood.DIRTY, false, StationKind.BATH.behavior.name),
        )
    }

    @Test fun `description d'un animal du monde`() {
        val p = testPet()
        assertEquals("${p.name}, lapin, ${A11yTexts.mood(p.stats.mood, false)}", A11yTexts.pet(p))
        p.setAct(Act.SLEEP)
        assertEquals("${p.name}, lapin, dort", A11yTexts.pet(p))
    }

    @Test fun `chaque activite et chaque humeur a un texte`() {
        for (m in Mood.entries) {
            assertTrue(A11yTexts.mood(m, false).isNotBlank())
            assertTrue(A11yTexts.mood(m, true).isNotBlank())
        }
        for (a in Act.entries) {
            val t = A11yTexts.pet("Cookie", "Lapin", a, Mood.OK, false)
            assertTrue(t, t.startsWith("Cookie, lapin, ") && t.length > "Cookie, lapin, ".length)
        }
    }

    // --- Stations, objets, menu : chaque élément a une description lisible sans son emoji ---

    @Test fun `stations - nom sans emoji et occupants`() {
        assertEquals("Baignoire, vide", A11yTexts.station("🛁 Baignoire", emptyList(), true))
        assertEquals("Lit, Cookie et Noisette dedans", A11yTexts.station("🛏️ Lit", listOf("Cookie", "Noisette"), true))
        assertEquals("Coffre", A11yTexts.station(StationKind.CHEST.behavior.name, emptyList(), false))
        for (k in StationKind.entries) {
            val t = A11yTexts.station(k.behavior.name, emptyList(), k.behavior.acceptsPet)
            assertTrue(k.name, t.isNotBlank() && t.first().isLetter())
        }
    }

    @Test fun `objets - jouet, nourriture, crotte`() {
        assertEquals("${Toy.BALL.label}, jouet", A11yTexts.prop(Prop(1, BallToy, 10, 10)))
        assertEquals("${Food.CARROT.label}, à manger", A11yTexts.prop(Prop(2, CarrotFood, 10, 10)))
        assertEquals("Crotte", A11yTexts.prop(Prop(3, Poop, 10, 10)))
        assertEquals("Crotte de Cookie", A11yTexts.prop(Prop(3, Poop, 10, 10), owner = "Cookie"))
        for (t in Toy.entries) assertTrue(t.name, SpokenText.plain(t.label).isNotBlank())
        for (f in Food.entries) assertTrue(f.name, SpokenText.plain(f.label).isNotBlank())
    }

    @Test fun `chaque entree du menu a un libelle lisible, y compris son texte dynamique`() {
        for (e in MenuEntries.ALL) {
            assertTrue(e.label, SpokenText.plain(e.label).isNotBlank())
            for (st in listOf(PetStats(), PetStats(sleeping = true))) {
                val t = SpokenText.plain(e.text(st))
                assertTrue(e.label, t.isNotBlank() && t.first().isLetter())
            }
        }
    }

    @Test fun `plain retire emojis, symboles et retours a la ligne`() {
        assertEquals("Dodo", SpokenText.plain("💤\nDodo"))
        assertEquals("Réveil", SpokenText.plain("☀️\nRéveil"))
        assertEquals("Santé", SpokenText.plain("❤️‍🩹 Santé"))
        assertEquals("Apparence", SpokenText.plain("✏️ Apparence"))
        assertEquals("Thèmes de saison : activés", SpokenText.plain("🎄🎃🌸 Thèmes de saison : activés"))
        assertEquals("Fermer", SpokenText.plain("✕ Fermer"))
        assertEquals("« Adopter ! » remplace Cookie…", SpokenText.plain("« Adopter ! » remplace Cookie…"))
        assertEquals("", SpokenText.plain("🏠"))
    }

    @Test fun `plainLines garde les lignes d'une liste a puces`() {
        assertEquals(
            "Touche-le pour le caresser\nCache-cache : va le chercher !",
            SpokenText.plainLines("👆  Touche-le pour le caresser\n🫣  Cache-cache : va le chercher !\n🐰"),
        )
    }

    // --- Bulles ---

    @Test fun `seules les reponses a l'utilisateur sont annoncees`() {
        assertTrue(A11yTexts.announces(SpeechPriority.HIGH))
        assertFalse(A11yTexts.announces(SpeechPriority.NORMAL))
        assertFalse(A11yTexts.announces(SpeechPriority.LOW))
    }

    // --- Animations réduites ---

    @Test fun `animations reduites d'apres les reglages d'Android`() {
        assertFalse(ReducedMotion.isReduced(1f, 1f, 1f))
        assertFalse(ReducedMotion.isReduced(0.5f, 1f, 1f))
        assertTrue(ReducedMotion.isReduced(0f, 1f, 1f))
        assertTrue(ReducedMotion.isReduced(0f, 0f, 0f))   // « Supprimer les animations »
        assertTrue(ReducedMotion.isReduced(1f, 0f, 0f))
        assertFalse(ReducedMotion.isReduced(1f, 0f, 1f))
    }

    /** Types de particules vus pendant 10 s dans l'état [s]. */
    private fun types(s: PetRenderState, reduced: Boolean): Set<PType> {
        val a = AmbientAnimator(Random(7)).apply { reducedMotion = reduced }
        val seen = HashSet<PType>()
        repeat(300) { a.update(1f / 30f, s, 120f, 140f, 1f); a.particles.forEach { seen += it.type } }
        return seen
    }

    @Test fun `animations reduites - plus de particules decoratives, il vit toujours`() {
        assertEquals(emptySet<PType>(), types(PetRenderState(pose = Pose.EAT), reduced = true))
        assertEquals(emptySet<PType>(), types(PetRenderState(expr = Expr.DIZZY), reduced = true))
        assertEquals(emptySet<PType>(), types(PetRenderState(veryDirty = true), reduced = true))
        assertEquals(emptySet<PType>(), types(PetRenderState(season = Season.CHRISTMAS), reduced = true))
        // ce qui dit son état reste
        assertEquals(setOf(PType.ZZZ), types(PetRenderState(pose = Pose.SLEEP), reduced = true))
        assertEquals(setOf(PType.DROP), types(PetRenderState(expr = Expr.SAD), reduced = true))
        // sans réduction : comme avant
        assertEquals(setOf(PType.CRUMB), types(PetRenderState(pose = Pose.EAT), reduced = false))
    }

    @Test fun `animations reduites - clignement garde, oreille immobile, une particule par demande`() {
        val a = AmbientAnimator(Random(1)).apply { reducedMotion = true }
        var blinks = 0
        var twitches = 0
        repeat(900) {
            val was = a.blinking
            a.update(1f / 30f, PetRenderState(), 120f, 140f, 1f)
            if (a.blinking && !was) blinks++
            if (a.earTwitch > 0f) twitches++
        }
        assertTrue(blinks > 0)
        assertEquals(0, twitches)
        a.emit(PType.HEART, 4, 120f, 140f, 1f, 1f)
        assertEquals(1, a.particles.count { it.type == PType.HEART })
        a.reducedMotion = false
        a.emit(PType.SPARKLE, 6, 120f, 140f, 1f, 1f)
        assertEquals(6, a.particles.count { it.type == PType.SPARKLE })
    }

    @Test fun `sans reduction, l'animation ambiante est inchangee`() {
        fun trace(a: AmbientAnimator): List<Any> {
            val out = ArrayList<Any>()
            repeat(400) {
                a.update(1f / 30f, PetRenderState(pose = Pose.EAT, veryDirty = true), 120f, 140f, 1f)
                out.addAll(a.particles.map { it.type to it.x })
                out.add(a.earTwitch)
            }
            return out
        }
        val ref = trace(AmbientAnimator(Random(5)))
        assertEquals(ref, trace(AmbientAnimator(Random(5)).apply { reducedMotion = false }))
    }

    // --- Contrastes de l'interface (Palette.UI_*) ---

    @Test fun `contraste - valeurs de reference WCAG`() {
        assertEquals(21.0, Contrast.ratio(0xFF000000.toInt(), Palette.WHITE), 0.01)
        assertEquals(1.0, Contrast.ratio(Palette.UI_BG, Palette.UI_BG), 0.0001)
        // l'ancien rose des boutons ne passait pas avec le blanc
        assertTrue(Contrast.ratio(Palette.WHITE, Palette.UI_SELECTED_STROKE) < Contrast.AA)
        assertTrue(Contrast.ratio(Palette.WHITE, 0xFFBDB3B8.toInt()) < Contrast.AA)
    }

    @Test fun `chaque paire texte - fond de l'interface atteint 4,5 pour 1`() {
        val pairs = listOf(
            "texte / écran" to (Palette.INK to Palette.UI_BG),
            "texte / blanc" to (Palette.INK to Palette.WHITE),
            "texte / panneau" to (Palette.INK to Palette.UI_PANEL),
            "texte / petit bouton" to (Palette.INK to Palette.UI_CHIP),
            "texte / choix sélectionné" to (Palette.INK to Palette.UI_SELECTED),
            "texte / bouton de panneau" to (Palette.INK to Palette.UI_BUTTON),
            "texte / autorisation accordée" to (Palette.INK to Palette.UI_OK),
            "texte / autorisation manquante" to (Palette.INK to Palette.UI_WARN),
            "secondaire / écran" to (Palette.UI_MUTED to Palette.UI_BG),
            "secondaire / panneau" to (Palette.UI_MUTED to Palette.UI_PANEL),
            "secondaire / blanc" to (Palette.UI_MUTED to Palette.WHITE),
            "action" to (Palette.WHITE to Palette.UI_ACTION),
            "action sans permission" to (Palette.INK to Palette.UI_ACTION_OFF),
            "action ranger" to (Palette.WHITE to Palette.UI_ACTION_STOP),
            "bulle" to (Palette.INK to Palette.WHITE),
        )
        for ((name, p) in pairs) {
            val r = Contrast.ratio(p.first, p.second)
            assertTrue("$name : %.2f".format(java.util.Locale.ROOT, r), r >= Contrast.AA)
        }
    }
}
