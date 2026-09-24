package com.bullythetrousse.app

// FICHIER GÉNÉRÉ depuis CHANGELOG (index.html) : ne pas éditer à la main.

/**
 * Le journal des changements du site, repris intégralement (96
 * versions, même ordre, entrées majeures et mineures) dans les deux langues.
 * Accessible en touchant le numéro de version sur le menu, comme
 * `#version-tag` côté web.
 */
data class ChangelogLine(val fr: String, val en: String)

data class ChangelogEntry(val version: String, val major: List<ChangelogLine>, val minor: List<ChangelogLine>)

/** La version affichée sur le menu : la plus récente du journal. */
val GAME_VERSION: String get() = CHANGELOG.first().version

val CHANGELOG: List<ChangelogEntry> by lazy { changelogPart0() + changelogPart1() }

// Découpé en deux fonctions : une seule dépasserait la taille de méthode JVM.
private fun changelogPart0(): List<ChangelogEntry> = listOf(
    ChangelogEntry(
        "10.2.2",
        listOf(),
        listOf(ChangelogLine("Ajoute un délai de 30s entre deux envois de cadeaux, pour empêcher le spam", "Added a 30s cooldown between gift sends, to prevent spamming")),
    ),
    ChangelogEntry(
        "10.2.1",
        listOf(),
        listOf(ChangelogLine("Chance de bonus de la Trousse Pièce augmentée (40% → 50%)", "Coin Pencil Case bonus chance increased (40% → 50%)")),
    ),
    ChangelogEntry(
        "10.2.0",
        listOf(ChangelogLine("8 nouveaux succès : Étourdi (oublier son argent à la plage), 20 000m et 50 000m, Multimillionnaire (10M\$), Change de tête (choisir un avatar), 5000m à la plage, 1000 lancers, et Niveau 100", "8 new achievements: Forgetful (forget your money at the beach), 20,000m and 50,000m, Multimillionaire (\$10M), New look (pick an avatar), 5000m at the beach, 1000 throws, and Level 100")),
        listOf(),
    ),
    ChangelogEntry(
        "10.1.15",
        listOf(),
        listOf(ChangelogLine("Petite retouche de contenu", "Small content tweak")),
    ),
    ChangelogEntry(
        "10.1.14",
        listOf(),
        listOf(ChangelogLine("Petite retouche de texte", "Small text tweak")),
    ),
    ChangelogEntry(
        "10.1.13",
        listOf(),
        listOf(ChangelogLine("Corrige le bouton 🦇 de la Trousse Vampire qui disparaissait pour le reste du lancer dès qu'on le relâchait avant les 3s, même s'il restait du temps de boost", "Fixed the Vampire Pencil Case's 🦇 button vanishing for the rest of the throw as soon as it was released before the 3s cap, even with boost time still remaining")),
    ),
    ChangelogEntry(
        "10.1.11",
        listOf(),
        listOf(ChangelogLine("Petite retouche de texte", "Small text tweak")),
    ),
    ChangelogEntry(
        "10.1.10",
        listOf(),
        listOf(ChangelogLine("Petite retouche de texte", "Small text tweak")),
    ),
    ChangelogEntry(
        "10.1.9",
        listOf(ChangelogLine("Nouvel easter egg caché à découvrir...", "New hidden easter egg to discover...")),
        listOf(),
    ),
    ChangelogEntry(
        "10.1.8",
        listOf(),
        listOf(ChangelogLine("Retire l'easter egg caché ajouté dans les versions précédentes", "Removed the hidden easter egg added in previous versions")),
    ),
    ChangelogEntry(
        "10.1.1 - 10.1.6",
        listOf(),
        listOf(ChangelogLine("Petites retouches sur un easter egg caché récemment ajouté (nom, succès, compatibilité tactile)", "Small tweaks to a recently added hidden easter egg (name, achievement, touch compatibility)")),
    ),
    ChangelogEntry(
        "10.1.0",
        listOf(ChangelogLine("Nouvel easter egg caché à découvrir...", "New hidden easter egg to discover...")),
        listOf(),
    ),
    ChangelogEntry(
        "10.0.5",
        listOf(),
        listOf(ChangelogLine("Boost 🦇 de la Trousse Vampire légèrement renforcé : le multiplicateur de vitesse passe de x1,5 à x1,65 par seconde maintenue (jusqu'à x4,5 sur les 3s cumulées, contre x3,4 avant)", "Vampire Pencil Case's 🦇 boost slightly buffed: the speed multiplier goes from x1.5 to x1.65 per second held (up to x4.5 over the full 3s, up from x3.4)")),
    ),
    ChangelogEntry(
        "10.0.4",
        listOf(),
        listOf(ChangelogLine("La portée de décor de la Plage passe de ~15000m à 100000m : des joueurs dépassaient déjà les 50000m, retrouvant une plage vide au-delà", "Beach scenery range goes from ~15000m to 100000m: some players already went past 50000m, ending up on an empty beach beyond that")),
    ),
    ChangelogEntry(
        "10.0.3",
        listOf(),
        listOf(ChangelogLine("Sur mobile en thème sombre, la lune du menu chevauchait le titre \"Bully the Trousse\" — repositionnée dans son coin, bien dégagée du texte", "On mobile in dark theme, the menu's moon overlapped the \"Bully the Trousse\" title — repositioned into its own corner, clear of the text")),
    ),
    ChangelogEntry(
        "10.0.2",
        listOf(),
        listOf(ChangelogLine("Se déconnecter d'un compte temporaire (sans code) efface maintenant son entrée de classement et son profil : sans ça, un pseudo fantôme restait affiché avec le score figé de l'appareil déconnecté. Un compte lié à un code, lui, n'est jamais effacé — on peut y revenir quand on veut", "Logging out of a temporary account (no code) now clears its leaderboard entry and profile: without this, a ghost nickname stayed on the board with the disconnected device's frozen score. An account tied to a code is never cleared — you can come back to it whenever you want"), ChangelogLine("L'entrée de classement est republiée à chaque connexion : un classement incohérent se répare désormais tout seul, sans attendre un nouveau record", "The leaderboard entry is republished on every login: an inconsistent leaderboard now repairs itself, without waiting for a new record")),
    ),
    ChangelogEntry(
        "10.0.1",
        listOf(),
        listOf(ChangelogLine("Correctif majeur de la refonte des comptes : à chaque chargement, le jeu recréait un compte anonyme par-dessus le compte connecté, ce qui annulait purement et simplement la synchronisation entre appareils", "Major fix to the account overhaul: on every load the game created a fresh anonymous account on top of the connected one, which plainly cancelled out cross-device syncing"), ChangelogLine("La synchronisation ne peut plus annuler un lancer : une sauvegarde reçue d'un autre appareil pendant une manche est ignorée si elle est plus ancienne que la manche qui vient de se terminer", "Syncing can no longer undo a throw: a save received from another device mid-run is discarded if it predates the run that just ended"), ChangelogLine("Plus de message \"partie synchronisée\" toutes les 30 secondes ni au lancement : seules les vraies différences sont signalées (le temps de jeu de chaque appareil ne compte plus comme un changement)", "No more \"save synced\" message every 30 seconds or on launch: only real differences are reported (each device's play time no longer counts as a change)"), ChangelogLine("Le thème et la langue suivent maintenant la synchronisation, et le temps de jeu ne peut plus reculer", "Theme and language now follow the sync, and play time can no longer go backwards"), ChangelogLine("Connexion à un compte plus sûre : si elle échoue, l'appareil reste utilisable sur son compte actuel au lieu de se retrouver déconnecté", "Safer account connection: if it fails, the device stays usable on its current account instead of ending up signed out"), ChangelogLine("Le nettoyage d'un compte temporaire efface aussi ses succès et ses abonnements : les pourcentages de succès ne peuvent plus dépasser 100%", "Cleaning up a temporary account now also clears its achievements and follows: achievement percentages can no longer exceed 100%")),
    ),
    ChangelogEntry(
        "10.0.0",
        listOf(ChangelogLine("Refonte complète du système de comptes : ton code de récupération n'est plus une simple copie de sauvegarde, c'est devenu ton véritable identifiant de connexion. Entrer ton code sur un autre appareil le connecte au MÊME compte — la partie se synchronise automatiquement dans les deux sens, et tu n'as plus qu'une seule entrée au classement au lieu de deux comptes en double qui ne communiquaient pas", "Complete account system overhaul: your recovery code is no longer just a save copy, it has become your actual login credential. Entering your code on another device connects it to the SAME account — the save syncs automatically both ways, and you now have a single leaderboard entry instead of two duplicate accounts that never talked to each other")),
        listOf(ChangelogLine("La partie se synchronise en direct entre tous tes appareils connectés : ce que tu gagnes sur l'un apparaît sur l'autre, sans rien faire (jamais au milieu d'un lancer, la manche en cours n'est pas interrompue)", "Your save syncs live across all your connected devices: what you earn on one shows up on the other, automatically (never mid-throw — the current run is not interrupted)"), ChangelogLine("Connecter un appareil ne laisse plus de compte fantôme derrière lui : l'ancien compte temporaire et son entrée de classement sont supprimés proprement", "Connecting a device no longer leaves a ghost account behind: the old temporary account and its leaderboard entry are properly deleted"), ChangelogLine("Les entrées fantômes déjà présentes dans le classement (anciennes récupérations) sont désormais masquées", "Ghost entries already sitting in the leaderboard (from past restores) are now hidden"), ChangelogLine("Les anciens codes continuent de fonctionner : ils restaurent la partie comme avant, puis deviennent automatiquement de vrais identifiants de connexion", "Old codes still work: they restore the save as before, then automatically become real login credentials")),
    ),
    ChangelogEntry(
        "9.7.7",
        listOf(),
        listOf(ChangelogLine("Corrige les skins qui s'affichaient toujours comme la Trousse Classique pendant le vol (et dans la cinématique du Volcan) sur certains navigateurs mobiles : le rendu utilisait CanvasRenderingContext2D.filter, non supporté par Safari/WebKit avant sa version 15.4. Remplacé par un filtrage pixel par pixel, compatible partout", "Fixed pencil case skins always rendering as the Classic one mid-flight (and in the Volcano cutscene) on some mobile browsers: rendering used CanvasRenderingContext2D.filter, unsupported by Safari/WebKit before version 15.4. Replaced with pixel-by-pixel filtering, compatible everywhere")),
    ),
    ChangelogEntry(
        "9.7.6",
        listOf(),
        listOf(ChangelogLine("Corrige (pour de bon) le bouton 🦇 de la trousse Vampire qui restait affiché sur d'autres trousses sur Chrome iOS : classList.toggle(nom, force) est remplacé par des add()/remove() explicites, le paramètre \"force\" ayant un historique de support inégal sur Safari/WebKit", "Properly fixed the Vampire pencil case's 🦇 button staying shown on other pencil cases on Chrome iOS: classList.toggle(name, force) replaced with explicit add()/remove(), since the \"force\" parameter has an inconsistent support history on Safari/WebKit")),
    ),
    ChangelogEntry(
        "9.7.5",
        listOf(),
        listOf(ChangelogLine("Le bouton 🦇 de la trousse Vampire ne touche plus le DOM qu'à un vrai changement d'état (au lieu de forcer classList.toggle() à chaque frame, 60 à 120 fois par seconde) : corrige un scintillement rapporté sur certains navigateurs mobiles (Chrome iOS), y compris sur des trousses sans lien avec la Vampire", "The Vampire pencil case's 🦇 button now only touches the DOM on an actual state change (instead of forcing classList.toggle() every frame, 60-120 times per second): fixes a flicker reported on some mobile browsers (Chrome iOS), including on pencil cases unrelated to the Vampire")),
    ),
    ChangelogEntry(
        "9.7.4",
        listOf(),
        listOf(ChangelogLine("Boost 🦇 de la trousse Vampire rééquilibré à la baisse : x3,4 sur les 3s cumulées au lieu de x5,8, jugé trop abusé", "Vampire pencil case's 🦇 boost toned down: x3.4 over the full 3s instead of x5.8, deemed too strong")),
    ),
    ChangelogEntry(
        "9.7.3",
        listOf(),
        listOf(ChangelogLine("Le bouton 🦇 de la trousse Vampire est déplacé en bas au centre de l'écran (au lieu d'en bas à droite)", "The Vampire pencil case's 🦇 button moved to bottom-center of the screen (instead of bottom-right)")),
    ),
    ChangelogEntry(
        "9.7.2",
        listOf(),
        listOf(ChangelogLine("Boost 🦇 de la trousse Vampire nettement plus fort et plus visible : vitesse quasi x6 sur les 3s cumulées (au lieu de x2,5), avec une vignette violette pulsante à l'écran tant qu'il est maintenu", "The Vampire pencil case's 🦇 boost is now much stronger and more visible: nearly x6 speed over the full 3s (up from x2.5), with a pulsing purple screen vignette while it's held")),
    ),
    ChangelogEntry(
        "9.7.1",
        listOf(),
        listOf(ChangelogLine("Corrige le bouton 🦇 de la trousse Vampire qui disparaissait de façon imprévisible : un simple survol (sans jamais appuyer) le consommait par erreur. Sa visibilité est aussi recalculée à chaque frame (plus seulement pendant le vol) pour ne jamais rester affiché par erreur", "Fixed the Vampire pencil case's 🦇 button disappearing unpredictably: a mere hover (without ever pressing) was wrongly consuming it. Its visibility is also recalculated every frame (not just mid-flight) so it can never stay shown by mistake")),
    ),
    ChangelogEntry(
        "9.7.0",
        listOf(),
        listOf(ChangelogLine("2 nouvelles trousses : Fantôme (100 000 \$) — +8 Vitesse — et Vampire (60 000 \$) — +5 Puissance/+6 Vitesse, mais vole jusqu'à 40% de l'argent gagné à chaque lancer (selon la distance) ; en échange, un bouton 🦇 apparaît en vol pour accélérer la trousse (3s cumulées max, une fois par lancer)", "2 new pencil cases: Ghost (\$100,000) — +8 Speed — and Vampire (\$60,000) — +5 Power/+6 Speed, but steals up to 40% of the money earned each throw (based on distance); in exchange, a 🦇 button appears mid-flight to speed up the pencil case (3s total max, once per throw)")),
    ),
    ChangelogEntry(
        "9.6.5",
        listOf(),
        listOf(ChangelogLine("Nouveau bouton \"Se déconnecter\" dans les Réglages (avec confirmation) : repart avec un compte tout neuf sur cet appareil", "New \"Log out\" button in Settings (with confirmation): starts fresh with a brand new account on this device")),
    ),
    ChangelogEntry(
        "9.6.4",
        listOf(),
        listOf(ChangelogLine("La pastille des onglets de la boutique a maintenant une vraie physique de ressort (accélération/rebond réaliste), et peut être attrapée et glissée au doigt d'un onglet à l'autre au lieu de seulement cliquer", "The shop tabs' pill now has real spring physics (realistic acceleration/bounce), and can be grabbed and dragged between tabs with your finger instead of only clicking")),
    ),
    ChangelogEntry(
        "9.6.3",
        listOf(),
        listOf(ChangelogLine("Nouvel effet visuel sur les onglets de la boutique (Améliorations/Skins/Traînées) : une pastille glisse maintenant avec un léger rebond entre les onglets au lieu d'un changement de couleur instantané, avec un petit effet d'onde au clic", "New visual effect on the shop tabs (Upgrades/Skins/Trails): a pill now slides with a slight bounce between tabs instead of an instant color change, with a small ripple effect on click")),
    ),
    ChangelogEntry(
        "9.6.2",
        listOf(),
        listOf(ChangelogLine("Un son se déclenche maintenant à l'envoi d'un cadeau d'argent", "A sound now plays when sending a money gift")),
    ),
    ChangelogEntry(
        "9.6.1",
        listOf(),
        listOf(ChangelogLine("Nouveau : un bouton dans les Réglages permet de télécharger les 3 musiques de fond du jeu (monde normal, Plage, Volcans)", "New: a button in Settings lets you download the game's 3 background music tracks (normal world, Beach, Volcanoes)")),
    ),
    ChangelogEntry(
        "9.6.0",
        listOf(),
        listOf(ChangelogLine("Rééquilibrage du coût des améliorations Puissance/Vitesse : au-delà du niveau 25, le coût grimpe maintenant de façon linéaire au lieu de rester exponentiel. Les joueurs de très haut niveau payaient jusqu'à plusieurs centaines de millions, voire des milliards, par palier", "Rebalanced Power/Speed upgrade costs: past level 25, the cost now climbs linearly instead of staying exponential. Very high-level players were paying up to several hundred million, even billions, per level")),
    ),
    ChangelogEntry(
        "9.5.3",
        listOf(),
        listOf(ChangelogLine("La portée de décor de la Plage passe de ~2500m à ~15000m : certains joueurs dépassaient déjà largement les 2500m avec des niveaux élevés, retrouvant une plage vide", "Beach scenery range goes from ~2500m to ~15000m: some players already went well past 2500m with high levels, ending up on an empty beach")),
    ),
    ChangelogEntry(
        "9.5.2",
        listOf(),
        listOf(ChangelogLine("Correction de la notification de cadeau invisible sur le menu (elle ne s'affichait qu'en jeu, à cause d'un décalage de hauteur sur mobile)", "Fixed the gift notification being invisible on the menu (it only showed up in-game, due to a mobile height offset)"), ChangelogLine("La Plage a maintenant du décor sur une bien plus grande distance : les lancers très puissants (trousse Fusée...) ne traversaient plus qu'une plage vide au-delà d'environ 750m", "The Beach now has scenery over a much longer distance: very powerful throws (Rocket pencil case...) used to cross an empty beach beyond about 750m"), ChangelogLine("Légère hausse des chances d'apparition des parasols/serviettes/châteaux sur la Plage", "Slightly higher odds of umbrellas/towels/castles appearing on the Beach")),
    ),
    ChangelogEntry(
        "9.5.1",
        listOf(),
        listOf(ChangelogLine("Correction d'un bug de la trousse Fusée sur la Plage : les parasols/serviettes/châteaux ne disparaissaient plus jamais après un boost en apesanteur ou une traversée lunaire, car l'accessoire à atteindre restait posé à l'ancienne position prédite avant le détour spatial", "Fixed a Rocket pencil case bug on the Beach: umbrellas/towels/castles could permanently vanish after a zero-g boost or a lunar traversal, because the target prop stayed at the old predicted position from before the space detour")),
    ),
    ChangelogEntry(
        "9.5.0",
        listOf(),
        listOf(ChangelogLine("Les cadeaux d'argent entre joueurs arrivent maintenant en temps réel : si le destinataire est déjà en ligne, il reçoit l'argent et une notification (\"🎁 X vous a envoyé N \$\") instantanément, sans avoir besoin de recharger la page", "Money gifts between players now arrive in real time: if the recipient is already online, they get the money and a notification (\"🎁 X sent you \$N\") instantly, without needing to reload the page")),
    ),
    ChangelogEntry(
        "9.4.0",
        listOf(ChangelogLine("2 nouvelles trousses : projet-trousseXfusée-012 (50 000 \$) — 50% plus rapide une fois propulsée en apesanteur, et traverse le sol de la Lune au lieu de s'y écraser, pour retomber directement sur le monde en cours — et Boeing 747 (100 000 \$) — ne peut jamais décoller vers l'espace, même en visant la zone verte, mais 50% plus rapide dans le monde en cours", "2 new pencil cases: projet-trousseXfusée-012 (\$50,000) — 50% faster once boosted in zero-g, and tunnels through the Moon's ground instead of crashing into it, landing straight back in the current world — and Boeing 747 (\$100,000) — can never launch into zero-g, even aiming the green zone, but 50% faster in the current world")),
        listOf(),
    ),
    ChangelogEntry(
        "9.3.0",
        listOf(),
        listOf(ChangelogLine("4 nouvelles traînées : Encre (un ruban épais qui bave en taches), Confettis (des rectangles multicolores qui tournoient en tombant), Aurore (de grands rideaux boréaux qui ondulent du vert au violet) et Trou Noir (la lumière aspirée en spirale derrière la trousse)", "4 new trails: Ink (a thick ribbon that bleeds into blots), Confetti (multicolored rectangles tumbling as they fall), Aurora (big northern-lights curtains rippling from green to violet) and Black Hole (light sucked into a spiral behind the pencil case)"), ChangelogLine("Traînées bien plus réalistes : le sillage est maintenant un vrai ruban fuselé et lissé (fini le chapelet de segments), il ondule avec sa propre turbulence comme de la fumée, et un halo lumineux qui palpite est collé à la trousse", "Much more realistic trails: the wake is now a real tapered, smoothed ribbon (no more string of segments), it ripples with its own turbulence like smoke, and a pulsing glow is attached to the pencil case"), ChangelogLine("Étincelles retouchées : chaque particule baigne dans sa propre lueur, et les fumées/taches ont des bords flous au lieu de cercles nets", "Reworked sparks: every particle now sits in its own bloom, and smoke/blots have soft edges instead of hard circles")),
    ),
    ChangelogEntry(
        "9.2.0",
        listOf(),
        listOf(ChangelogLine("Une trousse déjà lancée peut maintenant être mise en pause : ouvrir les Réglages (⚙️) pendant le vol fige la partie, et la refermer la reprend exactement où elle en était", "A pencil case already in the air can now be paused: opening Settings (⚙️) mid-flight freezes the game, and closing it resumes exactly where you left off")),
    ),
    ChangelogEntry(
        "9.1.0",
        listOf(),
        listOf(ChangelogLine("Chaque monde a maintenant sa propre musique de fond : une piste pour la Plage, une pour les Volcans, en plus de celle du monde normal. Le changement se fait automatiquement en changeant de monde", "Each world now has its own background music: one track for the Beach, one for the Volcanoes, on top of the normal world's. It switches automatically when you change world"), ChangelogLine("Nouveau succès \"Adorateur de la musique\" : écouter la musique de fond sans coupure pendant 3h d'affilée", "New \"Music worshipper\" achievement: listen to the background music without interruption for 3 hours straight")),
    ),
    ChangelogEntry(
        "9.0.0",
        listOf(ChangelogLine("Le classement (monde normal ET Plage) n'est plus limité à un top 15 : il affiche tout le monde, sans exception (l'ancien rappel de \"ta ligne\" en bas de classement n'a donc plus lieu d'être)", "The leaderboard (normal world AND Beach) is no longer capped at a top 15: it now shows everyone, no exceptions (the old \"your row\" recall at the bottom is no longer needed)"), ChangelogLine("Le code de récupération (Réglages) couvre maintenant aussi le classement de la Plage : récupérer sa partie sur un autre appareil retire proprement l'ancienne entrée des deux classements", "The recovery code (Settings) now also covers the Beach leaderboard: recovering your save on another device properly retires the old entry from both leaderboards")),
        listOf(),
    ),
    ChangelogEntry(
        "8.1.7",
        listOf(),
        listOf(ChangelogLine("Plus de double économie sur la plage : l'argent, les améliorations, les skins/traînées et la durabilité sont désormais partagés avec le monde normal. Seul l'argent est remis à 0 à l'arrivée, une seule fois (le classement de la plage reste séparé, avec son propre record)", "No more separate beach economy: money, upgrades, skins/trails and durability are now shared with the normal world. Only your money is reset to 0 on arrival, once (the beach leaderboard stays separate, with its own record)"), ChangelogLine("Suppression de la santé mentale : les serviettes trouvées pendant un lancer restent, mais sont désormais purement décoratives ; la carte serviette a disparu de la boutique", "Removed the mental health system: towels found during a throw remain, but are now purely decorative; the towel card is gone from the shop")),
    ),
    ChangelogEntry(
        "8.1.5",
        listOf(),
        listOf(ChangelogLine("Rééquilibrage du monde Plage : trois évènements aléatoires par lancer (parasol, serviette, château de sable), tirés indépendamment au-delà de 100 m ; 11 succès liés à la plage", "Beach world rebalance: three random events per throw (parasol, towel, sandcastle), rolled independently past 100 m; 11 beach-related achievements")),
    ),
    ChangelogEntry(
        "8.1.3",
        listOf(),
        listOf(ChangelogLine("Corrige un vrai bug de la cinématique (plage comme volcan) : cliquer pendant le fondu de sortie du menu (2,2 à 4 s) retombait sur les boutons du menu resté cliquable dessous — un clic malheureux sur la carte \"Volcans\" pouvait démarrer l'autre cinématique par-dessus", "Fixed a real cutscene bug (beach and volcano alike): clicking during the menu's fade-out (2.2 to 4 s) landed on the menu's buttons, which stayed clickable underneath — an unlucky tap on the \"Volcanoes\" card could start the other cutscene on top of it")),
    ),
    ChangelogEntry(
        "8.1.2",
        listOf(),
        listOf(ChangelogLine("Les parasols, serviettes et châteaux de sable n'apparaissent plus que sur les lancers de plus de 100 m ; en dessous, le stress des crabes (-5 de santé mentale) ne s'applique plus non plus", "Parasols, towels and sandcastles now only show up on throws past 100 m; below that, the crab stress (-5 mental health) no longer applies either"), ChangelogLine("Le billet de bus du retour ne coûte 250 000 \$ que la toute première fois : une fois payé, revenir sur la plage puis en repartir est gratuit pour toujours (comme la Trousse à Claquettes à l'entrée)", "The return bus ticket only costs \$250,000 the very first time: once paid, coming back to the beach and leaving again is free forever (like the Flip-Flop Pencil Case on the way in)")),
    ),
    ChangelogEntry(
        "8.1.1",
        listOf(),
        listOf(ChangelogLine("Corrige le soleil/la lune qui restait visible par-dessus le fondu au noir de la cinématique plage (fermeture en diaphragme)", "Fixed the sun/moon staying visible over the beach cutscene's fade to black (iris close)"), ChangelogLine("Ajout d'un bouton pour revoir le tutoriel (Réglages) : les bases du jeu, plus ceux du Volcan et de la Plage une fois ces mondes débloqués", "Added a button to replay the tutorial (Settings): the game basics, plus the Volcano and Beach ones once those worlds are unlocked")),
    ),
    ChangelogEntry(
        "8.1.0",
        listOf(ChangelogLine("Nouveau monde : la Plage ! Il s'ouvre avec la Trousse à Claquettes (100 000 \$ en boutique), puis une longue cinématique : le bus, le trajet, l'arrivée sur le sable, les crabes... et un tuto joué en direct", "New world: the Beach! It opens with the Flip-Flop Pencil Case (\$100,000 in the shop), then a long cutscene: the bus, the trip, the arrival on the sand, the crabs... and a tutorial played live")),
        listOf(ChangelogLine("La plage est regénérée à chaque lancer : parasols, serviettes et châteaux de sable ne sont jamais deux fois au même endroit", "The beach is regenerated on every throw: parasols, towels and sandcastles are never twice in the same place"), ChangelogLine("Trois évènements tirés au sort à chaque lancer : rebond sur un parasol (50%), serviette (60%) et château de sable (40%)", "Three events rolled on every throw: parasol bounce (50%), towel (60%) and sandcastle (40%)"), ChangelogLine("11 nouveaux succès liés à la plage : acheter les claquettes, débloquer le monde, rentrer en bus, 100 lancers sur le sable, records de 500 m et 2000 m, 250 000 \$ gagnés là-bas, 25 rebonds sur parasol, 20 serviettes, 15 châteaux écroulés, et le burn-out", "11 new beach achievements: buy the flip-flops, unlock the world, take the bus home, 100 throws on the sand, 500 m and 2000 m records, \$250,000 earned there, 25 parasol bounces, 20 towels, 15 collapsed castles, and the burnout")),
    ),
    ChangelogEntry(
        "8.0.0",
        listOf(ChangelogLine("Le compte email/mot de passe est remplacé par un code de récupération : dans Réglages, affiche ton code de 16 chiffres et note-le. Sur un autre téléphone (ou après un nettoyage du navigateur), il suffit de le saisir pour récupérer ta partie — avec le choix de garder la partie actuelle ou celle du code si les deux ont de la progression", "The email/password account is replaced by a recovery code: in Settings, show your 16-digit code and write it down. On another phone (or after a browser cleanup), just enter it to restore your save — with a choice between keeping the current save or the code's one if both have progress")),
        listOf(),
    ),
)

