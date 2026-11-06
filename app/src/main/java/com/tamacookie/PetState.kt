package com.tamacookie

import android.content.Context
import kotlin.math.max
import kotlin.math.min

/**
 * État "tamagotchi" du lapin.
 * Toutes les jauges vont de 0 (mauvais) à 100 (parfait).
 * L'état est sauvegardé et l'évolution hors-ligne est simulée au rechargement.
 */
class PetState(private val context: Context) {

    var name = "Cookie"
    var satiety = 80f       // 100 = repu, 0 = affamé
    var happiness = 80f
    var energy = 90f
    var cleanliness = 90f
    var health = 100f
    var sleeping = false
    var poops = 0
    var bornAt = System.currentTimeMillis()
    var lastUpdate = System.currentTimeMillis()
    var visitedApps = 0
    var lastPetAt = 0L
    var caged = false       // enfermé dans la cage : son bonheur chute 3x plus vite

    // Personnalisation
    var adopted = false
    var look = Look()
    var personality = Personality.CUDDLY
    var favoriteToy = Toy.BALL

    val ageDays: Int get() = ((System.currentTimeMillis() - bornAt) / 86_400_000L).toInt()

    val isSick: Boolean get() = health < 35f

    /** Humeur globale affichée par le lapin. */
    val mood: Mood
        get() = when {
            sleeping -> Mood.SLEEPY
            isSick -> Mood.SICK
            satiety < 20f -> Mood.HUNGRY
            energy < 15f -> Mood.SLEEPY
            cleanliness < 25f || poops >= 3 -> Mood.DIRTY
            happiness < 30f -> Mood.SAD
            happiness > 75f && satiety > 50f -> Mood.HAPPY
            else -> Mood.OK
        }

    enum class Mood { HAPPY, OK, HUNGRY, SLEEPY, DIRTY, SAD, SICK }

    private fun p(k: Personality, yes: Float, no: Float = 1f) = if (personality == k) yes else no

    /** Fait évoluer les jauges de [hours] heures. Retourne le nombre de crottes apparues. */
    fun tick(hours: Float): Int {
        if (hours <= 0f) return 0
        val sleepFactor = if (sleeping) 0.4f else 1f
        satiety -= 7f * hours * sleepFactor * p(Personality.GLUTTON, 1.35f)
        happiness -= (if (poops > 0) 6f else 4f) * hours * sleepFactor *
            p(Personality.PLAYFUL, 1.25f) * p(Personality.CUDDLY, 1.2f) * (if (caged) 3f else 1f)
        cleanliness -= (2.5f + poops * 2f) * hours
        energy += if (sleeping) 25f * hours else -6f * hours * p(Personality.LAZY, 1.3f)

        // Santé : baisse si négligé, remonte doucement si tout va bien
        val neglect = (if (satiety < 10f) 1 else 0) + (if (cleanliness < 10f) 1 else 0) +
            (if (happiness < 10f) 1 else 0)
        health += if (neglect > 0) -5f * neglect * hours else 3f * hours

        // Les crottes : environ une toutes les 3h quand on a mangé
        var newPoops = 0
        if (!sleeping && satiety > 30f) {
            poopAccumulator += hours / 3f
            while (poopAccumulator >= 1f && poops < 5) {
                poopAccumulator -= 1f
                poops++
                newPoops++
            }
        }

        if (sleeping && energy >= 100f) sleeping = false
        clampAll()
        return newPoops
    }

    private var poopAccumulator = 0f

    fun feed(food: Food = Food.CARROT): Boolean {
        if (satiety > 95f && food != Food.STRAWBERRY) return false
        satiety += food.satiety
        happiness += food.happiness * p(Personality.GLUTTON, 2f)
        health += food.health
        cleanliness -= 3f
        clampAll(); save(); return true
    }

    fun pet() {
        val now = System.currentTimeMillis()
        // Les caresses répétées rapportent de moins en moins
        val gain = (if (now - lastPetAt < 4000) 1.5f else 6f) * p(Personality.CUDDLY, 1.6f)
        lastPetAt = now
        happiness += gain
        clampAll()
    }

    /** Début d'une partie : retourne false s'il est trop fatigué. */
    fun startPlay(): Boolean {
        if (energy < 15f || sleeping) return false
        happiness += 6f
        energy -= 4f
        clampAll(); save(); return true
    }

    /** Une action pendant le jeu (balle frappée, frisbee attrapé…). */
    fun playHit(toy: Toy, amount: Float = 2f) {
        var g = amount * p(Personality.PLAYFUL, 1.5f)
        if (toy == favoriteToy) g *= 2f
        happiness += g
        energy -= 0.6f
        satiety -= 0.25f
        clampAll()
    }

