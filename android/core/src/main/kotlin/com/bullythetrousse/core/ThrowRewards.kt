package com.bullythetrousse.core

import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Statistiques du jour, portage de `recordDailyEarning()`,
 * `recordDailyDistance()`, `pruneDailyMap()` et `last7DayKeys()` côté site :
 * elles alimentent le graphique « progression hebdo » et la tuile « meilleur
 * lancer de la semaine » du profil.
 *
 * La date du jour est passée en paramètre plutôt que lue ici, pour que les
 * tests ne dépendent pas de l'horloge.
 */
object DailyStats {
    /** Nombre de jours gardés dans les maps quotidiennes. */
    const val DAYS_KEPT = 7

    /** `todayKey()` : "AAAA-MM-JJ", exactement le format de `LocalDate.toString()`. */
    fun key(date: LocalDate): String = date.toString()

    /**
     * `recordDailyEarning(amount)` : un gain compte à la fois dans le total
     * cumulé (jamais décrémenté, contrairement à `money`), dans le compteur
     * de la plage quand on y est — c'est lui qui rapproche du billet de bus —
     * et dans la case du jour. Un gain nul ou négatif ne compte nulle part.
     *
     * N'ajoute PAS l'argent à `money` : côté site, c'est fait à part, juste
     * avant l'appel.
     */
    fun recordEarning(save: GameSave, amount: Int, today: LocalDate): GameSave {
        if (amount <= 0) return save
        val k = key(today)
        val daily = save.dailyEarnings + (k to (save.dailyEarnings[k] ?: 0) + amount)
        return save.copy(
            totalMoneyEarned = save.totalMoneyEarned + amount,
            plageMoneyEarned = if (save.inPlage) save.plageMoneyEarned + amount else save.plageMoneyEarned,
            dailyEarnings = prune(daily, today),
        )
    }

    /** `recordDailyDistance(distanceMeters)` : garde le meilleur lancer du jour. */
    fun recordDistance(save: GameSave, distanceMeters: Double, today: LocalDate): GameSave {
        val k = key(today)
        val best = save.dailyBestDistance[k]
        val daily = if (best == null || distanceMeters > best) save.dailyBestDistance + (k to distanceMeters) else save.dailyBestDistance
        return save.copy(dailyBestDistance = prune(daily, today))
    }

    /**
     * `pruneDailyMap()` : ne garde que les [DAYS_KEPT] derniers jours, pour que
     * la sauvegarde ne grossisse pas indéfiniment. Une clé illisible est
     * jetée, comme le `isNaN(t)` du site.
     */
    fun <V> prune(map: Map<String, V>, today: LocalDate): Map<String, V> {
        val oldestKept = today.minusDays((DAYS_KEPT - 1).toLong())
        return map.filterKeys { k ->
            val date = runCatching { LocalDate.parse(k) }.getOrNull()
            date != null && !date.isBefore(oldestKept)
        }
    }

    /** `last7DayKeys()` : les 7 derniers jours, du plus ancien à aujourd'hui. */
    fun last7DayKeys(today: LocalDate): List<LocalDate> =
        (DAYS_KEPT - 1 downTo 0).map { today.minusDays(it.toLong()) }

    /** Meilleur lancer sur les 7 derniers jours (tuile « meilleur lancer de la semaine »). */
    fun weeklyBest(dailyBest: Map<String, Double>, today: LocalDate): Double =
        last7DayKeys(today).maxOf { dailyBest[key(it)] ?: 0.0 }.coerceAtLeast(0.0)
}

/** Un palier franchi : le nouveau palier atteint (en centaines de mètres) et son bonus. */
data class MilestoneReward(val milestone: Int, val bonus: Int) {
    /** Les mètres du palier, pour le toast « Palier des X m atteint ! ». */
    val meters: Int get() = milestone * 100
}

/**
 * Récompenses de palier, portage du bloc `milestone` de `onLanded()` : 50 $
 * par tranche de 100 m jamais atteinte auparavant, versés une seule fois par
 * palier — même si un lancer (boost spatial) en franchit plusieurs d'un coup,
 * chacun rapporte ses 50 $.
 */
object Milestones {
    const val STEP_METERS = 100.0
    const val BONUS_PER_MILESTONE = 50

    fun reward(save: GameSave, distanceMeters: Double): MilestoneReward? {
        val milestone = floor(distanceMeters / STEP_METERS).toInt()
        if (milestone <= save.milestoneReached) return null
        return MilestoneReward(milestone, BONUS_PER_MILESTONE * (milestone - save.milestoneReached))
    }
}

/**
 * Bonus de la Trousse Lunaire : +25 % sur un atterrissage qui a suivi la
 * séquence spatiale, et seulement celui-là. Appliqué côté site après les
 * bonus d'amélioration et AVANT le multiplicateur de la pièce.
 */
object LunarBonus {
    const val MULTIPLIER = 1.25

    fun apply(earn: Int, skin: Skin, cameFromSpace: Boolean): Int =
        if (skin.isLunar && cameFromSpace) (earn * MULTIPLIER).roundToInt() else earn
}
