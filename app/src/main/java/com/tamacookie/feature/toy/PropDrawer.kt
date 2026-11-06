package com.tamacookie.feature.toy

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.tamacookie.Palette
import com.tamacookie.PropView

/** Pinceaux réutilisés par les dessins des objets (aucune allocation pendant le rendu). */
class PropPen(val d: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * d
        color = Palette.OUTLINE
    }
    val path = Path()
    val r = RectF()
}

/**
 * Dessin d'un objet posé (Android) : son [PropSpec] (taille, jeu, répliques) est dans le cœur, le dessin
 * ici, un `object …Drawer` par objet. Il dessine l'état du modèle ([com.tamacookie.Prop.spin],
 * [com.tamacookie.Prop.tint], lus via `v.spin` / `v.tint`) avec les pinceaux de `v.pen`.
 */
interface PropDrawer {
    fun draw(c: Canvas, v: PropView, w: Float, h: Float)
}

/** Le dessin de chaque objet (`when` exhaustifs sur [Toy] et [Food] : un nouvel objet ne compile pas sans son dessin). */
object PropDrawers {
    fun of(spec: PropSpec): PropDrawer {
        spec.toy?.let { return of(it) }
        spec.food?.let { return of(it) }
        check(spec === Poop) { "objet sans dessin : $spec" }
        return PoopDrawer
    }

    fun of(t: Toy): PropDrawer = when (t) {
        Toy.BALL -> BallToyDrawer
        Toy.BALLOON -> BalloonToyDrawer
        Toy.FRISBEE -> FrisbeeToyDrawer
        Toy.PLUSH -> PlushToyDrawer
        Toy.ROPE -> RopeToyDrawer
        Toy.YARN -> YarnToyDrawer
        Toy.TUNNEL -> TunnelToyDrawer
    }

    fun of(f: Food): PropDrawer = when (f) {
        Food.CARROT -> CarrotFoodDrawer
        Food.LETTUCE -> LettuceFoodDrawer
        Food.STRAWBERRY -> StrawberryFoodDrawer
        Food.APPLE -> AppleFoodDrawer
        Food.HAY -> HayFoodDrawer
        Food.TREAT -> TreatFoodDrawer
    }
}
