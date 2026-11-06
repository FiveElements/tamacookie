package com.tamacookie

/**
 * Lecture de l'arbre d'accessibilité, **sans Android** : quels éléments deviennent des plateformes,
 * quelle page du bureau est affichée. L'arbre est lu au travers de [NodeReader] (adaptateur :
 * `AccessibilityNodeInfo` dans `ScreenSensorService` ; faux arbre en test).
 */
object ScreenScan {
    /** Nœuds visités au plus par scan. */
    const val MAX_NODES = 800
    /** Plateformes retenues au plus par scan. */
    const val MAX_PLATFORMS = 60
    /** Longueur maximale d'un nom court. */
    const val MAX_LABEL = 30

    /**
     * Numéro de page extrait d'une "page 2 sur 3" / "page 2 of 3" / "Pagina 2 van 3" /
     * "Strona 2 z 3"… (groupes 1 et 2), ou d'un simple "2 / 3" (groupes 3 et 4).
     * Utiliser [parsePage] plutôt que les groupes directement.
     */
    val PAGE_RE = Regex(
        "(?iu)(?:page|écran|ecran|seite|página|pagina|pagine|strona|stránka|stranka|sida|side|sivu|oldal|" +
            "страница|экран)\\s*(\\d+)\\s*(?:sur|of|/|de|do|von|di|van|z|ze|av|af|из|-)\\s*(\\d+)" +
            "|^\\s*(\\d+)\\s*/\\s*(\\d+)\\s*$"
    )

    /** (page courante, nombre de pages) d'une description d'indicateur de page, sinon null. */
    fun parsePage(desc: CharSequence?): Pair<Int, Int>? {
        if (desc.isNullOrEmpty()) return null
        val m = PAGE_RE.find(desc) ?: return null
        val g = m.groupValues
        val page = (g[1].ifEmpty { g[3] }).toIntOrNull() ?: return null
        val count = (g[2].ifEmpty { g[4] }).toIntOrNull() ?: return null
        return if (page in 1..count && count <= 50) page to count else null
    }

    /**
     * Bande verticale [minTop, maxTop] (px) où l'on cherche des plateformes : proportionnelle
     * à la hauteur pour rester non vide en paysage sur téléphone.
     */
    fun detectionBand(height: Int, density: Float): Pair<Float, Float> {
        val minTop = maxOf(120 * density, 0.22f * height)          // place pour la tête et le saut
        val maxTop = height - maxOf(90 * density, 0.12f * height)  // pas collé au sol
        return minTop to maxTop
    }

    /** Notre propre app et le volet de notifications ne changent pas la "vue". */
    fun ignoredPkg(pkg: String?, ownPkg: String): Boolean = pkg == ownPkg || pkg == "com.android.systemui"

    /** Taille et position acceptables pour une plateforme, sur un écran [width] × [height]. */
    class Geometry(val width: Int, val height: Int, density: Float) {
        val minTop: Float
        val maxTop: Float
        private val minW = (36 * density).toInt()
        private val maxW = (480 * density).toInt()
        private val minH = (36 * density).toInt()
        private val maxH = (260 * density).toInt()

        init {
            val (a, b) = detectionBand(height, density)
            minTop = a; maxTop = b
        }

        fun accepts(b: Box): Boolean {
            val w = b.width.toInt(); val h = b.height.toInt()
            return w in minW..maxW && h in minH..maxH &&
                b.top >= minTop && b.top <= maxTop && b.left >= 0f && b.right <= width
        }
    }

    /** Accès aux nœuds d'un arbre (sans allocation d'enveloppe par nœud). */
    interface NodeReader<N> {
        fun isVisible(n: N): Boolean
        fun isEditable(n: N): Boolean
        fun isPassword(n: N): Boolean
        fun isSelected(n: N): Boolean
        fun isClickable(n: N): Boolean
        fun description(n: N): CharSequence?
        fun text(n: N): CharSequence?
        fun bounds(n: N): Box
        fun childCount(n: N): Int
        fun child(n: N, i: Int): N?
    }

    /** Résultat du parcours : plateformes et (page, nombre de pages) du bureau si lue. */
    data class Walk(val platforms: List<Platform>, val page: Pair<Int, Int>?)

    /**
     * Parcours en profondeur (au plus [MAX_NODES] nœuds) : un élément de taille / position acceptable,
     * cliquable ou nommé, devient une plateforme (on ne descend pas dans ses enfants ; pas deux
     * plateformes qui se chevauchent au même niveau). L'indicateur de page : le point « sélectionné »
     * est prioritaire ; sinon (Pixel Launcher, [mayBeHome]) le premier indicateur lisible.
     */
    fun <N> walk(root: N, r: NodeReader<N>, g: Geometry, mayBeHome: Boolean): Walk {
        var page: Pair<Int, Int>? = null       // depuis le point "sélectionné" (prioritaire)
        var pageAny: Pair<Int, Int>? = null    // depuis un indicateur non sélectionné (Pixel)
        val out = ArrayList<Platform>()
        val stack = ArrayDeque<N>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val n = stack.removeLast()
            visited++
            if (!r.isVisible(n)) continue
            val hidden = r.isEditable(n) || r.isPassword(n)
            if (page == null && !hidden) {
                val cd = r.description(n)
                if (cd != null) {
                    val selected = r.isSelected(n)
                    if (selected || (pageAny == null && mayBeHome)) {
                        parsePage(cd)?.let { if (selected) page = it else pageAny = it }
                    }
                }
            }
            val b = r.bounds(n)
            if (g.accepts(b)) {
                // Nom court seulement pour les éléments non éditables (jamais le texte saisi)
                val label = if (!hidden) (r.description(n) ?: r.text(n))?.toString()?.take(MAX_LABEL) else null
                if (r.isClickable(n) || !label.isNullOrBlank()) {
                    // élément retenu : on ne descend pas dans ses enfants
                    if (out.size < MAX_PLATFORMS && out.none { it.box.intersects(b) && it.box.top == b.top }) {
                        out.add(Platform(b, label))
                    }
                    continue
                }
            }
            for (i in 0 until r.childCount(n)) r.child(n, i)?.let { stack.addLast(it) }
        }
        return Walk(out, page ?: pageAny)
    }
}
