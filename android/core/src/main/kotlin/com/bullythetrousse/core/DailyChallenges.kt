package com.bullythetrousse.core

import kotlin.math.pow
import kotlin.math.roundToInt

/** Un palier de difficulté d'un défi (portage des entrées `tiers` de `CHALLENGE_POOL`). */
data class ChallengeTier(val target: Double, val reward: Int)

/** Un type de défi quotidien, portage d'une entrée de `CHALLENGE_POOL` côté web. */
data class ChallengeDefinition(val kind: String, val tiers: List<ChallengeTier>, val requiresVolcan: Boolean = false)

/** Portage exact de `CHALLENGE_POOL` côté web (mêmes kinds, mêmes paliers, même ordre). */
object ChallengePool {
    val ALL: List<ChallengeDefinition> = listOf(
        ChallengeDefinition("throws", listOf(ChallengeTier(5.0, 150), ChallengeTier(10.0, 280), ChallengeTier(15.0, 400))),
        ChallengeDefinition("distance", listOf(ChallengeTier(60.0, 150), ChallengeTier(120.0, 300), ChallengeTier(200.0, 500))),
        ChallengeDefinition("earn", listOf(ChallengeTier(300.0, 150), ChallengeTier(800.0, 350), ChallengeTier(1500.0, 600))),
        ChallengeDefinition("perfect", listOf(ChallengeTier(1.0, 150), ChallengeTier(2.0, 300), ChallengeTier(3.0, 450))),
        ChallengeDefinition("distanceCumul", listOf(ChallengeTier(500.0, 150), ChallengeTier(1200.0, 300), ChallengeTier(2500.0, 500))),
        ChallengeDefinition("gift", listOf(ChallengeTier(100.0, 150), ChallengeTier(500.0, 350), ChallengeTier(1500.0, 600))),
        ChallengeDefinition("qte", listOf(ChallengeTier(1.0, 200), ChallengeTier(2.0, 400), ChallengeTier(3.0, 600))),
        ChallengeDefinition("spend", listOf(ChallengeTier(300.0, 150), ChallengeTier(1000.0, 350), ChallengeTier(3000.0, 600))),
        ChallengeDefinition("volcan", listOf(ChallengeTier(3.0, 200), ChallengeTier(6.0, 400)), requiresVolcan = true),
        ChallengeDefinition("skid", listOf(ChallengeTier(2.0, 200), ChallengeTier(4.0, 400)), requiresVolcan = true),
    )
}

/** Mode d'avancement d'un défi, voir [DailyChallenges.bump] (`bumpDailyChallenge()` côté web). */
enum class BumpMode { ADD, MAX }

/**
 * Génération et progression des défis quotidiens, portage de
 * generateDailyChallenges()/ensureDailyChallenges()/bumpDailyChallenge()/
 * claimDailyChallenge() côté web. Le RNG est injectable (voir [generate]) :
 * on utilise un vrai mélange de Fisher-Yates plutôt que le
 * `sort(() => Math.random() - 0.5)` du web (biaisé, mais qui n'a pas
 * d'incidence observable sur le joueur : ici comme là-bas, c'est juste "3
 * défis choisis au hasard parmi ceux disponibles").
 */
object DailyChallenges {
    /** `Math.pow(1 + p * 0.12, 2) * (1 + v * 0.09) * (1 + (p + v) * UPGRADE_EARN_BONUS_PER_LEVEL)`. */
    fun rewardMultiplier(totalPuissance: Int, totalVitesse: Int): Double {
        val p = totalPuissance
        val v = totalVitesse
        return (1 + p * 0.12).pow(2) * (1 + v * 0.09) * (1 + (p + v) * Economy.EARN_BONUS_PER_LEVEL)
    }

    fun generate(
        volcanUnlocked: Boolean,
        totalPuissance: Int,
        totalVitesse: Int,
        random: () -> Double = Math::random,
    ): List<DailyChallenge> {
        val available = ChallengePool.ALL.filter { !it.requiresVolcan || volcanUnlocked }.toMutableList()
        for (i in available.indices.reversed()) {
            val j = (random() * (i + 1)).toInt().coerceIn(0, i)
            val tmp = available[i]
            available[i] = available[j]
            available[j] = tmp
        }
        val multiplier = rewardMultiplier(totalPuissance, totalVitesse)
        return available.take(3).map { def ->
            val tier = def.tiers[(random() * def.tiers.size).toInt().coerceIn(0, def.tiers.size - 1)]
            DailyChallenge(kind = def.kind, target = tier.target, reward = (tier.reward * multiplier).roundToInt())
        }
    }

    /** `(Re)génère les défis du jour si la date a changé`, portage de ensureDailyChallenges(). */
    fun ensure(
        save: GameSave,
        todayKey: String,
        totalPuissance: Int,
        totalVitesse: Int,
        random: () -> Double = Math::random,
    ): GameSave {
        if (save.dailyChallengeDate == todayKey && save.dailyChallenges.isNotEmpty()) return save
        return save.copy(
            dailyChallengeDate = todayKey,
            dailyChallenges = generate(save.volcanUnlocked, totalPuissance, totalVitesse, random),
        )
    }

    /** Fait progresser tous les défis du `kind` donné, pas encore réclamés. */
    fun bump(save: GameSave, kind: String, amount: Double, mode: BumpMode): GameSave {
        val updated = save.dailyChallenges.map { c ->
            if (c.kind != kind || c.claimed) {
                c
            } else {
                c.copy(progress = if (mode == BumpMode.MAX) maxOf(c.progress, amount) else c.progress + amount)
            }
        }
        return save.copy(dailyChallenges = updated)
    }

    sealed interface ClaimResult {
        data class Success(val save: GameSave, val reward: Int) : ClaimResult
        data object NotReady : ClaimResult
    }

    /** Réclame la récompense du défi d'index [index] : refuse s'il n'existe
     *  pas, est déjà réclamé, ou n'a pas encore atteint sa cible. */
    fun claim(save: GameSave, index: Int): ClaimResult {
        val challenge = save.dailyChallenges.getOrNull(index) ?: return ClaimResult.NotReady
        if (challenge.claimed || challenge.progress < challenge.target) return ClaimResult.NotReady
        val updatedChallenges = save.dailyChallenges.mapIndexed { i, c -> if (i == index) c.copy(claimed = true) else c }
        val updated = save.copy(
            dailyChallenges = updatedChallenges,
            money = save.money + challenge.reward,
            totalMoneyEarned = save.totalMoneyEarned + challenge.reward,
        )
        return ClaimResult.Success(updated, challenge.reward)
    }
}
