package com.tamacookie

/**
 * Port des **réglages** (fichier [Prefs.FILE] : niveau de bêtises, largeur des pages, placement des stations…)
 * tels que les lit le cœur. Adaptateur Android : `SharedPrefsSettings` (SharedPreferences) ; faux en test :
 * `FakeSettings`. Les clés sont dans [Prefs] (jamais renommées : ce sont les réglages des utilisateurs).
 * Les animaux, eux, passent par le port `PetStore`.
 */
interface SettingsStore {
    fun getInt(key: String, default: Int): Int
    fun getFloat(key: String, default: Float): Float
    fun getString(key: String, default: String?): String?
    fun getBoolean(key: String, default: Boolean): Boolean

    /** Écrit plusieurs valeurs d'un coup (côté Android : un seul `apply()`, asynchrone). */
    fun edit(block: Editor.() -> Unit)

    interface Editor {
        fun putInt(key: String, value: Int)
        fun putFloat(key: String, value: Float)
        fun putString(key: String, value: String?)
        fun putBoolean(key: String, value: Boolean)
    }
}
