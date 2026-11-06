package com.tamacookie

import com.tamacookie.species.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Étape 8c : aucun texte du cœur, hors de `species/rabbit/`, ne nomme l'espèce ou sa nourriture en dur
 * (« lapin », « carotte », 🐰). Une réplique qui en parle va dans [com.tamacookie.species.SpeciesLines] ; un
 * texte d'interface prend [Species.label] / [Species.emoji] ou le nom de l'animal.
 *
 * Le test lit les **sources** (`src/main/kotlin`) et n'examine que les littéraux de chaîne : les commentaires
 * et les identifiants (`Food.CARROT`, `CarrotDispute`) ne comptent pas.
 */
class NoSpeciesWordsTest {

    private val forbidden = Regex("lapin|carott|🐰", RegexOption.IGNORE_CASE)

    /**
     * Exceptions justifiées : (fichier relatif à `src/main/kotlin/com/tamacookie/`, littéral exact).
     * La carotte est aussi un **objet** du jeu (une nourriture du coffre, comme la fraise ou la salade) : son nom
     * et ce qu'on dit en la voyant tomber appartiennent à cet objet, pas à l'espèce. Une autre espèce qui n'en
     * mange pas la refusera (`Species.accepts`) plutôt que de la renommer.
     */
    private val exceptions = setOf(
        "feature/toy/Toy.kt" to "Carotte",
        "feature/toy/Foods.kt" to "Une carotte !! 🥕",
    )

    private fun sourceRoot(): File {
        val candidates = listOf(File("src/main/kotlin/com/tamacookie"), File("core/src/main/kotlin/com/tamacookie"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("sources du cœur introuvables depuis ${File(".").absolutePath}")
    }

    @Test
    fun `aucun texte hors de l'espèce ne dit lapin ni carotte`() {
        val root = sourceRoot()
        val found = ArrayList<Pair<String, String>>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            if (rel.startsWith("species/rabbit/")) return@forEach
            for (lit in KotlinLiterals.strings(f.readText())) if (forbidden.containsMatchIn(lit)) found += rel to lit
        }
        val unexpected = found.filter { it !in exceptions }
        assertTrue("textes qui nomment l'espèce hors de species/rabbit/ : $unexpected", unexpected.isEmpty())
        // Une exception qui ne sert plus doit disparaître de la liste
        val stale = exceptions.filter { it !in found }
        assertTrue("exceptions périmées : $stale", stale.isEmpty())
    }

    @Test
    fun `les textes de l'espece qui nomment l'espece existent pour chaque espece`() {
        for (s in Species.ALL) {
            assertTrue(s.id, s.lines.SONGS.isNotEmpty())
            assertTrue(s.id, s.lines.GOING_TO_CHEST.isNotEmpty())
            assertTrue(s.id, s.lines.ADOPT_PROMPT.isNotBlank())
        }
    }

    @Test
    fun `l'extraction des litteraux ignore commentaires et identifiants`() {
        val src = """
            // un lapin en commentaire
            /* une carotte
               sur deux lignes */
            val CARROT = Food.CARROT
            val a = "Un ${'$'}{if (x) "lapin" else "chat"} !"
            val b = '"'
            val c = "\"guillemets\" et \\"
            val d = ""${'"'}brut "carotte" ""${'"'}
        """.trimIndent()
        val lits = KotlinLiterals.strings(src)
        val expected = listOf(
            "lapin", "chat", "Un \${if (x) \"lapin\" else \"chat\"} !",
            "\\\"guillemets\\\" et \\\\", "brut \"carotte\" ",
        )
        assertEquals(expected, lits)
    }
}

/**
 * Lexique minimal de Kotlin pour les tests : les littéraux de chaîne d'un fichier (contenu brut, échappements
 * non interprétés), sans les commentaires ni les caractères (`'"'`). Les chaînes imbriquées dans un gabarit
 * (`"${if (x) "a" else "b"}"`) sont rendues **avant** la chaîne qui les contient.
 */
object KotlinLiterals {
    fun strings(src: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < src.length) i = step(src, i, out)
        return out
    }

    /** Avance d'un élément de code à partir de [i] ; ajoute à [out] les chaînes rencontrées. */
    private fun step(src: String, i: Int, out: MutableList<String>): Int = when {
        src.startsWith("//", i) -> src.indexOf('\n', i).let { if (it < 0) src.length else it + 1 }
        src.startsWith("/*", i) -> src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
        src.startsWith("\"\"\"", i) -> string(src, i + 3, raw = true, out)
        src[i] == '"' -> string(src, i + 1, raw = false, out)
        src[i] == '\'' -> skipChar(src, i)
        else -> i + 1
    }

    private fun skipChar(src: String, i: Int): Int {
        var j = i + 1
        if (j < src.length && src[j] == '\\') j++
        j++
        return if (j < src.length && src[j] == '\'') j + 1 else i + 1
    }

    /** Chaîne commençant en [start] (après les guillemets d'ouverture) ; renvoie l'indice après la fermeture. */
    private fun string(src: String, start: Int, raw: Boolean, out: MutableList<String>): Int {
        var j = start
        var end = -1
        while (j < src.length && end < 0) {
            when {
                closes(src, j, raw) -> end = if (raw) rawEnd(src, j) else j
                !raw && src[j] == '\\' -> j += 2
                src.startsWith("\${", j) -> j = template(src, j + 2, out)
                else -> j++
            }
        }
        if (end < 0) { out += src.substring(start); return src.length }
        out += src.substring(start, end)
        return end + if (raw) 3 else 1
    }

    private fun closes(src: String, j: Int, raw: Boolean) = if (raw) src.startsWith("\"\"\"", j) else src[j] == '"'

    /** `"""a""""` : les guillemets en trop font partie de la chaîne ; début des trois derniers. */
    private fun rawEnd(src: String, j: Int): Int {
        var e = j
        while (e + 3 < src.length && src[e + 3] == '"') e++
        return e
    }

    /** Expression d'un gabarit `${…}` commençant en [start] ; renvoie l'indice après l'accolade fermante. */
    private fun template(src: String, start: Int, out: MutableList<String>): Int {
        var depth = 1
        var j = start
        while (j < src.length) {
            when (src[j]) {
                '{' -> { depth++; j++ }
                '}' -> { depth--; j++; if (depth == 0) return j }
                '"', '\'', '/' -> j = step(src, j, out)
                else -> j++
            }
        }
        return src.length
    }
}
