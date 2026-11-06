package com.tamacookie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parcours de l'arbre d'accessibilité sur un faux arbre (densité 1, écran 1080 × 2000). */
class ScreenScanTest {

    class Node(
        val box: Box = Box(0f, 0f, 1080f, 2000f),
        val desc: String? = null,
        val text: String? = null,
        val clickable: Boolean = false,
        val selected: Boolean = false,
        val editable: Boolean = false,
        val password: Boolean = false,
        val visible: Boolean = true,
        val children: List<Node> = emptyList(),
    )

    object Reader : ScreenScan.NodeReader<Node> {
        var reads = 0
        override fun isVisible(n: Node) = n.visible
        override fun isEditable(n: Node) = n.editable
        override fun isPassword(n: Node) = n.password
        override fun isSelected(n: Node) = n.selected
        override fun isClickable(n: Node) = n.clickable
        override fun description(n: Node) = n.desc
        override fun text(n: Node) = n.text
        override fun bounds(n: Node) = n.box.also { reads++ }
        override fun childCount(n: Node) = n.children.size
        override fun child(n: Node, i: Int) = n.children[i]
    }

    private val g = ScreenScan.Geometry(1080, 2000, 1f)
    private fun icon(x: Float, y: Float, desc: String? = "App", clickable: Boolean = true) =
        Node(Box(x, y, x + 100f, y + 100f), desc = desc, clickable = clickable)
    private fun walk(vararg children: Node, home: Boolean = true) =
        ScreenScan.walk(Node(children = children.toList()), Reader, g, home)

    @Test
    fun `geometrie - taille et bande de detection`() {
        val (minTop, maxTop) = ScreenScan.detectionBand(2000, 1f)
        assertEquals(minTop, g.minTop, 0f)
        assertEquals(maxTop, g.maxTop, 0f)
        assertTrue(g.accepts(Box(100f, 600f, 200f, 700f)))
        assertFalse(g.accepts(Box(100f, 600f, 120f, 700f)))         // trop étroit
        assertFalse(g.accepts(Box(100f, 600f, 700f, 700f)))         // trop large
        assertFalse(g.accepts(Box(100f, 100f, 200f, 200f)))         // trop haut sur l'écran
        assertFalse(g.accepts(Box(100f, 1900f, 200f, 1990f)))       // collé au sol
        assertFalse(g.accepts(Box(-10f, 600f, 90f, 700f)))          // déborde à gauche
        assertFalse(g.accepts(Box(1000f, 600f, 1100f, 700f)))       // déborde à droite
    }

    @Test
    fun `icones cliquables ou nommees retenues avec leur nom`() {
        val w = walk(icon(100f, 600f, "Chrome"), icon(300f, 600f, null), icon(500f, 600f, "Photos", clickable = false),
            icon(700f, 600f, null, clickable = false))
        assertEquals(listOf("Photos", null, "Chrome"), w.platforms.map { it.label })   // DFS : dernier enfant d'abord
        assertEquals(Box(100f, 600f, 200f, 700f), w.platforms[2].box)
        assertNull(w.page)
    }

    @Test
    fun `jamais le texte d'un champ de saisie ni d'un mot de passe`() {
        val w = walk(Node(Box(100f, 600f, 300f, 700f), text = "mon secret", editable = true, clickable = true),
            Node(Box(400f, 600f, 600f, 700f), text = "hunter2", password = true, clickable = true))
        assertEquals(2, w.platforms.size)
        assertTrue(w.platforms.all { it.label == null })
    }

    @Test
    fun `nom court tronque et elements invisibles ignores`() {
        val w = walk(icon(100f, 600f, "x".repeat(50)), Node(Box(300f, 600f, 400f, 700f), desc = "Caché", clickable = true, visible = false))
        assertEquals(1, w.platforms.size)
        assertEquals(ScreenScan.MAX_LABEL, w.platforms[0].label!!.length)
    }

    @Test
    fun `un element retenu cache ses enfants, deux chevauchements au meme niveau donnent une plateforme`() {
        val parent = Node(Box(100f, 600f, 300f, 760f), desc = "Dossier", clickable = true,
            children = listOf(icon(110f, 610f, "Dedans")))
        val w = walk(parent, icon(150f, 600f, "Doublon"), icon(150f, 650f, "Décalé"))
        // DFS : le dernier enfant d'abord (ordre d'origine)
        assertEquals(listOf("Décalé", "Doublon"), w.platforms.map { it.label })
        assertFalse(w.platforms.any { it.label == "Dedans" || it.label == "Dossier" })
    }

    @Test
    fun `au plus 60 plateformes et 800 noeuds`() {
        val many = (0 until 100).map { i -> icon((i % 9) * 110f, 500f + (i / 9) * 101f, "i$i") }
        assertEquals(ScreenScan.MAX_PLATFORMS, walk(*many.toTypedArray()).platforms.size)
        val deep = (0 until 2000).map { Node(Box(0f, 0f, 10f, 10f)) }
        Reader.reads = 0
        walk(*deep.toTypedArray())
        assertEquals(ScreenScan.MAX_NODES, Reader.reads)
    }

    @Test
    fun `indicateur de page - le point selectionne est prioritaire`() {
        // (le non sélectionné est lu en premier : DFS depuis le dernier enfant)
        val w = walk(Node(desc = "Page 2 sur 3", selected = true), Node(desc = "Page 1 sur 3"))
        assertEquals(2 to 3, w.page)
        // Pixel : indicateur non sélectionné, seulement si c'est peut-être le bureau
        assertEquals(1 to 3, walk(Node(desc = "Page 1 of 3")).page)
        assertNull(walk(Node(desc = "Page 1 of 3"), home = false).page)
        assertEquals(2 to 4, walk(Node(desc = "Page 2 sur 4", selected = true), home = false).page)
        // un champ éditable n'est pas un indicateur
        assertNull(walk(Node(desc = "Page 2 sur 4", selected = true, editable = true)).page)
    }

    @Test
    fun `applications ignorees`() {
        assertTrue(ScreenScan.ignoredPkg("com.tamacookie", "com.tamacookie"))
        assertTrue(ScreenScan.ignoredPkg("com.android.systemui", "com.tamacookie"))
        assertFalse(ScreenScan.ignoredPkg("com.android.chrome", "com.tamacookie"))
    }

    @Test
    fun `rectangles - chevauchement strict comme Rect`() {
        val a = Box(0f, 0f, 10f, 10f)
        assertTrue(a.intersects(Box(5f, 5f, 15f, 15f)))
        assertFalse(a.intersects(Box(10f, 0f, 20f, 10f)))           // bord commun
        assertTrue(Box(0f, 0f, 0f, 10f).isEmpty)
        assertFalse(a.isEmpty)
        assertEquals(10f, a.width, 0f)
    }
}