    fun clean() {
        cleanliness = 100f
        happiness += 3f
        clampAll(); save()
    }

    fun removePoop() {
        if (poops > 0) poops--
        cleanliness += 8f
        happiness += 2f
        clampAll(); save()
    }

    fun heal() {
        health += 40f
        happiness -= 5f   // il n'aime pas les médicaments
        clampAll(); save()
    }

    fun visitApp() {
        visitedApps++
        happiness += p(Personality.CURIOUS, 3f, 1f)
        clampAll()
    }

    private fun clampAll() {
        satiety = satiety.clamp(); happiness = happiness.clamp(); energy = energy.clamp()
        cleanliness = cleanliness.clamp(); health = health.clamp()
    }

    private fun Float.clamp() = max(0f, min(100f, this))

    fun save() {
        lastUpdate = System.currentTimeMillis()
        prefs().edit()
            .putString("name", name)
            .putFloat("satiety", satiety).putFloat("happiness", happiness)
            .putFloat("energy", energy).putFloat("cleanliness", cleanliness)
            .putFloat("health", health).putBoolean("sleeping", sleeping)
            .putInt("poops", poops).putFloat("poopAcc", poopAccumulator)
            .putLong("bornAt", bornAt).putLong("lastUpdate", lastUpdate)
            .putInt("visitedApps", visitedApps)
            .putBoolean("caged", caged)
            .apply()
    }

    /** Sauvegarde uniquement l'apparence / le caractère (écran d'adoption). */
    fun saveLook() {
        prefs().edit()
            .putString("name", name)
            .putBoolean("adopted", adopted)
            .putInt("look_fur", look.fur).putInt("look_ears", look.ears).putInt("look_eyes", look.eyes)
            .putInt("look_pattern", look.pattern).putInt("look_acc", look.accessory)
            .putInt("look_accColor", look.accColor)
            .putString("personality", personality.name).putString("favoriteToy", favoriteToy.name)
            .apply()
    }

    private fun loadLook() {
        val p = prefs()
        adopted = p.getBoolean("adopted", false)
        look = Look(
            p.getInt("look_fur", 0), p.getInt("look_ears", 0), p.getInt("look_eyes", 0),
            p.getInt("look_pattern", 0), p.getInt("look_acc", 0), p.getInt("look_accColor", 0),
        )
        personality = runCatching { Personality.valueOf(p.getString("personality", "")!!) }.getOrDefault(Personality.CUDDLY)
        favoriteToy = runCatching { Toy.valueOf(p.getString("favoriteToy", "")!!) }.getOrDefault(Toy.BALL)
    }

    /**
     * Recharge seulement le nom, l'apparence et le caractère (après l'écran d'adoption ou un renommage),
     * sans écraser les jauges en mémoire (sommeil, crottes…) non encore sauvegardées.
     */
    fun reloadAppearance() {
        loadLook()
        name = prefs().getString("name", name) ?: name
    }

    /** Charge l'état et rattrape le temps écoulé depuis la dernière sauvegarde. */
    fun load(): PetState {
        val p = prefs()
        loadLook()
        if (!p.contains("bornAt")) { save(); return this }
        name = p.getString("name", name) ?: name
        satiety = p.getFloat("satiety", satiety); happiness = p.getFloat("happiness", happiness)
        energy = p.getFloat("energy", energy); cleanliness = p.getFloat("cleanliness", cleanliness)
        health = p.getFloat("health", health); sleeping = p.getBoolean("sleeping", false)
        poops = p.getInt("poops", 0); poopAccumulator = p.getFloat("poopAcc", 0f)
        bornAt = p.getLong("bornAt", bornAt); lastUpdate = p.getLong("lastUpdate", lastUpdate)
        visitedApps = p.getInt("visitedApps", 0)
        caged = p.getBoolean("caged", false)
        val elapsedH = (System.currentTimeMillis() - lastUpdate) / 3_600_000f
        tick(min(elapsedH, 48f))
        save()
        return this
    }

    /** Nouveau lapin : jauges remises à zéro (l'apparence est sauvegardée à part). */
    fun reset() {
        val enabled = prefs().getBoolean(Prefs.ENABLED, false)
        prefs().edit().clear().putBoolean(Prefs.ENABLED, enabled).apply()
        satiety = 80f; happiness = 80f; energy = 90f; cleanliness = 90f; health = 100f
        sleeping = false; poops = 0; poopAccumulator = 0f; visitedApps = 0; caged = false
        bornAt = System.currentTimeMillis()
        save()
        saveLook()
    }

    private fun prefs() = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
}