// Découpé en deux fonctions : une seule dépasserait la taille de méthode JVM.
private fun changelogPart1(): List<ChangelogEntry> = listOf(
    ChangelogEntry(
        "7.2.0",
        listOf(),
        listOf(ChangelogLine("Classement : quand tu n'es pas dans le top 15, ta propre ligne est maintenant rappelée en bas avec ton vrai rang (et son halo doré), au lieu de n'apparaître nulle part", "Leaderboard: when you're not in the top 15, your own row is now shown at the bottom with your real rank (and its gold halo), instead of not appearing at all")),
    ),
    ChangelogEntry(
        "7.1.1",
        listOf(),
        listOf(ChangelogLine("Correctif : après une fusion de comptes, l'ancien compte abandonné n'apparaît plus en double dans le classement, et le halo doré \"c'est toi\" s'affiche de nouveau correctement", "Fix: after merging accounts, the abandoned account no longer shows up twice on the leaderboard, and the \"this is you\" gold halo shows up correctly again")),
    ),
    ChangelogEntry(
        "7.1.0",
        listOf(ChangelogLine("Sécurisation de compte déplacée dans Réglages, et ajout d'un choix quand une connexion mène à un compte déjà existant avec une progression différente : plus de fusion silencieuse, tu choisis laquelle des deux parties garder", "Account security moved into Settings, and added a choice when logging in leads to an existing account with different progress: no more silent merge, you choose which save to keep")),
        listOf(),
    ),
    ChangelogEntry(
        "7.0.1",
        listOf(),
        listOf(ChangelogLine("Modale \"Offrir de l'argent\" : boutons +10/+100/+1000/Max pour ne plus avoir à retenir/taper le montant à la main", "\"Send money\" popup: +10/+100/+1000/Max buttons so you don't have to remember/type the amount by hand")),
    ),
    ChangelogEntry(
        "7.0.0",
        listOf(ChangelogLine("Ajout d'un vrai système de compte : un lien \"Se connecter / Créer un compte\" sous ton pseudo (écran Profil) relie ton compte anonyme à un email + mot de passe. Toute ta progression est conservée, et tu peux ensuite te connecter au même compte depuis n'importe quel appareil", "Added a real account system: a \"Log in / Create account\" link under your pseudo (Profile screen) links your anonymous account to an email + password. All your progress is kept, and you can then log into the same account from any device")),
        listOf(),
    ),
    ChangelogEntry(
        "6.5.0",
        listOf(),
        listOf(ChangelogLine("6 nouvelles traînées avec leurs propres animations : Toxique (bulles), Sakura (pétales qui tournoient), Fantôme (volutes), Foudre (éclairs), Néon (pixels) et Stellaire (étoiles filantes)", "6 new trails with their own animations: Toxic (bubbles), Sakura (swirling petals), Ghost (wisps), Lightning (bolts), Neon (pixels) and Stellar (spinning stars)"), ChangelogLine("Chaque traînée a maintenant son aperçu coloré en boutique", "Each trail now has its own colored preview in the shop")),
    ),
    ChangelogEntry(
        "6.4.0",
        listOf(),
        listOf(ChangelogLine("Traînées refaites entièrement (elles sont encore plus réalistes)", "Trails completely redone (they're even more realistic now)")),
    ),
    ChangelogEntry(
        "6.3.1",
        listOf(),
        listOf(ChangelogLine("Retouche : opacité de la traînée légèrement augmentée", "Tweak: slightly increased trail opacity")),
    ),
    ChangelogEntry(
        "6.3.0",
        listOf(),
        listOf(ChangelogLine("Nouveau rendu des traînées : un ruban rectangulaire qui suit la trousse le long de sa trajectoire, à la place des petits points ronds", "New trail rendering: a rectangular ribbon that follows the pencil case along its trajectory, instead of small round dots")),
    ),
    ChangelogEntry(
        "6.2.0",
        listOf(),
        listOf(ChangelogLine("4 nouveaux types de défis quotidiens (🛣️ distance cumulée, 🎁 cadeaux envoyés, 🎯 QTE parfait dans l'espace, 🛍️ dépenses en boutique) pour que les 3 défis du jour soient moins souvent les mêmes", "4 new daily challenge types (🛣️ cumulative distance, 🎁 gifts sent, 🎯 perfect zero-G QTE, 🛍️ shop spending) so the day's 3 challenges repeat less often")),
    ),
    ChangelogEntry(
        "6.1.1",
        listOf(),
        listOf(ChangelogLine("La modale des cadeaux reçus fusionne maintenant les dons d'un même expéditeur en une seule ligne (montant cumulé) au lieu d'une ligne par don", "The received-gifts popup now merges gifts from the same sender into one line (combined amount) instead of one line per gift")),
    ),
    ChangelogEntry(
        "6.1.0",
        listOf(),
        listOf(ChangelogLine("Nouveau succès : 🎁 \"Grand donateur\" (envoie 1 000 000 \$ en cadeaux depuis le début)", "New achievement: 🎁 \"Big spender\" (send a cumulative \$1,000,000 in gifts)")),
    ),
    ChangelogEntry(
        "6.0.1",
        listOf(),
        listOf(ChangelogLine("Correctif : le système de cadeaux inclut maintenant les deux sens du suivi (les gens qui te suivent ET les gens que tu suis), pas seulement tes abonnés", "Fix: the gifting system now includes both directions of following (people who follow you AND people you follow), not just your followers")),
    ),
    ChangelogEntry(
        "6.0.0",
        listOf(ChangelogLine("Ajout d'un système de cadeaux : depuis ton profil, tu peux offrir de l'argent à tes abonnés. À sa prochaine connexion, la personne voit qui lui a envoyé combien", "Added a gifting system: from your profile, you can send money to your followers. On their next login, they see who sent them what")),
        listOf(),
    ),
    ChangelogEntry(
        "5.7.0",
        listOf(),
        listOf(ChangelogLine("6 nouveaux succès : 🌀 record de 10 000 m, 🤑 1 000 000 \$ gagnés, 🏋️ 500 lancers, 🏃 5 heures de jeu, 💯 niveau 50 (Puissance + Vitesse), 📅 les 3 défis quotidiens réclamés le même jour", "6 new achievements: 🌀 10,000 m record, 🤑 \$1,000,000 earned, 🏋️ 500 throws, 🏃 5 hours played, 💯 level 50 (Power + Speed), 📅 all 3 daily challenges claimed the same day")),
    ),
    ChangelogEntry(
        "5.6.0",
        listOf(),
        listOf(ChangelogLine("Rééquilibrage : coût des améliorations Puissance/Vitesse revu à la baisse (autour de 30, le farm devenait interminable par rapport à l'argent gagné)", "Rebalance: Power/Speed upgrade costs lowered (around level 30, farming took way too long compared to what you earned)")),
    ),
    ChangelogEntry(
        "5.5.0",
        listOf(),
        listOf(ChangelogLine("Rééquilibrage : les récompenses des défis quotidiens s'adaptent maintenant à ta Puissance/Vitesse (elles ne stagnaient plus assez vite pour un joueur bien avancé)", "Rebalance: daily challenge rewards now scale with your Power/Speed (they used to stay flat, so they barely mattered for an advanced player)")),
    ),
    ChangelogEntry(
        "5.4.0",
        listOf(),
        listOf(ChangelogLine("L'écran Profil affiche maintenant si le joueur est en ligne, ou depuis quand il ne l'est plus", "The Profile screen now shows whether the player is online, or how long ago they last were")),
    ),
    ChangelogEntry(
        "5.3.2",
        listOf(),
        listOf(ChangelogLine("Rééquilibrage : le second rebond de la Trousse à Baskets repasse de 3% à 6% de chance", "Rebalance: the Sneaker Case's second bounce chance goes back from 3% to 6%")),
    ),
    ChangelogEntry(
        "5.3.1",
        listOf(),
        listOf(ChangelogLine("Rééquilibrage : le QTE spatial revient à 0.9s par anneau (au lieu de 0.7s)", "Rebalance: the space QTE goes back to 0.9s per ring (instead of 0.7s)")),
    ),
    ChangelogEntry(
        "5.3.0",
        listOf(),
        listOf(ChangelogLine("Nouveau succès : 🕳️ \"4000 mètres\" (atteins un record de 4000 m)", "New achievement: 🕳️ \"4000 meters\" (reach a record of 4000 m)")),
    ),
    ChangelogEntry(
        "5.2.1",
        listOf(),
        listOf(ChangelogLine("Le succès secret est maintenant vraiment bien caché", "The secret achievement is now properly hidden")),
    ),
    ChangelogEntry(
        "5.2.0",
        listOf(),
        listOf(ChangelogLine("Un succès secret se cache quelque part dans le décor... à toi de le trouver 👀", "A secret achievement is hiding somewhere in the scenery... find it 👀")),
    ),
    ChangelogEntry(
        "5.1.0",
        listOf(),
        listOf(ChangelogLine("Nouveau succès : 🦵 \"J'ai des courbatures\" (effectue 100 lancers)", "New achievement: 🦵 \"I'm sore all over\" (make 100 throws)")),
    ),
    ChangelogEntry(
        "5.0.2",
        listOf(),
        listOf(ChangelogLine("Difficulté augmentée : le second rebond de la Trousse à Baskets passe de 5% à 3% de chance, et chaque anneau du QTE spatial rétrécit 0.2s plus vite", "Increased difficulty: the Sneaker Case's second bounce chance drops from 5% to 3%, and each ring in the space QTE now shrinks 0.2s faster")),
    ),
    ChangelogEntry(
        "5.0.1",
        listOf(),
        listOf(ChangelogLine("La modale des défis quotidiens affiche maintenant un compte à rebours en direct jusqu'au prochain renouvellement (minuit)", "The daily challenges modal now shows a live countdown until the next refresh (midnight)")),
    ),
    ChangelogEntry(
        "5.0.0",
        listOf(ChangelogLine("Ajout des défis quotidiens : 3 objectifs tirés au hasard chaque jour (lancers, distance, argent gagné, lancers parfaits, et des défis spéciaux Volcan une fois débloqué), avec une récompense en argent à réclamer une fois complétés", "Added daily challenges: 3 goals picked at random each day (throws, distance, money earned, perfect throws, and special Volcano challenges once unlocked), with a money reward to claim once completed")),
        listOf(),
    ),
    ChangelogEntry(
        "4.3.1",
        listOf(),
        listOf(ChangelogLine("Correctif : changer de pseudo dans les paramètres le met maintenant à jour tout de suite dans le classement mondial (avant, il fallait battre un nouveau record)", "Fix: changing your pseudo in settings now updates it right away on the global leaderboard (previously it only updated on your next record)")),
    ),
    ChangelogEntry(
        "4.3.0",
        listOf(),
        listOf(ChangelogLine("4 nouvelles trousses en boutique : Trousse à Baskets (rebonds bonus à l'atterrissage), Trousse Lunaire (QTE spatial simplifié + argent bonus en apesanteur), Trousse de Fer (300 de durabilité) et Trousse Claude (fenêtre de lancer parfait élargie)", "4 new pencil cases in the shop: Sneaker Case (bonus bounces on landing), Lunar Case (simplified space QTE + bonus money in zero-G), Iron Case (300 durability) and Claude Case (wider perfect-throw window)")),
    ),
    ChangelogEntry(
        "4.2.0",
        listOf(),
        listOf(ChangelogLine("Cliquer sur \"Abonnements\"/\"Abonnés\" d'un profil affiche la liste des comptes en question, avec un clic dessus pour aller voir leur profil", "Clicking a profile's \"Following\"/\"Followers\" now shows the list of accounts, clickable to open their profile")),
    ),
    ChangelogEntry(
        "4.1.0",
        listOf(),
        listOf(ChangelogLine("Cliquer sur la trousse d'un profil affiche maintenant toutes les trousses possédées (et celles qui manquent encore)", "Clicking a profile's pencil case now shows every pencil case owned (and the ones still missing)"), ChangelogLine("Cliquer sur \"Niveau total\" d'un profil détaille maintenant Puissance et Vitesse séparément", "Clicking a profile's \"Total level\" now breaks down Power and Speed separately")),
    ),
    ChangelogEntry(
        "4.0.1",
        listOf(),
        listOf(ChangelogLine("Photo de profil : remplacée par une liste de 24 icônes au choix", "Profile picture: replaced with a list of 24 icons to choose from")),
    ),
    ChangelogEntry(
        "4.0.0",
        listOf(ChangelogLine("Ajout d'un système de comptes complet : écran Profil (photo, trousse équipée, abonnés/abonnements, progression hebdomadaire, argent total gagné, rang mondial, niveau, succès), possibilité de suivre les autres joueurs depuis le classement, compte privé (paramètres), et photo de profil personnalisable", "Added a full account system: Profile screen (picture, equipped pencil case, followers/following, weekly progress, total money earned, global rank, level, achievements), the ability to follow other players from the leaderboard, private accounts (settings), and a customizable profile picture")),
        listOf(ChangelogLine("Les boutons thème et langue sont regroupés dans une nouvelle icône réglages (⚙️)", "Theme and language buttons are now grouped in a new settings icon (⚙️)")),
    ),
    ChangelogEntry(
        "3.6.9",
        listOf(),
        listOf(ChangelogLine("Correction du vrai problème derrière les messages \"Plage arrive bientôt !\"/\"Ville arrive bientôt !\" illisibles en thème clair : le texte du message (couleur sombre en mode jour) se fondait dans son fond toujours sombre. Texte forcé en blanc, quel que soit le thème", "Fixed the real issue behind unreadable \"Plage is coming soon!\"/\"Ville is coming soon!\" messages in light theme: the message text (dark in light theme) blended into its always-dark background. Text forced to white regardless of theme")),
    ),
    ChangelogEntry(
        "3.6.8",
        listOf(),
        listOf(ChangelogLine("Correction de deux textes illisibles selon le thème : la distance en jeu (blanc fixe, invisible sur le fond clair du mode jour) et le numéro de version (noir fixe, invisible sous le voile nocturne du mode sombre). Les deux s'adaptent maintenant au thème actif", "Fixed two hard-to-read texts depending on theme: the in-game distance (fixed white, invisible on the light theme's background) and the version number (fixed black, invisible under dark theme's night veil). Both now adapt to the active theme")),
    ),
    ChangelogEntry(
        "3.6.7",
        listOf(),
        listOf(ChangelogLine("Correction du petit message (ex: \"Plage arrive bientôt !\") qui passait derrière/sur les icônes musique/thème/langue/liens en bas de l'écran", "Fixed the small toast message (e.g. \"Plage is coming soon!\") overlapping the music/theme/language/links icons at the bottom of the screen")),
    ),
    ChangelogEntry(
        "3.6.6",
        listOf(),
        listOf(ChangelogLine("Changement du numéro de version", "Version number change")),
    ),
    ChangelogEntry(
        "3.5.7",
        listOf(),
        listOf(ChangelogLine("Coût des améliorations Puissance/Vitesse divisé par 2 (même courbe de progression, juste deux fois moins cher à chaque niveau)", "Power/Speed upgrade cost cut in half (same progression curve, just twice as cheap at every level)")),
    ),
    ChangelogEntry(
        "3.5.6",
        listOf(),
        listOf(ChangelogLine("Bonus d'argent des niveaux de Puissance/Vitesse monté de +1%/niveau à +3%/niveau : le coût des améliorations grimpe vite, ce bonus doit se sentir davantage en fin de partie", "Power/Speed levels' money bonus raised from +1%/level to +3%/level: upgrade costs climb fast, this bonus needed to matter more late-game"), ChangelogLine("Ajout d'une pastille dans la boutique qui affiche ce bonus en direct (📈 +X%)", "Added a live indicator in the shop showing this bonus (📈 +X%)")),
    ),
    ChangelogEntry(
        "3.5.5",
        listOf(),
        listOf(ChangelogLine("Nouveau succès \"Réflexes parfaits\" : réussir les 5 anneaux du QTE en apesanteur d'affilée", "New achievement \"Perfect reflexes\": land all 5 rings of the zero-G QTE in a row"), ChangelogLine("Rééquilibrage : chaque niveau de Puissance/Vitesse acheté augmente aussi l'argent gagné par lancer (+1%/niveau), pour que les améliorations restent rentables même à haut niveau", "Rebalance: each Power/Speed level purchased also increases money earned per throw (+1%/level), so upgrades stay worthwhile even at high levels")),
    ),
    ChangelogEntry(
        "3.5.4",
        listOf(),
        listOf(ChangelogLine("Correction de plusieurs problèmes d'affichage sur mobile : les icônes musique/thème/langue pouvaient se chevaucher, et le bas du menu (rangée des mondes) pouvait passer sous ces icônes flottantes sur petit écran. Le menu défile maintenant si besoin", "Fixed several mobile display issues: the music/theme/language icons could overlap each other, and the bottom of the menu (worlds row) could slide under those floating icons on small screens. The menu now scrolls when needed")),
    ),
    ChangelogEntry(
        "3.5.3",
        listOf(),
        listOf(ChangelogLine("5 nouveaux succès : records de 1500 m et 2000 m, 50 lancers effectués, toutes les traînées possédées, et un succès secret 🛸", "5 new achievements: 1500 m and 2000 m records, 50 throws made, every trail owned, and a secret 🛸 achievement")),
    ),
    ChangelogEntry(
        "3.5.2",
        listOf(),
        listOf(ChangelogLine("Correction d'un bug d'affichage pendant la cinématique du volcan : le voile de nuit posait problème en thème sombre. Le mode jour est maintenant forcé le temps de la cinématique, puis ton thème d'origine est restauré", "Fixed a display bug during the volcano cutscene: the night veil caused issues in dark theme. Day mode is now forced for the duration of the cutscene, then your original theme is restored"), ChangelogLine("Ajout d'un bouton \"Mes liens\" en bas à gauche, avec le site (evyverse.vercel.app) et la chaîne WhatsApp du jeu", "Added a \"My links\" button in the bottom-left corner, with the site (evyverse.vercel.app) and the game's WhatsApp channel")),
    ),
    ChangelogEntry(
        "3.4.1",
        listOf(),
        listOf(ChangelogLine("Ajout d'un système de succès (bouton entre Volcans et Plage), avec le % de joueurs actifs ayant débloqué chacun", "Added an achievement system (button between Volcans and Plage), showing the % of active players who unlocked each one"), ChangelogLine("Correction d'un bug d'affichage mobile : l'argent gagné après un lancer était caché derrière le bouton \"← Menu\"", "Fixed a mobile display bug: the money earned after a throw was hidden behind the \"← Menu\" button"), ChangelogLine("La Trousse Pièce peut maintenant tomber sur un multiplicateur x10 (0,1% de chance) ; le x5 passe de 1% à 0,9%", "The Coin Pencil Case can now roll a x10 multiplier (0.1% chance); x5 drops from 1% to 0.9%"), ChangelogLine("Difficulté augmentée sur le QTE en apesanteur : 5 anneaux au lieu de 3, avec une cible de taille aléatoire (petite/moyenne/grande) à chaque round", "Increased difficulty on the zero-G QTE: 5 rings instead of 3, with a randomly sized target (small/medium/large) each round")),
    ),
    ChangelogEntry(
        "3.3.1",
        listOf(),
        listOf(ChangelogLine("Correction d'un bug qui datait : quand la caméra suivait la trousse dans les airs, le décor (voile de nuit, soleil/lune) restait collé à sa position au lieu de suivre, ce qui laissait voir le fond derrière lui", "Fixed a long-standing bug: when the camera followed the pencil case into the air, the scenery (night veil, sun/moon) stayed glued to its old position instead of following, revealing the background behind it")),
    ),
    ChangelogEntry(
        "3.3",
        listOf(),
        listOf(ChangelogLine("Animation du soleil et de la lune restaurée (avait disparu par erreur)", "Sun and moon animation restored (had been dropped by mistake)"), ChangelogLine("La caméra suit maintenant aussi la trousse en hauteur, pas seulement en longueur", "The camera now follows the pencil case vertically too, not just horizontally"), ChangelogLine("Le multiplicateur de la Trousse Pièce est maintenant affiché dans le panneau de résultat", "The Coin Pencil Case's multiplier is now shown in the result panel")),
    ),
    ChangelogEntry(
        "3.2",
        listOf(),
        listOf(ChangelogLine("Chance de bonus de la Trousse Pièce augmentée (10% → 40%)", "Coin Pencil Case bonus chance increased (10% → 40%)"), ChangelogLine("Multiplicateur x2 plus fréquent (20% → 29%) : un bonus déclenché donne maintenant toujours un multiplicateur", "x2 multiplier is now more frequent (20% → 29%): a triggered bonus now always gives a multiplier")),
    ),
    ChangelogEntry(
        "3.1",
        listOf(ChangelogLine("Ajout d'un nouveau monde !", "Added a new world!"), ChangelogLine("Ajout d'un journal des changements", "Added a changelog")),
        listOf(ChangelogLine("Correction des bugs liés au nouveau monde", "Fixed bugs related to the new world"), ChangelogLine("Difficulté augmentée : temps de réaction réduit face aux roches volcaniques", "Increased difficulty: shorter reaction time against volcanic rocks")),
    ),
)
