package com.tamacookie

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenWakeTest {

    @Test fun eveilleAuDemarrage() {
        val w = ScreenWake()
        assertTrue(w.awake)
        assertFalse(w.screenOn(locked = false))   // déjà réveillé : rien à relancer
        assertFalse(w.touched())
    }

    @Test fun ecranRallumeDeverrouilleReveille() {
        val w = ScreenWake()
        w.screenOff()
        assertFalse(w.awake)
        assertTrue(w.screenOn(locked = false))
        assertTrue(w.awake)
    }

    @Test fun verrouilleAttendUserPresent() {
        val w = ScreenWake()
        w.screenOff()
        assertFalse(w.screenOn(locked = true))
        assertTrue(w.waitingUnlock)
        assertTrue(w.userPresent())
        assertTrue(w.awake)
        assertFalse(w.waitingUnlock)
        assertFalse(w.userPresent())   // un seul réveil
    }

    /** Le bug de la tablette : USER_PRESENT n'arrive jamais, la relecture du verrou réveille quand même. */
    @Test fun sansUserPresentLaRelectureReveille() {
        val w = ScreenWake()
        w.screenOff()
        w.screenOn(locked = true)
        assertFalse(w.poll(locked = true))
        assertFalse(w.awake)
        assertTrue(w.poll(locked = false))
        assertTrue(w.awake)
        assertFalse(w.poll(locked = false))
    }

    /** Toucher le lapin prouve que l'écran est utilisable (le glisser ne reste plus figé). */
    @Test fun unToucherReveille() {
        val w = ScreenWake()
        w.screenOff()
        w.screenOn(locked = true)
        assertTrue(w.touched())
        assertTrue(w.awake)
        assertFalse(w.waitingUnlock)
    }

    @Test fun ecranEteintPendantLAttenteArreteLaRelecture() {
        val w = ScreenWake()
        w.screenOff()
        w.screenOn(locked = true)
        w.screenOff()
        assertFalse(w.waitingUnlock)
        assertFalse(w.poll(locked = false))   // écran éteint : pas de réveil par la relecture
        assertFalse(w.awake)
    }

    @Test fun relectureSansAttenteNeFaitRien() {
        val w = ScreenWake()
        w.screenOff()
        assertFalse(w.poll(locked = false))   // pas d'écran allumé signalé
        assertFalse(w.awake)
    }
}
