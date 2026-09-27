package com.bullythetrousse.app

// FICHIER GÉNÉRÉ depuis TUTORIAL_SLIDES, VOLCANO_TUTORIAL, PLAGE_TUTORIAL et
// VILLE_TUTORIAL (index.html), textes du site mot pour mot dans les deux langues.

/** Une diapo de tutoriel : une icône, un titre et un texte par langue. */
data class TutorialSlide(
    val icon: String,
    val titleFr: String,
    val textFr: String,
    val titleEn: String,
    val textEn: String,
)

/** Les quatre jeux de diapos du site. */
enum class Tutorial(val slides: List<TutorialSlide>) {
    BASICS(
        listOf(
            TutorialSlide(
                "🎒",
                "Bienvenue dans Bully the Trousse !",
                "Le but du jeu : lancer ta trousse le plus loin possible !",
                "Welcome to Bully the Trousse!",
                "The goal: throw your pencil case as far as you can!",
            ),
            TutorialSlide(
                "🤓👆",
                "Comment lancer",
                "Clique 3 fois : une fois pour charger la PUISSANCE, une fois pour verrouiller la PRÉCISION, et une dernière fois pour lancer !",
                "How to throw",
                "Click 3 times: once to charge POWER, once to lock in ACCURACY, and once more to launch!",
            ),
            TutorialSlide(
                "💰",
                "Gagne de l'argent",
                "Plus tu lances loin, plus tu gagnes d'argent. Dépense-le dans la Boutique pour améliorer ta Puissance, ta Vitesse, ou débloquer des skins (d'autres skins arriveront plus tard).",
                "Earn money",
                "The further you throw, the more you earn. Spend it in the Shop on Power, Speed, or new skins (more skins are coming later).",
            ),
            TutorialSlide(
                "🏆",
                "Défie le monde entier (enfin, ceux qui jouent au jeu...)",
                "Bats ton record pour apparaître dans le Classement mondial, visible depuis le menu !",
                "Challenge the whole world (well, whoever plays this)",
                "Beat your record to show up on the global Leaderboard, right from the menu!",
            ),
            TutorialSlide(
                "🔁",
                "Alors partage un max !",
                "Partage le jeu à tes amis pour qu'ils puissent eux aussi battre ton record !",
                "So go share it!",
                "Share the game with your friends so they can try to beat your record too!",
            ),
            TutorialSlide(
                "🤗",
                "Visite mes autres sites !",
                "Va sur evyverse.vercel.app pour découvrir mes autres sites !",
                "Check out my other sites!",
                "Go to evyverse.vercel.app to discover my other projects!",
            ),
        ),
    ),
    VOLCANO(
        listOf(
            TutorialSlide(
                "🌋",
                "Bienvenue dans les Volcans !",
                "Le ciel est rouge, le sol est brûlant et la poussière vole partout. Sélectionne le monde depuis le menu pour y lancer ta trousse.",
                "Welcome to the Volcanoes!",
                "Red sky, burning ground and dust everywhere. Pick the world from the menu to throw your pencil case there.",
            ),
            TutorialSlide(
                "💨",
                "Dérapages incontrôlés",
                "Ici, 1 atterrissage sur 4 part en glissade : la trousse râpe le sol et gagne 5 à 30 mètres... mais perd 10 de durabilité.",
                "Uncontrolled skids",
                "Here, 1 landing out of 4 turns into a skid: the case scrapes the ground and gains 5 to 30 metres... but loses 10 durability.",
            ),
            TutorialSlide(
                "🔧",
                "Durabilité et réparation",
                "La durabilité va de 0 à 100. Dans la boutique, la réparation remet la trousse à neuf : 500 \$ par tranche de 10 de durabilité manquante.",
                "Durability and repairs",
                "Durability runs from 0 to 100. In the shop, a repair makes the case good as new: \$500 per 10 missing durability.",
            ),
            TutorialSlide(
                "🎒",
                "Ta fiche trousse",
                "Clique sur la trousse de l'écran d'accueil pour voir sa durabilité, son nom, ton temps de jeu et ton meilleur score.",
                "Your case sheet",
                "Tap the pencil case on the home screen to see its durability, its name, your playtime and your best score.",
            ),
        ),
    ),
    PLAGE(
        listOf(
            TutorialSlide(
                "🏖️",
                "🏖️ Bienvenue à la Plage !",
                "Tes améliorations, tes skins, tes traînées et ta trousse restent exactement les mêmes qu'ailleurs. Par contre... tu as oublié ton argent en chemin : tu repars de 0 \$. Le classement de la plage, lui, reste séparé de celui du monde normal.",
                "🏖️ Welcome to the Beach!",
                "Your upgrades, skins, trails and pencil case stay exactly the same as elsewhere. But... you forgot your money on the way: you're starting back at \$0. The beach leaderboard, though, stays separate from the normal world's.",
            ),
            TutorialSlide(
                "😈",
                "😈 Sans un sou",
                "Et la mauvaise nouvelle ? Vous n'avez pas assez d'argent pour reprendre le bus et retourner dans le monde normal 😈 Vous devez farmer jusqu'à 250 000 pour un billet de bus dans la boutique ! Bonne chance....",
                "😈 Not a penny to your name",
                "And the bad news? You don't have enough money to take the bus back to the normal world 😈 You'll have to farm up to 250,000 for a bus ticket in the shop! Good luck....",
            ),
            TutorialSlide(
                "⛱️",
                "⛱️ Ce qui traîne sur le sable",
                "Le sable est généré au hasard à chaque lancer : parasols (rebond), serviettes (juste pour le style) et châteaux de sable (fin du lancer) apparaissent chacun avec leur propre chance, mais seulement au-delà de 100 m.",
                "⛱️ What's lying around on the sand",
                "The sand is randomly generated on every throw: parasols (bounce), towels (just for style) and sandcastles (throw over) each show up with their own chance, but only past 100 m.",
            ),
        ),
    ),
    VILLE(
        listOf(
            TutorialSlide(
                "🏙️",
                "Bienvenue en Ville !",
                "Ici, la trousse se déplace à pied. Au clavier : Q/D ou ←/→ pour marcher, Z/Espace pour sauter (maintiens pour sauter plus haut), X pour un dash, E pour parler ou entrer. Sur téléphone, utilise les boutons à l'écran.",
                "Welcome to the City!",
                "Here, the pencil case gets around on foot. On a keyboard: A/D or ←/→ to walk, W/Space to jump (hold to jump higher), X to dash, E to talk or enter. On a phone, use the on-screen buttons.",
            ),
            TutorialSlide(
                "🚸",
                "Traverse prudemment",
                "Au passage piéton, attends que le bonhomme passe au vert : les voitures ne s'arrêtent pas pour une trousse.",
                "Cross carefully",
                "At the crosswalk, wait for the green walk signal: cars don't stop for a pencil case.",
            ),
            TutorialSlide(
                "🏢",
                "La Tour",
                "Au bout de la rue, entre dans la Tour et passe à la réception : l'ascenseur mène au toit, un nouveau terrain de lancer où tout coûte 20 % plus cher... mais rapporte 20 % de plus.",
                "The Tower",
                "At the end of the street, enter the Tower and stop by the reception: the elevator takes you to the rooftop, a new throwing field where everything costs 20% more... but earns 20% more.",
            ),
            TutorialSlide(
                "📌",
                "Les évènements",
                "Coupure de courant, fuite d'eau, pluie, canicule : les évènements tombent au même moment pour tous les joueurs. Le tableau de la réception annonce ceux des 5 prochains jours.",
                "Events",
                "Power outages, water leaks, rain, heatwaves: events hit every player at the same time. The board in the lobby lists those of the next 5 days.",
            ),
            TutorialSlide(
                "💦",
                "Flaques et canicule",
                "Quand il y a des flaques sur le toit, la trousse glisse dessus, puis une barre de visée apparaît : vise bien pour repartir encore plus vite. En pleine canicule, elle doit se reposer 2 minutes tous les 5 lancers.",
                "Puddles and heatwaves",
                "When there are puddles on the roof, the pencil case slides on them, then an aiming bar appears: aim well to take off even faster. During a heatwave, it needs 2 minutes of rest every 5 throws.",
            ),
            TutorialSlide(
                "👕",
                "La Trousserie",
                "Dans la rue, La Trousserie vend les skins au prix de la Ville et des cosmétiques purement décoratifs, à porter sur n'importe quel skin.",
                "La Trousserie",
                "On the street, La Trousserie sells skins at City prices, plus purely decorative cosmetics to wear on any skin.",
            ),
            TutorialSlide(
                "✈️",
                "Et ensuite ?",
                "L'ascenseur propose aussi un mode histoire... à toi de découvrir ce qu'il cache. Pour quitter la Ville, prends un vol au comptoir des départs de l'aéroport.",
                "What next?",
                "The elevator also offers a story mode... up to you to find out what it hides. To leave the City, catch a flight at the airport departures desk.",
            ),
        ),
    ),
}
