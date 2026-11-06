package com.tamacookie.feature.station

import android.graphics.Canvas
import android.graphics.Color
import com.tamacookie.BunnyView.Pose
import com.tamacookie.Palette
import com.tamacookie.Pet
import com.tamacookie.PetCtx
import com.tamacookie.StationView

/** Vétérinaire : il s'assoit sur la table, la fiche (stats + soin) s'ouvre à côté ; « Sortir » le libère. */
object VetStation : StationBehavior {
    override val name = "🩺 Véto"
    override val tapHint = "Dépose-moi chez le véto pour voir ma santé 🩺"
    override val seat = Seat(12f, 26f)

    override fun busyLine(ctx: PetCtx) = "Je suis chez le docteur !"

    override fun onEnter(ctx: StationCtx, pet: Pet, announce: Boolean) {
        ctx.openVetPanel(pet)
        if (announce) ctx.say(ctx.pick("Bonjour docteur… 😳", "J'ai peur des piqûres…", "Ça va être long ?"))
    }

    override fun update(ctx: StationCtx, pet: Pet, dt: Float) {
        pet.body.pose = Pose.SIT
        ctx.actor.faceToward(pet, ctx.arena.screenW / 2)
    }

    override fun onLeave(ctx: StationCtx, pet: Pet) {
        ctx.closeVetPanel()
    }

    override fun draw(c: Canvas, v: StationView, w: Float, h: Float) {
        val pen = v.pen
        val p = pen.p; val stroke = pen.stroke; val r = pen.r; val d = pen.d
        // panneau avec croix (en haut à gauche, laisse la place au lapin)
        r.set(4 * d, 4 * d, 44 * d, 40 * d)
        p.color = Color.WHITE; c.drawRoundRect(r, 6 * d, 6 * d, p); c.drawRoundRect(r, 6 * d, 6 * d, stroke)
        p.color = Palette.VET_GREEN
        r.set(19 * d, 10 * d, 29 * d, 34 * d); c.drawRect(r, p)
        r.set(12 * d, 17 * d, 36 * d, 27 * d); c.drawRect(r, p)
        // table d'examen
        val top = h - 26 * d
        r.set(6 * d, top, w - 6 * d, top + 10 * d)
        p.color = Palette.VET_TABLE; c.drawRoundRect(r, 4 * d, 4 * d, p); c.drawRoundRect(r, 4 * d, 4 * d, stroke)
        p.color = Palette.METAL
        r.set(w * 0.22f, top + 10 * d, w * 0.22f + 6 * d, h - 2 * d); c.drawRect(r, p)
        r.set(w * 0.78f - 6 * d, top + 10 * d, w * 0.78f, h - 2 * d); c.drawRect(r, p)
        // stéthoscope décoratif
        stroke.color = Palette.STETHOSCOPE
        r.set(w - 40 * d, 8 * d, w - 12 * d, 36 * d)
        c.drawArc(r, 0f, 180f, false, stroke)
        stroke.color = Palette.OUTLINE
    }
}
