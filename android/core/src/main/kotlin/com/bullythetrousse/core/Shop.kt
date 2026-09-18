package com.bullythetrousse.core

/**
 * Achat des niveaux Puissance/Vitesse, portage des deux gestionnaires de
 * clic quasi-identiques côté web (index.html, boutons boutique) :
 * ```
 * const cost = upgradeCost(save.puissanceLevel);
 * if (save.money >= cost) { save.money -= cost; save.puissanceLevel++; ... }
 * else { /* pas assez d'argent */ }
 * ```
 * `bumpDailyChallenge("spend", cost, "add")` n'est volontairement pas porté
 * ici : les défis quotidiens ne sont pas encore branchés côté Android (voir
 * android/README.md).
 */
object Shop {
    sealed interface PurchaseResult {
        data class Success(val save: GameSave, val cost: Int) : PurchaseResult
        data object NotEnoughMoney : PurchaseResult
    }

    fun buyPuissance(save: GameSave): PurchaseResult =
        buyLevel(save, save.puissanceLevel) { s, newLevel -> s.copy(puissanceLevel = newLevel) }

    fun buyVitesse(save: GameSave): PurchaseResult =
        buyLevel(save, save.vitesseLevel) { s, newLevel -> s.copy(vitesseLevel = newLevel) }

    private inline fun buyLevel(save: GameSave, currentLevel: Int, applyLevel: (GameSave, Int) -> GameSave): PurchaseResult {
        val cost = Economy.upgradeCost(currentLevel)
        if (save.money < cost) return PurchaseResult.NotEnoughMoney
        val debited = save.copy(money = save.money - cost)
        return PurchaseResult.Success(applyLevel(debited, currentLevel + 1), cost)
    }
}
