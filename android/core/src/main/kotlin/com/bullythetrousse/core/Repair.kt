package com.bullythetrousse.core

import kotlin.math.ceil

/**
 * Réparation de la trousse, portage de `repairCost()` et du bouton de la
 * carte "Réparer la trousse" côté web (onglet Améliorations, visible une
 * fois le monde Volcan découvert — les dérapages y sont la seule source de
 * dégâts, voir [Skid]).
 *
 * Coût : 500 $ par tranche de 10 de durabilité manquante, arrondie au
 * supérieur — donc réparer 1 seul point coûte déjà 500 $, exactement comme
 * `Math.ceil((maxDurability() - save.durability) / 10) * 500`.
 */
object Repair {
    const val COST_PER_10 = 500

    fun cost(save: GameSave): Int {
        val missing = SkinStats.maxDurability(save) - save.durability
        if (missing <= 0) return 0
        return ceil(missing / 10.0).toInt() * COST_PER_10
    }

    /** Prix d'un point de durabilité quand on n'a pas de quoi tout réparer. */
    const val PARTIAL_COST_PER_POINT = 50

    sealed interface Result {
        data class Success(val save: GameSave, val cost: Int) : Result
        /** Pas assez pour tout réparer : on répare avec tout l'argent disponible,
         *  à [PARTIAL_COST_PER_POINT] $ le point (`repairPartialDone` côté site). */
        data class Partial(val save: GameSave, val cost: Int, val points: Int) : Result
        /** Déjà intacte : rien à réparer, rien à débiter. */
        data object AlreadyFull : Result
        data object NotEnoughMoney : Result
    }

    fun repair(save: GameSave): Result {
        val price = cost(save)
        if (price == 0) return Result.AlreadyFull
        if (save.money >= price) {
            return Result.Success(
                save.copy(money = save.money - price, durability = SkinStats.maxDurability(save)),
                price,
            )
        }
        val points = save.money / PARTIAL_COST_PER_POINT
        if (points <= 0) return Result.NotEnoughMoney
        val spent = points * PARTIAL_COST_PER_POINT
        return Result.Partial(
            save.copy(
                money = save.money - spent,
                durability = minOf(SkinStats.maxDurability(save), save.durability + points),
            ),
            spent,
            points,
        )
    }
}
