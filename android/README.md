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
- **Boutique Puissance/Vitesse** : `Shop` dans `:core` (portage des deux
  gestionnaires de clic quasi-identiques de la boutique web — débite
  `save.money` de `Economy.upgradeCost(niveau)`, incrémente le niveau,
  refuse si pas assez d'argent), testé (36 tests au total). Branché dans
  `:app` (`ShopRow`, deux boutons sous le bouton de lancer) : acheter met
  vraiment à jour la sauvegarde et la persiste, et les niveaux achetés
  pilotent réellement la physique du lancer suivant (`Shop.buyPuissance`
  n'est pas encore accessible depuis un écran de boutique dédié — juste des
  boutons directement sur l'écran de jeu pour l'instant).

- **Monde Volcan (partiel)** : `Skid` dans `:core` (dérapage à l'atterrissage
  — 25% de chance, portage de `SKID_CHANCE`/le bloc "skidding" de
  `gameLoop()`, coût en durabilité) et `VolcanoCinematic` (moteur pur de la
  cinématique de déblocage — séquence de phases chronométrées, mini-jeu
  d'esquive de 5 roches avec 2 vies et esquive automatique "de justesse",
  QTE de 10 clics pendant la descente — portage de `cineUpdate()`/
  `CINE_TIMES`/`cineStartRock()`/`cineResolveRock()`), testés (55 tests au
  total). Branchés dans `:app` : un dérapage se déclenche vraiment après un
  atterrissage dans le monde Volcan (`animateSkid` dans `ThrowCanvas.kt`,
  coûte de la durabilité) ; `VolcanoCinematicScreen.kt` rejoue la
  cinématique avec un habillage visuel simplifié (fond de couleur par
  phase, indicateur de canal, compteur de clics) — pas encore le décor 3D
  animé (secousse d'écran, éclair, particules) du web, juste le
  déroulement/l'issue, qui eux sont fidèles.

- **Skins et traînées** : `Skins`/`SkinShop`/`SkinStats` et `Trails`/
  `TrailShop` dans `:core` (portage des 14 skins et 15 traînées de
  `SKINS`/`TRAILS`, de `getSkin()`/`getTrail()`, de `totalPuissance()`/
  `totalVitesse()`/`maxDurability()`, et des gestionnaires d'achat/
  équipement de `buildSkinCard()`/`buildTrailCard()`/`equipSkin()`), testés
  (65 tests au total). Branchés dans `:app` (`SkinsRow`/`TrailsRow`,
  listes déroulantes horizontales) : acheter/équiper marche vraiment, et la
  physique du lancer + les gains utilisent désormais les stats *effectives*
  (niveau acheté + bonus du skin équipé), pas juste le niveau acheté.

- **Mécaniques spéciales de skins (partiel)** : `SkinEarnings` dans `:core`
  (bonus/malus de gains de la Trousse Pièce — 50% de chance d'un
  multiplicateur aléatoire x1.6 à x10, avec jackpot au-delà de x5 — et de la
  Trousse Vampire — tribut de 5 à 40% du gain selon la distance —, portage
  du bloc correspondant de `onLanded()`, dans le même ordre : pièce
  d'abord, vampire ensuite) et fenêtre du lancer parfait élargie pour la
  Trousse Claude (`ThrowSequence.tap()` prend maintenant un `perfectWindow`,
  porté depuis `isClaude`), testés (73 tests au total). Branchés dans
  `:app` : le résultat affiche le multiplicateur pièce et le vol vampire
  quand ils se déclenchent. Le rebond de la Trousse à Baskets n'est PAS
  porté : contrairement aux autres mécaniques, il demanderait de relancer
  une charge de puissance/précision en accumulant la position déjà
  parcourue, ce que la machine à états actuelle de `ThrowSequence` ne gère
  pas (elle calcule tout le lancer en un coup au 3e tap plutôt que
  d'détecter un atterrissage intermédiaire pendant le vol). Les mécaniques
  liées à l'espace (Trousse Lunaire, Fusée, Avion) ne sont pas non plus
  portées, faute de séquence spatiale côté Android. Toujours pas de rendu
  visuel différent par skin (même silhouette orange dans `ThrowCanvas`).

## Ce qu'il reste à faire (dans un ordre logique)

1. **Vrai sprite pour la trousse + décor des autres mondes** — remplacer la
   forme vectorielle de `ThrowCanvas` par un vrai sprite (+ ses variantes de
   skins, maintenant que la liste des skins existe) si des assets sont
   fournis, et porter le décor du monde Volcan (le mini-jeu et le dérapage
   sont déjà là, juste pas leur habillage visuel riche) et de la Plage, sur
   le même principe que `CourDecor` (placement testé dans `:core`, dessin
   dans `:app`).
2. **Rebond de la Trousse à Baskets** — nécessite d'étendre `ThrowSequence`
   (ou une machine à états parallèle) pour représenter un atterrissage
   intermédiaire qui relance une charge, en accumulant la distance déjà
   parcourue plutôt que de tout calculer au 3e tap.
3. **Écrans séparés (menu / jeu / boutique)** — actuellement tout est sur un
   seul écran ; découper en vraies destinations (navigation Compose) une
   fois qu'il y a plus d'un écran à afficher.
4. **Sélecteur de monde + monde Plage** — pour l'instant, `currentWorld`
   ne bascule sur "volcans" qu'en réussissant sa cinématique (comme côté
   web), sans écran pour choisir/revenir sur "cour" ; le monde Plage
   (parasols/serviettes/châteaux, bus, cinématique dédiée) n'est pas
   commencé.
5. **Firebase** (SDK Android, différent du SDK JS utilisé côté web) pour
   les comptes, le classement, les cadeaux, les succès — probablement la
   partie la plus longue, vu tout ce qui a été construit et corrigé côté
   web cette session (comptes multi-appareils, cadeaux, etc.).
6. **Les 47 succès** et les défis quotidiens.

Chaque étape devrait suivre le même principe que celle-ci : porter la
logique dans `:core` avec des tests dont les valeurs de référence viennent
directement des formules JS, avant de la brancher à l'UI.
