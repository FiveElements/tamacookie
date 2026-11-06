package com.tamacookie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.tamacookie.feature.menu.MenuEntries
import com.tamacookie.feature.menu.MenuEntry
import com.tamacookie.feature.station.StationKind
import com.tamacookie.feature.toy.Food
import com.tamacookie.feature.toy.Toy

/** Jauge horizontale arrondie. */
@SuppressLint("ViewConstructor")
class StatBar(context: Context, private val color: Int) : View(context) {
    var value = 0f
        set(v) { field = v; invalidate() }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Palette.BAR_BG }
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        r.set(0f, 0f, width.toFloat(), h); c.drawRoundRect(r, h / 2, h / 2, bg)
        fg.color = if (value < 25f) Palette.BAR_LOW else color
        r.set(0f, 0f, width * (value / 100f).coerceIn(0.04f, 1f), h); c.drawRoundRect(r, h / 2, h / 2, fg)
    }
}

/** Base commune des panneaux flottants (menu, fiche véto). */
@SuppressLint("ViewConstructor")
open class Panel(context: Context) : LinearLayout(context) {
    protected val d = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        val pad = (14 * d).toInt()
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#FFFDF9"))
            cornerRadius = 22 * d
            setStroke((2 * d).toInt(), Color.parseColor("#5B4A55"))
        }
        elevation = 8 * d
    }

    protected fun header(title: TextView, onClose: () -> Unit) {
        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        title.setTextColor(Color.parseColor("#3A2E39"))
        title.paint.isFakeBoldText = true
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(context).apply {
            text = "✕"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#7A6B75"))
            setPadding((10 * d).toInt(), 0, (4 * d).toInt(), 0)
            setOnClickListener { onClose() }
        })
        addView(header)
    }

    protected fun small(text: String = "") = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(Color.parseColor("#7A6B75"))
    }

    protected fun btnLp() = LayoutParams(0, (56 * d).toInt(), 1f).apply {
        val m = (3 * d).toInt(); setMargins(m, m, m, m)
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    protected fun button(emoji: String, label: String, click: () -> Unit) = TextView(context).apply {
        text = "$emoji\n$label"
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(Color.parseColor("#3A2E39"))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#FBEFE6")); cornerRadius = 14 * d
        }
        isClickable = true
        setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
        setOnClickListener { click() }
    }

    protected fun row() = LinearLayout(context).apply { orientation = HORIZONTAL }
    protected fun rowLp(top: Int = 0) = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = (top * d).toInt() }
}

/**
 * Menu rapide (double-touche / appui long sur le lapin) : un bouton par [MenuEntry] ([MenuEntries.ALL] :
 * dodo, objets, cache-cache, ranger), [MenuEntries.PER_ROW] par rangée.
 * Les soins se font dans les "widgets" flottants : baignoire, véto, cage.
 */
@SuppressLint("ViewConstructor", "SetTextI18n")
class PetMenu(
    context: Context,
    private val pet: Pet,
    entries: List<MenuEntry>,
    onEntry: (MenuEntry) -> Unit,
    onClose: () -> Unit,
) : Panel(context) {

    // Manger, jouer, se laver, voir sa santé : ça passe par les objets posés à l'écran
    // (coffre, baignoire, véto, cage), pas par ce menu.

    private val title = TextView(context)
    private val moodText = small()
    /** Boutons dont le texte dépend de l'état de l'animal (ex. Dodo / Réveil). */
    private val dynamic = ArrayList<Pair<MenuEntry, TextView>>()

    init {
        header(title) { onClose() }
        moodText.setPadding(0, (2 * d).toInt(), 0, (4 * d).toInt())
        addView(moodText)

        entries.chunked(MenuEntries.PER_ROW).forEachIndexed { i, chunk ->
            val r = row()
            for (e in chunk) {
                val b = button(e.emoji, e.label) { onEntry(e) }
                if (e.dynamic) dynamic.add(e to b)
                r.addView(b, btnLp())
            }
            addView(r, if (i == 0) rowLp(6) else rowLp())
        }
        addView(small("💡 Touche le coffre pour lui donner à manger ou un jouet. Glisse ${pet.name} dans la baignoire pour le laver, chez le véto pour voir sa santé.").apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); setPadding(0, (6 * d).toInt(), 0, 0)
        })
        refresh()
    }

    fun refresh() {
        title.text = "${pet.species.emoji} ${pet.name} · ${pet.personality.emoji} ${pet.personality.label}"
        moodText.text = moodLabel(pet)
        for ((e, b) in dynamic) b.text = e.text(pet.stats)
    }
}

