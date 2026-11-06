package com.tamacookie

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/** Rectangle simple en pixels écran (icône, bouton…), sans dépendance Android. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    /** Vide (comme `Rect.isEmpty`). */
    val isEmpty: Boolean get() = left >= right || top >= bottom

    /** Les deux rectangles se chevauchent (comme `Rect.intersects` : bords stricts). */
    fun intersects(o: Box): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/** Corps soumis à la petite physique des objets (jouets, nourriture). */
interface PropBody {
    var x: Float
    var y: Float
    var vx: Float
    var vy: Float
    var onGround: Boolean
}

/** Résultat d'un pas de vol du lapin. */
sealed interface AirEvent {
    /** Toujours en l'air. */
    object Flying : AirEvent
    /** A touché un bord de l'écran et rebondi (toujours en l'air). */
    object HitWall : AirEvent
    /** Impact fort au sol : il rebondit (toujours en l'air). */
    object Bounced : AirEvent
    /** Arrivé au point visé par [BunnyPhysics.airLandY] (bêtise : assis sur un bouton). */
    object ForcedLanded : AirEvent
    /**
     * Posé. [platform] = indice de l'icône dans la liste passée à [BunnyPhysics.stepAir], -1 = le sol.
     * [hard] = atterrissage brutal (il sera étourdi).
     */
    data class Landed(val platform: Int, val hard: Boolean) : AirEvent {
        val onPlatform: Boolean get() = platform >= 0
    }
}

/**
 * Physique pure du lapin (position des pieds x,y en pixels écran, vitesses en px/s)
 * et des objets. Aucune dépendance Android : testable sur la JVM.
 *
 * @param density densité de l'écran (1 dp = density px)
 */
class BunnyPhysics(val density: Float = 1f) {
    var gravity = Tuning.GRAVITY_DP * density

    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f

    /** Si non null : il retombe exactement à cette hauteur (sans tenir compte des icônes ni du sol). */
    var airLandY: Float? = null
    /** Impact brutal au sol pendant ce vol (même s'il a rebondi depuis). */
    var hardLanding = false

    /** Vitesse verticale à afficher (étirement du lapin en l'air), 0 une fois posé. */
    var airVy = 0f
        private set
    /** Sens vers lequel se tourner après le dernier pas (1 / -1), 0 = inchangé. */
    var facing = 0f
        private set

    // Limites de l'écran
    var minX = 0f
    var maxX = 0f
    var topY = 0f
    var groundY = 0f

    fun setBounds(minX: Float, maxX: Float, topY: Float, groundY: Float) {
        this.minX = minX; this.maxX = maxX; this.topY = topY; this.groundY = groundY
    }

    /** Saut avec une vitesse initiale donnée. */
    fun jump(vyStart: Float, vxStart: Float) {
        airLandY = null
        vy = vyStart; vx = vxStart
        hardLanding = false
    }

    /** Saut balistique pour atterrir précisément en (tx, ty), avec un sommet [apexRise] px au-dessus du plus haut des deux points. */
    fun jumpTo(tx: Float, ty: Float, apexRise: Float) {
        val j = solveJump(x, y, tx, ty, apexRise, gravity)
        airLandY = null
        vy = j.vy
        vx = j.vx
        hardLanding = false
    }

    /** Lâché par l'utilisateur avec cette vitesse (bornée). */
    fun release(vxPx: Float, vyPx: Float) {
        val max = Tuning.THROW_MAX_DP * density
        vx = vxPx.coerceIn(-max, max)
        vy = vyPx.coerceIn(-max, max)
        hardLanding = false
    }

    fun stop() { vx = 0f; vy = 0f }

