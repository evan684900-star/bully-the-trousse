package com.bullythetrousse.core

import kotlin.math.roundToInt

/**
 * Résultat des bonus/malus de gains spécifiques au skin équipé, appliqués
 * par-dessus le gain de base ([Economy.moneyEarned]) — portage du bloc
 * "Skin Pièce"/"Trousse Vampire" de onLanded() côté web.
 */
data class SkinEarningsResult(
    val finalEarn: Int,
    val coinMultiplier: Double? = null,
    val hasJackpot: Boolean = false,
    val vampireStolen: Int = 0,
    val vampireStealPct: Double = 0.0,
)

/**
 * Bonus/malus de gains des skins "Trousse Pièce" et "Trousse Vampire",
 * portés depuis onLanded() côté web : le multiplicateur pièce s'applique
 * D'ABORD (sur le gain de base), et le tribut vampire est prélevé APRÈS,
 * sur le gain déjà multiplié — dans cet ordre précis, comme côté web.
 */
object SkinEarnings {
    const val COIN_BONUS_CHANCE = 0.50

    /** `COIN_MULTIPLIERS` côté web : sous-probabilités qui s'appliquent SI le
     *  bonus s'est déjà déclenché (COIN_BONUS_CHANCE), dans cet ordre. */
    val COIN_MULTIPLIERS: List<Pair<Double, Double>> = listOf(
        1.6 to 0.60,
        2.0 to 0.29,
        3.0 to 0.10,
        5.0 to 0.009,
        10.0 to 0.001,
    )

    const val VAMPIRE_STEAL_BASE_PCT = 5.0
    const val VAMPIRE_STEAL_PCT_PER_100M = 1.0
    const val VAMPIRE_STEAL_MAX_PCT = 40.0

    fun apply(baseEarn: Int, skin: Skin, distanceMeters: Double, random: () -> Double = Math::random): SkinEarningsResult {
        var earn = baseEarn
        var coinMultiplier: Double? = null
        var hasJackpot = false

        if (skin.isCoin && random() < COIN_BONUS_CHANCE) {
            val roll = random()
            var acc = 0.0
            for ((value, chance) in COIN_MULTIPLIERS) {
                acc += chance
                if (roll < acc) {
                    coinMultiplier = value
                    break
                }
            }
            coinMultiplier?.let { multiplier ->
                if (multiplier >= 5) hasJackpot = true
                earn = (earn * multiplier).roundToInt()
            }
        }

        var stolen = 0
        var stealPct = 0.0
        if (skin.isVampire) {
            stealPct = minOf(VAMPIRE_STEAL_MAX_PCT, VAMPIRE_STEAL_BASE_PCT + (distanceMeters / 100.0) * VAMPIRE_STEAL_PCT_PER_100M)
            stolen = (earn * stealPct / 100.0).roundToInt()
            earn -= stolen
        }

        return SkinEarningsResult(
            finalEarn = earn,
            coinMultiplier = coinMultiplier,
            hasJackpot = hasJackpot,
            vampireStolen = stolen,
            vampireStealPct = stealPct,
        )
    }
}
