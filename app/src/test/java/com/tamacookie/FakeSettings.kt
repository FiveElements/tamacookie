package com.tamacookie

/** Réglages en mémoire (port [SettingsStore]) pour les tests JVM. */
class FakeSettings : SettingsStore {
    val map = HashMap<String, Any?>()

    override fun getInt(key: String, default: Int) = map[key] as Int? ?: default
    override fun getFloat(key: String, default: Float) = map[key] as Float? ?: default
    override fun getString(key: String, default: String?) = map[key] as String? ?: default
    override fun getBoolean(key: String, default: Boolean) = map[key] as Boolean? ?: default

    override fun edit(block: SettingsStore.Editor.() -> Unit) {
        object : SettingsStore.Editor {
            override fun putInt(key: String, value: Int) { map[key] = value }
            override fun putFloat(key: String, value: Float) { map[key] = value }
            override fun putString(key: String, value: String?) { map[key] = value }
            override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        }.block()
    }
}
