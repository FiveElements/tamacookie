package com.tamacookie

import com.tamacookie.feature.station.StationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Actions TalkBack personnalisées (étape 8d) : un id de ressource par station, sans doublon. */
class A11yActionIdsTest {
    @Test fun `chaque station a son action, et on retrouve la station depuis l'action`() {
        assertTrue(
            "ajouter des access_action_station_N dans res/values/ids.xml",
            A11yActionIds.STATIONS.size >= StationKind.entries.size,
        )
        for (k in StationKind.entries) assertEquals(k, A11yActionIds.kindOf(A11yActionIds.station(k)))
    }

    @Test fun `les ids sont distincts, et distincts des autres actions`() {
        val all = A11yActionIds.STATIONS.toList() +
            listOf(R.id.access_action_menu, R.id.access_action_leave, R.id.access_action_throw)
        assertEquals(all.size, all.toSet().size)
        assertEquals(null, A11yActionIds.kindOf(R.id.access_action_menu))
    }
}
