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
import kotlin.random.Random

/**
 * Création / personnalisation du lapin : nom, pelage, oreilles, yeux, motif,
 * accessoire et caractère. Aperçu animé en direct.
 */
@SuppressLint("SetTextI18n")
class AdoptActivity : Activity(), Choreographer.FrameCallback {

    companion object { const val EXTRA_NEW = "new" }

    private val d by lazy { resources.displayMetrics.density }
    private lateinit var preview: BunnyView
    private lateinit var nameEdit: EditText
    private val look = Look()
    private var personality = Personality.CUDDLY
    private var isNew = true
    private var lastNs = 0L
    private var t = 0f
    private val refreshers = ArrayList<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#FFF4E6")
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val pet = PetState(this).load()
        isNew = intent.getBooleanExtra(EXTRA_NEW, !pet.adopted)
        if (!isNew || pet.adopted) {
            look.fur = pet.look.fur; look.ears = pet.look.ears; look.eyes = pet.look.eyes
            look.pattern = pet.look.pattern; look.accessory = pet.look.accessory; look.accColor = pet.look.accColor
            personality = pet.personality
        }

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
            text = if (isNew) "Adopte ton lapin 🐰" else "Personnaliser 🐰"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(Color.parseColor("#3A2E39")); paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        })

        preview = BunnyView(this)
        preview.look = look
        preview.setOnClickListener { preview.emit(BunnyView.PType.HEART, 3) }
        root.addView(preview, LinearLayout.LayoutParams((200 * d).toInt(), (200 * d).toInt()))

        nameEdit = EditText(this).apply {
            setText(pet.name)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
            hint = "Nom du lapin"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }
        root.addView(nameEdit, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        section(root, "Pelage")
        root.addView(swatches(Options.furs.map { it.second }, { look.fur }) { look.fur = it })
        section(root, "Oreilles")
        root.addView(chips(Options.ears, { look.ears }) { look.ears = it })
        section(root, "Yeux")
        root.addView(swatches(Options.eyes.map { it.second }, { look.eyes }) { look.eyes = it })
        section(root, "Motif")
        root.addView(chips(Options.patterns, { look.pattern }) { look.pattern = it })
        section(root, "Accessoire")
        root.addView(chips(Options.accessories, { look.accessory }) { look.accessory = it })
        section(root, "Couleur de l'accessoire")
        root.addView(swatches(Options.accColors.map { it.second }, { look.accColor }) { look.accColor = it })

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
        preview.pose = if (cycle < 2.5f) BunnyView.Pose.WALK else BunnyView.Pose.IDLE
        preview.walkPhase = (t * 2.2f) % 1f
        if (cycle < 0.02f) preview.facing = -preview.facing
        preview.update(dt)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun refreshAll() {
        preview.look = look
        refreshers.forEach { it() }
    }

    private fun randomize() {
        val r = Random(System.nanoTime())
        look.fur = r.nextInt(Options.furs.size); look.ears = r.nextInt(Options.ears.size)
        look.eyes = r.nextInt(Options.eyes.size); look.pattern = r.nextInt(Options.patterns.size)
        look.accessory = r.nextInt(Options.accessories.size); look.accColor = r.nextInt(Options.accColors.size)
        if (isNew) personality = Personality.values()[r.nextInt(Personality.values().size)]
        preview.emit(BunnyView.PType.SPARKLE, 6)
        refreshAll()
    }

    private fun validate() {
        val name = nameEdit.text.toString().trim().take(16).ifEmpty { "Cookie" }
        val app = applicationContext
        val chosenLook = look.copy()
        val chosenPersonality = personality
        val newPet = isNew
        val save = {
            val pet = PetState(app).load()
            if (newPet) {
                pet.reset()
                pet.personality = chosenPersonality
                pet.favoriteToy = Toy.values()[Random.nextInt(Toy.values().size)]
            }
            pet.name = name
            pet.look = chosenLook
            pet.adopted = true
            pet.saveLook()
            pet.save()
        }
        if (BunnyService.running && newPet) {
            // On arrête d'abord l'ancien lapin (il sauvegarde en partant), puis on enregistre le nouveau
            BunnyService.stop(app)
            android.os.Handler(mainLooper).postDelayed({ save(); BunnyService.start(app) }, 600)
        } else {
            save()
            if (BunnyService.running) BunnyService.send(app, BunnyService.ACTION_RELOAD)
        }
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

    private fun chips(labels: List<String>, get: () -> Int, set: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        labels.forEachIndexed { i, label ->
            val c = TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.parseColor("#3A2E39"))
                val p = (10 * d).toInt(); setPadding(p + p / 2, p, p + p / 2, p)
                setOnClickListener { set(i); refreshAll() }
            }
            refreshers += { c.background = chipBg(get() == i) }
            row.addView(c, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = (8 * d).toInt() })
        }
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun swatches(colors: List<String>, get: () -> Int, set: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        colors.forEachIndexed { i, col ->
            val v = View(this).apply { setOnClickListener { set(i); refreshAll() } }
            refreshers += {
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(col))
                    val sel = get() == i
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
