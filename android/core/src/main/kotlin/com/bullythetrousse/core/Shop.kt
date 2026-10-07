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
        /** [levels] : niveaux réellement achetés (moins que demandé si l'argent manque). */
        data class Success(val save: GameSave, val cost: Int, val levels: Int = 1) : PurchaseResult
        data object NotEnoughMoney : PurchaseResult
    }

    /** Boutons +10 / +100 du site : les quantités proposées en plus d'un niveau. */
    val BULK_COUNTS = listOf(10, 100)

    /** Achète [count] niveaux d'un coup (voir [priceOfLevels]) : tous, ou autant
     *  que l'argent le permet ; rien s'il ne couvre même pas le premier. */
    fun buyPuissance(save: GameSave, count: Int = 1): PurchaseResult =
        buyLevels(save, save.puissanceLevel, count) { s, newLevel -> s.copy(puissanceLevel = newLevel) }

    fun buyVitesse(save: GameSave, count: Int = 1): PurchaseResult =
        buyLevels(save, save.vitesseLevel, count) { s, newLevel -> s.copy(vitesseLevel = newLevel) }

    /**
     * Combien de niveaux (sur [count] voulus) l'argent paie à partir de
     * [currentLevel], et pour quel total : chaque niveau coûte le prix de son
     * propre niveau (il monte à chaque achat). On en prend autant que possible
     * quand l'argent manque. Les sommes passent par un Long : cent niveaux de
     * haut niveau dépassent largement un Int.
     */
    fun priceOfLevels(save: GameSave, currentLevel: Int, count: Int): Pair<Int, Int> {
        var levels = 0
        var total = 0L
        while (levels < count) {
            // Monde Ville : tout est 20 % plus cher (voir Ville.price).
            val cost = Ville.price(Economy.upgradeCost(currentLevel + levels), save)
            if (total + cost > save.money) break
            total += cost
            levels++
        }
        return levels to total.toInt()
    }

    private inline fun buyLevels(
        save: GameSave,
        currentLevel: Int,
        count: Int,
        applyLevel: (GameSave, Int) -> GameSave,
    ): PurchaseResult {
        val (levels, cost) = priceOfLevels(save, currentLevel, count.coerceAtLeast(1))
        if (levels == 0) return PurchaseResult.NotEnoughMoney
        val debited = save.copy(money = save.money - cost)
        return PurchaseResult.Success(applyLevel(debited, currentLevel + levels), cost, levels)
    }
}
