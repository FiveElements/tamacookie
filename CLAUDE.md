# TamaCookie — guide pour Claude

Lapin façon tamagotchi qui vit en **overlay** (WindowManager) par-dessus l'écran Android.
Kotlin 2.0, Android 13+ (minSdk 33, compile/target 34), deux modules — `:core` (Kotlin/JVM pur) et `:app`
(Android, dépend de `:core`) —, aucune dépendance runtime.
Code, commentaires, commits et UI sont **en français**.

## Commandes

```bash
./gradlew test                # tous les tests JVM (JUnit 4) — doivent rester verts à chaque étape
./gradlew :core:test          # tests du cœur (l'essentiel : ~470) ; ceux de l'app : ./gradlew testDebugUnitTest
./gradlew assembleDebug       # APK : app/build/outputs/apk/debug/
./gradlew :core:jacocoTestReport createDebugUnitTestCoverageReport   # couverture JaCoCo des deux modules :
#   core/build/reports/jacoco/test/ (html/, jacocoTestReport.xml) et app/build/reports/coverage/test/debug/ (index.html, report.xml)
#   total : python .github/scripts/coverage_badge.py core/…/jacocoTestReport.xml app/…/report.xml sortie.svg
./gradlew lintDebug           # ⚠️ plante avec JDK 25 (UastFacade) : lancer Gradle avec un JDK 17/21
```

## Pièges connus

- **Le cœur est dans `:core`** (Kotlin/JVM pur, `core/src/main/kotlin`) : il ne voit aucune classe Android, le
  compilateur le garantit. Une logique testable va dans `:core` ; ce qui touche Android (vues, `Canvas`,
  `SharedPreferences`, `Context`, services) reste dans `:app` derrière un port (`SettingsStore`, `PetStore`,
  `OverlayHost`, `ScreenSensor`, `Clock`…). Une constante Android utile au cœur y est **recopiée** et vérifiée par
  `AndroidConstantsTest` (app) : `Haptic`, `AppCategory`.
- `unitTests.isReturnDefaultValues = true` ne concerne plus que les tests de `:app` : les classes Android (`Rect`,
  `Color`, `org.json`…) y renvoient silencieusement 0/false/null. **Ne pas y tester de logique** : la mettre dans
  `:core`.
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
Depuis l'étape 6, l'écran des autres applications n'est connu que par le port `ScreenSensor` (`PetCtx.screen` :
`ScreenSnapshot` immuable + `UserSignal` sans perte ; faux : `FakeScreenSensor`) et l'app au premier plan par
`ForegroundAppSource` ; `TamaApp`/`AppGraph` (DI manuelle) relient le service d'accessibilité à `BunnyService`.
Depuis l'étape 7, plusieurs animaux vivent ensemble : l'espèce est un plugin (`species/`, lapin = `species/rabbit/`),
chaque animal affiché passe dans la boucle (`PetBehavior`) avec **lui pour animal courant** (`World.asPet` →
`PetCtx.pet`), `Director` arbitre ce qui est unique à l'écran et `social/` porte les interactions à deux.
Depuis l'étape 8a, ce cœur est le module `:core` (JVM pur) : `:app` ne garde que les adaptateurs Android (service,
activités, vues, fenêtres, dessin, persistance sur SharedPreferences, accessibilité, notification, widget, tuile).
Problèmes à résorber (audit) :
- `BunnyService` (~1 270 l.) est un god-object : ~16 responsabilités, ~45 `internal var`.
- `StationController`, `ToyController`, `MischiefController`, `PageTracker`, `OverlayWindows` reçoivent
  `svc: BunnyService` et modifient ses champs ; ils s'appellent en cycle via `svc`.  → étape 2 : ils reçoivent `PetCtx` et agissent sur le `Pet`.
- Le `when (act)` central de `updateBehavior` et les sous-ensembles de `Act` (BunnyBrain.kt) dispersent
  chaque fonctionnalité sur ~6 à 14 endroits.  → étape 3 : `ActRegistry` ; il ne reste au `when` que la
  locomotion et le sommeil (voir écarts 3b).
- Les vues sont de l'état de jeu (`bunny.pose`, `bunny.facing` écrits par 4 classes).  → étape 4 : `BunnyRenderState`.
- `Platforms` (dans ScreenSensorService.kt) : état global `@Volatile`, compteurs `*Version` qui perdent des événements.
  → étape 6 : port `ScreenSensor` (`ScreenSensorHub`), signaux en file, scan sur `HandlerThread` ; `Platforms` supprimé.
- `PetState` mélange règles, persistance et `Context` ; `Look` stocke des **index** (fragile).  → étape 5 :
  `PetStats` + `PetRules`, `PetRepository` (schéma versionné, ids stables), `PetState` supprimé.

## Architecture cible : hexagonal + monde d'entités (ECS léger)

