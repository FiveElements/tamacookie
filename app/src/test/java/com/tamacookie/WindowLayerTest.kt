package com.tamacookie

import com.tamacookie.feature.station.StationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ordre des fenêtres ([WindowLayer]) : le faux WindowManager empile dans l'ordre d'ajout, comme le vrai ;
 * [WindowTable] doit garder l'interface au-dessus du décor, et l'animal sous les stations.
 */
class WindowLayerTest {
    private val host = FakeOverlayHost()
    private val be get() = host.backend
    private val pet = WindowId.Pet(PetId("a"))
    private val bubble = WindowId.Bubble(PetId("a"))
    private val chest = WindowId.Station(StationKind.CHEST)
    private val cage = WindowId.Station(StationKind.CAGE)
    private val bath = WindowId.Station(StationKind.BATH)
    private val ball = WindowId.Prop(1)
    private val panel = WindowId.Panel("stations")

    /** [a] est affichée sous [b] dans le (faux) WindowManager. */
    private fun below(a: WindowId, b: WindowId) = be.zOrder.indexOf(a).let { it >= 0 && it < be.zOrder.indexOf(b) }

    /** Aucune fenêtre n'en recouvre une d'une couche supérieure qu'elle chevauche (tailles inconnues = chevauchement). */
    private fun assertLayered() {
        val z = be.zOrder
        for (i in z.indices) for (j in i + 1 until z.size)
            assertFalse("${z[j]} recouvre ${z[i]} : $z", z[j].layer < z[i].layer)
        assertEquals(z, host.stacking)
    }

    @Test fun `couches de chaque fenetre`() {
        assertEquals(WindowLayer.PET, pet.layer)
        assertEquals(WindowLayer.STATION, chest.layer)
        assertEquals(WindowLayer.PROP, ball.layer)
        assertEquals(WindowLayer.DOCK, WindowId.Dock.layer)
        assertEquals(WindowLayer.BUBBLE, bubble.layer)
        assertEquals(WindowLayer.PANEL, panel.layer)
        assertEquals(WindowLayer.MENU, WindowId.Menu.layer)
        // l'interface au-dessus du décor, l'animal sous les stations (cage, baignoire)
        assertTrue(WindowLayer.PET < WindowLayer.STATION && WindowLayer.STATION < WindowLayer.PROP)
        assertTrue(WindowLayer.PROP < WindowLayer.DOCK && WindowLayer.DOCK < WindowLayer.BUBBLE)
        assertTrue(WindowLayer.BUBBLE < WindowLayer.PANEL && WindowLayer.PANEL < WindowLayer.MENU)
    }

    @Test fun `ajoutees dans l'ordre des couches, rien n'est rajoute`() {
        host.attach(pet, 0, 0); host.attach(chest, 0, 0); host.attach(ball, 0, 0)
        host.attach(WindowId.Dock, 0, 0); host.attach(bubble, 0, 0); host.attach(panel, 0, 0)
        host.attach(WindowId.Menu, 0, 0)
        assertEquals(0L, host.restacks)
        assertEquals(0, be.count("remove"))
        assertLayered()
    }

    @Test fun `le coffre qui reapparait ne recouvre pas le panneau des objets`() {
        // panneau 🏠 ouvert, puis changement de page : le coffre est retiré puis rajouté
        host.attach(pet, 0, 0); host.attach(chest, 500, 500); host.attach(WindowId.Dock, 900, 400)
        host.attach(panel, 480, 480)
        host.detach(chest)
        host.attach(chest, 500, 500)
        assertTrue(below(chest, panel))
        assertTrue(below(chest, WindowId.Dock))
        assertLayered()
        assertEquals(2L, host.restacks)                      // dock et panneau (tailles inconnues)
    }

    @Test fun `le lapin qui revient reste sous sa cage, et sous la bulle`() {
        host.attach(cage, 100, 100); host.attach(bubble, 100, 0)
        host.attach(pet, 120, 120)                           // il redevient visible (setBunnyAway(false))
        assertTrue(below(pet, cage))
        assertTrue(below(pet, bubble))
        assertLayered()
    }

    @Test fun `seules les fenetres qui se chevauchent sont rajoutees`() {
        be.sizes[panel] = 300 to 400
        be.sizes[WindowId.Dock] = 60 to 60
        be.sizes[chest] = 120 to 100
        host.attach(WindowId.Dock, 1000, 50)                 // loin
        host.attach(panel, 100, 100)
        host.attach(chest, 150, 150)                         // sous le panneau, loin du dock
        assertTrue(below(chest, panel))
        assertEquals(1L, host.restacks)                      // le panneau seulement
        assertTrue(below(WindowId.Dock, chest))              // le dock n'a pas bougé (aucun chevauchement)
        assertEquals(listOf("add", "add", "add", "remove", "add"), be.ops.map { it.op })
    }

    @Test fun `un deplacement qui fait chevaucher retablit l'ordre`() {
        be.sizes[WindowId.Dock] = 60 to 60
        be.sizes[bath] = 120 to 100
        host.attach(WindowId.Dock, 1000, 50)
        host.attach(bath, 100, 100)                          // ne chevauche pas : reste au-dessus du dock
        assertEquals(0L, host.restacks)
        host.move(bath, 980, 40)                             // on glisse la baignoire sur le dock
        host.flush()
        assertTrue(below(bath, WindowId.Dock))
        assertEquals(1L, host.restacks)
        assertLayered()
    }

    @Test fun `le dock rajoute garde sa position, deplacement en attente compris`() {
        host.attach(WindowId.Dock, 10, 20)
        host.move(WindowId.Dock, 30, 40)                     // pas encore appliqué
        host.attach(ball, 0, 0)                              // objet lancé : le dock repasse au-dessus
        assertEquals(10 to 20, host.shownAt(WindowId.Dock))
        host.flush()
        assertEquals(30 to 40, host.shownAt(WindowId.Dock))
        assertLayered()
    }

    @Test fun `menu et panneaux restent au-dessus de tout, dans tous les ordres d'ajout`() {
        val all = listOf(pet, cage, chest, ball, WindowId.Prop(2), WindowId.Dock, bubble, panel, WindowId.Menu)
        val rnd = java.util.Random(5)
        repeat(50) {
            val h = FakeOverlayHost()
            for (id in all.shuffled(rnd)) {
                h.attach(id, 0, 0)
                if (rnd.nextInt(4) == 0) { h.detach(id); h.attach(id, 0, 0) }
            }
            val z = h.backend.zOrder
            assertEquals(all.size, z.size)
            assertEquals(z.sortedBy { it.layer }.map { it.layer }, z.map { it.layer })
            assertEquals(z, h.stacking)
        }
    }

    @Test fun `retirer ou oublier une fenetre la sort de l'empilement`() {
        host.attach(pet, 0, 0); host.attach(chest, 0, 0); host.attach(panel, 0, 0)
        host.detach(chest)
        assertEquals(listOf(pet, panel), host.stacking)
        host.release(panel)
        assertEquals(listOf(pet), host.stacking)
        host.detachAll()
        assertTrue(host.stacking.isEmpty())
        assertTrue(be.zOrder.isEmpty())
    }
}
