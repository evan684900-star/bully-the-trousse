# Bully the Trousse — portage natif Android

Portage natif (Kotlin + Jetpack Compose) du jeu web (`../index.html`), sans
WebView. Projet démarré en tranches vérifiables : chaque morceau de logique
de jeu est d'abord porté dans `:core` (Kotlin pur, testable sans le SDK
Android) avant d'être branché à l'affichage dans `:app`.

## Structure

- **`core/`** — Logique de jeu pure (physique du lancer, économie...),
  aucune dépendance Android. Se compile et se teste avec juste Gradle+JDK,
  y compris dans un environnement sans SDK Android :
  ```
  gradle :core:test
  ```
- **`app/`** — Application Android (Jetpack Compose) qui affiche le jeu et
  utilise `:core`. **Nécessite le SDK Android** (donc Android Studio) pour
  compiler — le dépôt Google Maven qu'utilise l'Android Gradle Plugin n'est
  pas joignable depuis certains environnements de développement sans accès
  réseau complet.

## Pour ouvrir dans Android Studio

1. Ouvre le dossier `android/` (pas la racine du repo) comme projet.
2. Décommente la ligne `include(":app")` dans `settings.gradle.kts` (elle
   est en commentaire exprès, voir le commentaire juste au-dessus dans ce
   fichier).
3. Laisse Android Studio synchroniser Gradle — il téléchargera l'Android
   Gradle Plugin et le SDK nécessaire automatiquement.
4. Lance `MainActivity` sur un émulateur ou un appareil connecté (ou, depuis
   un iPhone, via un service comme [Appetize.io](https://appetize.io/) qui
   fait tourner l'APK compilé dans le navigateur).

## Ce qui est fait

- Squelette Gradle multi-module (`:core` + `:app`).
- `:core` : physique du lancer (`ThrowPhysics`) et économie
  (`Economy.moneyEarned` / `Economy.upgradeCost`), portées formule par
  formule depuis `index.html` (voir les commentaires de chaque fichier
  pour la correspondance exacte avec le code web), avec des tests unitaires
  dont les valeurs attendues ont été calculées directement depuis les
  formules JS pour garantir un comportement identique aux deux endroits.
- `:app` : un seul écran Compose minimal qui prouve que l'UI parle bien à
  `:core` (bouton "Lancer" → distance + argent gagné affichés). Pas encore
  le vrai gameplay (charge de puissance, visée, rendu de la trousse).

## Ce qu'il reste à faire (dans un ordre logique)

1. **Vraie interaction de lancer** — charge de puissance (appui maintenu),
   visée (barre animée), au lieu du lancer "au hasard" actuel.
2. **Rendu de la trousse et du décor** — `Canvas` Compose, en portant
   `drawTrousse()`/les fonctions `drawXxx()` de skins depuis le JS.
3. **Sauvegarde locale** — équivalent de `defaultSave()`/`loadSave()`
   (DataStore ou fichier JSON), avec la migration des champs si jamais on
   veut un jour importer une sauvegarde depuis la version web.
4. **Les 3 mondes** (Cour, Volcans, Plage) et leurs mécaniques propres
   (dérapage, parasols/serviettes/châteaux, cinématiques de déblocage).
5. **Les skins et traînées** — un module `:core` supplémentaire pour la
   liste des skins et leurs bonus/mécaniques spéciales, séparé du rendu.
6. **Firebase** (SDK Android, différent du SDK JS utilisé côté web) pour
   les comptes, le classement, les cadeaux, les succès — probablement la
   partie la plus longue, vu tout ce qui a été construit et corrigé côté
   web cette session (comptes multi-appareils, cadeaux, etc.).
7. **Les 47 succès** et les défis quotidiens.

Chaque étape devrait suivre le même principe que celle-ci : porter la
logique dans `:core` avec des tests dont les valeurs de référence viennent
directement des formules JS, avant de la brancher à l'UI.