    /**
     * Un pas de vol : gravité, atterrissage forcé ([airLandY]), atterrissage sur une icône
     * (seulement en descendant, en venant d'au-dessus), murs, plafond, sol (rebond / atterrissage brutal).
     */
    fun stepAir(dt: Float, platforms: List<Box>): AirEvent {
        val d = density
        val prevY = y
        vy += gravity * dt
        x += vx * dt
        y += vy * dt
        facing = 0f

        val land = airLandY
        if (land != null) {
            airVy = vy
            if (abs(vx) > Tuning.FACE_VX_DP * d) facing = sign(vx)
            if (vy > 0f && y >= land) {
                y = land; vx = 0f; vy = 0f; airVy = 0f
                airLandY = null
                return AirEvent.ForcedLanded
            }
            return AirEvent.Flying
        }

        // Atterrissage sur une icône (y compris quand on le lance dessus)
        if (vy > 0f) {
            val margin = Tuning.PLATFORM_MARGIN_DP * d
            for (i in platforms.indices) {
                val p = platforms[i]
                if (prevY <= p.top + Tuning.PLATFORM_TOP_TOLERANCE_PX && y >= p.top &&
                    x >= p.left + margin && x <= p.right - margin
                ) {
                    y = p.top
                    // Impact brutal au sol plus tôt dans ce vol (rebond) : signalé ici, et oublié pour le vol suivant
                    val hard = vy > Tuning.HARD_LANDING_DP * d || hardLanding
                    hardLanding = false
                    vx = 0f; vy = 0f; airVy = 0f
                    return AirEvent.Landed(i, hard)
                }
            }
        }
        airVy = vy
        if (abs(vx) > Tuning.FACE_VX_DP * d) facing = sign(vx)

        var wall = false
        if (x < minX) { x = minX; vx = abs(vx) * Tuning.WALL_BOUNCE; wall = true }
        if (x > maxX) { x = maxX; vx = -abs(vx) * Tuning.WALL_BOUNCE; wall = true }
        val ceiling = topY + Tuning.CEILING_DP * d
        if (y < ceiling) { y = ceiling; vy = abs(vy) * Tuning.CEILING_BOUNCE }

        if (y >= groundY) {
            y = groundY
            if (vy > Tuning.HARD_LANDING_DP * d) hardLanding = true
            if (vy > Tuning.GROUND_BOUNCE_DP * d) {
                vy = -vy * Tuning.GROUND_BOUNCE_DAMP
                vx *= Tuning.GROUND_BOUNCE_FRICTION
                return AirEvent.Bounced
            }
            vx = 0f; vy = 0f; airVy = 0f
            val hard = hardLanding
            hardLanding = false
            return if (hard) LANDED_GROUND_HARD else LANDED_GROUND
        }
        return if (wall) AirEvent.HitWall else AirEvent.Flying
    }

    /** Résultat d'un calcul de saut balistique. */
    data class Jump(val vx: Float, val vy: Float, val time: Float)

    companion object {
        private val LANDED_GROUND = AirEvent.Landed(-1, false)
        private val LANDED_GROUND_HARD = AirEvent.Landed(-1, true)

        /**
         * Vitesses initiales pour aller de (fromX, fromY) à (tx, ty) en passant par un sommet
         * situé [apexRise] px au-dessus du plus haut des deux points (y vers le bas).
         */
        fun solveJump(fromX: Float, fromY: Float, tx: Float, ty: Float, apexRise: Float, gravity: Float): Jump {
            val apex = min(fromY, ty) - apexRise
            val vy0 = -sqrt(2f * gravity * (fromY - apex))
            val t = -vy0 / gravity + sqrt(2f * (ty - apex) / gravity)
            return Jump((tx - fromX) / t, vy0, t)
        }

        /** Vitesse verticale (négative) pour monter de [height] px. */
        fun impulseForHeight(gravity: Float, height: Float): Float = -sqrt(2f * gravity * height)

        /**
         * Physique générique d'un objet : gravité ([gFactor] x [gravity]), rebonds sur les bords,
         * le plafond et le sol, frottement au sol. [y] = bas de l'objet, [x] = son centre.
         */
        fun stepProp(
            b: PropBody, dt: Float, gravity: Float, gFactor: Float,
            bounce: Float, wallBounce: Float, groundFriction: Float,
            halfW: Float, height: Float,
            screenW: Float, topY: Float, groundY: Float, settleSpeed: Float,
        ) {
            b.vy += gravity * gFactor * dt
            b.x += b.vx * dt
            b.y += b.vy * dt
            if (b.x < halfW) { b.x = halfW; b.vx = abs(b.vx) * wallBounce }
            if (b.x > screenW - halfW) { b.x = screenW - halfW; b.vx = -abs(b.vx) * wallBounce }
            if (b.y - height < topY) { b.y = topY + height; b.vy = abs(b.vy) * Tuning.PROP_CEILING_BOUNCE }
            if (b.y >= groundY) {
                b.y = groundY
                b.vy = if (abs(b.vy) > settleSpeed) -abs(b.vy) * bounce else 0f
                b.vx *= (1f - groundFriction * dt).coerceAtLeast(0f)
                b.onGround = b.vy == 0f
            } else if (b.y < groundY - 2f) b.onGround = false
        }

        /** Chute de la nourriture (demi-gravité, sans rebond). Retourne true au moment où elle touche le sol. */
        fun stepFalling(b: PropBody, dt: Float, gravity: Float, minX: Float, maxX: Float, groundY: Float): Boolean {
            if (b.onGround) return false
            b.vy += gravity * 0.5f * dt
            b.x = (b.x + b.vx * dt).coerceIn(minX, maxX)
            b.y += b.vy * dt
            if (b.y >= groundY) {
                b.y = groundY; b.onGround = true; b.vx = 0f
                return true
            }
            return false
        }
    }
}