/**
 * Panneau du bouton 🏠 : chaque objet (baignoire, véto, cage) s'installe
 * indépendamment sur la vue courante, ou se range.
 */
@SuppressLint("ViewConstructor", "SetTextI18n")
class StationsPanel(
    context: Context,
    /** 0 = sur cette vue, 1 = sur une autre vue, 2 = rangé ; + libellé de l'endroit */
    private val status: (StationKind) -> Pair<Int, String>,
    private val onToggle: (StationKind) -> Unit,
    private val onClose: () -> Unit,
) : Panel(context) {

    private val rows = LinkedHashMap<StationKind, Pair<TextView, TextView>>()

    init {
        header(TextView(context).apply { text = "🏠 Objets de cette page" }, onClose)
        addView(small("Chaque objet reste sur la page (ou l'app) où tu l'as mis.").apply {
            setPadding(0, (2 * d).toInt(), 0, (8 * d).toInt())
        })
        for (k in StationKind.values()) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val info = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(Color.parseColor("#3A2E39"))
            }
            row.addView(info, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            val btn = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTextColor(Color.parseColor("#3A2E39"))
                val p = (10 * d).toInt(); setPadding(p + p / 2, p, p + p / 2, p)
                setOnClickListener { onToggle(k); refresh() }
            }
            row.addView(btn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = (6 * d).toInt() })
            rows[k] = Pair(info, btn)
            info.tag = k.behavior.name
        }
        refresh()
    }

    fun refresh() {
        for ((k, pair) in rows) {
            val (info, btn) = pair
            val (st, where) = status(k)
            info.text = "${info.tag}\n" + when (st) { 0 -> "✅ sur cette page"; 1 -> "📍 $where"; else -> "📦 rangé" }
            btn.text = if (st == 0) "Ranger" else "Mettre ici"
            btn.background = GradientDrawable().apply {
                cornerRadius = 12 * d
                setColor(Color.parseColor(if (st == 0) "#FBEFE6" else "#FFE0E8"))
            }
        }
    }
}

/** Coffre ouvert : choisir à manger ou un jouet. */
@SuppressLint("ViewConstructor", "SetTextI18n")
class ChestPanel(
    context: Context,
    pet: Pet,
    private val onFood: (Food) -> Unit,
    private val onToy: (Toy) -> Unit,
    private val onClose: () -> Unit,
) : Panel(context) {
    init {
        header(TextView(context).apply { text = "🧰 Le coffre de ${pet.name}" }, onClose)
        addView(small("🍽️ À manger").apply { setPadding(0, (6 * d).toInt(), 0, 0) })
        val foods = row()
        for (f in Food.values()) foods.addView(button(f.emoji, f.label) { onFood(f) }, btnLp())
        addView(foods, rowLp(2))
        addView(small("🎁 Jouets").apply { setPadding(0, (6 * d).toInt(), 0, 0) })
        val toys = row()
        for (t in Toy.values()) toys.addView(button(t.emoji, t.label + if (t == pet.favoriteToy) "⭐" else "") { onToy(t) }, btnLp())
        addView(toys, rowLp(2))
    }
}

