package com.tamacookie.feature.mischief

import com.tamacookie.PetEvent
import com.tamacookie.Act
import com.tamacookie.Expr
import com.tamacookie.PType
import com.tamacookie.Pose
import com.tamacookie.Pet
import com.tamacookie.Box
import java.util.EnumSet
import kotlin.math.abs

/**
 * Il saute et s'assoit SUR quelque chose (son corps le recouvre) : une icône quand il est ignoré, le
 * bouton qu'on vient de toucher ou le clavier (petit monstre seulement ; sinon il commente). Une
 * caresse le fait descendre.
 */
object SquatMischief : Mischief {
    override val acts: Set<Act> = EnumSet.of(Act.SQUAT)

    override fun chance(level: Int) = if (level == 2) 0.6f else 0.35f

    override fun canStart(ctx: MischiefCtx, pet: Pet) = ctx.screen.snapshot.platforms.isNotEmpty()

    /** Ignoré : il s'installe sur une des icônes les plus centrales. */
    override fun start(ctx: MischiefCtx, pet: Pet): Boolean {
        val a = ctx.arena
        val d = a.d
        val midY = (a.topY + a.groundY) / 2
        val w = a.screenW
        val p = ctx.screen.snapshot.platforms.items.sortedBy { abs(it.box.centerX - w / 2) + abs(it.box.centerY - midY) }
            .take(4).random(ctx.rnd)
        squatOn(ctx, pet, p.box, (p.box.centerY + 46 * d).coerceAtMost(a.groundY), if (ctx.level == 2) 60f else 25f,
            ctx.pick("Je m'installe ici ! 😼", "Tu ne m'as pas vu ? Et là ? 😏", "Coucou ! Je bloque ? 😇"), kbd = false)
        return true
    }

    /** Un clic (sur un petit bouton) ou une rafale de frappe : il s'assoit dessus, ou commente. */
    override fun react(ctx: MischiefCtx, pet: Pet, ev: UserEvents): Boolean {
        val a = ctx.arena
        val d = a.d
        val rnd = ctx.rnd
        val level = ctx.level
        val r = ev.click
        if (r != null) {
            if (rnd.nextFloat() < (if (level == 2) 0.22f else 0.10f)) {
                if (level == 2 && r.width < 600 * d && r.height < 300 * d && r.top > a.topY + 120 * d) {
                    squatOn(ctx, pet, r, (r.centerY + 46 * d).coerceAtMost(a.groundY), 40f,
                        ctx.pick("C'est MON bouton maintenant 😼", "Tu voulais appuyer ici ? Trop tard ! 😜"), kbd = false)
                } else {
                    ctx.actor.faceToward(pet, r.centerX)
                    pet.express(Expr.SURPRISED, 1f)
                    ctx.say(ctx.pick("Ooh, qu'est-ce que tu as touché ? 👀", "Je peux appuyer aussi ?", "Clic clic !"))
                    ctx.cooldownUntil = ev.now + 30_000
                }
                return true
            }
        }
        if (ev.takeTypingBurst()) {
            val ime = ctx.screen.snapshot.ime
            if (ime != null && rnd.nextFloat() < 0.5f) {
                if (level == 2) {
                    val x = (ime.left + ime.width * (0.25f + rnd.nextFloat() * 0.5f)).toInt()
                    squatOn(ctx, pet, Box(x - 1f, ime.top, x + 1f, ime.top), ime.top, 15f,
                        ctx.pick("Je t'aide à écrire ! ⌨️", "Tu écris à qui ? 👀 Je regarde !"), kbd = true)
                } else {
                    ctx.say(ctx.pick("Tu écris à qui ? 👀", "Tu tapes vite !", "Dis-lui que je fais coucou !"))
                    ctx.cooldownUntil = ev.now + 45_000
                }
                return true
            }
        }
        return false
    }

    private fun squatOn(ctx: MischiefCtx, pet: Pet, rect: Box, feetY: Float, limit: Float, line: String, kbd: Boolean) {
        val a = ctx.arena
        val s = ctx.state
        // Les coordonnées du bouton sont celles de l'écran affiché : il doit d'abord être sur cette page
        ctx.pages.rebaseToActivePage()
        s.squatRect = rect; s.squatKbd = kbd; s.squatLimit = limit
        s.squatVersion = ctx.screen.snapshot.platforms.version
        pet.mind.chatterIn = 7f
        ctx.cooldownUntil = ctx.clock.wallMs() + 60_000
        ctx.actor.jumpTo(pet, rect.centerX.coerceIn(a.minX, a.maxX), feetY, Act.SQUAT)
        pet.body.phys.airLandY = feetY
        ctx.say(line, 2.5f)
    }

    override fun update(ctx: MischiefCtx, pet: Pet, dt: Float) {
        val s = ctx.state
        pet.body.pose = Pose.SIT
        ctx.actor.faceToward(pet, ctx.arena.screenW / 2)
        if (pet.body.actTime > s.squatLimit) {
            ctx.say(ctx.pick("Bon… je m'ennuie ici.", "Tu ne veux pas jouer ? Tant pis…"))
            hopOff(ctx, pet)
            return
        }
        // L'élément sous lui a disparu (défilement, autre app, clavier fermé) : il tombe
        val screen = ctx.screen.snapshot
        val version = screen.platforms.version
        val gone = if (s.squatKbd) screen.ime == null
        else version != s.squatVersion && screen.connected &&
            screen.platforms.items.none { it.box.intersects(s.squatRect) }
        if (s.squatKbd || version != s.squatVersion) s.squatVersion = version
        if (gone) {
            pet.body.phys.stop(); pet.body.afterAir = Act.IDLE
            ctx.say(ctx.pick("Whoaaa !", "Oups, tout a bougé !"), 1.4f)
            pet.setAct(Act.AIR)
            return
        }
        pet.mind.chatterIn -= dt
        if (pet.mind.chatterIn <= 0f) {
            pet.mind.chatterIn = 7f + ctx.rnd.nextFloat() * 5f
            ctx.say(ctx.pick("Je ne bouge pas ! 😼", "C'est MA place maintenant", "Un câlin et je m'en vais…",
                "Tu veux appuyer ici ? Hihi 😜", "Un bisou magique pour passer ? 😘"), 2.2f)
        }
    }

    /** Une caresse pendant qu'il squatte : il descend. */
    override fun onPet(ctx: MischiefCtx, pet: Pet): Boolean {
        ctx.on(pet, PetEvent.Petted)
        ctx.emit(pet, PType.HEART, 3)
        pet.express(Expr.HAPPY, 1.5f)
        ctx.say(ctx.pick("Bon d'accord… 😇 je descends", "Un câlin ? Ça marche, je bouge !", "Hihi, gagné ! 💕"), 2f)
        hopOff(ctx, pet)
        return true
    }

    private fun hopOff(ctx: MischiefCtx, pet: Pet) {
        val a = ctx.arena
        ctx.actor.jumpTo(pet, (pet.body.fx + pet.body.facing * 80 * a.d).coerceIn(a.minX, a.maxX), a.groundY, Act.IDLE)
    }
}
