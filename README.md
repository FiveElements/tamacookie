# 🐰 TamaCookie

[![CI](https://github.com/FiveElements/tamacookie/actions/workflows/ci.yml/badge.svg)](https://github.com/FiveElements/tamacookie/actions/workflows/ci.yml)
[![Release](https://github.com/FiveElements/tamacookie/actions/workflows/release.yml/badge.svg)](https://github.com/FiveElements/tamacookie/actions/workflows/release.yml)
[![Dernière version](https://img.shields.io/github/v/release/FiveElements/tamacookie?label=version&logo=github)](https://github.com/FiveElements/tamacookie/releases/latest)
[![Téléchargements](https://img.shields.io/github/downloads/FiveElements/tamacookie/total?label=t%C3%A9l%C3%A9chargements&logo=android&color=3DDC84)](https://github.com/FiveElements/tamacookie/releases)
[![Téléchargements (dernière version)](https://img.shields.io/github/downloads/FiveElements/tamacookie/latest/total?label=derni%C3%A8re%20version)](https://github.com/FiveElements/tamacookie/releases/latest)
[![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/13)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Dependabot](https://img.shields.io/badge/Dependabot-auto--merge-025E8C?logo=dependabot)](.github/dependabot.yml)

Un petit lapin façon tamagotchi qui **vit par-dessus tes écrans Android** : il se promène sur le bureau,
saute sur les icônes, te suit d'application en application… et vient t'embêter quand tu l'oublies.

## 📥 Télécharger

➡️ **[Télécharger le dernier APK](https://github.com/FiveElements/tamacookie/releases/latest)**

1. Télécharge `tamacookie-<version>.apk` depuis la dernière release.
2. Autorise l'installation d'applications de source inconnue pour ton navigateur / gestionnaire de fichiers.
3. Ouvre l'APK, installe, puis lance **TamaCookie** et adopte ton lapin.

Chaque release fournit aussi un fichier `SHA256SUMS.txt` pour vérifier l'APK.

## ✨ Fonctionnalités

- **Adoption et personnalisation** : pelage, oreilles, yeux, motif, accessoire, personnalité
  (joueur, gourmand, paresseux, curieux, câlin) et jouet préféré.
- **Vie de tamagotchi** : faim, bonheur, énergie, propreté, santé, sommeil… et petites crottes à ramasser.
- **Fenêtre flottante** au-dessus de toutes les applications ; il commente l'app ouverte et la suit.
- **Saute sur les icônes** (service d'accessibilité, activé à la main : seule la *position* des éléments est lue).
- **Objets flottants** posés sur une page précise du bureau, qui suivent le swipe comme les icônes :
  - 🛁 **Bain** pour le laver, 🩺 **Vétérinaire** pour voir ses statistiques et le soigner,
  - 🔒 **Cage** pour l'isoler (son bonheur baisse plus vite),
  - 🎁 **Coffre** pour sortir à manger 🥕🥬🍓 ou un jouet ⚽🎈🥏🧸 — Cookie peut aussi l'ouvrir tout seul
    en sautant dedans quand il a faim ou envie de jouer.
- **Pages du bureau** : Cookie vit sur une page, défile avec elle, part explorer la page voisine
  et revient sur l'écran affiché.
- **Bêtises** (réglables) : il s'assoit sur un bouton, tape à l'écran, joue à cache-cache sur une autre page…

## 🔐 Permissions

| Permission | Pourquoi |
|---|---|
| Affichage par-dessus les autres apps | Faire vivre le lapin à l'écran |
| Notifications | Service au premier plan (obligatoire) et alertes (faim, maladie…) |
| Accès aux données d'utilisation | Savoir quelle application est ouverte |
| Accessibilité (optionnel) | Position des icônes, page du bureau, défilement — jamais le texte saisi |

Rien n'est collecté ni envoyé : tout reste sur l'appareil.

## 🛠️ Développement

Prérequis : JDK 17+ et le SDK Android (API 34).

```bash
./gradlew testDebugUnitTest   # tests unitaires (JVM)
./gradlew assembleDebug       # APK de debug : app/build/outputs/apk/debug/
```

Le projet n'utilise que le framework Android et la bibliothèque standard Kotlin (aucune dépendance AndroidX).

### CI / CD (GitHub Actions)

| Workflow | Déclencheur | Rôle |
|---|---|---|
| [`ci.yml`](.github/workflows/ci.yml) | push sur `main`, pull requests | tests unitaires, lint, APK debug (artefact) |
| [`release.yml`](.github/workflows/release.yml) | tag `v*` ou lancement manuel | tests, APK release signé, publication GitHub Release |
| [`dependabot-automerge.yml`](.github/workflows/dependabot-automerge.yml) | PR Dependabot | fusion automatique dès que la CI est verte, **uniquement pour les mises à jour patch et minor** (les majeures restent manuelles) |

Les actions GitHub sont épinglées par SHA de commit (le tag est indiqué en commentaire) ; Dependabot les met à jour.

**Publier une version** :

```bash
git tag v1.1.0 && git push origin v1.1.0
```

ou *Actions → Release → Run workflow* avec le numéro de version.

La version doit être de la forme `X.Y.Z` (ex. `1.2.0`), sinon le workflow échoue.

**Signature** : la release **exige** les 4 secrets du dépôt
`TAMACOOKIE_KEYSTORE_BASE64`, `TAMACOOKIE_KEYSTORE_PASSWORD`, `TAMACOOKIE_KEY_ALIAS` et `TAMACOOKIE_KEY_PASSWORD`.
Sans clé de signature, le workflow échoue (aucune release signée avec une clé de debug n'est publiée).