```
:core (Kotlin/JVM pur, module depuis 8a)                                 :app (Android)
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

Depuis 8a, les **packages** sont restés ceux d'avant (`com.tamacookie`, `.feature.*`, `.species.*`, `.social`,
`.persist`…) des deux côtés : seul le module dit ce qui est pur. Le dessin d'une station / d'un objet / d'une espèce
est dans `:app` à côté de son comportement (même package, fichier `…Drawer.kt` / `…Renderer.kt`).

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
| 6 | Port `ScreenSensor` devant `Platforms`, événements au lieu des compteurs, scan sur `HandlerThread` | ✅ |
| 7 | Multi-animal : `Species` (lapin = 1re), `Director`, `SocialSystem`, rendu de plusieurs animaux plafonné | ✅ |
| — | **Prochaine** : évolutions de contenu (stations, jouets, interactions, espèces) sur les plugins — voir « Évolutions » | ⏳ |
| 8 | Qualité : **8a** module `:core` (JVM pur) ✅ ; **8b** detekt ; **8c** chaînes dans `strings.xml` ; **8d** accessibilité | ⏳ |
| — | Ensuite : échange Bluetooth (BLE + L2CAP) — visites et transfert, protocole en deux phases | |

### Évolutions (contenu sur les plugins : comportement existant inchangé, seulement des ajouts)

| # | Évolution | État |
|---|---|---|
| 4a | Contenu peu coûteux : stations lit / gamelle / roue / jardin, jouets corde / pelote / tunnel, nourritures pomme / foin / friandise, bêtises bord de l'écran / langue, menu chanter / brosser | ✅ |
| 4b | Caractère et apparence : personnalités peureux / farceur / explorateur, pelages roux / tacheté crème / bleu-gris, accessoires couronne / bandana / nœud papillon, thèmes de saison (Noël, Halloween, printemps) | ✅ |
| 4c | Bien-être numérique (pause proposée, bouderie ; réglage, coupé par défaut), widget d'écran d'accueil (humeur, jauges, Nourrir, Appeler), tuile Réglages rapides (montrer / cacher) | ✅ |

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
- **Ordre des fenêtres** (bug : la station coffre, rajoutée à un changement de page, recouvrait le panneau 🏠) :
  `WindowLayer` (`OverlayHost.kt`) **PET < STATION < PROP < DOCK < BUBBLE < PANEL < MENU**, porté par
  `WindowId.layer`. L'animal est sous les stations (rebord de la baignoire, barreaux de la cage : `Seat` le fait
  asseoir dedans), comme à l'origine où il était ajouté avant elles — l'écart avec un ordre STATION < PET est
  voulu. `WindowTable` tient l'empilement réel (`stacking`) et, après chaque ajout, `relayout` ou `flush` ayant
  déplacé une fenêtre, **retire puis rajoute** toute fenêtre d'une couche supérieure qui se retrouve sous une
  fenêtre d'une couche inférieure **qu'elle chevauche** (`WindowBackend.width/height`, inconnue = chevauche) ;
  les autres ne bougent pas (pas de clignotement du dock à chaque station qui apparaît). Statistique : `restacks`.
  Le détacher / rattacher de la cage dans `followHolder` a disparu (garanti par les couches).
  Tests : `WindowLayerTest` (faux WindowManager qui empile dans l'ordre d'ajout, 50 ordres d'ajout aléatoires).
- **Couverture des tests** : JaCoCo **intégré à AGP** (`enableUnitTestCoverage = true` sur `debug`, aucun
  plugin ajouté ; `android.testCoverage.jacocoVersion = "0.8.13"` pour lire les classes des JDK récents). AGP
  exclut `R`, `BuildConfig`, `Manifest`. Rapport HTML + XML. CI : la tâche tourne avec les tests, le rapport est
  un artefact (`coverage-report`) et le taux va dans le résumé du job ; le job `coverage-badge` (push sur `main`
  seulement, seul à avoir `contents: write`) régénère le SVG avec
  `.github/scripts/coverage_badge.py` (taux de **lignes**, style « flat ») et le pousse (force, un seul commit)
  sur la branche orpheline **`badges`** : `main` est protégée (statut `build` requis), le bot ne peut pas y
  pousser. Le README lit `raw.githubusercontent.com/.../badges/coverage.svg`. La CI ne tourne pas sur `badges`. Pas de `cicirello/jacoco-badge-generator` : il ne lit que le CSV
  de JaCoCo, qu'AGP ne produit pas. Mesure de départ (7f49e65) : **41,5 % des lignes** (43,1 % des
  instructions, 38,9 % des branches) ; à 0 % : `BunnyService`, `MainActivity`, `PetMenu`, `AdoptActivity`,
  `TouchHandler`, `OverlayWindows`… (adaptateurs Android, attendus) ; `BunnyView` 0,8 %.

- **Étape 6** (capteur d'écran ; `Platforms` supprimé, `ForegroundAppWatcher` devenu pur) — 5 commits :
  - **Port `ScreenSensor`** (`ScreenSensor.kt`, pur) : `snapshot: ScreenSnapshot` (data class immuable : `connected`,
    `screenOn`, `platforms: PlatformSet` = `Platform(Box, label)` + `boxes` + `version`, `ime: Box?`,
    `foregroundPkg`, `viewKey`, `launcherPkg`, `pageCount`, `pageW`, `scroll: ScrollSamples` x0/t0/x1/t1),
    `observe(listener)` (**`Observable` maison**, fil principal), `takeSignals(): List<UserSignal>` (`Click(box)`,
    `Typing`, `Scroll`), `setPageWidth(w)` (`PageTracker` y écrit la largeur mémorisée / l'oublie à la rotation).
    **Pas de coroutines** : tout est sur le fil principal, un appel direct suffit (aucune dépendance ajoutée).
  - **`ScreenSensorHub`** (pur) = implémentation unique, tient ce qui était dans `Platforms` : suivi du défilement du
    bureau, calage au repos (`settle` : largeur de page apprise, recalage « bouton Accueil »), version des
    plateformes, file de signaux (plafond 64, les plus récents gardés). **`epoch`** : incrémenté à chaque vidage
    venu d'ailleurs qu'un scan (défilement, écran éteint, déconnexion) ; un scan lancé avant met à jour la vue et
    les pages mais **ne republie pas** ses plateformes (avant, scan et événement ne pouvaient pas se croiser).
  - **`ScreenScan`** (pur) : `parsePage`, `detectionBand`, `Geometry.accepts`, `ignoredPkg`, et le **parcours** de
    l'arbre (`walk`, DFS, 800 nœuds, 60 plateformes, pas de chevauchement au même `top`) derrière `NodeReader<N>`
    (pas d'enveloppe par nœud ; adaptateur `NodeInfoReader` sur `AccessibilityNodeInfo`). `Box` : `width`,
    `height`, `isEmpty`, `intersects` (sémantique de `Rect`). Plus de `android.graphics.Rect` dans les bêtises.
  - **Adaptateur `ScreenSensorService`** : publie dans le hub (`onConnected`, `onScreenOff/On`, `onViewScrolled`,
    `onClick`, `onTyping`, `onScan`). **Fils** : le fil principal reçoit les événements et décide quand scanner
    (mêmes délais, ≤ 1 scan / 600 ms, lapin arrêté = pas de scan) ; le **`HandlerThread` « TamaCookie-scan »**
    fait les appels IPC (`windows`, `rootInActiveWindow`, `getChild`…) avec des paramètres capturés (bureau
    connu, géométrie, epoch) et renvoie un `ScanResult` immuable par `post` ; le fil principal le replie dans
    le hub, qui reste à **un seul écrivain** sans verrou (d'où un `ScanResult` plutôt qu'un `ScreenSnapshot`
    fabriqué sur le fil du scan). Un scan à la fois (une demande pendant un scan est rejouée au retour).
    `onDestroy` : `quit()` du fil, résultats en vol ignorés. `onInterrupt` reste vide (il ne signale qu'une
    interruption du retour à l'utilisateur, le service reste connecté). minSdk 33 : `recycle()` est sans effet,
    rien à recycler. Une exception de lecture est journalisée (avant : plantage du processus).
  - **`TamaApp` + `AppGraph`** (`TamaApp.kt`, déclarée dans le manifeste) : `screen` (hub unique) et
    `foregroundWatcher()` (un par `BunnyService`). Le service d'accessibilité publie par `AppGraph.of(this).screen`,
    `BunnyService` l'expose en `PetCtx.screen`. `Pets.repository` reste un singleton à part (inchangé) ;
    `BunnyService.running` (statique) est toujours lu par le service d'accessibilité (garde de l'étape 0).
  - **Port `ForegroundAppSource`** (`ForegroundAppSource.kt`) : `AccessibilityForegroundSource` (snapshot),
    `UsageStatsForegroundSource` (`AndroidForegroundApps.kt` : requête ≤ 1 / 3 s, permission revérifiée / 30 s,
    `Clock` injectée), composite **`ConnectionAwareForegroundSource`** (accessibilité si connectée, UsageStats
    jamais interrogé ; sinon UsageStats). `ForegroundAppWatcher` (pur) : écran allumé, changements seulement,
    notre app / le volet ignorés, `describe` injecté (`AppDescriber`).
  - **Signaux sans perte, écarts voulus** (`MischiefController.checkUserEvents`, une fois par image) : chaque
    **frappe** compte pour la rafale de 12 (avant : une par image où l'on avait tapé ; identique sauf frappe plus
    rapide que la cadence) ; chaque **clic** est proposé aux bêtises dans l'ordre, jusqu'à la première qui réagit
    (avant : deux clics dans la même image n'en faisaient qu'un, avec le dernier rectangle) ; le **défilement**
    compte une fois par image (il arrive en rafale). Comme avant : les signaux d'avant le premier passage sont
    ignorés ; ceux reçus endormi / sage / dans une station / caché sont consommés sans réaction ; occupé (pas
    `FREE`), les frappes comptent mais les clics sont perdus. `UserEvents.click: Box?`.
  - Écart mineur : une fenêtre active **sans nom de package** est traitée comme « pas de fenêtre » (avant :
    parcourue avec un package nul).
  - **Travail a807 (session précédente, non fusionné)** : repris = les cas limites de `ScrollMathTest`, et l'idée
    d'un état remis à zéro par test (ici : un `FakeScreenSensor` par `FakePetCtx`, plus d'état global). **Non
    repris** (changements de comportement, pour une étape dédiée) : filtrage des noms (`safeLabel` : codes,
    soldes, numéros), apps sensibles (`isSensitivePkg`, `sensitiveScreen`), `setEventTypes(0)` écran éteint,
    relecture des launchers à l'installation d'une app, scan toutes les 1,5 s, `notificationTimeout` 250 ms,
    nouveau texte de description du service.
  - Piste : `getRootInActiveWindow(FLAG_PREFETCH_DESCENDANTS_HYBRID)` (API 33) réduirait les IPC du scan.
  - Tests : `ScreenScanTest` (faux arbre), `ScreenSensorHubTest` (signaux, défilement, scans, calage, epoch,
    écran éteint, observable), `UserSignalsTest` (2 clics entre 2 images, rafale arrêtée, frappes, défilement),
    `ForegroundAppSourceTest`, `PageTrackerTest` et `MischiefTest` (squat sur icône / clavier) alimentés par le
    port ; `FakePetCtx.screen`, `resetPlatforms()` supprimé. 332 → 376 tests ; couverture **41,3 → 44,3 % des
    lignes** (43,0 → 46,0 % des instructions, 38,8 → 45,0 % des branches) ; `ScreenSensorHub`, `ScreenScan`,
    `ForegroundAppWatcher` à 100 %.
  - **À vérifier sur tablette** (non couvert par les tests JVM) : pages du bureau (vue, page du lapin,
    cache-cache), défilement (le lapin suit les pages, recalage après « Accueil »), sauts sur les icônes et chute
    quand elles bougent, bêtises au clic / à la frappe (squat bouton / clavier au niveau 2), voyage d'app en app
    avec et sans accessibilité (UsageStats), écran éteint puis rallumé, service d'accessibilité désactivé pendant
    que le lapin tourne.

- **Étape 7** (multi-animal ; `BunnyService` 992 → 857 l. ; `BunnyView` 563 l. → `PetView` 127 + `RabbitRenderer`
  488) — 6 commits (Species ; boucle par animal ; Director ; SocialSystem ; adoption + UI ; ce fichier) :
  - **`Species`** (`species/Species.kt`, plugin) : `id` (sauvegardé : `rabbit`), `label`, `emoji`, `SpeciesSpec`
    (fenêtre, pieds, haut de la tête pour la bulle, vitesses de marche / voyage / page / cache-cache — ex-`Tuning.BUNNY_*`,
    `WALK_*`, `LAZY_SPEED_FACTOR`, `TRAVEL_*`, `PAGE_OUT_DP`, `HIDE_RUN_DP`), `behavior(Personality) → BehaviorMods`
    (influence du caractère sur le **comportement** : vitesse, durée de partie, seuil d'attention, exploration, saut sur
    les icônes, sommeil / sieste, gourmandise au coffre, envie d'aller voir un autre animal — ex-littéraux de
    `BunnyBrain`, `ToyController`, `StationController`, du service : mêmes valeurs et **même consommation du
    `Random`**, `BunnyBrainTest` inchangé), `accepts(StationKind)` / `accepts(Toy)` (glisser, entrée, partie),
    `lines: SpeciesLines` (ex-`Lines` → `RabbitLines`), `renderer(density): PetRenderer`. Registre
    `Species.ALL` / `byId` / `of` (inconnue → défaut) ; `Pet.species` résolu une fois par `Species.of(identity.species)` ;
    `PetSnapshot.SPECIES_KNOWN` = le registre. `Identity.RABBIT` a disparu (`Rabbit.ID`).
  - **Rendu** : `PetView` (générique : état, `AmbientAnimator`, particules, hit-test délégué) + `PetRenderer` ;
    `RabbitRenderer` = le dessin de l'ancien `BunnyView` (pinceaux, apparence, posture, visage), sorti tel quel.
    `BunnyRenderState` → `PetRenderState` ; `Pose` / `Expr` / `PType` au premier niveau (`PetRenderState.kt`).
    **Noms historiques gardés** (renommer coûte plus que ça n'apporte, ils servent à toutes les espèces) :
    `BunnyService`, `BunnyBrain`, `BunnyPhysics`, `BunnyActor`, `PageTracker.bunny*`, `Arena.bw/bh`.
    La fenêtre d'un animal a le gabarit de l'espèce **par défaut** (`Arena.bw/bh` : une seule espèce aujourd'hui).
  - **Ce qui reste « lapin » hors de `species/rabbit/`** : des répliques de fonctionnalités (carottes, « lapin » dans
    quelques textes de stations / jouets / menu / notification), les textes de l'écran principal et de l'adoption, le
    nom du canal de notification — du **contenu** (→ `strings.xml` à l'étape 8), pas du comportement.
  - **Animal courant** (`World.current`, `World.asPet(p) { … }`, `PetCtx.pet`) : la boucle, le toucher, le menu, les
    commandes l'installent ; les fonctionnalités voient l'animal dont on s'occupe sans changer de signature, et
    `PetCtx.rnd` (= `Mind.rnd` de l'animal courant), la bulle (`say`), la page (`PageTracker.bunnyPage` /
    `bunnyShift` / `returnAt` = `Body.page` / `pageShift` / `pageReturnAt`) et `bunnyAway` / `setBunnyAway` /
    `placeBunny` (raccourcis de `isAway` / `setAway` / `place(pet)`) sont les siens. Le résident garde **le même**
    `Random` qu'avant (`residentRnd`) ; chaque compagnon a le sien. `World.residentId` (explicite) ; `World.remove`.
  - **Boucle par animal** : `PetBehavior` (pur) = l'ancien `when` de `updateBehavior` + `decideNext`,
    `checkPlatform`, vol, atterrissage, bavardage, danse, `onAppChanged`, avec des `Hooks` (coffre, bêtises, page
    voisine, heure, social). `BunnyService.update` : chaque animal **affiché** (`World.shown`, résident en tête, au
    plus `Tuning.MAX_PETS_SHOWN = 3`) passe dans la boucle ; avec un seul animal, **même ordre et mêmes tirages**
    qu'avant (`MultiPetTest` : les décisions du résident sont identiques avec ou sans compagnon). Les autres
    « dorment hors écran » : jauges (`Elapsed`) seulement, ni fenêtre ni crotte. Jauges, sauvegarde, rotation,
    écran éteint : **tous** les animaux. Crottes : `Prop.owner` (ramassée = jauges de son auteur, qui remercie).
    Coffre : `tickChest` une fois par image, `checkChestBump` par animal (`chestPopFor`). Cadence : rapide si un
    animal bouge, lente si tous dorment ; suspension si aucun n'est visible.
  - **Fenêtres par animal** : `petWindows` / `awayPets` / `viewOwner` (la vue du résident est réutilisée à
    l'adoption) ; `TouchHandler.attach(view, petOf)` : un écouteur par fenêtre, le geste agit sur **l'animal
    touché**, le menu sur l'animal pour lequel il a été ouvert (`menuPet`, `closeMenuOf`) ; la cage transmet ses
    touchers à son occupant (`StationController.occupantOf`, `TouchHandler.forward`).
  - **`MischiefState` dans `Mind.mischief`** (comme prévu en 3b) ; `MischiefController.endHiding(p)` ; `hidingView`
    = celui d'un animal quelconque ; l'animal qui réagit aux clics / frappes est l'auteur de la bêtise en cours,
    sinon le premier affiché libre (un seul animal : comme avant).
  - **`Director`** (pur, `PetCtx.director`) : tout est **déduit du monde** (activités, occupation, objets) plutôt que
    réservé — rien à libérer, rien qui fuie quand un animal part. Bêtises : `mischiefOwner` / `mayStartMischief`
    (vérifié **avant** tout tirage : un refus ne consomme pas d'aléatoire), cache-cache du menu refusé si un autre
    joue ; coffre : `mayUseChest` (le cerveau ne le voit pas s'il est pris) ; stations de capacité 1 : déjà gardées
    par `Occupancy.canEnter` ; nourriture : `foodWinner` (le plus proche, puis le plus affamé, puis l'ordre du
    monde), le perdant renonce (`loseFood` / `mayChaseFood`, jusqu'à la prochaine) et le différend est noté
    (`takeDisputes`) ; bulles : `BubbleQueue` (une réplique à l'écran à la fois ; un animal remplace toujours la
    sienne ; un autre attend, ou passe devant s'il est plus prioritaire — `SpeechPriority` LOW / NORMAL / HIGH,
    `Speech.at(p) { … }` : HIGH pour les gestes, le menu et les commandes, LOW pour le bavardage et le social ;
    3 en attente, LOW abandonnée après 5 s ; la bulle interrompue est cachée) ; notifications : `title`
    (« Cookie se promène · +1 compagnon »), `statusLine` (jauges du résident + humeur de chaque compagnon), `alerts`
    (une alerte qui réunit les animaux qui viennent de basculer). Un seul animal : textes et alertes d'avant.
  - **`SocialSystem`** (`social/`, pur) : `nearest(p)` → `BrainInput.nearest: Neighbor(id, act, distance)` (même
    page, ni caché, ni ailleurs, ni dans une station) ; `Decision.Socialize`, tirée **seulement** si un autre animal
    tranquille est à moins de `SOCIAL_RANGE_DP` (`BehaviorMods.socialChance` 0,25, câlin 0,4) — un animal seul
    ne tire rien de plus. `SocialInteraction` (plugin : `chance`, `canStart`, `start`, `update`, `observe`,
    `travel`), `Engagement` dans `Mind.social`, `Act.SOCIAL` (dans `MOVING`) via `ActRegistry` ; cascade avec le
    `Random` de l'initiateur. Interactions : `HugSocial` (câlin mutuel : approche, face à face, cœurs),
    `FollowSocial` (il suit le meneur, qui n'est pas engagé), `PlayBallSocial` (déclenchée par le monde : deux
    animaux en `PLAY` avec la balle — ce que fait déjà la boucle — se font des passes), `CarrotDispute` (mise en
    scène du différend tranché par le `Director`). Une caresse, un glisser, un voyage interrompent l'engagement.
    Pas de « faire visiter » (il viendra avec l'échange Bluetooth).
  - **Adoption d'un compagnon** : `PetStore.addCompanion` / `companions`, `PetRepository.adoptCompanion` (toujours
    un nouvel animal, plafond `Tuning.MAX_PETS = 6`, jamais sans résident) / `removeCompanion` (jamais le
    résident) ; `PetCommands.ACTION_ADOPT_COMPANION` / `ACTION_REMOVE_PET` (service lancé ; sinon repository).
    Le service fait tomber le compagnon du ciel ; au départ d'un compagnon : cache-cache, station, menu, bulle,
    crottes et fenêtre nettoyés, et un animal qui dormait hors écran prend la place. Un **nouveau lapin** remplace
    toujours le résident et **garde les compagnons**. `AdoptActivity` : `EXTRA_COMPANION` (« Adopte un compagnon »,
    nom et apparence au hasard) et « …ou l'adopter comme compagnon » à côté de « Adopter ! ». `MainActivity` :
    « Tes animaux » (nom, rôle, jauges, « dort hors écran »), « Retirer », « Adopter un compagnon ».
  - **Persistance** : schéma v1 inchangé (la liste `pets` existait), aucune clé renommée, **aucune migration** ;
    chaque animal est sauvegardé par son `PetId`, le résident reste celui de `resident`.
  - **Reste propre au résident, volontairement** : l'accueil, « te revoilà », « me revoilà » après « Montrer », le
    renommage (`ACTION_SET_NAME`) et la personnalisation, « Nourrir » de la notification (`ACTION_FEED` nourrit le
    résident ; les autres accourent et le `Director` tranche), le titre de la notification (les compagnons y sont
    résumés) — ce sont des réponses de « l'animal du téléphone ». Les panneaux véto / coffre / 🏠 restent uniques
    (un à la fois, pour l'animal concerné). `replaceResident` retire tous les objets (les crottes des compagnons
    reviennent aussitôt). La fenêtre a le gabarit de l'espèce par défaut.
  - Coût IPC : ≤ 1 `updateViewLayout` par fenêtre déplacée et par image (inchangé) ; avec 3 animaux qui marchent,
    3 fenêtres d'animal (+ une bulle à la fois) ; le plafond d'affichage borne ce coût.
  - Tests : `SpeciesTest` (registre, exhaustivité, valeurs du lapin), `MultiPetTest` (animal courant, plafond,
    `Random` / bulle / page / fenêtre / crottes / bêtise par animal, boucle à deux, voyage), `DirectorTest` (une
    bêtise sans tirage, coffre, station de capacité 1 à deux animaux, nourriture, bulles, notifications),
    `SocialSystemTest` (perception, cerveau seul inchangé, cascade seedée, suivre, câlin, interruption, dispute,
    passes, boucle à deux déterministe), `PetRepositoryTest` (compagnons). `FakePetCtx` : `first`, `addPet`,
    `speakers`, `away`, `placed`, `director`. 376 → 424 tests ; couverture **44,3 → 48,3 % des lignes** (46,0 →
    49,0 % des instructions, 45,0 → 47,7 % des branches) ; `Director` 98 %, `SocialSystem` 98 %, interactions
    100 %, `PetBehavior` 65 %.
  - **À vérifier sur tablette** : un seul animal = comportement d'avant (balade, sauts, sommeil, voyage, bêtises,
    stations, bulle, notification) ; adopter un compagnon (service lancé puis arrêté), le retirer ; deux lapins qui
    se suivent, se font un câlin, des passes à la balle, se disputent une carotte ; quatrième animal « dort hors
    écran » (plafond de 3) puis prend la place d'un compagnon retiré ; la baignoire avec deux animaux (un seul
    dedans) ; un seul au coffre ; une seule bulle à la fois ; toucher / menu / glisser du **bon** animal ; cage et
    changement de page avec deux animaux ; nouveau lapin qui garde les compagnons.

- **Évolution 4a** (contenu peu coûteux ; recettes « Ajouter… » suivies, ajouts **à la fin** des enums) — 4 commits
  (jouets + nourritures ; stations ; bêtises + menu ; ce fichier) :
  - **Stations** (`StationKind` : `BED`, `BOWL`, `WHEEL`, `GARDEN` ; clés `st_<NOM>_*` créées d'elles-mêmes) :
    - 🛏️ **Lit** (`BedStation`, `capacity = 2`, `slowFrames`) : il s'y endort (pas fatigué — énergie > `notTiredAbove`
      — il s'assoit 3 s et ressort) ; endormi, **`PetEvent.Rested(hours)`** chaque image (énergie
      `bedEnergyGainPerH` = 25/h **en plus** du temps qui passe ; sans effet éveillé ; pas de sauvegarde immédiate).
      Une caresse le réveille **sans** mauvaise humeur (`Woken(grumpy = false)`), il sort 3 s après ; le sortir
      (glisser, changement de vue) le réveille. Deux places : `StationBehavior.seatFor(rang)` (rang d'arrivée dans
      `Occupancy`, lu par `positionInStation`) — le premier garde sa place quand le second sort.
      **`StationBehavior.sleepsInside`** (défaut = `holdsPet`) : « Dodo » (`BunnyService.startSleep`) le laisse dans
      le lit comme dans la cage. Le lit n'est **pas** `holdsPet` (il ne suit pas l'animal d'une vue à l'autre).
    - 🥣 **Gamelle** (`BowlStation`, nourriture `Food.HAY`) : il renifle 1 s ; s'il a faim (satiété <
      `bowlHungryBelow` = 70 et repas non refusé), il mange 3 s (`Pose.EAT`, `eatProgress`) puis
      `PetEvent.Fed(HAY)` — un repas ordinaire ; sinon il ressort. **Écart** : il n'y va pas tout seul (on l'y dépose) ;
      aller à la gamelle de lui-même demanderait une décision du cerveau (comme `GOTO_CHEST`) — à faire avec une
      évolution « cerveau ».
    - 🎡 **Roue** (`WheelStation`, `animated` : les rayons tournent quand elle est occupée) : il court 6 s
      (`Pose.WALK`), puis **`PetEvent.WheelRun`** (énergie −10, bonheur +10 × `PLAY_JOY`, satiété −3 ; refusé sous
      `playMinEnergy` ou endormi : il le dit et ressort aussitôt).
    - 🌱 **Jardin** (`GardenStation`) : il grignote 2,5 s puis se roule 2,5 s (il change de côté), puis
      **`PetEvent.Grazed`** (bonheur +5, satiété +3, propreté −4).
    - Gamelle, roue, jardin : `Travel.BUSY` (il commente l'app où l'on va, comme au bain).
    - **Placement par défaut** : quatre stations par rangée (`i % 4`, rangée `i / 4`) ; la première rangée est
      celle d'origine (avant, les stations 5 à 8 se seraient empilées au bord droit).
  - **Jouets** (`Toy` : `ROPE`, `YARN`, `TUNNEL`) : 🪢 **corde** (il la prend dans la gueule, la mâchouille 5 s
    en secouant la tête — un `PlayHit` toutes les 0,6 s —, puis la relance ; `complainsWhenTaken` : on « tire à la
    corde ») ; 🧶 **pelote** (`randomTint` ; coup de patte au ras du sol, rebond 0,18 et frottement 1,2 : elle roule
    loin et rebondit à peine) ; 🕳️ **tunnel** (posé au sol, il va à l'entrée la plus proche puis le traverse d'un
    bout à l'autre, encore et encore ; la fenêtre de l'objet est au-dessus de celle de l'animal — `WindowLayer` —
    donc le tunnel le cache pendant la traversée ; phase dans `ToyCtx.holdTime`, bout visé dans `Body.targetX`,
    pause dans `Mind.kickCooldown`).
  - **Nourritures** (`Food` : `APPLE`, `HAY`, `TREAT`) : pomme (22 / 6 / santé 3), foin (25 / 1 / santé 4),
    🍪 **friandise** (satiété 4, bonheur 18 : la plus forte joie, la plus faible satiété) ajoutée à
    `GameRules.alwaysWelcome` (acceptée même repu, comme la fraise).
  - Coffre : `ChestPanel` en rangées de 4 (`ChestPanel.PER_ROW` ; trois nourritures / quatre jouets = la rangée
    d'avant). Le tirage « jouet au hasard » du coffre et le jouet préféré à l'adoption incluent les nouveaux jouets
    (même nombre de tirages, valeurs plus variées).
  - **Trace de référence** (`PetRulesTest`) : le générateur tire ses nourritures / jouets parmi ceux **d'origine**
    (`TRACE_FOODS`, `TRACE_TOYS`) — sinon l'ajout aux enums changeait l'empreinte sans que les règles changent.
    `GOLDEN` est inchangé ; les nouveaux événements ont leurs propres tests.
  - **Bêtises** : `Mischiefs.ALL` = cache-cache, bouton, **bord de l'écran**, toc-toc, tournis, **langue**.
    - `ChewEdgeMischief` (`Act.CHEW_EDGE` ∈ `MOVING`, `RUSH_BACK`, `BUSY_FOR_TRAVEL`) : dans la cascade **avant
      toc-toc** (0,3 / 0,4 au niveau 2), mais `canStart` exige d'être à moins de 160 dp d'un bord (sans aléatoire) :
      loin d'un bord, la cascade consomme le `Random` exactement comme avant (testé : même choix, même graine) ; tous
      les tests seedés d'avant passent sans retouche. Il court au bord, s'y tourne, mâchouille 4 s (`Pose.EAT`,
      miettes) ; une caresse l'arrête. État : `MischiefState.chewing`. **Écart assumé** : près d'un bord, toc-toc
      devient un peu moins fréquent (une bêtise de plus avant lui).
    - `TongueMischief` (`Act.TEASE` ∈ `BUSY_FOR_TRAVEL`, **`Expr.TONGUE`** : clin d'œil + langue dans
      `RabbitRenderer`) : **réaction** à un clic (5 % / 8 %), pas dans la cascade ; **dernière** de la liste : elle
      ne tire au sort qu'après toutes les autres (le tournis ne consomme rien sans défilement), donc les tirages
      existants ne changent pas. Il se tourne vers le doigt, tire la langue 2,5 s, pause de 30 s ; une caresse le
      fait rire et arrêter.
  - **Menu** : 🎵 **Chanter** (4 notes, danse s'il est libre — même initialisation que `PetBehavior.startDance` ;
    endormi : berceuse, il ne se réveille pas) et 🪥 **Brosser** (**`PetEvent.Brushed`** : propreté +8, bonheur +3 ×
    `PET_JOY`, sauvegardé ; refusé endormi). Six entrées = trois rangées.
  - `PetEvent` (+ branches dans `apply` et `savesNow`) : `Rested` (non sauvegardé), `WheelRun`, `Grazed`, `Brushed`
    (sauvegardés) ; taux dans `GameRules` (`bed*`, `wheel*`, `garden*`, `bowlHungryBelow`, `brush*`). Anciens
    événements inchangés ; **aucune migration** (aucune clé ni nom changé).
  - Couleurs dans `Palette` (objets et stations). `Species.accepts` : le lapin accepte tout (rien à faire).
  - Tests : `ToySpecTest` (ajouts à la fin, friandise, rebond de la pelote vs balle, corde, tunnel),
    `StationBehaviorTest` (lit à deux, réveil, gamelle avec / sans faim, roue, refus, jardin), `StationControllerTest`
    (deux places dans le lit, troisième refusé, placement par défaut), `MischiefTest` (ordre, tirages inchangés loin
    des bords, bord de l'écran, langue), `MenuEntryTest` (chanter, brosser), `PetRulesTest` (nouveaux événements).
    424 → 447 tests ; couverture **48,3 → 50,0 % des lignes** (49,0 → 49,9 % des instructions, 47,7 → 48,9 % des
    branches) ; bêtises et entrées de menu 100 %, stations 52–74 % et jouets 61–70 % (le dessin, Android, n'est
    pas couvert).
  - **À vérifier sur tablette** : dessin des quatre stations (le lapin est **sous** la station : couverture du lit,
    bol, rayons de la roue, herbe) et leurs places (deux lapins dans le lit) ; « Dodo » dans le lit ; caresse au
    lit ; roue qui tourne ; gamelle repu / affamé ; panneau 🏠 avec huit stations (hauteur) et placement par défaut
    sur deux rangées ; coffre sur deux rangées ; corde dans la gueule (position, secousse), pelote, tunnel qui cache
    le lapin ; bêtise du bord (près d'un bord) et langue (au toucher de l'écran) ; menu à six boutons.

- **Évolution 4b** (caractère, apparence, saisons ; recettes « Ajouter une personnalité » suivies, ajouts **à la
  fin** des enums) — 4 commits (personnalités ; apparence ; saisons ; ce fichier) :
  - **Personnalités** (`Personality` : `SHY`, `PRANKSTER`, `EXPLORER` — noms sauvegardés et échangés, ordinaux
    0..4 inchangés) :
    - 🙈 **Peureux** : `HAPPINESS_LOSS` 1,15, `PET_JOY` 1,2 (bonheur plus sensible dans les deux sens) ;
      comportement : `socialChance` 0,1 (se lie lentement), `platformChance` 0,25, `attentionFactor` 1,3 (réclame plus
      tard), `hideChanceFactor` 2 (cache-cache 0,35 → 0,7 dans la cascade), `startleChance` 0,35 (sursaut).
    - 😜 **Farceur** : `HAPPINESS_LOSS` 1,1, `PLAY_JOY` 1,2 ; `attentionFactor` 0,5 (seuil d'attention divisé par
      deux), `mischiefChanceFactor` 1,5 (chaque bêtise de la cascade ; une probabilité qui atteint 1 est tirée sans
      aléatoire, comme toc-toc).
    - 🧭 **Explorateur** : `ENERGY_LOSS` 1,15, `APP_VISIT_JOY` 2 ; `pageWanderChance` 0,15, `platformChance` 0,7,
      `travelCooldownFactor` 0,5 (il peut repartir d'app en app après 3 s au lieu de 6).
  - **`BehaviorMods`** : quatre champs (`mischiefChanceFactor`, `hideChanceFactor`, `travelCooldownFactor`,
    `startleChance`) **neutres par défaut** (facteurs 1 : `x * 1f == x` au bit près ; chance 0 : aucun tirage) — les
    anciens caractères ont exactement le comportement et les tirages d'avant (testé : cascade identique avec et sans
    leurs modificateurs, `SpeciesTest`). `Mischief.chance(level, mods)` (défaut : `chance(level) ×
    mischiefChanceFactor` ; `HideSeekMischief` y ajoute `hideChanceFactor`) ; `Mischiefs.seek(…, mods)` ;
    `PetBehavior.onAppChanged` lit `travelCooldownFactor`.
  - **Sursaut** (`StartleMischief`) : un clic à moins de 140 dp, s'il a `startleChance` > 0 → petit bond en arrière
    loin du doigt (`actor.jump`, `Act.AIR` puis `IDLE`), yeux ronds (`Expr.SURPRISED`), « Hiii ! 🙈 ». **Écart
    assumé** : rangé dans les bêtises parce que c'est le chemin des clics (même garde : libre, pas en pause, niveau ≥ 1,
    app non exemptée) — donc pas de sursaut au niveau « Sage » ; il ne met pas les bêtises en pause. **Dernier** de
    `Mischiefs.ALL` (après la langue) et sans tirage hors peureux : les tirages d'avant ne changent pas. « Se cache
    plus facilement » = cache-cache plus probable (pas de nouvelle activité de cachette).
  - Traces seedées : `BunnyBrainTest`, `MischiefTest`, `MultiPetTest` passent sans retouche de leurs graines ;
    `PetRulesTest.GOLDEN` est calculé sur **`TRACE_PERSONALITIES`** (les cinq d'origine) — la trace itérait
    `Personality.entries` et aurait gagné trois lignes. Le test du seuil d'attention lit une table de facteurs.
  - Adoption : les cartes de caractère et « Au hasard » (`Personality.values()`, `Random(nanoTime)`, hors tests)
    proposent les nouveaux d'eux-mêmes. Échange : une version antérieure qui reçoit `SHY`/`PRANKSTER`/`EXPLORER` le
    remplace par `CUDDLY` (`PetSnapshot.decode`, inchangé).
  - **Apparence** (`Look.kt`, ids figés dans `LookTest`) : pelages `GINGER` (« ginger », roux), `SPOTTED_CREAM`
    (« spotted_cream », crème + taches `Palette.FUR_SPOT` sur le corps, la tête et endormi — `Fur.spotted`),
    `BLUE_GREY` (« blue_grey ») ; accessoires `CROWN` (« crown », couronne dorée, debout et endormi), `BANDANA`
    (« bandana », triangle à pois de la couleur de l'accessoire), `BOW_TIE` (« bow_tie », nœud papillon) — ces deux
    derniers seulement debout, comme l'écharpe. Lunettes et couronne de fleurs existaient déjà. Couleurs `FUR_SPOT`,
    `CROWN_GOLD`, `CROWN_SHINE` (`Palette.GOLD` existait pour les stations). La pastille du pelage tacheté est unie
    (crème) dans l'adoption.
  - **Migration du schéma 0** : `Look.fromLegacyIndexes` bornait au `lastIndex` des enums : il borne désormais aux
    listes d'alors (`Fur.LEGACY_COUNT` = 7, `Accessory.LEGACY_COUNT` = 6) — un vieil index hors bornes donne toujours
    rose / aucun accessoire, jamais une option ajoutée depuis (testé). **À retenir** : toute option ajoutée à un enum
    de `Look` laisse `fromLegacyIndexes` inchangé grâce à ces constantes.
  - **Saisons** (`Season.kt`, pur) : `Season.of(LocalDate)` / `at(wallMs, zone)` — **Noël** 1er déc. – 6 janv.,
    **Halloween** 24 oct. – 2 nov., **printemps** 20 mars – 30 avril (bornes incluses ; `NONE` sinon).
    `SeasonalTheme(clock, zone)` : recalcul au plus une fois par minute (et si l'horloge recule), `enabled` = réglage.
    `java.time` (API 26+, JVM) : aucune dépendance.
  - **Ajout du rendu, jamais du `Look`** : `PetRenderState.season` + `seasonHat` (Noël **et** aucun accessoire :
    bonnet rouge à pompon, à la place de rien — un accessoire choisi est gardé), `seasonPumpkin` (Halloween :
    citrouille au sol à côté de lui), `seasonFlowers` (printemps : trois fleurs à ses pieds) ; décor au sol caché en
    vol / tenu. Particules `PType.SNOW` (Noël) / `PETAL` (printemps), `Season.particle`, émises par
    `AmbientAnimator` **après** les autres et sans tirage hors saison (testé : mêmes particules, image après image,
    avec une saison sans particule). **Coût** : en saison à particules, une particule est presque toujours en vol,
    donc la vue est redessinée à chaque image (comme pendant les Zzz) — 0,4 particule/s.
  - **Répliques** : `SpeciesLines.seasonal(season)` (abstrait : une espèce doit répondre ; `RabbitLines` : 2–3 par
    saison, vide hors saison), glissées dans le bavardage (`PetBehavior.chatter`, 30 %, tirage seulement en saison).
    `PetCtx.season` (défaut `NONE` : `FakePetCtx` et toutes les traces seedées inchangées ; `FakePetCtx.season` est
    un `var` pour les tests), fourni par `BunnyService` (`SeasonalTheme`). Les aperçus de l'accueil et de l'adoption
    montrent l'apparence sans saison.
  - **Réglage** : nouvelle clé `seasonal` (`Prefs.SEASONAL`, Boolean, vrai par défaut) dans le fichier `pet` ; bouton
    « Thèmes de saison » sous « Bêtises » (écran principal), `ACTION_RELOAD` le relit. Aucune clé existante renommée,
    aucune migration.
  - Tests : `PersonalityTest`, `SpeciesTest`, `MischiefTest` (cascade des anciens caractères identique, farceur,
    cache-cache du peureux, sursaut sans tirage), `MultiPetTest` (voyage de l'explorateur), `LookTest`, `SeasonTest`
    (dates limites, année complète, fuseau, cache / réglage, rendu, particules, répliques, bavardage).
    447 → 461 tests ; couverture **50,0 → 49,8 % des lignes** (49,9 → 49,7 % des instructions, 48,9 → 49,4 % des
    branches) — le dessin ajouté (Android) n'est pas couvert ; `Season`, `StartleMischief`, `PetRenderState` 100 %,
    `SeasonalTheme` 92 %.
  - **À vérifier sur tablette** : choix des trois caractères à l'adoption (cartes, « Au hasard ») ; peureux qui
    sursaute quand on touche l'écran près de lui (niveau ≥ 1) ; farceur plus pénible ; explorateur qui suit plus
    vite d'une app à l'autre ; dessin des trois pelages (taches du tacheté debout / endormi / tenu) et des trois
    accessoires (position de la couronne sur les oreilles tombantes, bandana et nœud papillon sous le menton) ;
    saisons (changer la date du téléphone : bonnet sans accessoire, flocons, citrouille, fleurs et pétales,
    répliques) et le bouton « Thèmes de saison » (effet immédiat, service lancé).

- **Évolution 4c** (bien-être, widget, tuile ; comportement existant inchangé : tout est coupé ou absent tant
  qu'on ne l'active / ne le pose pas) — 4 commits (bien-être ; widget ; tuile ; ce fichier) :
  - **Bien-être numérique** (`feature/wellbeing/`, pur) : **`WellbeingTracker`** (`Clock.uptimeMs` injectée) compte le
    temps passé **d'affilée** dans la même app. Il est alimenté par **`ForegroundAppWatcher.lastSeen`** (nouveau :
    dernière app vue, ignorées comprises ; `poll` et `currentPkg` inchangés) au relevé de `appPoll` (toutes les
    1,5 s, écran allumé et lapin montré). Seuils **`GameRules.breakSuggestAfterMin` = 30** (`Cue.BREAK`) et
    **`breakSulkAfterMin` = 45** (`Cue.SULK`), chacun une fois par séance. Remise à zéro : autre app (`RELIEVED`
    s'il boudait), notre app ou le lanceur (`exempt` : `packageName`, lanceurs vus — `AppInfo.isHome` — et
    `ScreenSnapshot.launcherPkg` ; ils ne comptent pas), écran éteint, « Cacher », réglage coupé (sans réplique).
    Le volet (`com.android.systemui`) et une réponse nulle de la source (UsageStats entre deux requêtes) ne
    coupent pas la séance. Une caresse du résident (`PetEvent.Petted`, interceptée par **`BunnyService.on`**, qui
    appelle `super.on`) met fin à la bouderie (`MADE_UP`) et relance le compte.
  - **`WellbeingCoach`** (pur, `PetCtx`) : pause = `Expr.SLEEPY` 2,5 s, il s'assoit (`Act.SIT` 3 s s'il était
    `IDLE` / `WALK` : « s'étire ») et « *bâille* Et si on faisait une pause ? ☕ » ; bouderie = `Expr.ANGRY`
    **maintenue** à chaque relevé (`maintain`, sans écraser une autre expression) + réplique ; pardon = cœurs ;
    soulagement = réplique. **Jamais de jauge** (aucun `ctx.on`, testé), jamais de blocage. Muet s'il dort, est
    caché ou hors écran (le signal passe ; la bouderie reste un état). **Seul le résident** (« l'animal du
    téléphone ») réagit. Répliques dans le fichier (contenu, pas d'espèce).
  - Réglage : clé **`wellbeing`** (`Prefs.WELLBEING`, Boolean, **faux** par défaut) dans le fichier `pet` ; bouton
    « Pauses proposées » (section « Bien-être », écran principal) ; `ACTION_RELOAD` le relit. Coupé = aucun
    tirage, aucune réplique : les traces seedées ne changent pas.
  - **Widget** (`widget/PetWidget.kt`, `AppWidgetProvider` + `RemoteViews`, layout `widget_pet.xml`, info
    `xml/pet_widget_info.xml` 3 × 2, redimensionnable) : titre « emoji d'humeur + nom », sous-titre
    `PetNotifier.moodText` (+ « en cage »), quatre barres (`ProgressBar`) et valeurs des jauges de la notification
    (description d'accessibilité = `PetNotifier.statusLine`), ligne « Rangé » si le service ne tourne pas, ni
    jauges ni boutons avant le premier lancement (`WidgetTexts.NO_PET`). Textes purs : **`WidgetTexts`**.
    **Lecture** par `Pets.repository(ctx).loadResident()` seulement (état sauvegardé, sans rattrapage hors-ligne,
    comme les activités) ; **écriture** jamais : « Nourrir » = `ACTION_FEED` (le chemin de la notification),
    « Appeler » = nouvelle **`BunnyService.ACTION_SHOW`** (montré s'il était caché — `show()`, « me revoilà »
    compris —, sinon « Je suis là ! 👋 », sauf dans les 3 s qui suivent le démarrage pour laisser l'accueil).
  - **Démarrage depuis le widget / la tuile** : `BunnyService.userCommand(ctx, action)` = intent marqué
    `EXTRA_FOREGROUND`, envoyé par `PendingIntent.getForegroundService` (widget) ou `startForegroundService`
    (tuile) — un toucher sur un widget ou une tuile autorise le démarrage au premier plan. Le service rappelle
    `startForeground` pour **chaque** commande de ce type (le système l'attend même service lancé) et remet
    `Prefs.ENABLED` à vrai (relancé au redémarrage, comme par l'écran principal). Sans permission de
    surimpression, les boutons ouvrent l'app.
  - **Mises à jour du widget** : à chaque sauvegarde **du résident** (`BunnyService.save`), au plus **une par
    minute** (**`UpdateThrottle`**, pur : une demande trop tôt est **reportée** à la fin de la minute par
    `Handler.postDelayed`, une seule en attente ; annulée à `onDestroy`) ; au démarrage et à l'arrêt du service
    (immédiat : « Rangé ») ; après `PetCommands.adopt` / `rename` service arrêté ; `updatePeriodMillis` = 30 min
    (minimum du système). Coût : ≤ 1 `updateAppWidget` par minute en jeu.
  - **Tuile** (`tile/PetTileService.kt`, `BIND_QUICK_SETTINGS_TILE`, icône `drawable/ic_tile.xml`, mode non actif :
    `onStartListening` à chaque ouverture du volet) : libellé = nom du résident (repository ; repli
    `@string/tile_label` « Cookie »), **active = service lancé et lapin affiché** (`BunnyService.visible`, nouveau
    `@Volatile` statique tenu par le service), sous-titre « Sur l'écran » / « Caché » / « Rangé » (**`TileTexts`**,
    pur). Toucher : service lancé → `ACTION_TOGGLE` (le « Cacher » / « Montrer » de la notification), état affiché
    tout de suite puis confirmé ; arrêté → démarré avec `ACTION_SHOW` ; sans surimpression ou démarrage refusé →
    l'app s'ouvre (`startActivityAndCollapse` : `PendingIntent` à partir d'Android 14, `Intent` sur 13 —
    `@SuppressLint("StartActivityAndCollapseDeprecated")`, erreur lint sinon). Le service appelle
    `requestListeningState` à chaque changement (démarrage, arrêt, cacher, montrer).
  - Manifeste : `receiver .widget.PetWidget` (`exported="false"`, `APPWIDGET_UPDATE`), `service .tile.PetTileService`
    (`exported="true"`, permission `BIND_QUICK_SETTINGS_TILE`, action `QS_TILE`). Chaînes `widget_description`,
    `widget_feed`, `widget_call`, `tile_label`. Pas de Glance, aucune dépendance. Le layout du widget n'a aucun
    attribut RTL (le manifeste ne déclare pas `supportsRtl` : lint `RtlEnabled`), et l'espace entre les boutons est
    un `FrameLayout` (une `View` nue n'est pas permise dans un `RemoteViews`).
  - Tests : `WellbeingTest` (seuils, une fois chacun, changement d'app, notre app / lanceur, volet et réponse vide,
    écran éteint, caresse, réglage coupé, seuils des règles, gestes et répliques sans jauge, muet endormi / caché /
    hors écran), `HomeShortcutsTest` (textes du widget = ceux de la notification, bornes, emoji par humeur, cadence
    reportée, état de la tuile), `ForegroundAppSourceTest` (`lastSeen`). 461 → 479 tests ; couverture **49,8 → 49,6 %
    des lignes** (49,7 → 49,5 % des instructions, 49,4 → 49,2 % des branches) — `PetWidget` et `PetTileService`
    (Android) ne sont pas couverts ; `WellbeingTracker` 97 %, `WellbeingCoach` 94 %, `WidgetTexts`,
    `UpdateThrottle`, `TileTexts`, `ForegroundAppWatcher` 100 %.
  - **À vérifier sur tablette** : ajouter le widget (aperçu dans le sélecteur, redimensionnement, textes et barres,
    emoji d'humeur), « Nourrir » service lancé / arrêté (il démarre et mange), « Appeler » caché / affiché /
    arrêté, widget à jour en moins d'une minute après un repas, « Rangé » après « Ranger » ; la tuile (l'ajouter au
    volet, nom du lapin, état allumé / éteint, toucher qui cache / montre, démarrage service arrêté, permission de
    surimpression retirée → l'app s'ouvre) ; le bien-être (activer, rester 30 min dans une app — ou baisser
    `GameRules.breakSuggestAfterMin` dans un build de test —, bâillement et réplique, bouderie à 45 min, caresse
    ou changement d'app, écran éteint qui remet à zéro, notre app et le bureau qui ne comptent pas, réglage coupé =
    rien), avec et sans service d'accessibilité (UsageStats).

- **Après 4c — lapin figé en sortie de veille (tablette, reproduit et corrigé)** : écran éteint → boucle arrêtée ;
  écran rallumé **verrouillé** → on attendait `ACTION_USER_PRESENT`, qui n'est pas arrivé (Galaxy Tab S10+,
  Android 16). La boucle ne repartait jamais : fenêtres figées (lapin, laitue qui jaillit du coffre), seuls les
  gestes vivaient — la bulle suivait le doigt, le lapin restait en `DRAGGED` (« Hé ! Pose-moi ! »). Diagnostic par
  un build de journalisation temporaire (`looping=false awake=false` après le réveil). Correctif : **`ScreenWake`**
  (pur, `ScreenWakeTest`) — écran allumé verrouillé → l'hôte relit `isKeyguardLocked` toutes les secondes jusqu'au
  déverrouillage (`unlockPoll`) ; un toucher sur la fenêtre d'un animal réveille aussi (`PetCtx.userTouched`, appelé
  par `TouchHandler` à `ACTION_DOWN`). `USER_PRESENT` reste écouté. Suspect probable des « lapin endormi figé » de
  l'étape 5 (non reproduits à l'époque).

- **Étape 8a** (module `:core`, Kotlin/JVM pur ; refactorisation sans changement de comportement) — 7 commits :
  - **Module** : `core/build.gradle.kts` = plugin `org.jetbrains.kotlin.jvm` (même version que l'app, 2.4.21,
    déclarée dans le `build.gradle.kts` racine), JVM 17, JUnit 4, plugin `jacoco` de Gradle (0.8.13, comme l'app) ;
    **aucune dépendance** (la bibliothèque standard Kotlin vient du plugin). `:app` : `implementation(project(":core"))`.
    Sources dans `core/src/main/kotlin`, tests dans `core/src/test/kotlin`, **mêmes packages** qu'avant (`git mv` :
    l'historique suit ; aucun nom d'enum, id ou clé ne bouge).
  - **Dans `:core`** (86 fichiers) : règles et jauges (`PetStats`, `PetRules`, `GameRules`, `Personality`, `Look`,
    `Mood`), `Pet` / `World` / `Arena`, `BunnyBrain`, `BunnyPhysics`, `BunnyActor`, `PetBehavior`, `ScrollMath`,
    `Tuning`, `Season`, `Palette` (littéraux `Int`), `PetRenderState` / `AmbientAnimator`, ports (`Clock`,
    `SettingsStore`, `ScreenSensor`, `OverlayHost` / `WindowTable`, `PetStore`, `FrameLoop` / `FrameScheduler`,
    `ForegroundAppSource`, `StationPanels`), `ScreenSensorHub`, `ScreenScan`, `ScreenWake`, `ForegroundAppWatcher`,
    `Director`, `social/`, `Speech`, les contrôleurs devenus purs (`PageTracker`, `MischiefController`,
    `ToyController`, `StationController`), le **comportement** des plugins (`feature/station`, `feature/toy`,
    `feature/mischief`, `feature/menu`, `feature/wellbeing`, `ActHandler`), les espèces sans rendu (`Species`,
    `SpeciesLines`, `Rabbit`, `RabbitLines`), `PetSnapshot`, `Adoption`, textes purs (`NotifierTexts`,
    `WidgetTexts`, `TileTexts`), `Prefs` (les clés).
  - **Resté dans `:app`** (Android) : `BunnyService`, `TamaApp`/`AppGraph`, activités, `BootReceiver`, `PetCommands`
    (intents), `TouchHandler`, `OverlayManager` / `OverlayWindows` / `EntityWindows` / `FloatingPanel` /
    `StationPanelWindows` / `PetMenu` (fenêtres), vues (`PetView`, `PropView`, `StationView`, bulle), dessins et
    renderers, `PetNotifier`, `ScreenSensorService` (+ `NodeInfoReader`), `AndroidForegroundApps` (UsageStats),
    `AndroidClock`, `Options` (couleurs `Color`), `SharedPrefsSettings` + `Prefs.of(context)`, widget / tuile, et
    **`PetRepository` + `PetMigrations` + `Pets`** : c'est l'adaptateur du port `PetStore` sur `SharedPreferences`
    (lecture des clés, `commit()`, migration du fichier `"pet"`) ; l'abstraire derrière un magasin clé-valeur aurait
    doublé l'API de `SharedPreferences` sans rien rendre testable de plus (`PetRepositoryTest` tourne déjà en JVM
    avec `FakePrefs`). Seules ses deux fonctions pures utiles à `PetSnapshot` sont passées dans le port
    (`PetStore.isValidId`, `PetStore.enumOr` ; `PetRepository.isValidId` / `PetFields.enumOr` y délèguent).
  - **Ports et constantes ajoutés** pour couper les derniers liens Android du cœur : `SettingsStore` (réglages lus
    par le cœur : niveau de bêtises, largeur des pages, placement des stations ; `PetCtx.prefs` n'est plus un
    `SharedPreferences` ; adaptateur `SharedPrefsSettings`, faux `FakeSettings`) — nom choisi pour ne pas heurter
    `android.provider.Settings` ; `Haptic.KEYBOARD_TAP` et `AppCategory` (ex-copies privées dans `RabbitLines` /
    `PetBehavior`) recopient `HapticFeedbackConstants` / `ApplicationInfo.CATEGORY_*`, vérifiés par
    `AndroidConstantsTest` ; `ColorOption.argb` (teinte des jouets, ex-`Options.color` = `Color.parseColor`) ;
    `NotifierTexts` (ex-compagnon de `PetNotifier`). Le `Clock` réel (`AndroidClock`) et `Options` ont leur fichier
    dans l'app.
  - **Dessin séparé du comportement** : `StationBehavior.draw` → `StationDrawer` (un `object …StationDrawer` par
    station dans `app/…/feature/station/`, registre **`StationDrawers.of(kind)`** en `when` exhaustif : une station
    sans dessin ne compile pas) ; `PropSpec.draw` → `PropDrawer` (un `…ToyDrawer` par jouet, `FoodDrawers.kt` pour
    les nourritures et la crotte, registre **`PropDrawers`** en `when` exhaustifs sur `Toy` et `Food`) ; `PropPen`
    suit le dessin (`PropDrawer.kt`) ; `Species.renderer(density)` → **`SpeciesRenderers.of(species, d)`** (table
    par id d'espèce). Les constantes qui ne servaient qu'au dessin (`WheelStation.TURNS_PER_S`, `FLOWERS_X`,
    `SEEDS`, `CHIPS`) ont suivi. Code de dessin recopié tel quel. `DrawersTest` (app) : chaque station, jouet,
    nourriture, la crotte et chaque espèce ont leur dessin, distinct.
  - **Tests** : 41 fichiers, faux compris (`FakePetCtx`, `FakeClock`, `FakeOverlayHost`, `FakeScreenSensor`,
    `FakeSettings`, `testPet`) passent dans `core/src/test` — ils tournent **sans stubs Android**. Restent dans l'app :
    `PetRepositoryTest` (+ `FakePrefs`, horloge locale `RepoClock` : pas de `testFixtures`, inutile pour un seul
    faux de 5 lignes), `DrawersTest`, `AndroidConstantsTest`. **486 → 492 tests** : 468 dans `:core`, 24 dans `:app`
    (+1 `LookTest` pour `argb`, +3 `DrawersTest`, +2 `AndroidConstantsTest`).
  - **Couverture** : CI = `:core:test :core:jacocoTestReport testDebugUnitTest createDebugUnitTestCoverageReport` ;
    l'artefact `coverage-report` contient `core/` (`jacocoTestReport.xml` + `html/`) et `app/` (`report.xml` +
    HTML) ; `coverage_badge.py` prend plusieurs rapports et **somme** les compteurs LINE (tableau par module dans
    le résumé du job). **49,7 % → 49,5 % des lignes** (3 481 / 7 009 → 3 498 / 7 064 ; instructions 49,5 → 49,2 %,
    branches 49,3 → 49,1 %) : `:core` **86,7 %** des lignes (3 283 / 3 786), `:app` 6,6 % (215 / 3 278). La légère
    baisse vient du découpage, pas d'une perte de tests : +55 lignes Android non testées (en-têtes des 17 objets de
    dessin, registres, `SharedPrefsSettings`, `AndroidClock`, `Options`) contre +17 lignes couvertes (registres
    par `DrawersTest`, `argb`).
  - `lint-baseline.xml` inchangé (ses entrées `StationController` étaient déjà périmées depuis l'étape 4 ; lint
    ne passe pas sur `:core`, module JVM sans plugin lint — detekt viendra en 8b).

#### Schéma de persistance v1

Fichier **`pets`** (SharedPreferences, `Prefs.PETS_FILE`) :

| Clé | Type | Contenu |
|---|---|---|
| `schema_version` | Int | `1` (absent = schéma 0 : animal dans le fichier `"pet"`) |
| `pets` | String | ids des animaux séparés par `,`, ordre d'arrivée (résident et compagnons) |
| `resident` | String | id de l'animal qui vit sur ce téléphone (les autres de `pets` sont ses compagnons) |
| `pet.<id>.name` / `.species` | String | nom (≤ 16), espèce (`Species.id` : `rabbit`) |
| `pet.<id>.bornAt` / `.lastUpdate` | Long | naissance, dernière sauvegarde (ms, heure murale) |
| `pet.<id>.adopted` | Boolean | écran d'adoption validé |
| `pet.<id>.personality` / `.favoriteToy` | String | nom d'enum `Personality` / `Toy` |
| `pet.<id>.look.fur` / `.ears` / `.eyes` / `.pattern` / `.accessory` / `.accColor` | String | `id` de l'option (`chocolate`, `lop`…) |
| `pet.<id>.satiety` / `.happiness` / `.energy` / `.cleanliness` / `.health` | Float | jauges 0..100 |
| `pet.<id>.sleeping` / `.caged` | Boolean | endormi, en cage |
| `pet.<id>.poops` / `.visitedApps` | Int | crottes à l'écran, apps visitées |
| `pet.<id>.poopAcc` | Float | fraction de la prochaine crotte |

Fichier **`pet`** (réglages) : `enabled`, `mischief`, `dock_fx`, `dock_fy`, `pageW2_<largeur>`,
`st_<STATION>_anchor` / `_fx` / `_fy`, `seasonal` (Boolean, thèmes de saison, vrai si absent — évolution 4b),
`wellbeing` (Boolean, pauses proposées, faux si absent — évolution 4c).
Valeur inconnue ou absente → défaut (`PetFields.read`).

### Ajouter une station (ex. : le lit)

Chemins : `core/src/main/kotlin/com/tamacookie/…` (cœur) et `app/src/main/java/com/tamacookie/…` (dessin).

1. Cœur — `feature/station/BedStation.kt` : `object BedStation : StationBehavior` — `name` (« 🛏️ Lit »), `tapHint`,
   `busyLine`, `seat`, les propriétés utiles (`capacity = 2`, `slowFrames = true`, `holdsPet`…) et
   `onEnter` / `update` / `onLeave` / `onPet` si besoin (répliques dans le fichier).
2. Cœur — `StationKind` (`feature/station/StationKind.kt`) : ajouter **à la fin** `BED("Lit", 140, 90, BedStation)` —
   l'ordre fixe le placement par défaut, le nom les clés `st_BED_*` (créées automatiquement, rien à migrer).
3. App — `feature/station/BedStationDrawer.kt` : `object BedStationDrawer : StationDrawer` (`draw(c, v, w, h)` :
   pinceaux `v.pen`, état `v.t` / `v.open` / `v.occupied`) et sa branche dans `StationDrawers.of` (le `when`
   exhaustif l'exige) ; ses couleurs dans `Palette` (cœur).
4. (Facultatif) un test de son comportement à côté de `StationBehaviorTest` (`core/src/test`) ; les tests
   d'exhaustivité (`StationBehaviorTest`, `DrawersTest`) la couvrent déjà.

Rien d'autre : panneau 🏠, survol/dépôt, toucher, cadence, voyage, sommeil, caresse, sauvegarde du placement
suivent. Avant 3a il fallait toucher ~10 endroits (enum + `when` du dessin, `positionInStation`, `enterStation`,
`leaveStation`, `busyInStation`, tap, `Act` + `SLOW`/`BUSY_FOR_TRAVEL`, `updateBehavior`, `onAppChanged`,
`startSleep`, `onPetTap`, noms du menu). Seules exceptions : une station avec **son propre panneau** (ajouter une
méthode à `StationCtx` et le `FloatingPanel` dans `StationController`). Plusieurs animaux : `capacity` suffit (chacun
s'assoit à `seat` ; pour des places distinctes, `seatFor(rang)` — voir `BedStation`) ; une espèce peut la refuser
(`Species.accepts`). Il peut s'y endormir : `sleepsInside = true`. Une nouvelle règle de jauges : un `PetEvent`
(recette « Ajouter une stat », étape 1).

### Ajouter un jouet (ex. : le cerf-volant)

1. Cœur — `feature/toy/KiteToy.kt` : `object KiteToy : ToySpec` — `toy get() = Toy.KITE` (getter : pas de cycle
   d'initialisation avec l'enum), `wDp`/`hDp`, `startLines`, `play` (pendant `Act.PLAY`), `step` (sa physique,
   via `ctx.stepToy`) et au besoin `launch`, `randomTint`, `complainsWhenTaken` ; ses répliques dans le fichier.
2. Cœur — `Toy` (`feature/toy/Toy.kt`) : ajouter **à la fin** `KITE("Cerf-volant", "🪁", KiteToy)` (le nom est
   sauvegardé dans `favoriteToy`).
3. App — `feature/toy/KiteToyDrawer.kt` : `object KiteToyDrawer : PropDrawer` (`draw(c, v, w, h)` : `v.spin`,
   `v.tint`, pinceaux `v.pen`) et sa branche dans `PropDrawers.of(Toy)` (`when` exhaustif) ; ses couleurs dans
   `Palette`. `ToySpecTest` (cœur) et `DrawersTest` (app) le couvrent déjà (exhaustivité).

Coffre, jouet préféré à l'adoption, fiche du véto, partie, lancer au doigt, protestation, cadence suivent. Avant 3b :
~10 endroits (enum `Toy`, `PropView.Kind` + `when` du dessin, `Prop.toy`, taille dans `startPlay`, teinte, lancer,
répliques, `updatePlay`, physique dans `update`, protestation dans le toucher). Une nourriture : un `object … : FoodSpec`
dans `Foods.kt` + une ligne dans `Food` (cœur), son `…FoodDrawer` dans `FoodDrawers.kt` + sa branche dans
`PropDrawers.of(Food)` (app).

### Ajouter une bêtise (ex. : il tire la langue à l'écran)

Tout est dans le cœur (`core/src/main/kotlin/com/tamacookie/`), tests dans `core/src/test`.

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

1. `MenuEntries` (`feature/menu/MenuEntry.kt`, cœur) : `val SING = MenuEntry("🎵", "Chanter") { ctx, pet -> … }` (avec
   `refresh = { st -> … }` si le bouton change selon l'état) et l'ajouter à `ALL` (même fichier ; deux par rangée).
2. Seulement si l'action a besoin d'autre chose que `PetCtx` (un panneau, un autre contrôleur) : une méthode dans
   `MenuCtx` (cœur) et son implémentation dans `TouchHandler.menuCtx` (app).

Fermeture du menu, mise en page, rafraîchissement suivent. Avant 3b : 4 endroits (enum `PetMenu.Action`, bouton et
rangée dans `PetMenu`, branche de `TouchHandler.onMenuAction`, texte dynamique dans `refresh`).

### Ajouter une stat (ex. : la soif)

1. `PetStats` : `val thirst: Float = 80f` ; `PetRules.clamp` : la borner ; `PetRules.elapsed` : son évolution
   (taux dans `GameRules`, ex. `thirstLossPerH`) ; un `PetEvent` qui la remonte (`Drank`), sa branche dans
   `apply` et dans `savesNow` (le compilateur exige les deux) ; l'humeur dans `PetRules.mood` si besoin
   (`Mood.THIRSTY` → `SpeciesLines`, `NotifierTexts`, `PetMenu`, `PetRenderState.moodExpr` : signalés par le
   compilateur). Tout cela est dans le cœur, sauf `PetMenu` (app).
2. Persistance (app, `persist/PetRepository.kt`) : `PetFields` (`THIRST = "thirst"`, `write` + `read` avec défaut) — **pas de migration** : une clé
   absente prend la valeur par défaut. `PetSnapshot` : l'ajouter seulement si elle a du sens sur un autre
   téléphone (champ ignoré par les anciennes versions, défaut chez nous s'il manque).
3. Si un caractère la module : une entrée `StatMod` et sa valeur dans `Personality.modifiers`.
4. Tests : `PetRulesTest` (cœur : taux, bornes) — la trace de référence ne change pas tant que la nouvelle stat
   n'influence pas les autres ; `PetRepositoryTest` (app : aller-retour).

Un changement de **forme** (renommer / scinder une clé, changer une unité) demande une migration : une entrée à la
fin de `PetMigrations.ALL`, `SCHEMA_VERSION` + 1, et un test à partir d'un fichier de la version précédente.

### Ajouter une personnalité (ex. : peureux)

1. `Personality` (`Personality.kt`, cœur) : ajouter **à la fin** `SHY("Peureux", "🙈", "…", mapOf(StatMod.HAPPINESS_LOSS
   to 1.1f))` — le nom d'enum est sauvegardé et échangé (`PetSnapshot`) : ne plus le changer.
2. `PersonalityTest` : sa ligne dans la table `expected` (le test d'exhaustivité l'exige).
3. Comportement propre (hors jauges) : sa ligne dans `behavior()` de chaque espèce (`Rabbit.modsFor` : un champ de
   `BehaviorMods`, ou un nouveau champ lu là où il agit) — le `when` exhaustif l'exige. L'écran d'adoption la propose
   tout seul. Un nouveau champ de `BehaviorMods` doit être **neutre par défaut** (facteur 1, chance 0 testée avant
   tout tirage) pour ne rien changer aux autres caractères (modèle : `mischiefChanceFactor`, `startleChance`).
4. Les tests qui itèrent sur `Personality.entries` avec une empreinte figée (`PetRulesTest.GOLDEN`) s'en tiennent à
   `TRACE_PERSONALITIES` : donner son propre test au nouveau caractère.

Un pair qui envoie un caractère inconnu (version plus récente) le voit remplacé par `CUDDLY` (`PetSnapshot.decode`).

### Ajouter une espèce (ex. : le hamster)

1. `species/hamster/Hamster.kt` : `object Hamster : Species` — `id` (« hamster » : **sauvegardé et échangé**, ne plus
   le changer), `label`, `emoji`, `spec` (`SpeciesSpec` : gabarit de fenêtre, pieds, haut de la tête, vitesses),
   `behavior(p)` (un `BehaviorMods` par `Personality`, `when` exhaustif), `accepts(k)` / `accepts(t)` si certaines
   stations / jouets ne lui conviennent pas, et `lines` (cœur, `core/…/species/hamster/`).
2. Cœur — `species/hamster/HamsterLines.kt` : `object HamsterLines : SpeciesLines` (le compilateur liste ce qui
   manque). App — `species/hamster/HamsterRenderer.kt` : `class HamsterRenderer(d: Float) : PetRenderer` (`draw` du
   corps pour chaque `Pose` / `Expr`, `onState` pour l'apparence, `hitTest`) et sa ligne dans `SpeciesRenderers`
   (`Hamster.ID to ::HamsterRenderer`) ; ses couleurs dans `Palette`.
3. `Species.ALL` (`species/Species.kt`) : l'ajouter **après** le lapin (le premier est l'espèce par défaut des
   anciennes sauvegardes). `SpeciesTest` (cœur) et `DrawersTest` (app : un renderer par espèce) le couvrent
   (exhaustivité) ; `PetSnapshot` l'accepte tout seul.
4. Pour le proposer : un choix d'espèce dans `AdoptActivity` (l'`Adoption` porte alors l'espèce jusqu'à
   `Identity(species = …)`) — pas encore d'interface. Si son gabarit diffère du lapin : la fenêtre (`Arena.bw/bh`)
   devient par animal (`ensurePetWindow` prend `p.species.spec`), et `BunnyActor` / `PageTracker` lisent la largeur
   de l'animal.

Boucle, cerveau, jauges, stations, jouets, bêtises, social, persistance suivent.

### Ajouter une interaction sociale (ex. : jouer à chat)

1. `social/TagSocial.kt` (cœur) : `object TagSocial : SocialInteraction` — par le cerveau : `chance` (> 0), `canStart(ctx,
   a, b)` (sans aléatoire), `start(sys, a, b)` qui engage les participants (`sys.engage(p, this, partner, durée,
   leads)` : un seul pour une interaction « passive » comme suivre, les deux pour une interaction mutuelle) et
   `update(sys, self, partner, e, dt)` (chaque image pour chaque engagé ; `false` = fini ; `partner == null` = parti
   ou indisponible) ; ou par le monde : `observe(sys, a, b, dt)` (chaque paire affichée, sans engagement : les passes
   à la balle). Répliques dans le fichier, dites par `sys.say(p, …)` (priorité basse) ; tirages avec `p.mind.rnd`.
2. `Socials.ALL` (`social/SocialSystem.kt`) : l'insérer à sa place (ordre de la cascade).
3. Si elle demande un arbitrage (une ressource unique) : une question au `Director` (déduite du monde), interrogée
   dans `canStart` ; si c'est le `Director` qui la déclenche (dispute) : une file comme `takeDisputes`.

`Act.SOCIAL`, `ActRegistry`, interruption (caresse, glisser, voyage), cadence et perception suivent. Tests : à côté de
`SocialSystemTest` (`core/src/test`), avec un `FakePetCtx` + `addPet` et des `Random` seedés.

Règles pour chaque étape : pas de big-bang, pas de nouvelle dépendance sans justification écrite,
ajouter des tests JVM pour tout ce qui devient pur, mettre à jour ce fichier (colonne État + écarts).
