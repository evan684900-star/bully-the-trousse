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

- Squelette Gradle multi-module (`:core` + `:app`, avec le plugin Kotlin
  `plugin.serialization` en plus sur `:core` pour la sauvegarde).
- `:core` : physique du lancer (`ThrowPhysics`), les barres de
  puissance/précision (`PowerAndAccuracy`), la machine à états du lancer en
  3 taps (`ThrowSequence`, horloge injectable pour des tests déterministes),
  l'économie (`Economy.moneyEarned` / `Economy.upgradeCost`), l'intégration
  physique du vol image par image (`FlightSimulator`, portage de la boucle
  "flying" de `gameLoop()`) et la conversion coordonnées monde → écran +
  caméra suiveuse (`Camera`, portage de `worldToScreen()` et de la mise à
  jour de `cameraX`/`cameraY`) — tout porté formule par formule depuis
  `index.html` (voir les commentaires de chaque fichier pour la
  correspondance exacte avec le code web), avec 21 tests unitaires dont les
  valeurs attendues ont été calculées directement depuis les formules JS
  (y compris un test qui fait converger l'intégration pas à pas vers la
  même distance que la formule fermée, pour vérifier que le portage de la
  boucle de vol est fidèle).
- `:app` : un écran Compose avec la vraie interaction en 3 taps (idle →
  charge de puissance → charge de précision → lancé) via `ThrowSequence`,
  les barres de puissance/précision qui s'animent en temps réel pendant la
  charge (`LiveOscillatingBar`, `LaunchedEffect` + `withFrameNanos`, calé
  sur `System.currentTimeMillis()` pour rester sur la même horloge que
  `ThrowSequence`), et un rendu `Canvas` du monde "Cour d'école" et de la
  trousse (`ThrowCanvas.kt`) : après le 3e tap, la trousse vole réellement
  à l'écran (animée pas à pas via `FlightSimulator`/`animateFlight`, caméra
  qui la suit) jusqu'à l'atterrissage, avant d'afficher le résultat. Le
  décor (bâtiments en parallaxe, arbres) est dessiné en pur Canvas, avec son
  placement calculé et testé dans `:core` (`CourDecor`, portage de
  `drawBackground()`/`drawBuilding()`/`drawTree()`/`seededRand()`) ; la
  trousse elle-même reste une forme vectorielle stylisée (corps + rabat +
  fermeture éclair), pas encore le vrai sprite bitmap du web.
- **Sauvegarde locale** : `GameSave` dans `:core` (portage champ par champ de
  `defaultSave()`), sérialisée en JSON par `SaveCodec` (kotlinx.serialization,
  `ignoreUnknownKeys` + valeurs par défaut pour les champs absents — le même
  esprit que `Object.assign(defaultSave(), parsed)` côté web), persistée
  côté `:app` dans un fichier privé de l'app (`SaveRepository`). Branchée
  dans `MainActivity` : la sauvegarde se charge au lancement et se met à
  jour/persiste (argent gagné via `Economy.moneyEarned`, record, nombre de
  lancers) à chaque atterrissage. Pas encore de migration d'anciens champs
  (pas nécessaire : aucune sauvegarde Android n'existe encore) ni de
  synchronisation cloud (voir Firebase plus bas).

## Ce qu'il reste à faire (dans un ordre logique)

1. **Vrai sprite pour la trousse + décor des autres mondes** — remplacer la
   forme vectorielle de `ThrowCanvas` par un vrai sprite (+ ses variantes de
   skins) si des assets sont fournis, et porter le décor des mondes Volcan
   et Plage (parasols/serviettes/châteaux, dérapage) sur le même principe
   que `CourDecor` (placement testé dans `:core`, dessin dans `:app`).
2. **Boutique et niveaux** — écran pour dépenser `save.money` sur
   `Economy.upgradeCost()` (Puissance/Vitesse), déjà calculé dans `:core`
   mais pas encore accessible en jeu (`save.puissanceLevel`/`vitesseLevel`
   pilotent déjà la physique, juste pas encore modifiables depuis l'UI).
3. **Les 3 mondes** (Cour, Volcans, Plage) et leurs mécaniques propres
   (dérapage, parasols/serviettes/châteaux, cinématiques de déblocage).
4. **Les skins et traînées** — un module `:core` supplémentaire pour la
   liste des skins et leurs bonus/mécaniques spéciales, séparé du rendu.
5. **Firebase** (SDK Android, différent du SDK JS utilisé côté web) pour
   les comptes, le classement, les cadeaux, les succès — probablement la
   partie la plus longue, vu tout ce qui a été construit et corrigé côté
   web cette session (comptes multi-appareils, cadeaux, etc.).
6. **Les 47 succès** et les défis quotidiens.

Chaque étape devrait suivre le même principe que celle-ci : porter la
logique dans `:core` avec des tests dont les valeurs de référence viennent
directement des formules JS, avant de la brancher à l'UI.
