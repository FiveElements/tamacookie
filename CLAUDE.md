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

Package `com.tamacookie` (+ `com.tamacookie.feature` depuis l'étape 3a). Cœur pur et testé : `BunnyBrain` (décisions),
`BunnyPhysics`, `ScrollMath`, `Tuning`, et depuis l'étape 2 `Pet`/`World`/`BunnyActor`/`Clock` ; les contrôleurs reçoivent
`PetCtx` (faux hôte : `FakePetCtx`). Les fonctionnalités sont des plugins (`feature/station/`, `feature/toy/`,
`feature/mischief/`, `feature/menu/` : voir les recettes « Ajouter… » en fin de fichier). Depuis l'étape 4, toutes les
fenêtres passent par le port `OverlayHost` (adaptateur `OverlayManager`, ids `WindowId` par entité) et la vue de
l'animal n'affiche qu'un `BunnyRenderState`.
Problèmes à résorber (audit) :
- `BunnyService` (~1 270 l.) est un god-object : ~16 responsabilités, ~45 `internal var`.
- `StationController`, `ToyController`, `MischiefController`, `PageTracker`, `OverlayWindows` reçoivent
  `svc: BunnyService` et modifient ses champs ; ils s'appellent en cycle via `svc`.  → étape 2 : ils reçoivent `PetCtx` et agissent sur le `Pet`.
- Le `when (act)` central de `updateBehavior` et les sous-ensembles de `Act` (BunnyBrain.kt) dispersent
  chaque fonctionnalité sur ~6 à 14 endroits.  → étape 3 : `ActRegistry` ; il ne reste au `when` que la
  locomotion et le sommeil (voir écarts 3b).
- Les vues sont de l'état de jeu (`bunny.pose`, `bunny.facing` écrits par 4 classes).  → étape 4 : `BunnyRenderState`.
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
| 2 | `Pet` + `World` (un seul animal), `Body`/`BunnyActor`, `PetCtx`/`PetHost`, `Clock` ; faux hôte de test | ✅ |
| 3 | Fonctionnalités en plugins recevant `pet` : **3a** stations (occupant, capacité, `FloatingPanel`) ; **3b** jouets, bêtises, menu | ✅ |
| 4 | `OverlayManager` unique + `RenderState` par entité | ✅ |
| 5 | `PetStats` + `PetRules` + `PetRepository` versionné par `PetId`, look par ids stables (migration) | ⏳ |
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

- **Étape 2** (`BunnyService` 956 → 938 l., mais il ne contient plus d'état d'animal) :
  - Nouveaux types (package `com.tamacookie`) : `Pet.kt` (`PetId`, `Origin.Resident`, `Identity`, `Body`, `Mind`, `Pet`),
    `World.kt` (`Arena` = géométrie partagée, `World` = `pets` + `props`), `BunnyActor.kt` (marcher, se tourner,
    sauter, `surfaceY`), `Clock.kt` (`Clock`, `AndroidClock`), `PetCtx.kt` (façade, implémentée par `BunnyService`).
    Tests : `FakePetCtx` + `FakeClock` + `resetPlatforms()` (test/FakePetCtx.kt), `PetTest`, `BunnyActorTest`,
    `MischiefControllerTest`, `PageTrackerTest`.
  - L'ancienne interface `Body` (physique des objets) s'appelle désormais `PropBody`.
  - `PetId.resident(bornAt)` : identifiant **dérivé de `bornAt`** (déjà sauvegardé), stable sans nouvelle clé de
    prefs ; un vrai UUID persistant viendra avec `PetRepository` (étape 5). `Identity.name` lit `PetState.name`.
  - `Body` possède `pose` et `facing` : `BunnyView.pose`/`facing` délèguent au `Body` branché (`bunny.body = pet.body`) ;
    les aperçus (`MainActivity`, `AdoptActivity`) gardent leur propre `Body`. `walkPhase`, `airVy`, `eatProgress`,
    `expr` restent copiés dans la vue par le service (→ `RenderState`, étape 4).
  - `Mind.rnd` est **le même** `Random` que `PetCtx.rnd` (paroles, objets) tant qu'il n'y a qu'un animal.
  - Restent dans le service (global ou fenêtre) : géométrie (`Arena`), cache des plateformes, `hidden`,
    `bunnyAway`/`bunnyAdded` (fenêtre de l'animal → `OverlayManager` étape 4), boucle, minuteurs de notification.
  - `props` appartient au `World` (`hasToy`/`hasFood`/`hasToyOrFood`/`currentToy`/`dropHeldToy`) ; `ToyController`
    les gère toujours.
  - Les `updateXxx` appelés par la machine à états reçoivent `pet` (`updateSquat(pet, dt)`…) ; ailleurs les
    contrôleurs prennent `ctx.pet` (getter privé `pet`). Le dispatch central `when (act)` est inchangé (étape 3).
  - `Clock` remplace `SystemClock`/`System.currentTimeMillis()` dans le service et les contrôleurs migrés.
    Hors périmètre : `PetState`, `PetNotifier`, `ScreenSensorService`, activités.
  - Appui long des stations : `s.view.postDelayed` au lieu du `Handler` du service (même fil principal).
  - **Contrôleurs encore couplés** (accès `svc.` → 0 partout ; accès `ctx.` : `PageTracker` 22, `MischiefController` 75,
    `ToyController` 93, `StationController` 111, `TouchHandler` 41, `OverlayWindows` 11) :
    - `PageTracker`, `MischiefController` : uniquement `PetCtx` + `Platforms` (global) → testés en JVM.
    - `ToyController`, `StationController` : `ctx.windows` / `ctx.context` (vues `PropView`/`StationView`, panneaux,
      `WindowManager`) → intestables en JVM tant qu'il n'y a pas d'`OverlayManager` (étape 4).
    - `TouchHandler` : reçoit en plus `StationController` et `MischiefController` (adaptateur d'entrée qui les pilote :
      survol / dépôt, trouvé, caresse pendant une bêtise) ; gestes Android.
    - `OverlayWindows` : adaptateur Android (lit la position de l'animal pour la bulle).
    - `PetCtx` expose encore `pages` (le `PageTracker`) et des délégués vers les contrôleurs (`feed`, `startPlay`,
      `endHiding`, `lastInteraction`) : à remplacer par des requêtes au monde à l'étape 3b (`inStation` et
      `busyInStation` ont disparu en 3a). → fait en 3b (voir plus bas).

- **Étape 3a** (stations ; `StationController` 597 → 524 l., `Stations.kt` 178 → 56 l.) :
  - **Dispatch générique** (`feature/ActHandler.kt`) : `ActHandler` (= une fonctionnalité qui pilote l'animal pendant
    une activité : `update(pet, dt)`, `onPet(pet)`, propriétés `slowFrames`, `travel`/`blocksTravel`) et `ActRegistry`
    (`Act → (Pet) -> ActHandler?`), exposé par `PetCtx.acts` et rempli par le service. La boucle (`updateBehavior`),
    la cadence (`targetFps`), le voyage (`onAppChanged`) et la caresse (`TouchHandler.onPetTap`) le consultent avant
    leur `when (act)`. **3b** : enregistrer `PLAY`, `SQUAT`, `HIDE_*`… de la même façon et vider le `when`.
  - `Act.BATH/VET/CAGED` → **`Act.IN_STATION`** (dans aucun ensemble d'`Act`). `SLOW` = `{SLEEP}`, `BUSY_FOR_TRAVEL`
    sans `BATH` : ces propriétés sont lues sur le comportement. Tests `BunnyBrainTest` inchangés.
  - Le voyage a **trois** issues (`Travel` : `FOLLOWS`, `BUSY` = ne suit pas mais commente — le bain, `IGNORES` =
    se tait — véto, cage) : un simple `blocksTravel` ne suffisait pas à garder le comportement.
  - **Sous-package `com.tamacookie.feature.station`** : `StationKind` (identité : nom = clés `st_<NOM>_*`, ordre =
    placement par défaut, inchangés) porte son `behavior` ; `StationBehavior` + `StationCtx` + `Seat`
    (`StationBehavior.kt`) ; un `object` par station (`BathStation.kt`, `VetStation.kt`, `CageStation.kt`,
    `ChestStation.kt`) avec propriétés, place, répliques (dont `Lines.PETTED_IN_CAGE` → `CageStation.PETTED`),
    `onEnter/update/onLeave/onPet/onTap/restores/onShownAgain` et **dessin** (`draw(c, StationView, w, h)` : les
    pinceaux sont dans `StationView.pen`). Le dessin garde donc un import Android dans `feature/` jusqu'au
    `RenderState` (étape 4). Le côté fenêtres (`StationController`, `Station`, `StationView`, `FloatingPanel`) reste
    dans `com.tamacookie` (→ `OverlayManager`).
  - Propriétés portées par le comportement : `acceptsPet` (faux : coffre), `capacity`, `slowFrames`, `travel`,
    `holdsPet` (cage : il y dort, la suit d'une vue à l'autre, on ne la range pas avec lui — remplace tous les
    `== CAGE`), `animated` (baignoire), `seat`, `tapHint`, `occupiedLine`, `stayLine`, `busyLine`.
  - **Occupation** dans le monde : `World.station(kind)` → `Occupancy` (occupants `PetId`, `capacity`, `canEnter`,
    `has` sans allocation), `World.stationOf(pet)`, `World.busy(pet)`. `PetCtx.sayIfBusy(pet)` (méthode par défaut,
    réplique de la station ou du cache-cache) remplace `busyInStation()`. Multi-animal **préparé, pas activé** :
    le contrôleur agit encore sur `ctx.pet` (glisser, panneaux), `view.occupied = !isEmpty`, les touchers d'une
    station occupée vont à la fenêtre de l'animal courant.
  - `StationCtx` = `PetCtx` + `leaveStation(pet, hopOut)`, `openVetPanel/closeVetPanel`, `openChest` : les panneaux
    restent dans `StationController` (une station avec son propre panneau demandera d'y toucher).
  - Restent spécifiques dans `StationController` : le coffre (`visibleChest`, `checkChestBump`, `popFromChest`,
    `updateGoToChest`, `selfServeAt` — candidat à un `ActHandler` pour `GOTO_CHEST` en 3b) et la fiche du véto
    (placement, `reopenVetPanelIfNeeded`).
  - `FloatingPanel<V>` (`FloatingPanel.kt`) : vue + `lp` + placement + fermeture au toucher extérieur + `reposition()`,
    pour les panneaux véto, coffre et 🏠.
  - Cas jusque-là impossibles, rendus sûrs : `Act.IN_STATION` sans station → `IDLE` ; entrer dans une station alors
    qu'il est dans une autre le retire (sans `onLeave`) de la première.
  - Tests : `StationBehaviorTest` (+ `FakeStationCtx`) — exhaustivité (chaque `StationKind` a un comportement et un
    nom), ordre/clés, propriétés, capacité / occupants, `stationOf`/`busy`/`sayIfBusy`, registre, bain, véto, cage,
    tap. `FakePetCtx` : `inStation`/`busy` supprimés (on occupe une station du monde à la place).

- **Étape 3b** (jouets, bêtises, menu ; `ToyController` 516 → 363 l., `MischiefController` 331 → 111 l.,
  `BunnyService` 941 → 917 l.) — 4 commits :
  - **Jouets et nourriture** (`feature/toy/`) : `PropSpec` (taille, `touchable`, `draw`), `ToySpec` (lancer `Launch`,
    `randomTint`, `startLines`, `complainsWhenTaken`, `play` pendant `Act.PLAY`, `step` = physique propre),
    `FoodSpec` (`restSpin`, `lines`), `Poop`. Un `object` par jouet (`BallToy.kt`…), les nourritures dans `Foods.kt`.
    Les enums `Toy` et `Food` ont **déménagé** dans `feature.toy` (noms sauvegardés inchangés : `favoriteToy`) et
    portent leur `spec` ; `PropView.Kind`, son `when` de dessin et `Prop.toy` (`when`) ont disparu
    (`Prop.spec/toy/food/isPoop`, pinceaux `PropView.pen` = `PropPen`, comme `StationPen`).
  - `ToyCtx` (= `PetCtx` + état de la partie) : `userHolds`, `holdTime`, `catchCooldown`, `groundSaidAt`, et `hit` /
    `stepToy` communs. `holdTime` **fusionne** `toyHoldTime` (frisbee) et `hugTime` (doudou) : un seul jouet à la fois
    et les deux étaient remis à zéro ensemble.
  - Factorisations : `popFrom` (objet qui jaillit du coffre), `refuses` (garde occupé / endormi — les deux répliques
    « Chut… il dort » et l'ordre de `noteInteraction` sont conservés), `OverlayWindows.placeAnchored` (fenêtre ancrée
    à une vue : crottes et stations).
  - `PLAY`, `GOTO_FOOD`, `EAT` (sorti du service) et `GOTO_CHEST` passent par `ActRegistry`
    (`ToyController.registerActs`, `StationController.registerActs`). `Body.eatProgress` remplace l'écriture directe
    dans la vue (recopié par le service comme `walkPhase`).
  - **Exception assumée** : `StationController` reçoit `ToyController` (le coffre donne à manger / un jouet) depuis que
    `feed`/`startPlay` ont quitté `PetCtx`.
  - **Bêtises** (`feature/mischief/`) : `Mischief` (`acts`, `travel`, `slowFrames`, `chance(level)`, `canStart`,
    `start`, `react`, `update`, `onPet`), `MischiefCtx` (= `PetCtx` + `level`, `cooldownUntil`, `state`),
    `MischiefState`, `UserEvents`. Objets : `HideSeekMischief` (cache-cache), `SquatMischief` (s'asseoir sur une
    icône / un bouton / le clavier, ou commenter), `KnockMischief` (toc-toc), `ScrollDizzy`. Registre : `Mischiefs.ALL`.
  - **`chance(level)` au lieu de `weight(level)`** : la sélection `Mischiefs.seek` reproduit la cascade d'origine
    (chaque bêtise possible, dans l'ordre, est tirée avec sa probabilité ; 1 = sans tirage) — mêmes probabilités et
    même consommation du `Random`, ce que des poids normalisés n'auraient pas donné. La dernière (toc-toc, 1) ferme la
    cascade (testé). `checkUserEvents` consulte `react` dans l'ordre de la liste ; `UserEvents.takeTypingBurst`
    garde l'ordre d'origine (le compteur de frappe n'est remis à zéro que si le clic n'a rien déclenché).
  - Pas d'`Act.MISCHIEF` générique : `SQUAT`, `KNOCK`, `HIDE_RUN`, `HIDDEN`, `PEEK` restent (lus par `PageTracker` et
    les ensembles `RUSH_BACK`, `HIDING`, `NO_PAGE_FOLLOW`…) et sont enregistrés par `MischiefController.registerActs`.
    **Les ensembles d'`Act` sont inchangés** (`BunnyBrainTest`) : `travel` des gestionnaires (`BUSY` pour jouer,
    manger, squatter, toquer ; `IGNORES` pour cache-cache) les double de façon cohérente (testé).
  - `MischiefState` est tenu par le contrôleur (un seul animal) → à porter par `Pet` à l'étape 7. Les objets sont sans état.
  - **Menu** (`feature/menu/`) : `MenuEntry(emoji, label, refresh, run)` + `MenuCtx` (= `PetCtx` + panneau 🏠 +
    cache-cache, implémenté par `TouchHandler`) ; `MenuEntries.ALL`, deux par rangée. `PetMenu.Action` et
    `TouchHandler.onMenuAction` ont disparu. Pas d'`enabled` : aucune entrée n'est grisée aujourd'hui.
  - **`PetCtx`** : `feed`, `startPlay`, `endHiding` retirés ; `lastInteraction` appartient à l'hôte (simple
    propriété du service, `noteInteraction` par défaut) au lieu d'être délégué aux bêtises. Restent hors monde :
    `pages` (→ étapes 4/6), `startSleep` (logique du service : cage, cache-cache), `closeMenu` / `hide`, et les
    fenêtres (→ `OverlayManager`, étape 4). `TouchHandler.startDrag` appelle encore `mischief.endHiding()` quand on
    attrape l'animal caché (candidat à un crochet « attrapé » sur `ActHandler`).
  - **Ce qui reste dans le `when (act)` de `updateBehavior`** : `IDLE`, `WALK`, `AIR`, `DRAGGED`, `SLEEP`,
    `TRAVEL_OUT`/`TRAVEL_IN`, `SIT`, `DIZZY`, `DANCE` (locomotion, sommeil, voyage, humeur : le corps et le cerveau de
    l'animal, qui rejoindront `Species`/`Director` à l'étape 7), `PAGE_OUT` (`PageTracker`, étape 6), et `else` =
    activité de fonctionnalité sans gestionnaire (station introuvable) → `IDLE`. `TouchHandler.onPetTap` ne garde que
    la caresse ordinaire (réveil, chatouilles).
  - Tests : `ToySpecTest`, `MischiefTest` (cascade avec `Random` seedé, registre, chaque bêtise), `MenuEntryTest` ;
    `MischiefControllerTest` passe par le registre (`step` / `tap`) ; `PlatformsTest` suit la fin de `PropView.Kind`.

- **Étape 4** (fenêtres et rendu ; `ToyController` 363 → 338 l., `StationController` 524 → 485 l., `BunnyView`
  624 → 562 l.) — 3 commits :
  - **Port `OverlayHost`** (`OverlayHost.kt`, Kotlin pur) : `attach(id, x, y, fadeIn)` (vue d'entité fabriquée par
    l'adaptateur), `move`, `redraw`, `detach`, `release` (détache et oublie la vue : objet disparu), `detachAll`,
    `isAttached`, `flush`. Ids typés **`WindowId`** : `Pet(petId)`, `Bubble(petId)`, `Station(kind)`, `Prop(id)`,
    `Dock`, `Menu`, `Panel(nom)` (« vet », « chest », « stations »). La logique (idempotence, regroupement) est dans
    **`WindowTable<W>`** (pur, générique sur la fenêtre, `WindowBackend`) ; `FakeOverlayHost` = `WindowTable` +
    `RecordingBackend` (faux WindowManager qui lève sur un add en double / un update ou remove absent).
  - **`OverlayManager`** (`OverlayManager.kt`) = `WindowTable<Win(view, lp)>` : **seul** appelant de `addView` /
    `updateViewLayout` / `removeViewImmediate` du service ; `attach(id, view, lp)` pour les fenêtres Android (animal,
    bulle, dock, menu, panneaux) et `relayout(id)` (taille changée : bulle, panneau replacé — immédiat).
    **Regroupement** : `move` ne fait que noter la position ; `flush()` en fin de `BunnyService.update` envoie au plus
    **un `updateViewLayout` par fenêtre et par image**, rien si la position n'a pas changé (aller-retour compris).
    Un `move` hors boucle (glisser le dock / une station) programme un flush par `Handler.post`. Ajout : alpha remis
    à 1 ou fondu (`fadeIn`). Boucle suspendue : `placeBunny` ne déplace pas (comme avant), `requestFrames` le refait.
  - **Booléens supprimés** : `bunnyAdded`, `Station.shown`, `Prop.attached`, `bubbleShown`, `dockAdded` (et les
    `windows.add/update/safeRemove/placeAnchored` dispersés) → `overlay.isAttached(id)`. `removeAllWindows` = fermer
    menu + panneaux (leur état), `detachAll()`, `toys.onDetached()`. `placeAnchored` est une extension du port.
  - **`Prop` et `Station` sont des modèles purs** (`Prop.kt`, `Station.kt`) : position, `spin`, `tint` (objet) ;
    `x`/`y` de la fenêtre, `highlight`, `occupied`, `open`, `t` (station). `PropView(prop)` / `StationView(station)`
    les lisent (`v.spin`, `v.t`, `v.open` sont des getters : les `draw` des plugins n'ont pas changé de signature) ;
    `StationView` est `Refreshable` (anime son agrandissement quand `highlight` change). `Prop.id` vient de
    `World.newPropId()`.
  - **`EntityWindows`** (adaptateur) fabrique les vues des objets / stations au premier `attach` et traduit leurs
    gestes en appels purs : `ToyController.grab/drag/release/pickUp`, `StationController.touchGoesToPet/dragStation/
    dropStation/tapStation` (appui long, slop, haptique, VelocityTracker restent Android). Panneaux des stations
    derrière le port **`StationPanels`** (`feature/station/StationPanels.kt`, Android : `StationPanelWindows`) ;
    `FloatingPanel` prend un `WindowId`. `StationController.toggleStation(k)` sort du panneau 🏠.
  - **`OverlayWindows`** ne lit plus `act` / `fx` / `pages` : une bulle **par animal** (créée à sa première réplique),
    position fournie par `BubbleAnchor` (le service : tête de l'animal + décalage de page). Le refus de parler
    (caché, absent) est dans le service. Dock : `dockX/dockY` (à jour avant le flush) au lieu de `dockLp`.
  - `PetCtx` : `context`, `windows`, `forwardTouch`, `showFood` retirés ; `overlay: OverlayHost` ajouté.
    `Body.eatingFood` remplace `ToyController.eatingFood` + `showFood`. `TouchHandler` reçoit `context` et
    `OverlayWindows` (menu = `WindowId.Menu` ; un nouveau menu remplace l'ancien sous le même id).
  - **`BunnyRenderState`** (data class immuable : pose, expr, facing, walkPhase, airVy, eatProgress, food, dirty,
    veryDirty, look **copié**) : `BunnyRenderState.of(pet)` à chaque image pour **chaque animal du monde** ayant une
    fenêtre ; `moodExpr` y a déménagé. `BunnyView.render(state, dt)` ; `BunnyView.body/pose/facing/expr/…` ont disparu
    (pont de l'étape 2 supprimé). Les aperçus (`MainActivity`, `AdoptActivity`) fabriquent leur état.
  - **`AmbientAnimator`** (pur, `Random` injecté ; un par vue) : temps, clignement, oreille, particules **et leur
    choix** selon pose / humeur / saleté (mêmes tirages, même ordre). `update` renvoie si l'image change : la vue
    n'appelle plus `invalidate()` sans condition (redessin si état différent, clignement / oreille / particules en
    cours, ou dessin qui dépend du temps : respiration au repos / sommeil / repas / tenu, oreilles joyeuses, spirales,
    écharpe, oreilles tombantes en vol). En pratique la marche et le vol immobiles sont les seuls cas économisés.
    `emit` reçoit le sens du regard de l'animal (`pet.body.facing`). Hit-test : purement géométrique, resté dans la vue.
  - Coût IPC (estimation) : **avant** 1 `updateViewLayout` par fenêtre déplacée et par appel (lapin, bulle, chaque
    objet en vol, stations au défilement), avec des doublons dans la même image (objet ajouté puis placé, lapin placé
    par la station puis par la boucle, panneau / dock pendant un glisser) — typiquement 2–4 par image en jeu, plus
    pendant un défilement ; **après** ≤ 1 par fenêtre **déplacée** et par image, 0 pour une fenêtre immobile.
  - Tests : `OverlayHostTest` (idempotence, regroupement, ids par entité, ancrage, remplacement, `release`),
    `ToyControllerTest`, `StationControllerTest` (+ `FakeStationPanels`), `BunnyRenderStateTest`.
  - Restent : les `draw` des stations / objets reçoivent encore la vue Android (pinceaux) ; `PetCtx.setBunnyAway` /
    `placeBunny` / `bunnyAway` / `pages.bunnyShift` ne concernent que le résident (→ étape 7, par animal) ;
    `ScreenSensorService` a son propre WindowManager (hors périmètre, étape 6).

### Ajouter une station (ex. : le lit)

1. `feature/station/BedStation.kt` : `object BedStation : StationBehavior` — `name` (« 🛏️ Lit »), `tapHint`,
   `busyLine`, `draw`, `seat`, les propriétés utiles (`capacity = 2`, `slowFrames = true`, `holdsPet`…) et
   `onEnter` / `update` / `onLeave` / `onPet` si besoin (répliques dans le fichier).
2. `StationKind` (`feature/station/StationKind.kt`) : ajouter **à la fin** `BED("Lit", 140, 90, BedStation)` —
   l'ordre fixe le placement par défaut, le nom les clés `st_BED_*` (créées automatiquement, rien à migrer).
3. (Facultatif) ses couleurs dans `Palette` ; un test de son comportement à côté de `StationBehaviorTest`
   (le test d'exhaustivité le couvre déjà).

Rien d'autre : panneau 🏠, survol/dépôt, toucher, cadence, voyage, sommeil, caresse, sauvegarde du placement
suivent. Avant 3a il fallait toucher ~10 endroits (enum + `when` du dessin, `positionInStation`, `enterStation`,
`leaveStation`, `busyInStation`, tap, `Act` + `SLOW`/`BUSY_FOR_TRAVEL`, `updateBehavior`, `onAppChanged`,
`startSleep`, `onPetTap`, noms du menu). Seules exceptions : une station avec **son propre panneau** (ajouter une
méthode à `StationCtx` et le `FloatingPanel` dans `StationController`) ; plusieurs animaux **dans** la station
demandent l'étape 7 (multi-animal).

### Ajouter un jouet (ex. : le cerf-volant)

1. `feature/toy/KiteToy.kt` : `object KiteToy : ToySpec` — `toy get() = Toy.KITE` (getter : pas de cycle
   d'initialisation avec l'enum), `wDp`/`hDp`, `startLines`, `draw`, `play` (pendant `Act.PLAY`), `step` (sa physique,
   via `ctx.stepToy`) et au besoin `launch`, `randomTint`, `complainsWhenTaken` ; ses répliques dans le fichier.
2. `Toy` (`feature/toy/Toy.kt`) : ajouter **à la fin** `KITE("Cerf-volant", "🪁", KiteToy)` (le nom est sauvegardé
   dans `favoriteToy`).
3. (Facultatif) ses couleurs dans `Palette` ; `ToySpecTest` le couvre déjà (exhaustivité).

Coffre, jouet préféré à l'adoption, fiche du véto, partie, lancer au doigt, protestation, cadence suivent. Avant 3b :
~10 endroits (enum `Toy`, `PropView.Kind` + `when` du dessin, `Prop.toy`, taille dans `startPlay`, teinte, lancer,
répliques, `updatePlay`, physique dans `update`, protestation dans le toucher). Une nourriture : un `object … : FoodSpec`
dans `Foods.kt` + une ligne dans `Food`.

### Ajouter une bêtise (ex. : il tire la langue à l'écran)

1. `feature/mischief/XxxMischief.kt` : `object XxxMischief : Mischief` — pour qu'il la fasse quand on l'ignore :
   `chance(level)` (> 0), `canStart`, `start` ; pour réagir à l'utilisateur : `react(ctx, pet, ev)` ; si elle a sa
   propre activité : `acts`, `update`, `onPet`, `travel`. Son état éventuel va dans `MischiefState`, ses répliques
   dans le fichier.
2. `Mischiefs.ALL` (`feature/mischief/Mischief.kt`) : l'insérer à sa place — l'ordre est celui de la cascade et des
   réactions ; une bêtise de probabilité 1 doit rester la dernière (toc-toc).
3. Seulement si elle a une nouvelle activité : l'ajouter à `Act` (BunnyBrain.kt) et aux ensembles utiles (`MOVING`
   pour une animation fluide, `RUSH_BACK`, `BUSY_FOR_TRAVEL` — `BunnyBrainTest` vérifie leur cohérence).

Enregistrement dans `ActRegistry`, boucle, caresse, voyage, cadence, niveau / pause / applications exemptées suivent.
Avant 3b : ~6 endroits (cascade et état dans `MischiefController`, `Act` + ensembles, `when` de `updateBehavior`,
`when` de `onPetTap`, `onAppChanged`).

### Ajouter une action de menu (ex. : chanter)

1. `MenuEntries` (`feature/menu/MenuEntry.kt`) : `val SING = MenuEntry("🎵", "Chanter") { ctx, pet -> … }` (avec
   `refresh = { st -> … }` si le bouton change selon l'état) et l'ajouter à `ALL` (même fichier ; deux par rangée).
2. Seulement si l'action a besoin d'autre chose que `PetCtx` (un panneau, un autre contrôleur) : une méthode dans
   `MenuCtx` et son implémentation dans `TouchHandler.menuCtx`.

Fermeture du menu, mise en page, rafraîchissement suivent. Avant 3b : 4 endroits (enum `PetMenu.Action`, bouton et
rangée dans `PetMenu`, branche de `TouchHandler.onMenuAction`, texte dynamique dans `refresh`).

Règles pour chaque étape : pas de big-bang, pas de nouvelle dépendance sans justification écrite,
ajouter des tests JVM pour tout ce qui devient pur, mettre à jour ce fichier (colonne État + écarts).
