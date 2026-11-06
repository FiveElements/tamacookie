package com.tamacookie

/** Un animal de test (jauges par défaut, sans persistance). */
fun testPet(
    phys: BunnyPhysics = BunnyPhysics(),
    rnd: kotlin.random.Random = kotlin.random.Random(1),
    id: String = "test-pet",
    stats: PetStats = PetStats(),
    traits: Traits = Traits(),
): Pet = Pet(Identity(PetId(id), bornAt = 42L), traits, Look(), stats, Body(phys), Mind(rnd))
