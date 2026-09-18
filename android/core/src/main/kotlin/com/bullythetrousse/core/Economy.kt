package com.bullythetrousse.core

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Économie du jeu : argent gagné par lancer et coût des améliorations,
 * portés depuis onLanded() et upgradeCost() côté web (index.html).
 *
 * Ce module ne couvre volontairement que la base commune à tous les
 * lancers (distance, lancer parfait, bonus des niveaux) : les modificateurs
 * spécifiques à un skin (Trousse Pièce, Trousse Vampire, Trousse Lunaire...)
 * seront portés plus tard, une fois le système de skins lui-même en place.
 */
object Economy {
    /** +3% d'argent gagné par niveau cumulé (Puissance + Vitesse). */
    const val EARN_BONUS_PER_LEVEL = 0.03

    /** Bonus sur un lancer parfait (fenêtre de précision, voir ThrowResult.isPerfect). */
    const val PERFECT_THROW_BONUS = 1.3

    /** Au-delà de ce niveau, le coût d'amélioration passe d'exponentiel à linéaire. */
    const val UPGRADE_COST_CAP_LEVEL = 25

    /** Palier de base de la courbe exponentielle (avant le niveau plafond). */
    const val UPGRADE_COST_BASE = 25.0
    const val UPGRADE_COST_GROWTH = 1.40

    /** Incrément fixe par niveau une fois le plafond dépassé. */
    const val UPGRADE_COST_LINEAR_STEP = 15000

    /**
     * Argent gagné pour un lancer, avant les modificateurs spécifiques aux
     * skins (voir la note de classe ci-dessus).
     */
    fun moneyEarned(distanceMeters: Double, isPerfect: Boolean, totalLevels: Int): Int {
        var earn = maxOf(1, (distanceMeters * 2).roundToInt())
        if (isPerfect) earn = (earn * PERFECT_THROW_BONUS).roundToInt()
        earn = (earn * (1 + totalLevels * EARN_BONUS_PER_LEVEL)).roundToInt()
        return earn
    }

    /**
     * Coût pour acheter le niveau donné (0-indexé, comme côté web) :
     * croissance exponentielle jusqu'à UPGRADE_COST_CAP_LEVEL, puis un
     * incrément fixe par niveau au-delà (voir le commentaire détaillé de
     * upgradeCost() côté web pour l'historique du rééquilibrage).
     */
    fun upgradeCost(level: Int): Int {
        if (level <= UPGRADE_COST_CAP_LEVEL) {
            return (UPGRADE_COST_BASE * UPGRADE_COST_GROWTH.pow(level)).roundToInt()
        }
        val capCost = (UPGRADE_COST_BASE * UPGRADE_COST_GROWTH.pow(UPGRADE_COST_CAP_LEVEL)).roundToInt()
        return capCost + (level - UPGRADE_COST_CAP_LEVEL) * UPGRADE_COST_LINEAR_STEP
    }
}
