package com.tamacookie

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dessin et animation de Cookie, entièrement en Canvas (aucune image).
 * Le service pilote la pose / l'expression ; la vue gère le clignement des yeux,
 * les petits mouvements et les particules (cœurs, Zzz, notes...).
 */
@SuppressLint("ViewConstructor")
class BunnyView(context: Context) : View(context) {

    enum class Pose { IDLE, WALK, AIR, SLEEP, EAT, DRAGGED, SIT }
    enum class Expr { NORMAL, HAPPY, SAD, SLEEPY, SURPRISED, SICK, ANGRY, DIZZY }
    enum class PType { HEART, ZZZ, NOTE, SPARKLE, DROP, CRUMB, STAR, BUBBLE }

    private class Particle(
        var type: PType, var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val size: Float, val rot: Float
    )

    // --- Pilotage par le service ---
    var pose = Pose.IDLE
    var expr = Expr.NORMAL
    var facing = 1f                 // 1 = droite, -1 = gauche
    var walkPhase = 0f              // 0..1, cycle de saut pendant la marche
    var airVy = 0f                  // vitesse verticale (px/s) pour l'étirement en l'air
    var eatProgress = 0f            // 0..1
    var foodColor = Palette.CARROT
        set(v) { field = v; carrot.color = v }
    var dirty = false
    var veryDirty = false

    private val d = resources.displayMetrics.density
    private var t = 0f
    private var blinkIn = 2.5f
    private var blink = 0f
    private var earTwitch = 0f
    private var earTwitchIn = 4f
    private val particles = ArrayList<Particle>()
    private val rnd = Random(System.nanoTime())

