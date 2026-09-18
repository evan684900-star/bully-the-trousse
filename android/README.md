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
  quand ils se déclenchent. Les mécaniques liées à l'espace (Trousse
  Lunaire, Fusée, Avion) ne sont pas portées, faute de séquence spatiale
  côté Android. Toujours pas de rendu visuel différent par skin (même
  silhouette orange dans `ThrowCanvas`).

- **Rebond de la Trousse à Baskets** : `ThrowSequence.tap()` accepte
  maintenant `basketBounceChances` (`Skins.BASKET_BOUNCE_CHANCES`, 20% puis
  6%, portage de `BASKET_BOUNCE_CHANCES`/`tryBasketBounce()`) — au 3e tap,
  un tirage peut relancer une charge de puissance (`ChargingPower` avec
  `cumulativeDistanceMeters`/`bounceCount`) au lieu de conclure le lancer,
  jusqu'à 2 fois ; la distance finale cumule tous les segments, testé
  (78 tests au total). Branché dans `:app` : équiper la Trousse à Baskets
  active vraiment les rebonds, avec un message "🏀 Rebond !" pendant la
  charge suivante, la trousse posée à sa position cumulée entre deux
  segments (vérifié contre le comportement web : `tryBasketBounce()` ne
  réinitialise jamais `worldX`, et il n'y a en fait *pas* d'animation de vol
  entre deux segments côté web non plus — la trousse reste simplement
  visible, immobile, à l'endroit où elle vient de rebondir, pendant que la
  charge redémarre. Le portage Android fait donc déjà la bonne chose ici).

- **Succès et défis quotidiens** : `Achievements` (les 47 succès, portage
  exact de `ACHIEVEMENTS` — mêmes ids/émojis/conditions) et
  `DailyChallenges`/`ChallengePool` (génération de 3 défis parmi 10 types,
  progression, réclamation — portage de `CHALLENGE_POOL`/
  `generateDailyChallenges()`/`ensureDailyChallenges()`/
  `bumpDailyChallenge()`/`claimDailyChallenge()`) dans `:core`, testés (98
  tests au total). Branchés dans `:app` : les défis du jour se génèrent
  tout seuls au premier lancer/achat de la journée, progressent vraiment
  (lancers, distance, distance cumulée, argent gagné, lancers parfaits,
  dérapages, dépenses) et peuvent être réclamés depuis un petit panneau ;
  les succès se débloquent automatiquement après chaque lancer/achat, avec
  un compteur "🏆 X / 47" affiché en bas de l'écran.

- **Monde Plage** : `Beach` dans `:core` (accès via la Trousse à Claquettes/
  le billet de bus, argent oublié à la première arrivée, rebond sur un
  parasol pendant le vol — priorité parasol > serviette > château, portage
  de `enterBeachProfile()`/`leaveBeachProfile()`/`tryBeachParasolBounce()`/
  `beachFinalizeLanding()`) et `BeachCinematic` (séquencement de la
  cinématique de déblocage, mêmes 30 durées exactes que `BCINE` côté web),
  testés (112 tests au total). Branché dans `:app` : `BeachRow` (achat
  Claquettes → cinématique → aller à la plage → billet de bus du retour),
  `animateBeachFlight` (le rebond sur un parasol continue vraiment le vol
  au lieu d'atterrir), record/argent/compteurs (`plageBestDistance`,
  `plageThrows`, `plageMoneyEarned`, `plageParasolBounces`, etc.) tous mis
  à jour pour de vrai. **Simplification assumée et documentée dans le code**
  (`BeachCinematicScreen.kt`) : contrairement à la cinématique du volcan,
  celle de la plage n'a pas d'issue possible autre que le succès côté web
  (`bcFinish()` est toujours atteint) — le séquencement porte donc les
  vraies durées de chaque phase, mais pas les mini-séquences interactives
  du web (course-poursuite de crabes, visée, dialogues), qui n'influencent
  que l'habillage là-bas.

- **Décor des mondes Volcan et Plage** : `VolcanoDecor` (deux couches de
  volcans en parallaxe + fissures incandescentes au sol, portage de
  `drawVolcanoBackground()`) et `BeachDecor` (dunes lointaines, bosses de
  sable + coquillages, accessoires décoratifs parasol/serviette/château,
  portage de `drawBeachBackground()`/`generateBeachDecor()`) dans `:core`,
  testés (129 tests au total). `generateBeachDecor()` utilise
  `Math.random()` côté web ; `BeachDecor.decorativeProps()` utilise
  `seededRand` à la place pour rester déterministe et testable (la position
  exacte de ces accessoires est purement décorative). Branché dans
  `:app` (`ThrowCanvas` prend maintenant un paramètre `world` et choisit sa
  palette + son décor en conséquence) : la Cour, le Volcan et la Plage ont
  chacun leur ciel/sol/décor visuellement distincts. Simplifications
  assumées : thème "jour" uniquement (pas de variante nuit), pas de lune/
  cendres/mer animées, formes vectorielles simples pour les accessoires
  (comme la trousse elle-même).

- **Écrans séparés et sélecteur de monde** : l'app est découpée en vraies
  destinations (`Screen` scellé dans `MainActivity.kt`, dispatché par
  `GameRoot` — pas de pile d'historique, juste "où on est", chaque écran
  sait revenir au Menu) au lieu d'un unique écran fourre-tout :
  - `MenuScreen.kt` — résumé (argent, record, succès), `WorldSelector`
    (portage de `buildWorldsRow()`/`handleWorldCardClick()` : Cour toujours
    accessible, Volcan/Plage verrouillés/lancent leur cinématique si besoin,
    impossible de changer de monde tant qu'on est coincé sur la plage sans
    payer le bus), défis du jour, et les boutons vers Jeu/Boutique.
  - `GameScreen.kt` — uniquement la trousse (Canvas + boucle de lancer).
  - `ShopScreen.kt` — niveaux, skins, traînées.
  - `Purchases.kt` — `applyPurchase()` (progression du défi "spend" +
    vérification des succès), factorisé puisque plusieurs écrans achètent
    des choses (Boutique, mais aussi Claquettes/billet de bus sur le Menu).

  Simplification assumée (documentée dans `GameScreen.kt`) : revenir au
  Menu en pleine charge (avant le 3e tap) abandonne ce lancer plutôt que de
  le mettre en pause — un nouvel écran Jeu repart toujours de `Idle`.

## Ce qu'il reste à faire (dans un ordre logique)

1. **Vrai sprite pour la trousse** — remplacer sa forme vectorielle par un
   vrai sprite (+ ses variantes de skins, maintenant que la liste des skins
   existe), si des assets sont fournis.
2. **Firebase** (SDK Android, différent du SDK JS utilisé côté web) pour
   les comptes, le classement, les cadeaux — la synchronisation cloud des
   succès/défis déjà portés localement inclue. Probablement la partie la
   plus longue, vu tout ce qui a été construit et corrigé côté web cette
   session (comptes multi-appareils, cadeaux, etc.) — **et pas vérifiable
   dans ce bac à sable** : le SDK Firebase Android a le même problème que
   l'Android Gradle Plugin (dépôt Google Maven injoignable ici), et il
   faudrait de vrais identifiants de projet Firebase. À faire dans Android
   Studio directement.

Chaque étape devrait suivre le même principe que celle-ci : porter la
logique dans `:core` avec des tests dont les valeurs de référence viennent
directement des formules JS, avant de la brancher à l'UI.
