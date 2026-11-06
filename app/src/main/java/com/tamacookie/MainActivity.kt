package com.tamacookie

import com.tamacookie.persist.Adoption
import com.tamacookie.persist.Pets
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

@SuppressLint("SetTextI18n")
class MainActivity : Activity(), Choreographer.FrameCallback {

    private val d by lazy { resources.displayMetrics.density }
    private lateinit var preview: BunnyView
    private lateinit var overlayBtn: TextView
    private lateinit var notifBtn: TextView
    private lateinit var usageBtn: TextView
    private lateinit var a11yBtn: TextView
    private lateinit var a11yHelp: TextView
    private lateinit var mainBtn: TextView
    private lateinit var nameEdit: EditText
    private var lastNs = 0L
    private var previewT = 0f
    /** Ce qu'affiche l'aperçu (il n'a pas de [Pet] : son état est fabriqué ici). */
    private var previewLook = Look()
    private var previewExpr = BunnyView.Expr.NORMAL
    private var previewFacing = 1f
    /** Le lapin vient d'être lancé : on passera en arrière-plan après la réponse à la demande de permission. */
    private var backgroundAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Anti-tapjacking : aucune surimpression d'une autre app par-dessus nos réglages
        window.setHideOverlayWindows(true)
        window.statusBarColor = Color.parseColor("#FFF4E6")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = (22 * d).toInt(); setPadding(p, p, p, p)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#FFF4E6"))
            isFillViewport = true
            addView(root)
        }

        root.addView(TextView(this).apply {
            text = "TamaCookie"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            setTextColor(Color.parseColor("#3A2E39")); paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Un petit lapin qui vit sur ton écran"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.parseColor("#7A6B75")); gravity = Gravity.CENTER
        })

        preview = BunnyView(this)
        preview.setOnClickListener {
            preview.emit(BunnyView.PType.HEART, 3)
            previewExpr = BunnyView.Expr.HAPPY
            preview.postDelayed({ previewExpr = BunnyView.Expr.NORMAL }, 1200)
        }
        root.addView(preview, LinearLayout.LayoutParams((170 * d).toInt(), (170 * d).toInt()).apply {
            topMargin = (8 * d).toInt()
        })

        // Nom
        val nameRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        nameEdit = EditText(this).apply {
            setText(residentName())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
            hint = "Nom du lapin"
        }
        nameRow.addView(nameEdit, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        nameRow.addView(smallButton("Renommer") { rename() })
        nameRow.addView(smallButton("✏️ Apparence") {
            startActivity(Intent(this, AdoptActivity::class.java).putExtra(AdoptActivity.EXTRA_NEW, false))
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = (8 * d).toInt() })
        root.addView(nameRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = (12 * d).toInt() })

        // Niveau de bêtises
        section(root, "Bêtises")
        val prefs = Prefs.of(this)
        val levels = listOf(
            "😇 Sage" to "Il réclame de l'attention, sans plus",
            "😜 Taquin" to "Frappe à l'écran, s'assoit parfois sur un bouton",
            "😈 Petit monstre" to "S'assoit sur tes boutons et ton clavier, commente tout",
        )
        val levelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val levelDesc = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setTextColor(Color.parseColor("#7A6B75"))
            setPadding((4 * d).toInt(), (4 * d).toInt(), 0, 0)
        }
        val levelChips = ArrayList<TextView>()
        fun paintLevels() {
            val cur = prefs.getInt(Prefs.MISCHIEF, 1)
            levelChips.forEachIndexed { i, c -> c.background = chipBg(i == cur) }
            levelDesc.text = levels[cur].second + if (cur > 0) " (bouton/clavier : nécessite l'accessibilité)" else ""
        }
        levels.forEachIndexed { i, (label, _) ->
            val c = TextView(this).apply {
                text = label; gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setTextColor(Color.parseColor("#3A2E39"))
                val p = (10 * d).toInt(); setPadding(p, p, p, p)
                setOnClickListener {
                    prefs.edit().putInt(Prefs.MISCHIEF, i).apply()
                    BunnyService.send(this@MainActivity, BunnyService.ACTION_RELOAD)
                    paintLevels()
                }
            }
            levelChips += c
            levelRow.addView(c, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { rightMargin = (6 * d).toInt() })
        }
        root.addView(levelRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(levelDesc, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        paintLevels()


        section(root, "Autorisations")
        overlayBtn = permButton(root) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        notifBtn = permButton(root) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
        usageBtn = permButton(root) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }

        a11yBtn = permButton(root) { confirmAccessibility() }
        a11yHelp = TextView(this).apply {
            text = "Paramètres › Accessibilité › « Applications installées » (Samsung) ou " +
                "« Applications téléchargées » (Pixel) › TamaCookie. " +
                "Si c'est grisé (« paramètre restreint ») : touche ici, puis ⋮ › « Autoriser les paramètres restreints », et réessaie."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#7A6B75"))
            setPadding((12 * d).toInt(), 0, (12 * d).toInt(), (6 * d).toInt())
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        root.addView(a11yHelp, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        mainBtn = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.WHITE); paint.isFakeBoldText = true
            val p = (16 * d).toInt(); setPadding(p, p, p, p)
            setOnClickListener { toggleBunny() }
        }
        root.addView(mainBtn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = (18 * d).toInt(); bottomMargin = (18 * d).toInt()
        })

        section(root, "Comment jouer")
        root.addView(TextView(this).apply {
            text = """
                👆  Touche-le pour le caresser
                👆👆  Double-touche ou appui long : menu (dodo, objets, cache-cache…)
                ✋  Fais-le glisser pour l'attraper… et lance-le !
                🎾  4 jouets : balle, ballon, frisbee, doudou (tu peux les lancer aussi)
                🏠  Bouton flottant 🏠 : installe la baignoire, le véto, la cage — chacun sur la page de ton choix
                🫣  Cache-cache : il se cache sur une autre page du bureau, va le chercher !
                🛁  Glisse-le dans la baignoire pour le laver
                🩺  Chez le véto : ses statistiques et les soins
                🔒  En cage, il est isolé… mais son bonheur chute 3x plus vite
                💩  Touche ses crottes pour nettoyer
                😈  Ignoré trop longtemps, il fait des bêtises pour attirer ton attention
                📱  Change d'application : il te suit et commente ce que tu fais
                🦘  Avec l'accessibilité : il saute sur les icônes et les boutons (lance-le dessus !)
                💤  Il dort la nuit ou quand il est fatigué
            """.trimIndent()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#3A2E39"))
            setLineSpacing(0f, 1.35f)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(smallButton("Adopter un nouveau lapin") { confirmReset() }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            topMargin = (24 * d).toInt()
        })

        scroll.filterTouchesWhenObscured = true
        setContentView(scroll)

        // Premier lancement : écran d'adoption
        if (Pets.repository(this).loadResident()?.adopted != true) {
            startActivity(Intent(this, AdoptActivity::class.java).putExtra(AdoptActivity.EXTRA_NEW, true))
        }
    }

    private fun chipBg(selected: Boolean) = GradientDrawable().apply {
        cornerRadius = 14 * d
        setColor(Color.parseColor(if (selected) "#FFE0E8" else "#FFFFFF"))
        setStroke((if (selected) 3 * d else 1 * d).toInt(), Color.parseColor(if (selected) "#F07893" else "#E3D6CC"))
    }

    override fun onResume() {
        super.onResume()
        // Lecture seule : si le service tourne, l'état en mémoire peut être un peu plus récent (jauges)
        val pet = Pets.repository(this).loadResident()
        previewLook = pet?.look ?: Look()
        nameEdit.setText(pet?.identity?.name ?: Identity.DEFAULT_NAME)
        refresh()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onPause() {
        super.onPause()
        Choreographer.getInstance().removeFrameCallback(this)
        lastNs = 0L
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
        // La boîte de dialogue a reçu sa réponse : on peut laisser le lapin vivre sur l'écran
        if (requestCode == REQ_NOTIF_THEN_BACKGROUND && backgroundAfterPermission) {
            backgroundAfterPermission = false
            if (BunnyService.running) moveTaskToBack(true)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        val dt = if (lastNs == 0L) 0.016f else ((frameTimeNanos - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = frameTimeNanos
        previewT += dt
        // petite marche sur place dans l'aperçu
        val cycle = previewT % 8f
        if (cycle < 0.02f) previewFacing = -previewFacing
        preview.render(BunnyRenderState(
            pose = if (cycle < 3f) BunnyView.Pose.WALK else BunnyView.Pose.IDLE,
            expr = previewExpr,
            facing = previewFacing,
            walkPhase = (previewT * 2.2f) % 1f,
            look = previewLook,
        ), dt)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun refresh() {
        val overlay = Settings.canDrawOverlays(this)
        val notif = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val usage = ForegroundAppWatcher.hasPermission(this)
        setPerm(overlayBtn, overlay, "Afficher par-dessus les autres apps", "obligatoire")
        setPerm(notifBtn, notif, "Notifications", "recommandé")
        setPerm(usageBtn, usage, "Accès aux données d'utilisation", "pour qu'il te suive d'app en app")
        val a11y = Platforms.isEnabled(this)
        setPerm(a11yBtn, a11y, "Sauter sur les icônes (accessibilité)", "optionnel")
        a11yHelp.visibility = if (a11y) View.GONE else View.VISIBLE

        val running = BunnyService.running
        mainBtn.text = if (running) "Ranger ${nameEdit.text} 🏠" else "Faire apparaître ${nameEdit.text} 🐰"
        mainBtn.background = GradientDrawable().apply {
            cornerRadius = 18 * d
            setColor(Color.parseColor(if (!overlay) "#BDB3B8" else if (running) "#7A6B75" else "#F07893"))
        }
    }

    private fun toggleBunny() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (BunnyService.running) BunnyService.stop(this) else {
            BunnyService.start(this)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Ne pas reculer maintenant : la boîte de dialogue partirait en arrière-plan.
                backgroundAfterPermission = true
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF_THEN_BACKGROUND)
            } else {
                moveTaskToBack(true)   // on laisse le lapin vivre sur l'écran
            }
        }
        mainBtn.postDelayed({ refresh() }, 300)
    }

    /** Divulgation et consentement explicites AVANT d'ouvrir les réglages d'accessibilité. */
    private fun confirmAccessibility() {
        AlertDialog.Builder(this)
            .setTitle("Autoriser l'accessibilité ?")
            .setMessage(
                "TamaCookie utilise l'API d'accessibilité d'Android uniquement pour le jeu :\n\n" +
                    "• Ce qui est lu : la position des icônes et boutons affichés, leur nom court " +
                    "(ex. « Chrome »), l'app au premier plan et le numéro de page du bureau ; " +
                    "le fait que tu touches un bouton, fasses défiler ou tapes au clavier.\n" +
                    "• Ce qui n'est JAMAIS lu : le texte que tu tapes, le contenu des champs de saisie " +
                    "et des mots de passe.\n" +
                    "• Pourquoi : pour que le lapin saute sur les icônes, te suive d'app en app, " +
                    "joue à cache-cache sur le bureau et fasse ses bêtises.\n" +
                    "• Rien n'est enregistré ni envoyé : tout reste en mémoire sur ton téléphone " +
                    "(l'app n'a pas accès à Internet).\n\n" +
                    "Tu peux le désactiver à tout moment dans Paramètres › Accessibilité."
            )
            .setPositiveButton("J'accepte") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Non merci", null)
            .show()
    }

    private fun residentName(): String = Pets.repository(this).loadResident()?.identity?.name ?: Identity.DEFAULT_NAME

    private fun rename() {
        val n = Adoption.cleanName(nameEdit.text.toString()) ?: return
        // Commande : le service applique et sauvegarde (plus de course avec sa sauvegarde périodique)
        PetCommands.rename(this, n)
        refresh()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Adopter un nouveau lapin ?")
            .setMessage("${nameEdit.text} sera remplacé par un nouveau lapin que tu vas créer.")
            .setPositiveButton("Oui") { _, _ ->
                startActivity(Intent(this, AdoptActivity::class.java).putExtra(AdoptActivity.EXTRA_NEW, true))
            }
            .setNegativeButton("Non", null)
            .show()
    }

    // --- petits helpers UI ---
    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#3A2E39")); paint.isFakeBoldText = true
            setPadding(0, (8 * d).toInt(), 0, (6 * d).toInt())
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun permButton(root: LinearLayout, onClick: () -> Unit): TextView {
        val tv = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#3A2E39"))
            val p = (12 * d).toInt(); setPadding(p, p, p, p)
            setOnClickListener { onClick() }
        }
        root.addView(tv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = (6 * d).toInt() })
        return tv
    }

    private fun setPerm(tv: TextView, ok: Boolean, label: String, why: String) {
        tv.text = if (ok) "✅  $label" else "⚠️  $label — $why (toucher pour autoriser)"
        tv.isEnabled = !ok
        tv.background = GradientDrawable().apply {
            cornerRadius = 12 * d
            setColor(Color.parseColor(if (ok) "#E3F4E8" else "#FFE3E3"))
        }
    }

    private companion object {
        const val REQ_NOTIF = 1
        const val REQ_NOTIF_THEN_BACKGROUND = 2
    }

    private fun smallButton(label: String, click: () -> Unit) = TextView(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(Color.parseColor("#3A2E39"))
        val p = (10 * d).toInt(); setPadding(p + p / 2, p, p + p / 2, p)
        background = GradientDrawable().apply { cornerRadius = 12 * d; setColor(Color.parseColor("#FBE3D6")) }
        setOnClickListener { click() }
    }
}