    // --- Peintures ---
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Palette.OUTLINE
        strokeWidth = 5f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val fur = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.FUR }
    private val furShade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.FUR_SHADE }
    private val pink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.PINK }
    private val cheek = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.CHEEK }
    private val sickCheek = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.SICK_CHEEK }
    private val nose = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.NOSE }
    private val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.INK }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Palette.INK
        strokeWidth = 2.6f; strokeCap = Paint.Cap.ROUND
    }
    private val dirt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.DIRT }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.SHADOW }
    private val carrot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.CARROT }
    private val leaf = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.LEAF }
    private val pPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val rect = RectF()
    private val path = Path()
    private val clip = Path()

    // --- Personnalisation ---
    private val patch = Paint(Paint.ANTI_ALIAS_FLAG)
    private val acc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeJoin = Paint.Join.ROUND
    }
    private val hatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.HAT }
    private val flowerCenter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.YELLOW }
    private val lens = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.LENS }
    private var darkFur = false

    var look: Look = Look()
        set(v) { field = v.copy(); applyLook() }

    private fun applyLook() {
        val f = Options.furColor(look.fur)
        fur.color = f
        furShade.color = Options.shade(f, 0.93f)
        darkFur = Options.isDark(f)
        outline.color = if (darkFur) Palette.OUTLINE_DARK else Palette.OUTLINE
        line.color = if (darkFur) Palette.CREAM else Palette.INK
        eye.color = Options.eyeColor(look.eyes)
        val lum = 0.299f * Color.red(f) + 0.587f * Color.green(f) + 0.114f * Color.blue(f)
        patch.color = when {
            lum > 190f -> Palette.PATCH_LIGHT
            lum > 110f -> Options.shade(f, 0.55f)
            else -> Palette.CREAM
        }
        acc.color = Options.accColor(look.accColor)
        accStroke.color = outline.color
        invalidate()
    }

    init { applyLook() }

    /** Avance l'animation de [dt] secondes. */
    fun update(dt: Float) {
        t += dt
        // clignement
        if (blink > 0f) blink -= dt
        else {
            blinkIn -= dt
            if (blinkIn <= 0f) { blink = 0.14f; blinkIn = 2f + rnd.nextFloat() * 4f }
        }
        // petit mouvement d'oreille
        if (earTwitch > 0f) earTwitch -= dt
        else {
            earTwitchIn -= dt
            if (earTwitchIn <= 0f) { earTwitch = 0.35f; earTwitchIn = 3f + rnd.nextFloat() * 6f }
        }
        // particules ambiantes
        if (pose == Pose.SLEEP && rnd.nextFloat() < dt * 0.9f) emit(PType.ZZZ)
        if (pose == Pose.EAT && rnd.nextFloat() < dt * 4f) emit(PType.CRUMB)
        if (expr == Expr.SAD && rnd.nextFloat() < dt * 0.4f) emit(PType.DROP)
        if (expr == Expr.SICK && rnd.nextFloat() < dt * 0.3f) emit(PType.DROP)
        if (expr == Expr.DIZZY && rnd.nextFloat() < dt * 5f) emit(PType.STAR)
        if (veryDirty && rnd.nextFloat() < dt * 0.6f) emit(PType.BUBBLE)

        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.x += p.vx * dt; p.y += p.vy * dt
            if (p.type == PType.CRUMB || p.type == PType.DROP) p.vy += 300f * d * dt
            if (p.type == PType.ZZZ || p.type == PType.NOTE) p.x += sin(t * 3f + p.rot) * 12f * d * dt
        }
        invalidate()
    }

    /** Ajoute une particule au-dessus de la tête. */
    fun emit(type: PType, count: Int = 1) {
        repeat(count) {
            val cx = width / 2f
            val headY = height - 6f * d - 60f * d * unit()
            val p = when (type) {
                PType.HEART -> Particle(type, cx + rnd.nextFloat(-25f, 25f) * d, headY,
                    rnd.nextFloat(-20f, 20f) * d, -55f * d, 1.4f, 1.4f, rnd.nextFloat(9f, 14f) * d, 0f)
                PType.ZZZ -> Particle(type, cx + 20f * d * facing, headY + 6f * d, 8f * d * facing,
                    -28f * d, 2.2f, 2.2f, rnd.nextFloat(11f, 16f) * d, rnd.nextFloat() * 6f)
                PType.NOTE -> Particle(type, cx + rnd.nextFloat(-30f, 30f) * d, headY,
                    rnd.nextFloat(-10f, 10f) * d, -45f * d, 1.6f, 1.6f, rnd.nextFloat(13f, 18f) * d, rnd.nextFloat() * 6f)
                PType.SPARKLE -> Particle(type, cx + rnd.nextFloat(-40f, 40f) * d,
                    height - rnd.nextFloat(10f, 80f) * d, 0f, -15f * d, 0.8f, 0.8f, rnd.nextFloat(5f, 9f) * d, 0f)
                PType.DROP -> Particle(type, cx + 16f * d * facing, headY + 8f * d, 6f * d * facing,
                    -20f * d, 0.9f, 0.9f, 4f * d, 0f)
                PType.CRUMB -> Particle(type, cx + 24f * d * facing, height - 50f * d, rnd.nextFloat(-30f, 30f) * d,
                    -40f * d, 0.7f, 0.7f, 2.2f * d, 0f)
                PType.STAR -> Particle(type, cx, headY - 4f * d, 0f, 0f, 0.6f, 0.6f, 5f * d, rnd.nextFloat() * 6.28f)
                PType.BUBBLE -> Particle(type, cx + rnd.nextFloat(-20f, 20f) * d, height - 40f * d,
                    rnd.nextFloat(-6f, 6f) * d, -25f * d, 1.5f, 1.5f, rnd.nextFloat(3f, 6f) * d, 0f)
            }
            particles.add(p)
        }
    }

    private fun Random.nextFloat(a: Float, b: Float) = a + nextFloat() * (b - a)

    /** Échelle du lapin : 1 unité de dessin = unit() dp. */
    private fun unit() = 0.95f

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val fy = height - 6f * d
        val s = d * unit()

        if (pose != Pose.AIR && pose != Pose.DRAGGED) {
            val w = if (pose == Pose.WALK) 24f - 6f * sin(walkPhase * PI.toFloat()) else 28f
            rect.set(cx - w * s, fy - 5f * s, cx + w * s, fy + 4f * s)
            c.drawOval(rect, shadow)
        }

        c.save()
        c.translate(cx, fy)
        c.scale(facing * s, s)

        // --- squash & stretch ---
        var yOff = 0f
        var sx = 1f
        var sy = 1f
        when (pose) {
            Pose.IDLE, Pose.SIT -> {
                val b = 1f + 0.025f * sin(t * 2.6f); sy = b; sx = 1f / b
            }
            Pose.WALK -> {
                val h = sin(walkPhase * PI.toFloat())
                yOff = -h * 14f
                val squash = if (walkPhase < 0.12f || walkPhase > 0.88f) 0.1f else -0.05f * h
                sy = 1f - squash; sx = 1f + squash
            }
            Pose.AIR -> {
                val st = (-airVy / (2600f * d)).coerceIn(-0.15f, 0.18f)
                sy = 1f + st; sx = 1f - st * 0.6f
            }
            Pose.SLEEP -> {
                val b = 1f + 0.04f * sin(t * 1.6f); sy = b; sx = 1f
            }
            Pose.EAT -> {
                val b = 1f + 0.02f * sin(t * 14f); sy = b; sx = 1f / b
            }
            Pose.DRAGGED -> { sy = 1.06f; sx = 0.96f }
        }
        c.translate(0f, yOff)
        c.scale(sx, sy)

        if (pose == Pose.SLEEP) drawSleeping(c) else drawBunny(c)
        c.restore()

        drawParticles(c)
    }

    // ------------------------------------------------------------------
    //  Lapin debout
    // ------------------------------------------------------------------
    private fun drawBunny(c: Canvas) {
        val dragged = pose == Pose.DRAGGED
        // Oreilles : angle selon l'humeur
        val twitch = if (earTwitch > 0f) sin(earTwitch * 30f) * 8f else 0f
        var a1: Float
        var a2: Float
        when {
            dragged -> { a1 = -25f + sin(t * 7f) * 18f; a2 = 15f + sin(t * 7f + 1f) * 18f }
            expr == Expr.SAD || expr == Expr.SICK -> { a1 = -62f; a2 = -48f }
            expr == Expr.SLEEPY -> { a1 = -40f; a2 = -22f }
            expr == Expr.HAPPY -> { a1 = -14f + sin(t * 8f) * 6f; a2 = 10f - sin(t * 8f) * 6f }
            expr == Expr.SURPRISED -> { a1 = -6f; a2 = 12f }
            pose == Pose.WALK -> { a1 = -22f - sin(walkPhase * 6.28f) * 8f; a2 = -4f - sin(walkPhase * 6.28f) * 8f }
            else -> { a1 = -16f; a2 = 4f + twitch }
        }

        val bodyCx = if (dragged) 0f else -4f
        val bodyCy = if (dragged) -28f else -24f
        val bodyRx = if (dragged) 21f else 26f
        val bodyRy = if (dragged) 24f else 21f
        val headCx = if (dragged) 4f else 8f
        val headCy = if (dragged) -60f else -54f

        // Style d'oreilles
        var b1x = headCx - 7f; var b1y = headCy - 14f
        var b2x = headCx + 7f; var b2y = headCy - 15f
        var earLen = 1f
        when (look.ears) {
            1 -> { // tombantes (bélier)
                val sway = when {
                    dragged -> sin(t * 7f) * 14f
                    pose == Pose.WALK -> sin(walkPhase * 6.28f) * 7f
                    else -> sin(t * 1.5f) * 2f
                }
                a1 = -158f + sway; a2 = 158f + sway
                b1x = headCx - 16f; b1y = headCy - 9f
                b2x = headCx + 19f; b2y = headCy - 9f
                earLen = 0.82f
            }
            2 -> a2 = if (dragged) a2 + 60f else 80f + twitch   // une oreille pliée
        }

        for (pass in 0..1) {
            val o = pass == 0
            // oreille arrière
            ear(c, b1x, b1y, a1, o, back = true, len = earLen)
            // queue
            circle(c, bodyCx - bodyRx + 1f, bodyCy + 2f, 8.5f, if (o) outline else fur)
            // pattes arrière
            if (dragged) {
                val sw = sin(t * 7f) * 3f
                oval(c, -9f + sw, -4f, 6f, 10f, if (o) outline else fur)
                oval(c, 9f + sw, -4f, 6f, 10f, if (o) outline else fur)
            } else {
                oval(c, -14f, -5f, 13f, 5.5f, if (o) outline else fur)
            }
            // corps
            oval(c, bodyCx, bodyCy, bodyRx, bodyRy, if (o) outline else fur)
            if (!o) oval(c, bodyCx + 6f, bodyCy + 6f, bodyRx * 0.55f, bodyRy * 0.6f, furShade)
            if (!o && look.pattern == 2) {
                // Hollandais : arrière du corps coloré
                c.save(); clipOval(c, bodyCx, bodyCy, bodyRx, bodyRy)
                c.drawRect(bodyCx - bodyRx - 2f, bodyCy - bodyRy - 2f, bodyCx - bodyRx * 0.1f, bodyCy + bodyRy + 2f, patch)
                c.restore()
            }
            // patte avant
            if (!dragged) oval(c, 12f, -5f, 10f, 5f, if (o) outline else fur)
            else {
                oval(c, -12f, -30f, 5f, 8f, if (o) outline else fur)
                oval(c, 16f, -30f, 5f, 8f, if (o) outline else fur)
            }
            // tête
            oval(c, headCx, headCy, 22f, 20f, if (o) outline else fur)
            if (!o && look.pattern != 0) {
                c.save(); clipOval(c, headCx, headCy, 22f, 20f)
                if (look.pattern == 1) oval(c, headCx - 7f, headCy - 1f, 8.5f, 7.5f, patch)
                else {
                    oval(c, headCx - 15f, headCy - 2f, 12f, 15f, patch)
                    oval(c, headCx + 18f, headCy - 2f, 10f, 15f, patch)
                }
                c.restore()
            }
            // oreille avant
            ear(c, b2x, b2y, a2, o, back = false, len = earLen)
        }

        // taches de saleté
        if (dirty) {
            circle(c, bodyCx - 10f, bodyCy - 6f, 3.5f, dirt)
            circle(c, bodyCx + 8f, bodyCy + 8f, 2.6f, dirt)
            circle(c, headCx - 12f, headCy + 6f, 2.4f, dirt)
        }

        drawFace(c, headCx, headCy)
        drawAccessory(c, headCx, headCy, 20f, standing = true)

        if (pose == Pose.EAT) drawCarrotInMouth(c, headCx + 16f, headCy + 12f)
    }

    private fun clipOval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float) {
        clip.reset()
        rect.set(x - rx, y - ry, x + rx, y + ry)
        clip.addOval(rect, Path.Direction.CW)
        c.clipPath(clip)
    }

    private fun ear(c: Canvas, x: Float, y: Float, angle: Float, outlinePass: Boolean, back: Boolean, len: Float = 1f) {
        c.save()
        c.translate(x, y)
        c.rotate(angle)
        rect.set(-6.5f, -38f * len, 6.5f, 4f)
        val furP = if (look.pattern == 2) patch else if (back) furShade else fur
        c.drawOval(rect, if (outlinePass) outline else furP)
        if (!outlinePass) {
            rect.set(-3.2f, -33f * len, 3.2f, -2f)
            c.drawOval(rect, pink)
        }
        c.restore()
    }

    // ------------------------------------------------------------------
    //  Accessoires
    // ------------------------------------------------------------------
    private fun drawAccessory(c: Canvas, hx: Float, hy: Float, hr: Float, standing: Boolean) {
        when (look.accessory) {
            1 -> bow(c, hx + hr * 0.6f, hy - hr * 0.85f)
            2 -> hat(c, hx + 1f, hy - hr * 0.88f)
            3 -> if (standing) glasses(c, hx, hy)
            4 -> if (standing) scarf(c, hx, hy)
            5 -> flowers(c, hx, hy, hr)
        }
    }

    private fun bow(c: Canvas, x: Float, y: Float) {
        c.save(); c.translate(x, y); c.rotate(15f)
        path.reset()
        path.moveTo(0f, 0f); path.lineTo(-10f, -6.5f); path.lineTo(-10f, 6.5f); path.close()
        path.moveTo(0f, 0f); path.lineTo(10f, -6.5f); path.lineTo(10f, 6.5f); path.close()
        c.drawPath(path, acc); c.drawPath(path, accStroke)
        c.drawCircle(0f, 0f, 3f, acc); c.drawCircle(0f, 0f, 3f, accStroke)
        c.restore()
    }

    private fun hat(c: Canvas, x: Float, y: Float) {
        c.save(); c.translate(x, y); c.rotate(-10f)
        rect.set(-15f, -3.5f, 15f, 3.5f); c.drawOval(rect, hatPaint); c.drawOval(rect, accStroke)
        rect.set(-9f, -18f, 9f, 0f); c.drawRoundRect(rect, 2f, 2f, hatPaint); c.drawRoundRect(rect, 2f, 2f, accStroke)
        rect.set(-9f, -6f, 9f, -2f); c.drawRect(rect, acc)
        c.restore()
    }

    private fun glasses(c: Canvas, hx: Float, hy: Float) {
        val sw = accStroke.strokeWidth
        accStroke.color = acc.color; accStroke.strokeWidth = 2.2f
        c.drawCircle(hx - 7f, hy - 1f, 6.5f, lens); c.drawCircle(hx - 7f, hy - 1f, 6.5f, accStroke)
        c.drawCircle(hx + 9f, hy - 1f, 6.5f, lens); c.drawCircle(hx + 9f, hy - 1f, 6.5f, accStroke)
        c.drawLine(hx - 0.5f, hy - 2f, hx + 2.5f, hy - 2f, accStroke)
        accStroke.color = outline.color; accStroke.strokeWidth = sw
    }

    private fun scarf(c: Canvas, hx: Float, hy: Float) {
        rect.set(hx - 18f, hy + 14f, hx + 15f, hy + 22f)
        c.drawRoundRect(rect, 4f, 4f, acc); c.drawRoundRect(rect, 4f, 4f, accStroke)
        c.save(); c.translate(hx - 12f, hy + 19f); c.rotate(12f + sin(t * 3f) * 4f)
        rect.set(-4f, 0f, 4f, 17f)
        c.drawRoundRect(rect, 3f, 3f, acc); c.drawRoundRect(rect, 3f, 3f, accStroke)
        c.restore()
    }

    private fun flowers(c: Canvas, hx: Float, hy: Float, hr: Float) {
        for (i in 0..4) {
            val a = Math.toRadians((205.0 + i * 32.5)).toFloat()
            val px = hx + cos(a) * hr * 1.08f
            val py = hy + sin(a) * hr * 1.0f
            for (k in 0..3) {
                val pa = k * 1.5708f + i
                c.drawCircle(px + cos(pa) * 2.4f, py + sin(pa) * 2.4f, 2.5f, acc)
            }
            c.drawCircle(px, py, 1.6f, flowerCenter)
        }
    }

    private fun drawFace(c: Canvas, hx: Float, hy: Float) {
        val e1x = hx - 7f; val e2x = hx + 9f; val ey = hy - 1f
        val closed = blink > 0f
        when {
            expr == Expr.HAPPY -> { arcEye(c, e1x, ey, up = true); arcEye(c, e2x, ey, up = true) }
            expr == Expr.SURPRISED || pose == Pose.DRAGGED -> { bigEye(c, e1x, ey); bigEye(c, e2x, ey) }
            expr == Expr.DIZZY -> { spiralEye(c, e1x, ey); spiralEye(c, e2x, ey) }
            expr == Expr.SLEEPY || expr == Expr.SICK -> { lidEye(c, e1x, ey); lidEye(c, e2x, ey) }
            closed -> { c.drawLine(e1x - 3.5f, ey, e1x + 3.5f, ey, line); c.drawLine(e2x - 3.5f, ey, e2x + 3.5f, ey, line) }
            else -> { dotEye(c, e1x, ey); dotEye(c, e2x, ey) }
        }
        // sourcils
        if (expr == Expr.SAD) {
            c.drawLine(e1x - 4f, ey - 9f, e1x + 3f, ey - 11f, line)
            c.drawLine(e2x - 3f, ey - 11f, e2x + 4f, ey - 9f, line)
        } else if (expr == Expr.ANGRY) {
            c.drawLine(e1x - 4f, ey - 11f, e1x + 4f, ey - 7f, line)
            c.drawLine(e2x - 4f, ey - 7f, e2x + 4f, ey - 11f, line)
        }
        // joues
        val ck = if (expr == Expr.SICK) sickCheek else cheek
        oval(c, e1x - 4f, hy + 7f, 4.5f, 2.8f, ck)
        oval(c, e2x + 5f, hy + 7f, 4.5f, 2.8f, ck)
        // nez
        val nx = hx + 2f; val ny = hy + 5f
        path.reset(); path.moveTo(nx - 3f, ny); path.lineTo(nx + 3f, ny); path.lineTo(nx, ny + 2.8f); path.close()
        c.drawPath(path, nose)
        // bouche
        if (pose == Pose.EAT) {
            val open = (sin(t * 14f) + 1f) * 1.5f
            oval(c, nx, ny + 6f, 2.5f, 1f + open, eye)
        } else if (expr == Expr.HAPPY || expr == Expr.SURPRISED) {
            oval(c, nx, ny + 6.5f, 3f, 3f, eye)
            oval(c, nx, ny + 7.5f, 2f, 1.6f, pink)
        } else if (expr == Expr.SAD || expr == Expr.SICK || expr == Expr.ANGRY) {
            rect.set(nx - 4f, ny + 5f, nx + 4f, ny + 11f)
            c.drawArc(rect, 200f, 140f, false, line)
        } else {
            rect.set(nx - 4f, ny + 1f, nx, ny + 6f); c.drawArc(rect, 20f, 140f, false, line)
            rect.set(nx, ny + 1f, nx + 4f, ny + 6f); c.drawArc(rect, 20f, 140f, false, line)
        }
    }

    private fun dotEye(c: Canvas, x: Float, y: Float) {
        if (darkFur || look.pattern != 0) oval(c, x, y, 4.6f, 5.4f, white)
        oval(c, x, y, 3.4f, 4.2f, eye)
        circle(c, x + 1.2f, y - 1.5f, 1.3f, white)
    }

    private fun bigEye(c: Canvas, x: Float, y: Float) {
        circle(c, x, y, 5.5f, white)
        val sw = line.strokeWidth; line.strokeWidth = 1.6f
        rect.set(x - 5.5f, y - 5.5f, x + 5.5f, y + 5.5f); c.drawOval(rect, line)
        line.strokeWidth = sw
        circle(c, x, y, 2.2f, eye)
    }

    private fun arcEye(c: Canvas, x: Float, y: Float, up: Boolean) {
        rect.set(x - 4f, y - 3f, x + 4f, y + 4f)
        c.drawArc(rect, if (up) 200f else 20f, 140f, false, line)
    }

    private fun lidEye(c: Canvas, x: Float, y: Float) {
        rect.set(x - 3.4f, y - 2f, x + 3.4f, y + 4f)
        c.drawArc(rect, 0f, 180f, true, eye)
        c.drawLine(x - 4f, y + 0.8f, x + 4f, y + 0.8f, line)
    }

    private fun spiralEye(c: Canvas, x: Float, y: Float) {
        path.reset()
        var first = true
        var a = 0f
        while (a < 12f) {
            val r = a * 0.42f
            val px = x + cos(a + t * 8f) * r; val py = y + sin(a + t * 8f) * r
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
            a += 0.4f
        }
        val sw = line.strokeWidth; line.strokeWidth = 1.5f
        c.drawPath(path, line)
        line.strokeWidth = sw
    }

    private fun drawCarrotInMouth(c: Canvas, x: Float, y: Float) {
        val len = 24f * (1f - eatProgress.coerceIn(0f, 0.9f))
        c.save()
        c.translate(x, y)
        c.rotate(30f)
        path.reset()
        path.moveTo(-1f, -5f); path.lineTo(len, -1.2f); path.lineTo(len, 1.2f); path.lineTo(-1f, 5f); path.close()
        c.drawPath(path, carrot)
        if (eatProgress < 0.4f) {
            oval(c, len + 3f, -3f, 4f, 1.6f, leaf)
            oval(c, len + 3f, 3f, 4f, 1.6f, leaf)
        }
        c.restore()
    }

    // ------------------------------------------------------------------
    //  Lapin endormi (roulé en boule)
    // ------------------------------------------------------------------
    private fun drawSleeping(c: Canvas) {
        for (pass in 0..1) {
            val o = pass == 0
            circle(c, -27f, -16f, 8f, if (o) outline else fur)
            oval(c, -4f, -17f, 29f, 17f, if (o) outline else fur)
            // oreilles couchées vers l'arrière
            c.save(); c.translate(6f, -32f); c.rotate(-100f)
            rect.set(-6f, -36f, 6f, 3f); c.drawOval(rect, if (o) outline else furShade)
            c.restore()
            oval(c, 14f, -24f, 19f, 16f, if (o) outline else fur)
            c.save(); c.translate(12f, -36f); c.rotate(-82f)
            rect.set(-6f, -36f, 6f, 3f); c.drawOval(rect, if (o) outline else fur)
            if (!o) { rect.set(-3f, -31f, 3f, -2f); c.drawOval(rect, pink) }
            c.restore()
        }
        if (dirty) { circle(c, -12f, -20f, 3f, dirt); circle(c, 2f, -10f, 2.4f, dirt) }
        // yeux fermés
        arcEye(c, 9f, -24f, up = false)
        arcEye(c, 22f, -24f, up = false)
        oval(c, 6f, -17f, 4f, 2.4f, cheek)
        oval(c, 26f, -17f, 4f, 2.4f, cheek)
        path.reset(); path.moveTo(15f, -19f); path.lineTo(20f, -19f); path.lineTo(17.5f, -16.6f); path.close()
        c.drawPath(path, nose)
        drawAccessory(c, 14f, -24f, 16f, standing = false)
    }

    // ------------------------------------------------------------------
    //  Particules
    // ------------------------------------------------------------------
    private fun drawParticles(c: Canvas) {
        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            val alpha = (min(1f, a * 2.5f) * 255).toInt()
            when (p.type) {
                PType.HEART -> { pPaint.color = Palette.HEART; pPaint.alpha = alpha; heart(c, p.x, p.y, p.size) }
                PType.ZZZ -> text(c, "z", p.x, p.y, p.size * (1.4f - a * 0.5f), Palette.ZZZ, alpha)
                PType.NOTE -> text(c, if (p.rot > 3f) "♪" else "♫", p.x, p.y, p.size, Palette.NOTE, alpha)
                PType.SPARKLE -> { pPaint.color = Palette.SPARKLE; pPaint.alpha = alpha; sparkle(c, p.x, p.y, p.size * (0.6f + a)) }
                PType.DROP -> { pPaint.color = Palette.DROP; pPaint.alpha = alpha; c.drawCircle(p.x, p.y, p.size, pPaint) }
                PType.CRUMB -> { pPaint.color = Palette.CARROT; pPaint.alpha = alpha; c.drawCircle(p.x, p.y, p.size, pPaint) }
                PType.STAR -> {
                    val ang = p.rot + t * 6f
                    text(c, "★", p.x + cos(ang) * 24f * d, p.y + sin(ang) * 7f * d, 11f * d, Palette.STAR, alpha)
                }
                PType.BUBBLE -> {
                    pPaint.color = Palette.DIRT_BUBBLE; pPaint.alpha = alpha / 2
                    c.drawCircle(p.x, p.y, p.size, pPaint)
                }
            }
        }
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, alpha: Int) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.alpha = alpha
        c.drawText(s, x, y, textPaint)
    }

    private fun heart(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y + s * 0.35f)
        path.cubicTo(x - s * 1.1f, y - s * 0.35f, x - s * 0.45f, y - s * 1.05f, x, y - s * 0.45f)
        path.cubicTo(x + s * 0.45f, y - s * 1.05f, x + s * 1.1f, y - s * 0.35f, x, y + s * 0.35f)
        path.close()
        c.drawPath(path, pPaint)
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y - s); path.lineTo(x + s * 0.25f, y - s * 0.25f); path.lineTo(x + s, y)
        path.lineTo(x + s * 0.25f, y + s * 0.25f); path.lineTo(x, y + s); path.lineTo(x - s * 0.25f, y + s * 0.25f)
        path.lineTo(x - s, y); path.lineTo(x - s * 0.25f, y - s * 0.25f); path.close()
        c.drawPath(path, pPaint)
    }

    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, p: Paint) {
        rect.set(x - rx, y - ry, x + rx, y + ry); c.drawOval(rect, p)
    }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, p: Paint) = c.drawCircle(x, y, r, p)

    /** Vrai si le point (coordonnées de la vue) touche approximativement le lapin. */
    fun hitTest(x: Float, y: Float): Boolean {
        val cx = width / 2f
        val fy = height - 6f * d
        val s = d * unit()
        return abs(x - cx) < 38f * s && y > fy - 100f * s && y < fy + 6f * s
    }

    @Suppress("unused")
    private fun clamp01(v: Float) = max(0f, min(1f, v))
}
