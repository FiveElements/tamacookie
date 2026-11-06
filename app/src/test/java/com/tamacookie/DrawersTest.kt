package com.tamacookie

import com.tamacookie.feature.station.StationDrawers
import com.tamacookie.feature.station.StationKind
import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.Poop
import com.tamacookie.feature.toy.PropDrawers
import com.tamacookie.feature.toy.Toy
import com.tamacookie.species.Species
import com.tamacookie.species.SpeciesRenderers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exhaustivité du dessin (côté Android) : chaque station, objet et espèce du cœur a le sien, et un seul. */
class DrawersTest {
    @Test
    fun `chaque station a son dessin`() {
        val drawers = StationKind.entries.map(StationDrawers::of)
        assertEquals(StationKind.entries.size, drawers.toSet().size)
    }

    @Test
    fun `chaque jouet, nourriture et la crotte ont leur dessin`() {
        val specs = Toy.entries.map { it.spec } + Food.entries.map { it.spec } + Poop
        val drawers = specs.map(PropDrawers::of)
        assertEquals(specs.size, drawers.toSet().size)
        Toy.entries.forEach { assertSame(PropDrawers.of(it), PropDrawers.of(it.spec)) }
        Food.entries.forEach { assertSame(PropDrawers.of(it), PropDrawers.of(it.spec)) }
    }

    @Test
    fun `chaque espece a son renderer`() {
        Species.ALL.forEach { assertTrue(it.id, SpeciesRenderers.has(it)) }
    }
}
