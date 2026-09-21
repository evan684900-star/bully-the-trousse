package com.bullythetrousse.app

/**
 * Les libellés de la boutique, repris mot pour mot des tableaux `SKINS` et
 * `TRAILS` d'index.html. `:core` ne porte que les identifiants et les effets
 * (c'est de la logique testée) ; les noms et descriptions affichés sont de
 * l'habillage, donc ils vivent ici — comme côté web, où ils viennent du
 * tableau de traductions et non du moteur de jeu.
 */
val SKIN_LABELS: Map<String, Pair<String, String>> = mapOf(
    "classique" to ("Trousse Classique" to "La trousse de base, fidèle au poste."),
    "doree" to ("Trousse Dorée" to "+2 Puissance. Brille de mille feux."),
    "glacee" to ("Trousse Glacée" to "+3 Vitesse. Fraîche comme un glaçon."),
    "feu" to ("Trousse de Feu" to "+3 Puissance, +2 Vitesse. Ça chauffe !"),
    "arcenciel" to ("Trousse Arc-en-ciel" to "+5 Puissance, +5 Vitesse. La légende ultime."),
    "piece" to ("Trousse Pièce" to "Aucun bonus de stats, mais 50% de chance à chaque lancer de multiplier ton score (x1.6, x2, x3, x5... voire x10) !"),
    "basket" to ("Trousse à Baskets" to "+3 Puissance, +6 Vitesse. 20% de chance à l'atterrissage de rebondir sur ses baskets pour un lancer bonus (jusqu'à 2 rebonds d'affilée, 6% de chance pour le second) ; ne fonctionne pas en apesanteur."),
    "lunaire" to ("Trousse Lunaire" to "QTE en apesanteur simplifié (3 anneaux de taille moyenne au lieu de 5) et +25% d'argent gagné sur les lancers qui passent par la séquence spatiale. Aucun effet dans le monde normal ou au Volcan."),
    "fer" to ("Trousse de Fer" to "300 de durabilité au lieu de 100, mais -1 Puissance et -1 Vitesse. Increvable, mais pas franchement agile."),
    "claude" to ("Trousse Claude" to "Aucun bonus de stats, mais un coup de main de l'IA : la fenêtre du lancer parfait est élargie de 75%, donc bien plus facile à viser."),
    "fusee" to ("projet-trousseXfusée-012" to "Aucun bonus de stats, mais 50% plus rapide une fois propulsée en apesanteur, et ne s'écrase jamais sur la Lune : elle la traverse et retombe directement sur le monde en cours pour continuer sa course."),
    "avion" to ("Boeing 747" to "Aucun bonus de stats, mais 50% plus rapide dans le monde en cours ; ne peut en revanche jamais décoller vers l'espace, même en visant la zone verte."),
    "fantome" to ("Trousse Fantôme" to "+8 Vitesse. Traverse les airs sans faire de bruit."),
    "vampire" to ("Trousse Vampire" to "+5 Puissance, +6 Vitesse. Un pacte se paie : elle vole un % de l'argent gagné à ce lancer (jusqu'à 40%, plus la distance est grande). En échange, maintiens 🦇 pendant le vol pour accélérer (3s cumulées max, une seule fois par lancer)."),
)

val TRAIL_LABELS: Map<String, Pair<String, String>> = mapOf(
    "blanche" to ("Traînée Blanche" to "La traînée de base, offerte."),
    "doree" to ("Traînée Dorée" to "Un sillage doré et scintillant."),
    "glacee" to ("Traînée Glacée" to "Un sillage bleu glacial."),
    "toxique" to ("Traînée Toxique" to "Un sillage vert acide qui bouillonne de bulles."),
    "feu" to ("Traînée de Feu" to "Un sillage rouge ardent."),
    "sakura" to ("Traînée Sakura" to "Des pétales de cerisier qui tournoient en tombant."),
    "arcenciel" to ("Traînée Arc-en-ciel" to "Un sillage qui change de couleur en continu."),
    "fantome" to ("Traînée Fantôme" to "Des volutes spectrales qui ondulent et s'évaporent."),
    "foudre" to ("Traînée Foudre" to "Des éclairs qui claquent autour de la trousse."),
    "neon" to ("Traînée Néon" to "Un sillage cyber qui crache des pixels magenta."),
    "stellaire" to ("Traînée Stellaire" to "Une poussière d'étoiles qui tournent lentement."),
    "encre" to ("Traînée d'Encre" to "Un ruban d'encre épais qui bave et diffuse en taches."),
    "confetti" to ("Traînée Confettis" to "Une pluie de confettis multicolores qui tournoient."),
    "aurore" to ("Traînée Aurore" to "De grands rideaux boréaux qui ondulent du vert au violet."),
    "trounoir" to ("Traînée Trou Noir" to "Un vortex qui aspire la lumière en spirale derrière la trousse."),
)
