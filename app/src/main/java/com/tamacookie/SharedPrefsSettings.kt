package com.tamacookie

import android.content.Context
import android.content.SharedPreferences

/** Adaptateur du port [SettingsStore] sur les SharedPreferences des réglages ([Prefs.FILE]). */
class SharedPrefsSettings(private val sp: SharedPreferences) : SettingsStore {
    override fun getInt(key: String, default: Int) = sp.getInt(key, default)
    override fun getFloat(key: String, default: Float) = sp.getFloat(key, default)
    override fun getString(key: String, default: String?): String? = sp.getString(key, default)
    override fun getBoolean(key: String, default: Boolean) = sp.getBoolean(key, default)

    override fun edit(block: SettingsStore.Editor.() -> Unit) {
        val e = sp.edit()
        object : SettingsStore.Editor {
            override fun putInt(key: String, value: Int) { e.putInt(key, value) }
            override fun putFloat(key: String, value: Float) { e.putFloat(key, value) }
            override fun putString(key: String, value: String?) { e.putString(key, value) }
            override fun putBoolean(key: String, value: Boolean) { e.putBoolean(key, value) }
        }.block()
        e.apply()
    }
}

/** Les SharedPreferences des réglages ([Prefs.FILE]) — côté Android de [Prefs] (les clés sont dans le cœur). */
fun Prefs.of(ctx: Context): SharedPreferences = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
