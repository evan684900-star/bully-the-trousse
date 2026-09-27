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

/*
 * Versions anglaises. Le site n'en a pas (il affiche ces textes en français
 * quelle que soit la langue) : c'est un ajout de l'app, pour qu'un joueur en
 * anglais ne tombe pas sur une boutique à moitié en français. Le texte
 * français, lui, reste mot pour mot celui du site.
 */
private val SKIN_LABELS_EN: Map<String, Pair<String, String>> = mapOf(
    "classique" to ("Classic Pencil Case" to "The basic pencil case, always reliable."),
    "doree" to ("Golden Pencil Case" to "+2 Power. Shines bright."),
    "glacee" to ("Frozen Pencil Case" to "+3 Speed. Cool as an ice cube."),
    "feu" to ("Fire Pencil Case" to "+3 Power, +2 Speed. Things are heating up!"),
    "arcenciel" to ("Rainbow Pencil Case" to "+5 Power, +5 Speed. The ultimate legend."),
    "piece" to ("Coin Pencil Case" to "No stat bonus, but a 50% chance on every throw to multiply your score (x1.6, x2, x3, x5... even x10)!"),
    "basket" to ("Sneaker Pencil Case" to "+3 Power, +6 Speed. 20% chance on landing to bounce off its sneakers for a bonus throw (up to 2 bounces in a row, 6% chance for the second); doesn't work in zero gravity."),
    "lunaire" to ("Lunar Pencil Case" to "Simplified zero-G QTE (3 medium rings instead of 5) and +25% money on throws that go through the space sequence. No effect in the normal world or the Volcano."),
    "fer" to ("Iron Pencil Case" to "300 durability instead of 100, but -1 Power and -1 Speed. Indestructible, but not exactly nimble."),
    "claude" to ("Claude Pencil Case" to "No stat bonus, but a helping hand from the AI: the perfect-throw window is 75% wider, so much easier to hit."),
    "fusee" to ("project-pencilcaseXrocket-012" to "No stat bonus, but 50% faster once launched into zero gravity, and never crashes on the Moon: it goes straight through and falls back onto the current world to keep going."),
    "avion" to ("Boeing 747" to "No stat bonus, but 50% faster in the current world; can never take off to space though, even when aiming for the green zone."),
    "fantome" to ("Ghost Pencil Case" to "+8 Speed. Glides through the air without a sound."),
    "vampire" to ("Vampire Pencil Case" to "+5 Power, +6 Speed. Every pact has a price: it steals a % of the money earned on that throw (up to 40%, more with distance). In return, hold 🦇 during the flight to speed up (3s total max, once per throw)."),
)

private val TRAIL_LABELS_EN: Map<String, Pair<String, String>> = mapOf(
    "blanche" to ("White Trail" to "The basic trail, free."),
    "doree" to ("Golden Trail" to "A sparkling golden wake."),
    "glacee" to ("Frozen Trail" to "An icy blue wake."),
    "toxique" to ("Toxic Trail" to "An acid-green wake bubbling away."),
    "feu" to ("Fire Trail" to "A blazing red wake."),
    "sakura" to ("Sakura Trail" to "Cherry petals twirling as they fall."),
    "arcenciel" to ("Rainbow Trail" to "A wake that keeps changing colour."),
    "fantome" to ("Ghost Trail" to "Spectral wisps that ripple and fade away."),
    "foudre" to ("Lightning Trail" to "Lightning bolts crackling around the pencil case."),
    "neon" to ("Neon Trail" to "A cyber wake spitting magenta pixels."),
    "stellaire" to ("Stellar Trail" to "Stardust slowly swirling around."),
    "encre" to ("Ink Trail" to "A thick ribbon of ink that smudges and spreads."),
    "confetti" to ("Confetti Trail" to "A shower of multicoloured confetti twirling around."),
    "aurore" to ("Aurora Trail" to "Great northern-light curtains rippling from green to purple."),
    "trounoir" to ("Black Hole Trail" to "A vortex spiralling the light in behind the pencil case."),
)

/** Nom et description d'un skin dans la langue active. */
@androidx.compose.runtime.Composable
fun skinLabel(id: String): Pair<String, String> {
    val fr = SKIN_LABELS[id] ?: (id to "")
    return if (LocalLang.current == com.bullythetrousse.core.Lang.EN) SKIN_LABELS_EN[id] ?: fr else fr
}

/** Nom et description d'une traînée dans la langue active. */
@androidx.compose.runtime.Composable
fun trailLabel(id: String): Pair<String, String> {
    val fr = TRAIL_LABELS[id] ?: (id to "")
    return if (LocalLang.current == com.bullythetrousse.core.Lang.EN) TRAIL_LABELS_EN[id] ?: fr else fr
}
