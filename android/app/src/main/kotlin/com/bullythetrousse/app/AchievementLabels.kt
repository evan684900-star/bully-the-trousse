package com.bullythetrousse.app

/**
 * Les libellés des 47 succès, repris mot pour mot des clés `nameKey`/
 * `descKey` du tableau `ACHIEVEMENTS` d'index.html.
 *
 * Même partage des rôles que [SKIN_LABELS] : `:core` ne porte que
 * l'identifiant, l'émoji et la condition (de la logique, testée), le texte
 * affiché vit ici — comme côté site, où il vient du tableau de traductions
 * et non du moteur de jeu.
 */
val ACHIEVEMENT_LABELS: Map<String, Pair<String, String>> = mapOf(
    "premierLancer" to ("Premier envol" to "Fais un premier lancer."),
    "cent100m" to ("100 mètres" to "Atteins un record de 100 m."),
    "cinq500m" to ("500 mètres" to "Atteins un record de 500 m."),
    "mille1000m" to ("1000 mètres" to "Atteins un record de 1000 m."),
    "mille1500m" to ("1500 mètres" to "Atteins un record de 1500 m."),
    "deuxMille2000m" to ("2000 mètres" to "Atteins un record de 2000 m."),
    "quatreMille4000m" to ("4000 mètres" to "Atteins un record de 4000 m."),
    "dixMille10000m" to ("10 000 mètres" to "Atteins un record de 10 000 m."),
    "lancerParfait" to ("Lancer parfait" to "Réussis un lancer parfait."),
    "volcanDebloque" to ("Éruption maîtrisée" to "Débloque le monde Volcans."),
    "claquettes" to ("Pieds nus dans le sable" to "Achète la Trousse à Claquettes."),
    "plageDebloquee" to ("Sable entre les pages" to "Débloque le monde Plage."),
    "retourDeVacances" to ("Retour de vacances" to "Achète le billet de bus et reviens de la Plage."),
    "plageLancers" to ("Habitué(e) du sable" to "Effectue 100 lancers dans le monde Plage."),
    "plage500m" to ("Loin du bus" to "Atteins un record de 500 m sur la plage."),
    "plage2000m" to ("Jusqu'à l'horizon" to "Atteins un record de 2000 m sur la plage."),
    "plageEconomies" to ("Économies de vacances" to "Gagne 250 000 \$ sur la plage : de quoi payer le bus du retour."),
    "parasols25" to ("Rebondi(e) mais pas vaincu(e)" to "Rebondis 25 fois sur un parasol."),
    "serviettes20" to ("Ramasse-serviettes" to "Atterris 20 fois dans une serviette trouvée sur le sable."),
    "chateaux15" to ("Démolisseur de châteaux" to "Fais s'écrouler 15 châteaux de sable sur la trousse."),
    "trousseCassee" to ("Trousse en miettes" to "Casse complètement ta trousse (durabilité à 0)."),
    "collectionneur" to ("Collectionneur" to "Possède tous les skins de trousse."),
    "riche5000" to ("Fortune faite" to "Accumule 5000 \$."),
    "jackpotX5" to ("Jackpot !" to "Obtiens un multiplicateur x5 (ou plus) avec la Trousse Pièce."),
    "joueurAssidu" to ("Joueur assidu" to "Cumule 1 heure de jeu."),
    "cinquanteLancers" to ("Bras rodé" to "Effectue 50 lancers."),
    "centLancers" to ("J'ai des courbatures" to "Effectue 100 lancers."),
    "cacaCache" to ("💩" to "💩"),
    "toutesTrainees" to ("Sillage complet" to "Possède toutes les traînées."),
    "easterEggEspace" to ("Vers l'infini..." to "???"),
    "qteParfait" to ("Réflexes parfaits" to "Réussis les 5 anneaux du QTE en apesanteur d'affilée."),
    "millionnaire" to ("Millionnaire" to "Cumule 1 000 000 \$ gagnés depuis le début."),
    "cinqCentsLancers" to ("Bras de fer" to "Effectue 500 lancers."),
    "marathonien" to ("Marathonien" to "Cumule 5 heures de jeu."),
    "niveau50" to ("Niveau 50" to "Atteins un total de 50 en Puissance + Vitesse."),
    "defisAccomplis" to ("Défis accomplis" to "Réclame les 3 défis quotidiens le même jour."),
    "grandDonateur" to ("Grand donateur" to "Envoie 1 000 000 \$ en cadeaux depuis le début."),
    "adorateurMusique" to ("Adorateur de la musique" to "Écoute la musique de fond pendant 3h consécutives."),
    "etourdi" to ("Étourdi" to "Oublie ton argent en arrivant à la plage."),
    "vingtMille20000m" to ("20 000 m" to "Atteins un record de 20 000 m."),
    "cinquanteMille50000m" to ("50 000 m" to "Atteins un record de 50 000 m."),
    "multiMillionnaire" to ("Multimillionnaire" to "Gagne 10 000 000 \$ depuis le début."),
    "changeDeTete" to ("Change de tête" to "Choisis une photo de profil."),
    "plage5000" to ("5000 m à la plage" to "Atteins un record de 5000 m sur la plage."),
    "milleLancers" to ("1000 lancers" to "Effectue 1000 lancers au total."),
    "niveau100" to ("Niveau 100" to "Atteins un total de 100 niveaux (Puissance + Vitesse)."),
    "secretTrouve" to ("..." to "Il n'y a rien à voir ici."),
)