fun moodLabel(pet: Pet): String = when (pet.stats.mood) {
    Mood.HAPPY -> "Aux anges ✨"
    Mood.OK -> "Tranquille"
    Mood.HUNGRY -> "A très faim…"
    Mood.SLEEPY -> if (pet.stats.sleeping) "Fait dodo… chut" else "Tombe de sommeil"
    Mood.DIRTY -> "Aurait bien besoin d'un bain"
    Mood.SAD -> "Se sent seul…"
    Mood.SICK -> "Est malade 🤒"
}

/** Fiche du vétérinaire : toutes les statistiques + soin. */
@SuppressLint("ViewConstructor", "SetTextI18n")
class VetPanel(
    context: Context,
    private val pet: Pet,
    private val onHeal: () -> Unit,
    private val onClose: () -> Unit,
) : Panel(context) {

    private val title = TextView(context)
    private val info = small()
    private val diag = TextView(context)
    private val bars = ArrayList<StatBar>()
    private val values = ArrayList<TextView>()
    private val healBtn: TextView

    init {
        header(title, onClose)
        info.setPadding(0, (2 * d).toInt(), 0, (8 * d).toInt())
        addView(info)
        for ((label, color) in listOf(
            "🥕 Faim" to "#F28C28", "💖 Bonheur" to "#FF5C8A", "⚡ Énergie" to "#F5C542",
            "🛁 Propreté" to "#4FB3E8", "❤️‍🩹 Santé" to "#5CC489",
        )) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(context).apply {
                text = label; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setSingleLine()
                setTextColor(Color.parseColor("#3A2E39"))
            }, LayoutParams((112 * d).toInt(), LayoutParams.WRAP_CONTENT))
            val bar = StatBar(context, Color.parseColor(color))
            row.addView(bar, LayoutParams(0, (10 * d).toInt(), 1f))
            val v = small().apply { gravity = Gravity.END }
            row.addView(v, LayoutParams((36 * d).toInt(), LayoutParams.WRAP_CONTENT))
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = (6 * d).toInt() })
            bars.add(bar); values.add(v)
        }
        diag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        diag.setTextColor(Color.parseColor("#3A2E39"))
        diag.setPadding(0, (6 * d).toInt(), 0, (4 * d).toInt())
        addView(diag)
        val r = row()
        healBtn = button("💉", "Soigner") { onHeal() }
        r.addView(healBtn, btnLp())
        r.addView(button("🚪", "Sortir") { onClose() }, btnLp())
        addView(r, rowLp(4))
        refresh()
    }

    fun refresh() {
        title.text = "🩺 Fiche de ${pet.name}"
        info.text = "${pet.personality.emoji} ${pet.personality.label} · ${pet.identity.ageDays(System.currentTimeMillis())} jour(s) · " +
            "jouet préféré : ${pet.favoriteToy.emoji} · ${pet.stats.visitedApps} apps visitées"
        val v = pet.stats.let { listOf(it.satiety, it.happiness, it.energy, it.cleanliness, it.health) }
        v.forEachIndexed { i, x -> bars[i].value = x; values[i].text = "${x.toInt()}" }
        val issues = ArrayList<String>()
        if (pet.stats.isSick) issues += "malade : un vaccin s'impose 💉"
        else if (pet.stats.health < 70f) issues += "santé fragile"
        if (pet.stats.satiety < 25f) issues += "il a faim"
        if (pet.stats.cleanliness < 35f) issues += "un bain serait bien"
        if (pet.stats.energy < 20f) issues += "il est épuisé"
        if (pet.stats.happiness < 30f) issues += "il a besoin d'attention"
        if (pet.stats.poops > 0) issues += "${pet.stats.poops} crotte(s) à ramasser"
        diag.text = if (issues.isEmpty()) "✅ Diagnostic : en pleine forme !" else "📋 Diagnostic : " + issues.joinToString(", ")
        healBtn.alpha = if (pet.stats.health < GameRules.DEFAULT.healthyAbove) 1f else 0.45f
    }
}
