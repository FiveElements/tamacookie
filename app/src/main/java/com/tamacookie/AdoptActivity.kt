package com.tamacookie

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tamacookie.feature.toy.Toy
import com.tamacookie.persist.Adoption
import com.tamacookie.persist.Pets
import kotlin.random.Random

/**
 * Création / personnalisation du lapin : nom, pelage, oreilles, yeux, motif,
 * accessoire et caractère. Aperçu animé en direct.
 *
 * Trois modes : personnaliser le résident ([EXTRA_NEW] faux), adopter un nouveau lapin qui **remplace**
 * le résident ([EXTRA_NEW] vrai — avec, si un résident existe déjà, le choix de l'adopter plutôt comme
 * compagnon), ou adopter directement un **compagnon** ([EXTRA_COMPANION]).
 */
@SuppressLint("SetTextI18n")
class AdoptActivity : Activity(), Choreographer.FrameCallback {

    companion object {
        const val EXTRA_NEW = "new"
        /** Adopter un compagnon (le résident reste). */
        const val EXTRA_COMPANION = "companion"
        private val COMPANION_NAMES = listOf("Noisette", "Praline", "Caramel", "Biscuit", "Guimauve", "Pistache")
    }

    private val d by lazy { resources.displayMetrics.density }
    private lateinit var preview: PetView
    private lateinit var nameEdit: EditText
    private var look = Look()
    private var personality = Personality.CUDDLY
    private var isNew = true
    private var asCompanion = false
    private var lastNs = 0L
    private var t = 0f
    private var previewFacing = 1f
    private val refreshers = ArrayList<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Anti-tapjacking : aucune surimpression d'une autre app par-dessus cet écran
        window.setHideOverlayWindows(true)
        window.statusBarColor = Color.parseColor("#FFF4E6")
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        // Lecture seule (pas de rattrapage ni d'écriture) : le service ou PetCommands appliqueront le choix
        val repo = Pets.repository(this)
        val pet = repo.loadResident()
        val adopted = pet?.adopted == true
        asCompanion = adopted && intent.getBooleanExtra(EXTRA_COMPANION, false)
        isNew = asCompanion || intent.getBooleanExtra(EXTRA_NEW, !adopted)
        val taken = (listOfNotNull(pet) + repo.companions()).map { it.identity.name }.toSet()
        if (asCompanion) {
            randomLook(Random(System.nanoTime()))
        } else if (pet != null && (!isNew || adopted)) {
            look = pet.look
            personality = pet.traits.personality
        }
        val canAddCompanion = adopted && repo.ids().size < Tuning.MAX_PETS

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = (20 * d).toInt(); setPadding(p, p, p, p)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#FFF4E6"))
            addView(root)
        }

        root.addView(TextView(this).apply {
            text = when {
                asCompanion -> "Adopte un compagnon 🐰"
                isNew -> "Adopte ton lapin 🐰"
                else -> "Personnaliser 🐰"
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(Color.parseColor("#3A2E39")); paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        })

        preview = PetView(this)
        preview.setOnClickListener { preview.emit(PType.HEART, 3) }
        root.addView(preview, LinearLayout.LayoutParams((200 * d).toInt(), (200 * d).toInt()))

        nameEdit = EditText(this).apply {
            setText(if (asCompanion) COMPANION_NAMES.firstOrNull { it !in taken }.orEmpty() else pet?.identity?.name ?: Identity.DEFAULT_NAME)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
            hint = "Nom du lapin"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }
        root.addView(nameEdit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        section(root, "Pelage")
        root.addView(swatches(Fur.entries, { look.fur }) { look = look.copy(fur = it) })
        section(root, "Oreilles")
        root.addView(chips(Ears.entries, { look.ears }) { look = look.copy(ears = it) })
        section(root, "Yeux")
        root.addView(swatches(EyeColor.entries, { look.eyes }) { look = look.copy(eyes = it) })
        section(root, "Motif")
        root.addView(chips(Pattern.entries, { look.pattern }) { look = look.copy(pattern = it) })
        section(root, "Accessoire")
        root.addView(chips(Accessory.entries, { look.accessory }) { look = look.copy(accessory = it) })
        section(root, "Couleur de l'accessoire")
        root.addView(swatches(AccColor.entries, { look.accColor }) { look = look.copy(accColor = it) })

        section(root, "Caractère")
        if (isNew) {
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for (p in Personality.values()) {
                val card = TextView(this).apply {
                    text = "${p.emoji}  ${p.label} — ${p.desc}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(Color.parseColor("#3A2E39"))
                    val pd = (12 * d).toInt(); setPadding(pd, pd, pd, pd)
                    setOnClickListener { personality = p; refreshAll() }
                }
                refreshers += { card.background = chipBg(personality == p) }
                list.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = (6 * d).toInt() })
            }
            root.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        } else {
            root.addView(TextView(this).apply {
                text = "${personality.emoji}  ${personality.label} — ${personality.desc}\n(le caractère se choisit à l'adoption)"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.parseColor("#7A6B75"))
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(bigButton("🎲 Au hasard", "#FBE3D6", "#3A2E39") { randomize() },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = (8 * d).toInt() })
        btnRow.addView(bigButton(if (isNew) "Adopter ! 💕" else "Enregistrer ✨", "#F07893", "#FFFFFF") { validate() },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(btnRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = (22 * d).toInt() })
        // Nouveau lapin alors qu'un résident existe : il peut aussi le rejoindre au lieu de le remplacer
        if (isNew && !asCompanion && canAddCompanion) {
            root.addView(bigButton("…ou l'adopter comme compagnon 💞", "#E3F4E8", "#3A2E39") { asCompanion = true; validate() },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = (10 * d).toInt() })
            root.addView(TextView(this).apply {
                text = "« Adopter ! » remplace ${pet?.identity?.name ?: "ton lapin"} ; un compagnon vit avec lui."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(Color.parseColor("#7A6B75"))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = (6 * d).toInt() })
        }

        scroll.filterTouchesWhenObscured = true
        setContentView(scroll)
        refreshAll()
    }

    override fun onResume() { super.onResume(); Choreographer.getInstance().postFrameCallback(this) }
    override fun onPause() { super.onPause(); Choreographer.getInstance().removeFrameCallback(this); lastNs = 0L }

    override fun doFrame(frameTimeNanos: Long) {
        val dt = if (lastNs == 0L) 0.016f else ((frameTimeNanos - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = frameTimeNanos
        t += dt
        val cycle = t % 7f
        if (cycle < 0.02f) previewFacing = -previewFacing
        // L'aperçu affiche l'apparence en cours de choix ([Look] est immuable)
        preview.render(PetRenderState(
            pose = if (cycle < 2.5f) Pose.WALK else Pose.IDLE,
            facing = previewFacing,
            walkPhase = (t * 2.2f) % 1f,
            look = look,
        ), dt)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun refreshAll() {
        refreshers.forEach { it() }
    }

    private fun randomize() {
        val r = Random(System.nanoTime())
        randomLook(r)
        preview.emit(PType.SPARKLE, 6)
        refreshAll()
    }

    private fun randomLook(r: Random) {
        look = Look(
            fur = Fur.entries[r.nextInt(Fur.entries.size)], ears = Ears.entries[r.nextInt(Ears.entries.size)],
            eyes = EyeColor.entries[r.nextInt(EyeColor.entries.size)], pattern = Pattern.entries[r.nextInt(Pattern.entries.size)],
            accessory = Accessory.entries[r.nextInt(Accessory.entries.size)], accColor = AccColor.entries[r.nextInt(AccColor.entries.size)],
        )
        if (isNew) personality = Personality.values()[r.nextInt(Personality.values().size)]
    }

    private fun validate() {
        val name = Adoption.cleanName(nameEdit.text.toString()) ?: if (asCompanion) COMPANION_NAMES.random() else Identity.DEFAULT_NAME
        val a = Adoption(
            name = name,
            look = look,
            personality = personality,
            favoriteToy = Toy.entries[Random.nextInt(Toy.entries.size)],
            isNew = isNew,
        )
        // Une commande : le service (s'il tourne) applique sans redémarrer, sinon le repository est écrit
        if (asCompanion) PetCommands.adoptCompanion(applicationContext, a) else PetCommands.adopt(applicationContext, a)
        setResult(RESULT_OK)
        finish()
    }

    // --- UI helpers ---
    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.parseColor("#3A2E39")); paint.isFakeBoldText = true
            setPadding(0, (14 * d).toInt(), 0, (6 * d).toInt())
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun chipBg(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = 14 * d
        setColor(Color.parseColor(if (selected) "#FFE0E8" else "#FFFFFF"))
        setStroke((if (selected) 3 * d else 1 * d).toInt(), Color.parseColor(if (selected) "#F07893" else "#E3D6CC"))
    }

    private fun <E : LookOption> chips(options: List<E>, get: () -> E, set: (E) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEach { o ->
            val c = TextView(this).apply {
                text = o.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.parseColor("#3A2E39"))
                val p = (10 * d).toInt(); setPadding(p + p / 2, p, p + p / 2, p)
                setOnClickListener { set(o); refreshAll() }
            }
            refreshers += { c.background = chipBg(get() == o) }
            row.addView(c, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = (8 * d).toInt() })
        }
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun <E : ColorOption> swatches(options: List<E>, get: () -> E, set: (E) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEach { o ->
            val col = o.hex
            val v = View(this).apply { setOnClickListener { set(o); refreshAll() } }
            refreshers += {
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(col))
                    val sel = get() == o
                    setStroke((if (sel) 4 * d else 1.5f * d).toInt(), Color.parseColor(if (sel) "#F07893" else "#B9ACA3"))
                }
            }
            val s = (44 * d).toInt()
            row.addView(v, LinearLayout.LayoutParams(s, s).apply { rightMargin = (10 * d).toInt() })
        }
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun bigButton(label: String, bg: String, fg: String, click: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setTextColor(Color.parseColor(fg)); paint.isFakeBoldText = true
        val p = (15 * d).toInt(); setPadding(p, p, p, p)
        background = GradientDrawable().apply { cornerRadius = 18 * d; setColor(Color.parseColor(bg)) }
        setOnClickListener { click() }
    }
}
