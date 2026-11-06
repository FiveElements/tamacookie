# TamaCookie — guide pour Claude

Lapin façon tamagotchi qui vit en **overlay** (WindowManager) par-dessus l'écran Android.
Kotlin 2.0, Android 13+ (minSdk 33, compile/target 34), un seul module `:app`, aucune dépendance runtime.
Code, commentaires, commits et UI sont **en français**.

## Commandes

```bash
./gradlew testDebugUnitTest   # tests JVM (JUnit 4) — doivent rester verts à chaque étape
./gradlew assembleDebug       # APK : app/build/outputs/apk/debug/
./gradlew lintDebug           # ⚠️ plante avec JDK 25 (UastFacade) : lancer Gradle avec un JDK 17/21
```

## Pièges connus

- `unitTests.isReturnDefaultValues = true` : dans les tests JVM, les classes Android (`Rect`, `Color`,
  `org.json`…) renvoient silencieusement 0/false/null. **Le code testé ne doit pas dépendre d'Android.**
- Clés SharedPreferences : passer par `Prefs` (fichier `"pet"`). Ne jamais renommer une clé existante
  sans migration — ce sont les sauvegardes des utilisateurs.
- `PetState.save()` ne réécrit pas si un autre lapin est né depuis (garde sur `bornAt`) ; `reset()`
  n'efface que `PetState.PET_KEYS` (à tenir à jour si `save()` change).
- Ignorer `.claude/worktrees/` (copies de travail) dans les recherches.
- Le repo est indexé par **graft** : `graft ask "<question>" --source`, `graft grep`, `graft skeleton <fichier>`,
  `graft callers <symbole> --depth all` avant tout déplacement/renommage.

## Architecture actuelle (point de départ, nov. 2026)

Package unique `com.tamacookie`. Cœur pur et testé : `BunnyBrain` (décisions), `BunnyPhysics`, `ScrollMath`, `Tuning`.
Problèmes à résorber (audit) :
- `BunnyService` (~1 270 l.) est un god-object : ~16 responsabilités, ~45 `internal var`.
- `StationController`, `ToyController`, `MischiefController`, `PageTracker`, `OverlayWindows` reçoivent
  `svc: BunnyService` et modifient ses champs ; ils s'appellent en cycle via `svc`.
- Le `when (act)` central de `updateBehavior` et les sous-ensembles de `Act` (BunnyBrain.kt) dispersent
  chaque fonctionnalité sur ~6 à 14 endroits.
- Les vues sont de l'état de jeu (`bunny.pose`, `bunny.facing` écrits par 4 classes).
- `Platforms` (dans ScreenSensorService.kt) : état global `@Volatile`, compteurs `*Version` qui perdent des événements.
- `PetState` mélange règles, persistance et `Context` ; `Look` stocke des **index** (fragile).

## Architecture cible : hexagonal + monde d'entités (ECS léger)

```
:core (Kotlin pur, à terme module JVM)                                   :app (Android)
  domain/   PetStats + PetRules (événements → état + effets), GameRules,   adapters/  Platforms→ScreenSensor,
            Personality.modifiers, Act/Pose/Expr, Brain, Physics                      PetRepository, OverlayManager,
  world/    World { pets: Map<PetId, Pet>, props, stations }                          UsageStats, Notifier
            Pet = Identity(id, name, origin) + Species + Look + Stats      overlay/   renderers par entité,
                  + Body (position, vitesse, act) + Mind (brain, Random)              RenderState, Palette
  systems/  Needs → Brain → Feature → Social → Physics ; Director          app/       BunnyService = cycle de vie
  feature/  un package par fonctionnalité (bath/, vet/, cage/, chest/,                + boucle + câblage ;
            bed/, toys/, mischief/…) : comportement + répliques                       TamaApp/AppGraph (DI manuelle)
  species/  rabbit/ (renderer, physique, fonctionnalités, répliques)
  ports/    ScreenSensor, ForegroundAppSource, PetStore, OverlayHost, Clock, Notifier, (PeerTransport)
```

Principes :
1. **Hexagonal à la frontière seulement** : le cœur ne connaît Android qu'au travers de ports. Pas de couche
   « use case » par action (la boucle de tick est le pilote).
2. **L'animal est une entité** : jamais de singleton « le lapin ». Tout état d'animal vit dans `Pet`
   (composants), toute logique qui le concerne reçoit le `Pet` (`update(ctx, pet, dt)`).
   Pas de bibliothèque ECS : classes Kotlin + systèmes qui bouclent sur `world.pets`.
