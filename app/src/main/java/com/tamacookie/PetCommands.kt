package com.tamacookie

import android.content.Context
import android.content.Intent
import com.tamacookie.feature.toy.Toy
import com.tamacookie.persist.Adoption
import com.tamacookie.persist.PetFields
import com.tamacookie.persist.Pets

/**
 * Ce que les activités demandent pour l'animal. **Une seule autorité** : si le service tourne, c'est lui
 * qui possède l'état (commande par intent, il applique et sauvegarde) ; sinon le [com.tamacookie.persist.PetRepository]
 * est écrit directement. Les activités ne lisent l'animal que par le repository (lecture sans effet de bord).
 */
object PetCommands {
    const val ACTION_ADOPT = "com.tamacookie.ADOPT"
    const val ACTION_SET_NAME = "com.tamacookie.SET_NAME"

    const val EXTRA_NAME = "name"
    private const val EXTRA_NEW = "new"
    private const val EXTRA_PERSONALITY = "personality"
    private const val EXTRA_TOY = "favoriteToy"
    private const val EXTRA_FUR = "look.fur"
    private const val EXTRA_EARS = "look.ears"
    private const val EXTRA_EYES = "look.eyes"
    private const val EXTRA_PATTERN = "look.pattern"
    private const val EXTRA_ACC = "look.accessory"
    private const val EXTRA_ACC_COLOR = "look.accColor"

    fun adopt(ctx: Context, a: Adoption) {
        if (BunnyService.running) {
            ctx.startService(Intent(ctx, BunnyService::class.java).setAction(ACTION_ADOPT)
                .putExtra(EXTRA_NAME, a.name).putExtra(EXTRA_NEW, a.isNew)
                .putExtra(EXTRA_PERSONALITY, a.personality.name).putExtra(EXTRA_TOY, a.favoriteToy.name)
                .putExtra(EXTRA_FUR, a.look.fur.id).putExtra(EXTRA_EARS, a.look.ears.id)
                .putExtra(EXTRA_EYES, a.look.eyes.id).putExtra(EXTRA_PATTERN, a.look.pattern.id)
                .putExtra(EXTRA_ACC, a.look.accessory.id).putExtra(EXTRA_ACC_COLOR, a.look.accColor.id))
        } else {
            Pets.repository(ctx).adopt(a)
        }
    }

    fun rename(ctx: Context, name: String) {
        val n = Adoption.cleanName(name) ?: return
        if (BunnyService.running) {
            ctx.startService(Intent(ctx, BunnyService::class.java).setAction(ACTION_SET_NAME).putExtra(EXTRA_NAME, n))
        } else {
            Pets.repository(ctx).rename(n)
        }
    }

    /** Relit une commande d'adoption (null si incomplète). Les ids inconnus retombent sur les défauts. */
    fun adoptionFrom(i: Intent): Adoption? {
        val name = Adoption.cleanName(i.getStringExtra(EXTRA_NAME)) ?: return null
        val d = Look()
        return Adoption(
            name = name,
            look = Look(
                fur = Look.byId(Fur.entries, i.getStringExtra(EXTRA_FUR), d.fur),
                ears = Look.byId(Ears.entries, i.getStringExtra(EXTRA_EARS), d.ears),
                eyes = Look.byId(EyeColor.entries, i.getStringExtra(EXTRA_EYES), d.eyes),
                pattern = Look.byId(Pattern.entries, i.getStringExtra(EXTRA_PATTERN), d.pattern),
                accessory = Look.byId(Accessory.entries, i.getStringExtra(EXTRA_ACC), d.accessory),
                accColor = Look.byId(AccColor.entries, i.getStringExtra(EXTRA_ACC_COLOR), d.accColor),
            ),
            personality = PetFields.enumOr(i.getStringExtra(EXTRA_PERSONALITY), Personality.CUDDLY),
            favoriteToy = PetFields.enumOr(i.getStringExtra(EXTRA_TOY), Toy.BALL),
            isNew = i.getBooleanExtra(EXTRA_NEW, false),
        )
    }
}
