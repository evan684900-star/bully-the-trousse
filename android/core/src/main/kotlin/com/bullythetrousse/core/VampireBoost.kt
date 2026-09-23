package com.bullythetrousse.core

import kotlin.math.min
import kotlin.math.pow

/**
 * État du boost 🦇 de la Trousse Vampire pour UN lancer.
 *
 * @param boosting Le bouton est maintenu appuyé en ce moment même.
 * @param secondsLeft Budget de boost restant, décrémenté uniquement pendant
 *   [boosting] : relâcher avant le plafond met le boost en PAUSE, ça ne
 *   consomme pas le reste.
 * @param used Budget épuisé : le bouton disparaît pour le reste du lancer.
 */
data class VampireBoostState(
    val boosting: Boolean = false,
    val secondsLeft: Double = VampireBoost.MAX_SECONDS,
    val used: Boolean = false,
)

/**
 * Boost 🦇 de la Trousse Vampire, porté depuis startVampireBoost() /
 * endVampireBoost() et la section "flying" de gameLoop() côté web.
 *
 * Contrepartie du vol d'argent de la trousse (voir [SkinEarnings]) : maintenir
 * le bouton pendant le vol multiplie vx, jusqu'à [MAX_SECONDS] cumulées et une
 * seule fois par lancer.
 */
object VampireBoost {
    /** Budget total de boost par lancer, en secondes cumulées. */
    const val MAX_SECONDS = 3.0

    /** vx est multiplié par ce facteur à chaque seconde maintenue : x1,65 sur
     *  1s pile, jusqu'à x4,5 environ sur les 3s cumulées. */
    const val GROWTH_PER_SEC = 1.65

    /**
     * Appui sur le bouton. Sans effet si le budget est déjà épuisé ou si la
     * trousse équipée n'est pas la Vampire.
     */
    fun start(state: VampireBoostState, skin: Skin): VampireBoostState =
        if (state.used || !skin.isVampire) state else state.copy(boosting = true)

    /**
     * Relâchement du bouton. La garde sur [VampireBoostState.boosting]
     * reproduit celle du site : sans elle, un évènement de sortie de pointeur
     * reçu sans qu'on ait jamais appuyé consommait l'utilisation du lancer.
     */
    fun end(state: VampireBoostState): VampireBoostState {
        if (!state.boosting) return state
        return state.copy(boosting = false, used = state.secondsLeft <= 0.0)
    }

    /**
     * Consomme [dt] de budget si le boost est maintenu et renvoie le nouvel
     * état accompagné de la vitesse horizontale accélérée.
     *
     * Le budget est plafonné à ce qu'il reste, donc une frame plus longue que
     * [VampireBoostState.secondsLeft] n'accélère que du temps réellement
     * disponible.
     */
    fun step(state: VampireBoostState, dt: Double, vx: Double): Pair<VampireBoostState, Double> {
        if (!state.boosting || state.secondsLeft <= 0.0) return state to vx
        val usedDt = min(dt, state.secondsLeft)
        val boostedVx = vx * GROWTH_PER_SEC.pow(usedDt)
        val remaining = state.secondsLeft - usedDt
        // Budget épuisé : le site rappelle endVampireBoost(), qui marque le
        // boost comme consommé et fait disparaître le bouton.
        val next = if (remaining <= 0.0) {
            state.copy(boosting = false, secondsLeft = 0.0, used = true)
        } else {
            state.copy(secondsLeft = remaining)
        }
        return next to boostedVx
    }

    /** Le bouton 🦇 ne s'affiche que pendant le vol d'une Trousse Vampire dont
     *  le budget n'est pas épuisé. */
    fun isVisible(state: VampireBoostState, skin: Skin, flying: Boolean): Boolean =
        flying && skin.isVampire && !state.used
}