3. **Fonctionnalités en plugins** : une interface par famille (`StationBehavior`, `ToySpec`, `Mischief`,
   `MenuEntry`) rattachée à son enum/registre. Les propriétés (« bloque le voyage », « frames lentes »,
   « accepte un animal ») sont portées par le comportement, pas par des listes d'`Act`.
   Une fonctionnalité ne dépend que de `PetCtx` (façade étroite) et ne connaît pas les autres : elle interroge
   le monde (`world.busy(pet)`), elle n'appelle pas un autre contrôleur.
4. **Stations** : occupant(s) `PetId` + capacité (deux lapins dans le lit).
5. **Species** en plugin : le lapin est la première espèce (renderer, paramètres physiques, fonctionnalités
   acceptées, modificateurs de personnalité, répliques). Rien de « lapin » en dur hors de `species/rabbit/`.
6. **SocialSystem** : le cerveau perçoit les autres animaux ; interactions à deux participants (jouer ensemble,
   suivre, se disputer une carotte, faire visiter).
7. **Director** : arbitre le global (une seule bêtise à la fois, le coffre, la bulle de texte).
8. **Rendu** : les vues ne font qu'afficher un `RenderState` immuable ; un seul `OverlayManager` (idempotent,
   ids par entité) ; couleurs dans `Palette`. Plafond de 3–4 animaux affichés (coût IPC `updateViewLayout`).
9. **Persistance** : `PetRepository` par `PetId`, schéma versionné + migrations, apparence par **identifiant stable**.
   Une seule autorité sur l'état : le service ; les activités envoient des commandes.
10. **Temps et aléatoire injectés** (`Clock`, `Random` par animal) : tout le cœur est déterministe et testable.

## Feuille de route (chaque étape : comportement inchangé sauf mention, tests verts, un commit)

| # | Étape | État |
|---|---|---|
| 0 | Corrections : reset(), course à l'adoption, scan/poll batterie, clés Prefs, receiver flag | ✅ |
| 1 | Extractions de `BunnyService` : répliques (`Lines`/`Speech`), `PetNotifier`, `TouchHandler` ; `Palette` | ✅ |
| 2 | `Pet` + `World` (un seul animal), `Body`/`BunnyActor`, `PetCtx`/`PetHost`, `Clock` ; faux hôte de test | ⏳ |
| 3 | Fonctionnalités en plugins recevant `pet` : stations (occupant, capacité, `FloatingPanel`), jouets, bêtises, menu | |
| 4 | `OverlayManager` unique + `RenderState` par entité | |
| 5 | `PetStats` + `PetRules` + `PetRepository` versionné par `PetId`, look par ids stables (migration) | |
| 6 | Port `ScreenSensor` devant `Platforms`, événements au lieu des compteurs, scan sur `HandlerThread` | |
| 7 | Multi-animal : `Species` (lapin = 1re), `Director`, `SocialSystem`, rendu de plusieurs animaux plafonné | |
| 8 | Module `:core` (JVM pur), detekt, chaînes dans `strings.xml`, accessibilité | |
| — | Ensuite : échange Bluetooth (BLE + L2CAP) — visites et transfert, protocole en deux phases | |

### Écarts et décisions

- **Étape 1** (`BunnyService` 1 276 → 956 l.) :
  - `Lines` (Kotlin pur) ne contient que les répliques du **service** ; celles des contrôleurs (stations, jouets,
    bêtises, pages) restent en place et rejoindront leur fonctionnalité à l'étape 3. `Lines` recopie les valeurs
    `ApplicationInfo.CATEGORY_*` pour rester sans import Android.
  - `Speech(rnd, show)` : `pickFrom(Array)` + `pick(vararg)` (même consommation du `Random` qu'avant).
    `svc.say` / `svc.pick` sont conservés en délégués pour ne pas toucher aux contrôleurs avant l'étape 2.
  - `PetNotifier` reçoit des lambdas (`pet`, `hidden`, `hiding`) ; ses textes (`statusLine`, `moodText`,
    `alertText`) sont des fonctions pures testées. `TouchHandler` reçoit encore le service (trop de champs lus
    et écrits : il passera par `PetCtx` à l'étape 2) ; il possède le menu (`menuOpen`, `refreshMenu`, `closeMenu`).
  - `Palette` : littéraux `Int` (pas d'annotation `@ColorInt`, faute de dépendance androidx). Appliquée à
    `BunnyView`, `PropView`, `StationView`, `StatBar` ; les activités (`MainActivity`, `AdoptActivity`), les
    panneaux et `Look`/`Options` gardent `Color.parseColor` (hors boucle de rendu).

Règles pour chaque étape : pas de big-bang, pas de nouvelle dépendance sans justification écrite,
ajouter des tests JVM pour tout ce qui devient pur, mettre à jour ce fichier (colonne État + écarts).
