package com.bullythetrousse.app

import com.bullythetrousse.core.BumpMode
import com.bullythetrousse.core.DailyChallenges
import com.bullythetrousse.core.GameSave
import com.bullythetrousse.core.SkinStats
import java.time.LocalDate

/**
 * Porté de `bumpDailyChallenge("spend", cost, "add")` côté web, appelé après
 * tout achat (niveau, skin, traînée, Trousse à Claquettes, billet de bus) :
 * fait progresser le défi "spend" du montant dépensé ; les succès sont
 * ensuite vérifiés par updateSave (MainActivity). Utilisé depuis plusieurs
 * écrans (Boutique, sélecteur de monde sur le Menu), donc factorisé ici
 * plutôt que dupliqué dans chacun.
 */
fun applyPurchase(updated: GameSave, cost: Int, onSaveChange: (GameSave) -> Unit) {
    var s = DailyChallenges.ensure(updated, LocalDate.now().toString(), SkinStats.totalPuissance(updated), SkinStats.totalVitesse(updated))
    s = DailyChallenges.bump(s, "spend", cost.toDouble(), BumpMode.ADD)
    onSaveChange(s)
}
