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
- **Réglages** : passer par `Prefs` (fichier `"pet"`). **Animaux** : uniquement par `PetRepository` (fichier
  `"pets"`, schéma versionné, voir « Schéma de persistance v1 »). Ne jamais renommer une clé ni un identifiant
  sauvegardé (`Look` : `id` des options ; noms d'enum `Personality`, `Toy`) sans migration — ce sont les
  sauvegardes des utilisateurs (et, à terme, ce qu'échangent deux téléphones).
- Les jauges ne changent **que** par `PetCtx.on(pet, PetEvent)` (→ `PetRules`) : jamais `pet.stats = …` hors
  tests. `PetRepository.save()` ignore un animal qui n'est plus enregistré (remplacé par une adoption).
- Les activités ne lisent l'animal que par le repository et n'écrivent que par `PetCommands` (le service est
  l'autorité quand il tourne).
- **APK à installer : toujours `./gradlew clean assembleDebug`.** La compilation incrémentale de Kotlin a déjà
  laissé une classe périmée (`TouchHandler$menuCtx$1`, délégation `PetCtx by ctx` compilée avant l'ajout de
  `PetCtx.rules`) : `AbstractMethodError` au toucher de « Dodo ». Le fil principal reste alors bloqué dans le
  gestionnaire de plantage et **toutes les fenêtres overlay sont figées** (même symptôme qu'une boucle arrêtée).
  Les tests JVM ne le voient pas (ils recompilent).
- Ignorer `.claude/worktrees/` (copies de travail) dans les recherches.
- Le repo est indexé par **graft** : `graft ask "<question>" --source`, `graft grep`, `graft skeleton <fichier>`,
  `graft callers <symbole> --depth all` avant tout déplacement/renommage.

## Architecture actuelle (point de départ, nov. 2026)

Package `com.tamacookie` (+ `com.tamacookie.feature` depuis l'étape 3a). Cœur pur et testé : `BunnyBrain` (décisions),
`BunnyPhysics`, `ScrollMath`, `Tuning`, et depuis l'étape 2 `Pet`/`World`/`BunnyActor`/`Clock` ; les contrôleurs reçoivent
`PetCtx` (faux hôte : `FakePetCtx`). Les fonctionnalités sont des plugins (`feature/station/`, `feature/toy/`,
`feature/mischief/`, `feature/menu/` : voir les recettes « Ajouter… » en fin de fichier). Depuis l'étape 4, toutes les
fenêtres passent par le port `OverlayHost` (adaptateur `OverlayManager`, ids `WindowId` par entité) et la vue de
l'animal n'affiche qu'un `BunnyRenderState`. Depuis l'étape 5, les jauges sont `PetStats` + `PetRules` (événements → état + effets, `GameRules`) et
les animaux ne sont lus / écrits que par `PetRepository` (`persist/` : schéma versionné, `PetSnapshot` portable).
Problèmes à résorber (audit) :
- `BunnyService` (~1 270 l.) est un god-object : ~16 responsabilités, ~45 `internal var`.
- `StationController`, `ToyController`, `MischiefController`, `PageTracker`, `OverlayWindows` reçoivent
  `svc: BunnyService` et modifient ses champs ; ils s'appellent en cycle via `svc`.  → étape 2 : ils reçoivent `PetCtx` et agissent sur le `Pet`.
- Le `when (act)` central de `updateBehavior` et les sous-ensembles de `Act` (BunnyBrain.kt) dispersent
  chaque fonctionnalité sur ~6 à 14 endroits.  → étape 3 : `ActRegistry` ; il ne reste au `when` que la
  locomotion et le sommeil (voir écarts 3b).
- Les vues sont de l'état de jeu (`bunny.pose`, `bunny.facing` écrits par 4 classes).  → étape 4 : `BunnyRenderState`.
- `Platforms` (dans ScreenSensorService.kt) : état global `@Volatile`, compteurs `*Version` qui perdent des événements.
- `PetState` mélange règles, persistance et `Context` ; `Look` stocke des **index** (fragile).  → étape 5 :
  `PetStats` + `PetRules`, `PetRepository` (schéma versionné, ids stables), `PetState` supprimé.

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
| 5 | `PetStats` + `PetRules` + `PetRepository` versionné par `PetId`, look par ids stables (migration) | ✅ |
| 6 | Port `ScreenSensor` devant `Platforms`, événements au lieu des compteurs, scan sur `HandlerThread` | ⏳ |
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
    → **étape 5** : `PetId` = UUID persistant (créé à la migration / l'adoption), `Identity` immuable porte le nom.
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

- **Étape 5** (jauges, persistance, apparence ; `PetState` 249 l. supprimé, `BunnyService` 918 → 992 l. avec
  l'adoption sans redémarrage) — 5 commits :
  - **`PetStats`** (data class immuable) + **`PetRules.apply(stats, event, now, traits, rules) → Result(stats,
    effects)`** (`PetRules.kt`) : `PetEvent` (`Elapsed`, `Fed`, `Petted`, `PlayStarted`, `PlayHit`, `Bathed`,
    `PoopRemoved`, `Healed`, `AppVisited`, `HardLanding`, `FellAsleep`, `Woken(grumpy)`, `Caged`, `Released`,
    `Found`, `NotFound`) et `PetEffect` (`PoopSpawned(n)`, `Refused`, `WokeUp`). Bornes appliquées dans
    `PetRules.clamp` seulement. `Mood` est une enum de premier niveau (ex-`PetState.Mood`). `Traits` = caractère +
    jouet préféré (ce que la règle sait en plus des jauges).
  - **Comportement numérique identique** : `PetRulesTest` contient une **trace de référence** (400 événements
    aléatoires par caractère, empreinte au bit près de toutes les jauges à chaque étape) produite par l'ancien
    `PetState` ; le commit 1/5 contenait le test différentiel (ancien / nouveau) qui l'a validée, supprimé avec
    `PetState`. Les anciennes mutations directes (bonheur −4 à l'atterrissage, +15 / −3 au cache-cache, réveil −3,
    `sleeping`, `caged`) sont des événements ; leurs bornes unilatérales (`coerceAtMost`…) étaient équivalentes.
  - **Seul chemin** : `PetCtx.on(pet, event)` (méthode par défaut) → `Pet.on` → `PetRules` ; si
    `event.savesNow` (nourri, partie commencée, bain, crotte ramassée, soin, dodo, réveil, cage) et non refusé →
    `PetCtx.save(pet)` (avant : `save()` appelé par ces mêmes méthodes de `PetState` ; le réveil par caresse et
    l'entrée au bain sauvegardent désormais aussi, sans effet visible). `Pet.wouldRefuse(event)` = essai à blanc
    (repas refusé par `ToyController.feed` avant de sortir la nourriture).
  - **`GameRules`** (`GameRules.kt`) : taux, gains, seuils d'humeur, et (4/5) les seuils de comportement qui lisent
    les jauges (saleté affichée, marche, fin de partie, doudou, « pas fatigué », véto, coffre) via `PetCtx.rules`.
    Restent des littéraux hors jauges : `BunnyBrain` (décisions), les durées de `Tuning`, les seuils de texte de la
    fiche du véto (`PetMenu`), et l'influence du caractère sur le **comportement** (`PLAYFUL` joue plus longtemps,
    `LAZY` marche lentement, `GLUTTON` au coffre, seuil d'attention des bêtises) — à porter par `Species` / le
    cerveau à l'étape 7.
  - **`Personality.modifiers: Map<StatMod, Float>`** (`Personality.kt`) remplace `p(k, yes)` : `SATIETY_LOSS`,
    `HAPPINESS_LOSS`, `ENERGY_LOSS`, `FOOD_JOY`, `PLAY_JOY`, `PET_JOY`, `APP_VISIT_JOY` (`PersonalityTest` :
    table complète, exhaustivité).
  - **Format retenu : (a) SharedPreferences + codec maison**, pas de `kotlinx.serialization` : aucune dépendance ni
    plugin, chaque champ reste lisible / migrable clé par clé, et le `PetSnapshot` d'échange tient en ~20 lignes de
    texte. `org.json` n'est pas utilisé (stub dans les tests JVM).
  - **Port `PetStore`** (`persist/PetStore.kt`) + adaptateur **`PetRepository`** : seule classe qui lit / écrit les
    animaux ; `load*` sans effet de bord ; `Clock` injecté (horodate `lastUpdate`) ; `newId` injecté (UUID). Le
    **rattrapage hors-ligne** est fait explicitement par le service au démarrage (`Elapsed` depuis `lastUpdate`,
    plafonné à 48 h par `GameRules.maxElapsedHours`) ; les activités ne le font plus (elles affichent l'état
    sauvegardé). `residentOrCreate()` crée le résident (non adopté) au premier lancement. `Pets.repository(ctx)` :
    instance par processus, migrations jouées à la première ouverture.
  - **Migrations** (`persist/PetMigrations.kt`, liste `ALL`) : v0 → v1 lit les 22 clés à plat de l'ancien
    `PetState` (`LEGACY_PET_KEYS`, testées contre un vrai fichier « ancienne version »), convertit l'apparence
    (index → ids, bornés comme l'ancien affichage), génère le `PetId`, écrit le schéma 1 par `commit()` **puis**
    efface les anciennes clés du fichier `"pet"` (les réglages y restent, clés inchangées). Idempotente ; si le
    processus est tué entre les deux, l'ouverture suivante finit le ménage.
  - **Apparence par ids stables** (`Look.kt`) : `Look` immuable de six enums (`Fur`, `Ears`, `EyeColor`, `Pattern`,
    `Accessory`, `AccColor`) dont l'`id` est figé (`LookTest`) ; `Look.byId(…, défaut)` ; `Options` ne garde que les
    utilitaires de couleur. `BunnyRenderState` n'a plus besoin de copier l'apparence.
  - **`PetSnapshot`** (`persist/PetSnapshot.kt`) : représentation portable versionnée (id, espèce, nom, naissance,
    caractère, jouet, apparence par ids, cinq jauges — pas la cage, les crottes, le sommeil) ; texte
    `tamacookie-pet 1` + lignes `clé=valeur` encodées ; `decode` valide (taille ≤ 2 Ko en octets, en-tête, version,
    doublons, id, espèce → rejet ; ids inconnus → défaut ; jauges bornées ; nom nettoyé / tronqué ; champs inconnus
    ignorés). Pas encore branché (transport Bluetooth à venir).
  - **Autorité unique** : `PetCommands.adopt` / `rename` (`PetCommands.kt`) envoient `ACTION_ADOPT` /
    `ACTION_SET_NAME` au service s'il tourne, sinon écrivent par le repository. Le service applique, sauvegarde et
    fête ; une **nouvelle adoption remplace le résident sans stop/start** (`replaceResident` : cache-cache, station,
    menu, panneaux et objets fermés ; fenêtre de l'animal réutilisée sous le nouvel id ; bulle oubliée ; page
    remise à zéro ; il tombe du haut de l'écran). La course au renommage (étape 0) disparaît : le service ne relit
    plus jamais les prefs. `ACTION_RELOAD` ne sert plus qu'aux réglages (niveau de bêtises).
  - **`PetState` a disparu** (pas de façade) : `Pet` porte `identity` (renommer = `rename`), `traits`, `look`,
    `stats`, `adopted` ; `Pet.toRecord()` / `Pet.fromRecord()` ; `PetMenu`, `PetNotifier`, panneaux reçoivent le
    `Pet`, `MenuEntry.text` reçoit `PetStats`.
  - Tests : `PetRulesTest`, `PersonalityTest`, `GameRulesTest`, `PetRepositoryTest` (migration, idempotence,
    reprise, premier lancement, lecture sans effet, adoption / ancien lapin ignoré, renommage), `LookTest`,
    `PetSnapshotTest` ; `FakePetCtx.saved`, helper `testPet()` (remplace `newPet()`).
  - **À vérifier sur téléphone** : mise à jour par-dessus la version installée (le lapin, son nom, son apparence,
    ses jauges, la cage et les réglages doivent être là ; `adb shell run-as com.tamacookie cat
    shared_prefs/pets.xml`) ; adoption et renommage service lancé / arrêté ; l'écran principal juste après
    l'adoption (la commande est asynchrone : nom affiché éventuellement en retard d'un `onResume`) ; un retour à
    une version antérieure perdrait l'animal (anciennes clés effacées).

- **Après l'étape 5 — lapin endormi figé (tablette, build 2fb081d)** : non reproduit. Sur émulateur Android 16
  (API 36, accessibilité active), des builds **propres** de 2fb081d et de 5d552af animent le sommeil (respiration,
  Zzz), y compris boucle suspendue (forcée) ; la tablette n'a enregistré ni plantage ni ANR (`dumpsys activity
  exit-info`). Seule cause trouvée d'un écran figé après « Dodo » : un APK incrémental périmé (voir Pièges). La
  boucle est sortie du service dans **`FrameLoop`** (pur : cadence, dt borné, suspension, reprise ; port
  `FrameScheduler` = Choreographer + Handler) et `AmbientAnimator.frame` décide du redessin ; `FrameLoopTest`
  simule les images (faux Choreographer à horloge virtuelle) : un animal visible qui dort reçoit ≈ 12 i/s, chaque
  image redessine, le temps avance, des Zzz apparaissent ; idem IDLE / SIT / EAT / tenu.

#### Schéma de persistance v1

Fichier **`pets`** (SharedPreferences, `Prefs.PETS_FILE`) :

| Clé | Type | Contenu |
|---|---|---|
| `schema_version` | Int | `1` (absent = schéma 0 : animal dans le fichier `"pet"`) |
| `pets` | String | ids des animaux séparés par `,`, ordre d'arrivée |
| `resident` | String | id de l'animal qui vit sur ce téléphone |
| `pet.<id>.name` / `.species` | String | nom (≤ 16), espèce (`rabbit`) |
| `pet.<id>.bornAt` / `.lastUpdate` | Long | naissance, dernière sauvegarde (ms, heure murale) |
| `pet.<id>.adopted` | Boolean | écran d'adoption validé |
| `pet.<id>.personality` / `.favoriteToy` | String | nom d'enum `Personality` / `Toy` |
| `pet.<id>.look.fur` / `.ears` / `.eyes` / `.pattern` / `.accessory` / `.accColor` | String | `id` de l'option (`chocolate`, `lop`…) |
| `pet.<id>.satiety` / `.happiness` / `.energy` / `.cleanliness` / `.health` | Float | jauges 0..100 |
| `pet.<id>.sleeping` / `.caged` | Boolean | endormi, en cage |
| `pet.<id>.poops` / `.visitedApps` | Int | crottes à l'écran, apps visitées |
| `pet.<id>.poopAcc` | Float | fraction de la prochaine crotte |

Fichier **`pet`** (réglages, inchangé) : `enabled`, `mischief`, `dock_fx`, `dock_fy`, `pageW2_<largeur>`,
`st_<STATION>_anchor` / `_fx` / `_fy`. Valeur inconnue ou absente → défaut (`PetFields.read`).

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

### Ajouter une stat (ex. : la soif)

1. `PetStats` : `val thirst: Float = 80f` ; `PetRules.clamp` : la borner ; `PetRules.elapsed` : son évolution
   (taux dans `GameRules`, ex. `thirstLossPerH`) ; un `PetEvent` qui la remonte (`Drank`), sa branche dans
   `apply` et dans `savesNow` (le compilateur exige les deux) ; l'humeur dans `PetRules.mood` si besoin
   (`Mood.THIRSTY` → `Lines`, `PetNotifier`, `PetMenu`, `BunnyRenderState.moodExpr` : signalés par le compilateur).
2. Persistance : `PetFields` (`THIRST = "thirst"`, `write` + `read` avec défaut) — **pas de migration** : une clé
   absente prend la valeur par défaut. `PetSnapshot` : l'ajouter seulement si elle a du sens sur un autre
   téléphone (champ ignoré par les anciennes versions, défaut chez nous s'il manque).
3. Si un caractère la module : une entrée `StatMod` et sa valeur dans `Personality.modifiers`.
4. Tests : `PetRulesTest` (taux, bornes) — la trace de référence ne change pas tant que la nouvelle stat
   n'influence pas les autres ; `PetRepositoryTest` (aller-retour).

Un changement de **forme** (renommer / scinder une clé, changer une unité) demande une migration : une entrée à la
fin de `PetMigrations.ALL`, `SCHEMA_VERSION` + 1, et un test à partir d'un fichier de la version précédente.

### Ajouter une personnalité (ex. : peureux)

1. `Personality` (`Personality.kt`) : ajouter **à la fin** `SHY("Peureux", "🙈", "…", mapOf(StatMod.HAPPINESS_LOSS
   to 1.1f))` — le nom d'enum est sauvegardé et échangé (`PetSnapshot`) : ne plus le changer.
2. `PersonalityTest` : sa ligne dans la table `expected` (le test d'exhaustivité l'exige).
3. Comportement propre (hors jauges) : là où il agit (`BunnyBrain.attentionThresholdMs`, `ToyController`…), en
   attendant `Species` / le cerveau par animal (étape 7). L'écran d'adoption la propose tout seul.

Un pair qui envoie un caractère inconnu (version plus récente) le voit remplacé par `CUDDLY` (`PetSnapshot.decode`).

Règles pour chaque étape : pas de big-bang, pas de nouvelle dépendance sans justification écrite,
ajouter des tests JVM pour tout ce qui devient pur, mettre à jour ce fichier (colonne État + écarts).
