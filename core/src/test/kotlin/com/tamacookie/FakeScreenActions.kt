package com.tamacookie

/** Faux port [ScreenActions] : enregistre chaque appui demandé (sa cible) ; [accepts] = ce que répond le port. */
class FakeScreenActions(var accepts: Boolean = true) : ScreenActions {
    val pressed = ArrayList<Box>()

    override fun pressAdSkip(target: Box): Boolean {
        pressed += target
        return accepts
    }
}
