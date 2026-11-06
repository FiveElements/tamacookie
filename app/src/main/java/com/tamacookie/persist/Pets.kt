package com.tamacookie.persist

import android.content.Context
import com.tamacookie.AndroidClock
import com.tamacookie.Prefs

/** Accès au [PetRepository] de l'application (un par processus ; migrations jouées à la première ouverture). */
object Pets {
    @Volatile private var repo: PetRepository? = null

    fun repository(ctx: Context): PetRepository = repo ?: synchronized(this) {
        repo ?: PetRepository.open(
            ctx.applicationContext.getSharedPreferences(Prefs.PETS_FILE, Context.MODE_PRIVATE),
            Prefs.of(ctx.applicationContext),
            AndroidClock,
        ).also { repo = it }
    }
}
